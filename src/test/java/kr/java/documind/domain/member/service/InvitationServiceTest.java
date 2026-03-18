package kr.java.documind.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.auth.exception.AlreadyProjectMemberException;
import kr.java.documind.domain.auth.exception.ProjectNotFoundException;
import kr.java.documind.domain.auth.model.entity.Project;
import kr.java.documind.domain.auth.model.enums.GlobalRole;
import kr.java.documind.domain.auth.model.enums.OAuthProvider;
import kr.java.documind.domain.auth.model.enums.ProjectRole;
import kr.java.documind.domain.auth.model.repository.ProjectRepository;
import kr.java.documind.domain.member.event.InvitationCreatedEvent;
import kr.java.documind.domain.member.exception.InvalidInviteTokenException;
import kr.java.documind.domain.member.exception.InviteEmailMismatchException;
import kr.java.documind.domain.member.model.dto.InviteSendRequest;
import kr.java.documind.domain.member.model.entity.Company;
import kr.java.documind.domain.member.model.entity.Invitation;
import kr.java.documind.domain.member.model.entity.Member;
import kr.java.documind.domain.member.model.entity.ProjectMember;
import kr.java.documind.domain.member.model.enums.AccountStatus;
import kr.java.documind.domain.member.model.enums.InvitationStatus;
import kr.java.documind.domain.member.model.repository.InvitationRepository;
import kr.java.documind.domain.member.model.repository.ProjectMemberRepository;
import kr.java.documind.global.exception.BadRequestException;
import kr.java.documind.global.exception.ForbiddenException;
import kr.java.documind.global.util.HmacApiKeyUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("InvitationService 단위 테스트")
class InvitationServiceTest {

    @InjectMocks private InvitationService invitationService;

    @Mock private InvitationRepository invitationRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private MemberService memberService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ApplicationEventPublisher eventPublisher;

    @SuppressWarnings("unchecked")
    @Mock
    private ValueOperations<String, String> valueOperations;

    private static final String HMAC_SECRET = "test-invite-secret-32chars-long!!";
    private static final long EXPIRATION_HOURS = 24L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(invitationService, "hmacSecret", HMAC_SECRET);
        ReflectionTestUtils.setField(invitationService, "expirationHours", EXPIRATION_HOURS);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ── 공통 픽스처 헬퍼 ─────────────────────────────────────────────────────

    private Member createMember(String email, GlobalRole role) {
        return Member.createByOAuth(
                email, "User", "UserNick", OAuthProvider.GOOGLE, "g-" + email, role);
    }

    private Company createApprovedCompany(long id) {
        Company company = Company.create("TestCo-" + id);
        company.approve();
        ReflectionTestUtils.setField(company, "id", id);
        return company;
    }

    private Project createProject(String publicId, Company company) {
        return Project.create(publicId, company, "TestProject", null);
    }

    /**
     * 초대 토큰 흐름 셋업: rawToken → tokenHash → Redis key → invitationId Redis에서 invitationId를 반환하도록 스텁
     * 설정
     */
    private void stubRedisForToken(String rawToken, UUID invitationId) {
        String tokenHash = HmacApiKeyUtil.computeHmac(rawToken, HMAC_SECRET);
        String redisKey = "invite:" + tokenHash;
        given(valueOperations.get(redisKey)).willReturn(invitationId.toString());
    }

    // ── sendInvitation() ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("sendInvitation()")
    class SendInvitation {

        @Test
        @DisplayName("기능: 정상 요청 → 초대 생성, Redis 저장, 이벤트 발행")
        void sendInvitation_정상요청_초대생성및이벤트발행() {
            // Given
            String publicId = "proj123";
            String targetEmail = "target@example.com";
            UUID inviterMemberId = UUID.randomUUID();

            Company company = createApprovedCompany(1L);
            Project project = createProject(publicId, company);
            Member inviter = createMember("inviter@example.com", GlobalRole.EMPLOYEE);
            inviter.assignCompany(company);

            given(projectRepository.findByPublicId(publicId)).willReturn(Optional.of(project));
            given(memberService.getMember(inviterMemberId)).willReturn(inviter);
            given(invitationRepository.existsActiveMemberByProjectAndEmail(project, targetEmail))
                    .willReturn(false);
            given(
                            invitationRepository.findAllByProjectAndTargetEmailIgnoreCaseAndStatus(
                                    project, targetEmail, InvitationStatus.PENDING))
                    .willReturn(List.of());
            given(invitationRepository.save(any(Invitation.class)))
                    .willAnswer(
                            invocation -> {
                                Invitation inv = invocation.getArgument(0);
                                ReflectionTestUtils.setField(inv, "id", UUID.randomUUID());
                                return inv;
                            });

            InviteSendRequest request = new InviteSendRequest(targetEmail, ProjectRole.MEMBER);

            // When
            invitationService.sendInvitation(publicId, inviterMemberId, request);

            // Then
            then(invitationRepository).should().save(any(Invitation.class));
            then(valueOperations).should().set(any(), any(), any());
            then(eventPublisher).should().publishEvent(any(InvitationCreatedEvent.class));
        }

        @Test
        @DisplayName("예외: 자기 자신에게 초대 → BadRequestException 발생")
        void sendInvitation_자기자신초대_BadRequestException발생() {
            // Given
            String publicId = "proj123";
            String selfEmail = "self@example.com";
            UUID inviterMemberId = UUID.randomUUID();

            Company company = createApprovedCompany(1L);
            Project project = createProject(publicId, company);
            Member inviter = createMember(selfEmail, GlobalRole.EMPLOYEE);

            given(projectRepository.findByPublicId(publicId)).willReturn(Optional.of(project));
            given(memberService.getMember(inviterMemberId)).willReturn(inviter);

            InviteSendRequest request = new InviteSendRequest(selfEmail, ProjectRole.MEMBER);

            // When & Then
            assertThatThrownBy(
                            () ->
                                    invitationService.sendInvitation(
                                            publicId, inviterMemberId, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("자신에게 초대를 보낼 수 없습니다");
        }

        @Test
        @DisplayName("예외: 이미 프로젝트 활성 멤버인 경우 → BadRequestException 발생")
        void sendInvitation_이미활성멤버_BadRequestException발생() {
            // Given
            String publicId = "proj123";
            String targetEmail = "member@example.com";
            UUID inviterMemberId = UUID.randomUUID();

            Company company = createApprovedCompany(1L);
            Project project = createProject(publicId, company);
            Member inviter = createMember("inviter@example.com", GlobalRole.EMPLOYEE);

            given(projectRepository.findByPublicId(publicId)).willReturn(Optional.of(project));
            given(memberService.getMember(inviterMemberId)).willReturn(inviter);
            given(invitationRepository.existsActiveMemberByProjectAndEmail(project, targetEmail))
                    .willReturn(true);

            InviteSendRequest request = new InviteSendRequest(targetEmail, ProjectRole.MEMBER);

            // When & Then
            assertThatThrownBy(
                            () ->
                                    invitationService.sendInvitation(
                                            publicId, inviterMemberId, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("이미 해당 프로젝트에 참여 중인 멤버입니다");
        }

        @Test
        @DisplayName("기능: 중복 PENDING 초대 존재 → 기존 초대 REVOKE 후 신규 생성")
        void sendInvitation_중복PENDING초대존재_기존초대REVOKE후신규생성() {
            // Given
            String publicId = "proj123";
            String targetEmail = "target@example.com";
            UUID inviterMemberId = UUID.randomUUID();

            Company company = createApprovedCompany(1L);
            Project project = createProject(publicId, company);
            Member inviter = createMember("inviter@example.com", GlobalRole.EMPLOYEE);
            Invitation existingInvitation =
                    Invitation.create(
                            project,
                            inviter,
                            targetEmail,
                            ProjectRole.MEMBER,
                            OffsetDateTime.now(ZoneOffset.UTC).plusHours(24));

            given(projectRepository.findByPublicId(publicId)).willReturn(Optional.of(project));
            given(memberService.getMember(inviterMemberId)).willReturn(inviter);
            given(invitationRepository.existsActiveMemberByProjectAndEmail(project, targetEmail))
                    .willReturn(false);
            given(
                            invitationRepository.findAllByProjectAndTargetEmailIgnoreCaseAndStatus(
                                    project, targetEmail, InvitationStatus.PENDING))
                    .willReturn(List.of(existingInvitation));
            given(invitationRepository.save(any(Invitation.class)))
                    .willAnswer(
                            invocation -> {
                                Invitation inv = invocation.getArgument(0);
                                ReflectionTestUtils.setField(inv, "id", UUID.randomUUID());
                                return inv;
                            });

            InviteSendRequest request = new InviteSendRequest(targetEmail, ProjectRole.MEMBER);

            // When
            invitationService.sendInvitation(publicId, inviterMemberId, request);

            // Then
            assertThat(existingInvitation.getStatus()).isEqualTo(InvitationStatus.REVOKED);
            then(invitationRepository).should().save(any(Invitation.class));
        }

        @Test
        @DisplayName("예외: 존재하지 않는 프로젝트 → ProjectNotFoundException 발생")
        void sendInvitation_존재하지않는프로젝트_ProjectNotFoundException발생() {
            // Given
            String publicId = "nonexistent";
            UUID inviterMemberId = UUID.randomUUID();
            given(projectRepository.findByPublicId(publicId)).willReturn(Optional.empty());

            InviteSendRequest request =
                    new InviteSendRequest("target@example.com", ProjectRole.MEMBER);

            // When & Then
            assertThatThrownBy(
                            () ->
                                    invitationService.sendInvitation(
                                            publicId, inviterMemberId, request))
                    .isInstanceOf(ProjectNotFoundException.class);
        }
    }

    // ── resolveToken() via acceptInvitation() ────────────────────────────────

    @Nested
    @DisplayName("resolveToken() - acceptInvitation() 경유")
    class ResolveToken {

        @Test
        @DisplayName("예외: Redis 장애 → InvalidInviteTokenException 발생")
        void resolveToken_Redis장애_InvalidInviteTokenException발생() {
            // Given
            String rawToken = "any-raw-token";
            UUID memberId = UUID.randomUUID();
            given(valueOperations.get(any())).willThrow(new RuntimeException("Redis 연결 실패"));

            // When & Then
            assertThatThrownBy(() -> invitationService.acceptInvitation(rawToken, memberId, false))
                    .isInstanceOf(InvalidInviteTokenException.class)
                    .hasMessageContaining("일시적인 오류");
        }

        @Test
        @DisplayName("예외: Redis에 토큰 없음(TTL 만료) → InvalidInviteTokenException 발생")
        void resolveToken_Redis토큰없음_InvalidInviteTokenException발생() {
            // Given
            String rawToken = "expired-raw-token";
            UUID memberId = UUID.randomUUID();
            given(valueOperations.get(any())).willReturn(null);

            // When & Then
            assertThatThrownBy(() -> invitationService.acceptInvitation(rawToken, memberId, false))
                    .isInstanceOf(InvalidInviteTokenException.class)
                    .hasMessageContaining("초대 링크가 만료되었습니다");
        }

        @Test
        @DisplayName("예외: Redis에는 있으나 DB에 Invitation 없음 → InvalidInviteTokenException 발생")
        void resolveToken_DB에초대없음_InvalidInviteTokenException발생() {
            // Given
            String rawToken = "valid-raw-token";
            UUID memberId = UUID.randomUUID();
            UUID invitationId = UUID.randomUUID();
            stubRedisForToken(rawToken, invitationId);
            given(invitationRepository.findById(invitationId)).willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> invitationService.acceptInvitation(rawToken, memberId, false))
                    .isInstanceOf(InvalidInviteTokenException.class)
                    .hasMessageContaining("유효하지 않은 초대 링크입니다");
        }

        @Test
        @DisplayName("예외: PENDING 상태이나 시간 만료 → expire() 호출 후 InvalidInviteTokenException 발생")
        void resolveToken_PENDING이나시간만료_expire호출후예외발생() {
            // Given
            String rawToken = "time-expired-token";
            UUID memberId = UUID.randomUUID();
            UUID invitationId = UUID.randomUUID();

            Company company = createApprovedCompany(1L);
            Project project = createProject("proj123", company);
            Member inviter = createMember("inviter@example.com", GlobalRole.EMPLOYEE);
            // expiresAt을 과거로 설정 → isExpired() = true
            Invitation invitation =
                    Invitation.create(
                            project,
                            inviter,
                            "target@example.com",
                            ProjectRole.MEMBER,
                            OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
            ReflectionTestUtils.setField(invitation, "id", invitationId);

            stubRedisForToken(rawToken, invitationId);
            given(invitationRepository.findById(invitationId)).willReturn(Optional.of(invitation));

            // When & Then
            assertThatThrownBy(() -> invitationService.acceptInvitation(rawToken, memberId, false))
                    .isInstanceOf(InvalidInviteTokenException.class)
                    .hasMessageContaining("초대 링크가 만료되었습니다");

            // 만료 상태 전환 검증
            assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.EXPIRED);
        }
    }

    // ── acceptInvitation() ───────────────────────────────────────────────────

    @Nested
    @DisplayName("acceptInvitation()")
    class AcceptInvitation {

        /**
         * PENDING 상태의 유효한 초대를 위한 셋업 헬퍼
         *
         * @return (rawToken, invitationId, project, inviter) 일관된 픽스처
         */
        private AcceptFixture buildPendingInvitation(
                String rawToken, String targetEmail, Company company, ProjectRole targetRole) {
            String publicId = "proj123";
            Project project = createProject(publicId, company);
            Member inviter = createMember("inviter@example.com", GlobalRole.EMPLOYEE);
            Invitation invitation =
                    Invitation.create(
                            project,
                            inviter,
                            targetEmail,
                            targetRole,
                            OffsetDateTime.now(ZoneOffset.UTC).plusHours(24));
            UUID invitationId = UUID.randomUUID();
            ReflectionTestUtils.setField(invitation, "id", invitationId);

            stubRedisForToken(rawToken, invitationId);
            given(invitationRepository.findById(invitationId)).willReturn(Optional.of(invitation));

            given(projectRepository.findByPublicIdWithCompany(publicId))
                    .willReturn(Optional.of(project));

            return new AcceptFixture(project, invitation, invitationId);
        }

        private record AcceptFixture(Project project, Invitation invitation, UUID invitationId) {}

        @Test
        @DisplayName("기능: 이메일 일치 + 신규 멤버 → ProjectMember 생성 및 초대 사용 처리")
        void acceptInvitation_신규멤버_ProjectMember생성() {
            // Given
            String rawToken = "valid-raw-token";
            String targetEmail = "target@example.com";
            UUID memberId = UUID.randomUUID();

            Company company = createApprovedCompany(1L);
            AcceptFixture fixture =
                    buildPendingInvitation(rawToken, targetEmail, company, ProjectRole.MEMBER);
            Project project = fixture.project();
            Invitation invitation = fixture.invitation();

            Member member = createMember(targetEmail, GlobalRole.EMPLOYEE);
            member.assignCompany(company); // 같은 회사 → checkDifferentCompany = false

            given(memberService.getMemberWithCompany(memberId)).willReturn(member);
            given(projectMemberRepository.findByProjectAndMember(project, member))
                    .willReturn(Optional.empty()); // 기존 멤버십 없음
            given(projectMemberRepository.save(any(ProjectMember.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            // When
            String resultPublicId = invitationService.acceptInvitation(rawToken, memberId, false);

            // Then
            assertThat(resultPublicId).isEqualTo(project.getPublicId());
            assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.USED);
            then(projectMemberRepository).should().save(any(ProjectMember.class));
        }

        @Test
        @DisplayName("기능: DELETED 상태 기존 멤버십 → activate() + changeRole() 재활성화")
        void acceptInvitation_삭제된기존멤버십_재활성화() {
            // Given
            String rawToken = "valid-raw-token";
            String targetEmail = "target@example.com";
            UUID memberId = UUID.randomUUID();

            Company company = createApprovedCompany(1L);
            AcceptFixture fixture =
                    buildPendingInvitation(rawToken, targetEmail, company, ProjectRole.MANAGER);
            Project project = fixture.project();

            Member member = createMember(targetEmail, GlobalRole.EMPLOYEE);
            member.assignCompany(company);

            // 기존 DELETED 멤버십 (이전에 나간 멤버)
            ProjectMember deletedPm = ProjectMember.create(project, member, ProjectRole.MEMBER);
            deletedPm.softDelete();

            given(memberService.getMemberWithCompany(memberId)).willReturn(member);
            given(projectMemberRepository.findByProjectAndMember(project, member))
                    .willReturn(Optional.of(deletedPm));

            // When
            invitationService.acceptInvitation(rawToken, memberId, false);

            // Then
            assertThat(deletedPm.getStatus()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(deletedPm.getProjectRole()).isEqualTo(ProjectRole.MANAGER);
            then(projectMemberRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("예외: 이메일 불일치 → InviteEmailMismatchException 발생")
        void acceptInvitation_이메일불일치_InviteEmailMismatchException발생() {
            // Given
            String rawToken = "valid-raw-token";
            String targetEmail = "target@example.com";
            UUID memberId = UUID.randomUUID();

            Company company = createApprovedCompany(1L);
            buildPendingInvitation(rawToken, targetEmail, company, ProjectRole.MEMBER);

            // 이메일이 다른 멤버
            Member member = createMember("different@example.com", GlobalRole.EMPLOYEE);
            member.assignCompany(company);
            given(memberService.getMemberWithCompany(memberId)).willReturn(member);

            // When & Then
            assertThatThrownBy(() -> invitationService.acceptInvitation(rawToken, memberId, false))
                    .isInstanceOf(InviteEmailMismatchException.class)
                    .hasMessageContaining("이 초대 링크를 사용할 권한이 없습니다");
        }

        @Test
        @DisplayName("예외: 다른 회사 소속 CEO → ForbiddenException 발생 (CEO 보호 규칙)")
        void acceptInvitation_다른회사CEO_ForbiddenException발생() {
            // Given
            String rawToken = "valid-raw-token";
            String targetEmail = "ceo@example.com";
            UUID memberId = UUID.randomUUID();

            Company projectCompany = createApprovedCompany(1L);
            AcceptFixture fixture =
                    buildPendingInvitation(
                            rawToken, targetEmail, projectCompany, ProjectRole.MEMBER);
            Project project = fixture.project();

            // CEO가 다른 회사에 소속
            Company memberCompany = createApprovedCompany(2L);
            Member ceomember = createMember(targetEmail, GlobalRole.CEO);
            ceomember.assignCompany(memberCompany);

            given(memberService.getMemberWithCompany(memberId)).willReturn(ceomember);

            // When & Then
            assertThatThrownBy(() -> invitationService.acceptInvitation(rawToken, memberId, false))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("CEO는 현재 회사를 탈퇴하고 다른 프로젝트에 참여할 수 없습니다");
        }

        @Test
        @DisplayName("예외: 다른 회사 소속 + forceLeave=false → BadRequestException 발생")
        void acceptInvitation_다른회사소속forceLeave미동의_BadRequestException발생() {
            // Given
            String rawToken = "valid-raw-token";
            String targetEmail = "emp@example.com";
            UUID memberId = UUID.randomUUID();

            Company projectCompany = createApprovedCompany(1L);
            buildPendingInvitation(rawToken, targetEmail, projectCompany, ProjectRole.MEMBER);

            // EMPLOYEE가 다른 회사에 소속, forceLeave=false
            Company memberCompany = createApprovedCompany(2L);
            Member employee = createMember(targetEmail, GlobalRole.EMPLOYEE);
            employee.assignCompany(memberCompany);

            given(memberService.getMemberWithCompany(memberId)).willReturn(employee);

            // When & Then
            assertThatThrownBy(() -> invitationService.acceptInvitation(rawToken, memberId, false))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("다른 회사에 소속되어 있습니다");
        }

        @Test
        @DisplayName("기능: 다른 회사 소속 + forceLeave=true → 회사 전환 후 프로젝트 참여")
        void acceptInvitation_다른회사소속forceLeave동의_회사전환후참여() {
            // Given
            String rawToken = "valid-raw-token";
            String targetEmail = "emp@example.com";
            UUID memberId = UUID.randomUUID();

            Company projectCompany = createApprovedCompany(1L);
            AcceptFixture fixture =
                    buildPendingInvitation(
                            rawToken, targetEmail, projectCompany, ProjectRole.MEMBER);
            Project project = fixture.project();

            Company memberCompany = createApprovedCompany(2L);
            Member employee = createMember(targetEmail, GlobalRole.EMPLOYEE);
            employee.assignCompany(memberCompany);

            given(memberService.getMemberWithCompany(memberId)).willReturn(employee);
            given(projectMemberRepository.findByProjectAndMember(project, employee))
                    .willReturn(Optional.empty());
            given(projectMemberRepository.save(any(ProjectMember.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            // When
            String resultPublicId = invitationService.acceptInvitation(rawToken, memberId, true);

            // Then
            assertThat(resultPublicId).isEqualTo(project.getPublicId());
            // 회사가 projectCompany로 전환됨
            assertThat(employee.getCompany().getId()).isEqualTo(projectCompany.getId());
        }
    }

    // ── getInviteViewData() ──────────────────────────────────────────────────

    @Nested
    @DisplayName("getInviteViewData()")
    class GetInviteViewData {

        @Test
        @DisplayName("예외: 이미 활성 프로젝트 멤버 → AlreadyProjectMemberException 발생")
        void getInviteViewData_이미활성멤버_AlreadyProjectMemberException발생() {
            // Given
            String rawToken = "valid-raw-token";
            String targetEmail = "target@example.com";
            UUID memberId = UUID.randomUUID();
            UUID invitationId = UUID.randomUUID();

            Company company = createApprovedCompany(1L);
            Project project = createProject("proj123", company);
            Member inviter = createMember("inviter@example.com", GlobalRole.EMPLOYEE);
            Invitation invitation =
                    Invitation.create(
                            project,
                            inviter,
                            targetEmail,
                            ProjectRole.MEMBER,
                            OffsetDateTime.now(ZoneOffset.UTC).plusHours(24));
            ReflectionTestUtils.setField(invitation, "id", invitationId);

            Member member = createMember(targetEmail, GlobalRole.EMPLOYEE);
            member.assignCompany(company);

            ProjectMember activePm = ProjectMember.create(project, member, ProjectRole.MEMBER);

            stubRedisForToken(rawToken, invitationId);
            given(invitationRepository.findById(invitationId)).willReturn(Optional.of(invitation));

            given(memberService.getMemberWithCompany(memberId)).willReturn(member);
            given(projectMemberRepository.findByProjectAndMember(project, member))
                    .willReturn(Optional.of(activePm)); // 이미 ACTIVE 멤버

            // When & Then
            assertThatThrownBy(() -> invitationService.getInviteViewData(rawToken, memberId))
                    .isInstanceOf(AlreadyProjectMemberException.class)
                    .hasMessageContaining("이미 해당 프로젝트의 멤버입니다");
        }
    }
}
