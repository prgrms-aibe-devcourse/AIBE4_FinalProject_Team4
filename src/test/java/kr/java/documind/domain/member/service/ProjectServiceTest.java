package kr.java.documind.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.auth.exception.ProjectNotFoundException;
import kr.java.documind.domain.auth.model.entity.Project;
import kr.java.documind.domain.auth.model.entity.ProjectApiKey;
import kr.java.documind.domain.auth.model.enums.ApiKeyType;
import kr.java.documind.domain.auth.model.enums.OAuthProvider;
import kr.java.documind.domain.auth.model.enums.ProjectRole;
import kr.java.documind.domain.auth.model.repository.ProjectApiKeyRepository;
import kr.java.documind.domain.auth.model.repository.ProjectRepository;
import kr.java.documind.domain.member.model.dto.ApiKeyIssueResponse;
import kr.java.documind.domain.member.model.dto.ProjectCreateResponse;
import kr.java.documind.domain.member.model.entity.Company;
import kr.java.documind.domain.member.model.entity.Member;
import kr.java.documind.domain.member.model.entity.ProjectMember;
import kr.java.documind.domain.member.model.enums.AccountStatus;
import kr.java.documind.domain.member.model.enums.ApiKeyStatus;
import kr.java.documind.domain.member.model.repository.ProjectMemberRepository;
import kr.java.documind.global.exception.ForbiddenException;
import kr.java.documind.global.storage.FileStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectService 단위 테스트")
class ProjectServiceTest {

    @InjectMocks private ProjectService projectService;

    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private ProjectApiKeyRepository projectApiKeyRepository;
    @Mock private MemberService memberService;
    @Mock private FileStore fileStore;
    @Mock private PlatformTransactionManager txManager;
    @Mock private TransactionStatus txStatus;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(projectService, "hmacSecret", "test-hmac-secret-32bytes!!");
        // TransactionTemplate 내부에서 txManager.getTransaction()이 호출되므로 lenient 설정
        lenient().when(txManager.getTransaction(any())).thenReturn(txStatus);
    }

    // ── 공통 픽스처 헬퍼 ─────────────────────────────────────────────────────

    private Member createMemberWithApprovedCompany(
            kr.java.documind.domain.auth.model.enums.GlobalRole role) {
        Member member =
                Member.createByOAuth(
                        "user@example.com",
                        "User",
                        "UserNick",
                        OAuthProvider.GOOGLE,
                        "google-user",
                        role);
        Company company = Company.create("TestCo");
        company.approve();
        ReflectionTestUtils.setField(company, "id", 1L);
        member.assignCompany(company);
        return member;
    }

    private Member createMemberWithPendingCompany() {
        Member member =
                Member.createByOAuth(
                        "ceo@example.com",
                        "CEO",
                        "CeoNick",
                        OAuthProvider.GOOGLE,
                        "google-ceo",
                        kr.java.documind.domain.auth.model.enums.GlobalRole.CEO);
        Company pendingCompany = Company.create("PendingCo");
        ReflectionTestUtils.setField(pendingCompany, "id", 2L);
        member.assignCompany(pendingCompany);
        return member;
    }

    // ── createProject() ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("createProject()")
    class CreateProject {

        @Test
        @DisplayName("기능: APPROVED 회사 보유 CEO → 프로젝트 생성 및 MANAGER 자동 등록")
        void createProject_APPROVED회사보유_프로젝트생성성공() {
            // Given
            UUID memberId = UUID.randomUUID();
            Member member =
                    createMemberWithApprovedCompany(
                            kr.java.documind.domain.auth.model.enums.GlobalRole.CEO);
            given(memberService.getMemberWithCompany(memberId)).willReturn(member);

            // When
            ProjectCreateResponse result = projectService.createProject(memberId, "NewProject");

            // Then
            assertThat(result.publicId()).isNotNull().isNotBlank();
            then(projectRepository).should().saveAndFlush(any(Project.class));
            then(projectMemberRepository).should().save(any(ProjectMember.class));
        }

        @Test
        @DisplayName("예외: PENDING 회사 보유 → ForbiddenException 발생")
        void createProject_PENDING회사보유_ForbiddenException발생() {
            // Given
            UUID memberId = UUID.randomUUID();
            Member member = createMemberWithPendingCompany();
            given(memberService.getMemberWithCompany(memberId)).willReturn(member);

            // When & Then
            assertThatThrownBy(() -> projectService.createProject(memberId, "NewProject"))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("승인된 회사가 있어야 프로젝트를 생성할 수 있습니다");
        }

        @Test
        @DisplayName("예외: 소속 회사 없음 → ForbiddenException 발생")
        void createProject_소속회사없음_ForbiddenException발생() {
            // Given
            UUID memberId = UUID.randomUUID();
            Member member =
                    Member.createByOAuth(
                            "ceo@example.com",
                            "CEO",
                            "CeoNick",
                            OAuthProvider.GOOGLE,
                            "google-ceo",
                            kr.java.documind.domain.auth.model.enums.GlobalRole.CEO);
            given(memberService.getMemberWithCompany(memberId)).willReturn(member);

            // When & Then
            assertThatThrownBy(() -> projectService.createProject(memberId, "NewProject"))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("승인된 회사가 있어야 프로젝트를 생성할 수 있습니다");
        }
    }

    // ── leaveProject() ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("leaveProject()")
    class LeaveProject {

        @Test
        @DisplayName("기능: EMPLOYEE 나가기 요청 → ProjectMember 소프트 딜리트")
        void leaveProject_EMPLOYEE나가기_소프트딜리트() {
            // Given
            String publicId = "proj123";
            UUID memberId = UUID.randomUUID();
            Member member =
                    createMemberWithApprovedCompany(
                            kr.java.documind.domain.auth.model.enums.GlobalRole.EMPLOYEE);
            Company company = member.getCompany();
            Project project = Project.create(publicId, company, "TestProject", null);
            ProjectMember pm = ProjectMember.create(project, member, ProjectRole.MEMBER);

            given(projectRepository.findByPublicId(publicId)).willReturn(Optional.of(project));
            given(memberService.getMemberWithCompany(memberId)).willReturn(member);
            given(projectMemberRepository.findByProjectAndMember(project, member))
                    .willReturn(Optional.of(pm));

            // When
            projectService.leaveProject(publicId, memberId);

            // Then
            assertThat(pm.getStatus()).isEqualTo(AccountStatus.DELETED);
        }

        @Test
        @DisplayName("예외: CEO 나가기 요청 → ForbiddenException 발생 (CEO 보호 규칙)")
        void leaveProject_CEO나가기_ForbiddenException발생() {
            // Given
            String publicId = "proj123";
            UUID memberId = UUID.randomUUID();
            Member ceo =
                    createMemberWithApprovedCompany(
                            kr.java.documind.domain.auth.model.enums.GlobalRole.CEO);
            Company company = ceo.getCompany();
            Project project = Project.create(publicId, company, "TestProject", null);

            given(projectRepository.findByPublicId(publicId)).willReturn(Optional.of(project));
            given(memberService.getMemberWithCompany(memberId)).willReturn(ceo);

            // When & Then
            assertThatThrownBy(() -> projectService.leaveProject(publicId, memberId))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("대표(CEO) 계정은 프로젝트에서 나갈 수 없습니다");
        }

        @Test
        @DisplayName("예외: 존재하지 않는 프로젝트 → ProjectNotFoundException 발생")
        void leaveProject_존재하지않는프로젝트_ProjectNotFoundException발생() {
            // Given
            String publicId = "nonexistent";
            UUID memberId = UUID.randomUUID();
            given(projectRepository.findByPublicId(publicId)).willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> projectService.leaveProject(publicId, memberId))
                    .isInstanceOf(ProjectNotFoundException.class);
        }
    }

    // ── deleteProject() ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteProject()")
    class DeleteProject {

        @Test
        @DisplayName("기능: 프로젝트 삭제 → 프로젝트 소프트 딜리트 및 모든 활성 멤버 소프트 딜리트")
        void deleteProject_성공_프로젝트및멤버소프트딜리트() {
            // Given
            String publicId = "proj123";
            UUID memberId = UUID.randomUUID();
            Member member =
                    createMemberWithApprovedCompany(
                            kr.java.documind.domain.auth.model.enums.GlobalRole.CEO);
            Company company = member.getCompany();
            Project project = Project.create(publicId, company, "TestProject", null);

            Member employee =
                    Member.createByOAuth(
                            "emp@example.com",
                            "Emp",
                            "EmpNick",
                            OAuthProvider.GOOGLE,
                            "g-emp",
                            kr.java.documind.domain.auth.model.enums.GlobalRole.EMPLOYEE);
            ProjectMember pm1 = ProjectMember.create(project, member, ProjectRole.MANAGER);
            ProjectMember pm2 = ProjectMember.create(project, employee, ProjectRole.MEMBER);

            given(projectRepository.findByPublicId(publicId)).willReturn(Optional.of(project));
            given(
                            projectMemberRepository.findAllByProjectAndStatusNot(
                                    project, AccountStatus.DELETED))
                    .willReturn(List.of(pm1, pm2));

            // When
            projectService.deleteProject(publicId, memberId);

            // Then
            assertThat(project.isDeleted()).isTrue();
            assertThat(pm1.getStatus()).isEqualTo(AccountStatus.DELETED);
            assertThat(pm2.getStatus()).isEqualTo(AccountStatus.DELETED);
        }
    }

    // ── issueApiKey() (전체 재발급) ──────────────────────────────────────────

    @Nested
    @DisplayName("issueApiKey() - 전체 재발급")
    class IssueApiKey {

        @Test
        @DisplayName("기능: 전체 API Key 발급 (INGEST, QUERY) -> 각 타입별 기존 키 벌크 폐기 후 새 키 2개 생성")
        void issueApiKey_전체발급_성공() {
            // Given
            String publicId = "proj123";
            UUID memberId = UUID.randomUUID();
            Member member =
                    createMemberWithApprovedCompany(
                            kr.java.documind.domain.auth.model.enums.GlobalRole.CEO);
            Project project = Project.create(publicId, member.getCompany(), "TestProject", null);

            given(projectRepository.findByPublicId(publicId)).willReturn(Optional.of(project));
            given(projectApiKeyRepository.save(any(ProjectApiKey.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            // When
            ApiKeyIssueResponse result = projectService.issueApiKey(publicId, memberId);

            // Then
            // 1. INGEST 키 검증 (dmi_ 접두사)
            assertThat(result.ingestApiKey()).isNotNull();
            assertThat(result.ingestApiKey().plainKey()).startsWith("dmi_");
            assertThat(result.ingestApiKey().maskedKey()).isNotNull();

            // 2. QUERY 키 검증 (dmq_ 접두사)
            assertThat(result.queryApiKey()).isNotNull();
            assertThat(result.queryApiKey().plainKey()).startsWith("dmq_");
            assertThat(result.queryApiKey().maskedKey()).isNotNull();

            // 3. 벌크 폐기 로직(revokeAll)이 각 타입에 맞게 정확히 호출되었는지 검증
            List<ApiKeyStatus> targetStatuses =
                    List.of(ApiKeyStatus.ACTIVE, ApiKeyStatus.SUSPENDED);
            then(projectApiKeyRepository)
                    .should()
                    .revokeAllByProjectAndKeyType(
                            project, ApiKeyType.INGEST, ApiKeyStatus.REVOKED, targetStatuses);
            then(projectApiKeyRepository)
                    .should()
                    .revokeAllByProjectAndKeyType(
                            project, ApiKeyType.QUERY, ApiKeyStatus.REVOKED, targetStatuses);
        }
    }

    // ── reissueApiKey() (특정 타입 재발급) ───────────────────────────────────

    @Nested
    @DisplayName("reissueApiKey() - 특정 타입 개별 재발급")
    class ReissueApiKey {

        @Test
        @DisplayName("기능: 수집(INGEST) API Key 재발급 -> INGEST 키만 새로 생성되고, QUERY 키는 null 반환")
        void reissueApiKey_수집키만_성공() {
            // Given
            String publicId = "proj123";
            UUID memberId = UUID.randomUUID();
            Member member =
                    createMemberWithApprovedCompany(
                            kr.java.documind.domain.auth.model.enums.GlobalRole.CEO);
            Project project = Project.create(publicId, member.getCompany(), "TestProject", null);

            given(projectRepository.findByPublicId(publicId)).willReturn(Optional.of(project));
            given(projectApiKeyRepository.save(any(ProjectApiKey.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            // When
            ApiKeyIssueResponse result =
                    projectService.reissueApiKey(publicId, memberId, ApiKeyType.INGEST);

            // Then
            // 1. INGEST 키는 생성되어야 함
            assertThat(result.ingestApiKey()).isNotNull();
            assertThat(result.ingestApiKey().plainKey()).startsWith("dmi_");

            // 2. QUERY 키는 재발급 요청이 아니므로 null 반환
            assertThat(result.queryApiKey()).isNull();

            // 3. INGEST 타입에 대해서만 벌크 폐기 로직이 호출되어야 함
            then(projectApiKeyRepository)
                    .should()
                    .revokeAllByProjectAndKeyType(
                            eq(project), eq(ApiKeyType.INGEST), eq(ApiKeyStatus.REVOKED), any());

            // 4. QUERY 타입의 키는 건드리지 않아야 함
            then(projectApiKeyRepository)
                    .should(never())
                    .revokeAllByProjectAndKeyType(eq(project), eq(ApiKeyType.QUERY), any(), any());
        }

        @Test
        @DisplayName("기능: 조회(QUERY) API Key 재발급 -> QUERY 키만 새로 생성되고, INGEST 키는 null 반환")
        void reissueApiKey_조회키만_성공() {
            // Given
            String publicId = "proj123";
            UUID memberId = UUID.randomUUID();
            Member member =
                    createMemberWithApprovedCompany(
                            kr.java.documind.domain.auth.model.enums.GlobalRole.CEO);
            Project project = Project.create(publicId, member.getCompany(), "TestProject", null);

            given(projectRepository.findByPublicId(publicId)).willReturn(Optional.of(project));
            given(projectApiKeyRepository.save(any(ProjectApiKey.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            // When
            ApiKeyIssueResponse result =
                    projectService.reissueApiKey(publicId, memberId, ApiKeyType.QUERY);

            // Then
            assertThat(result.queryApiKey()).isNotNull();
            assertThat(result.queryApiKey().plainKey()).startsWith("dmq_");
            assertThat(result.ingestApiKey()).isNull(); // 수집 키는 건드리지 않음

            then(projectApiKeyRepository)
                    .should()
                    .revokeAllByProjectAndKeyType(
                            eq(project), eq(ApiKeyType.QUERY), eq(ApiKeyStatus.REVOKED), any());
            then(projectApiKeyRepository)
                    .should(never())
                    .revokeAllByProjectAndKeyType(eq(project), eq(ApiKeyType.INGEST), any(), any());
        }
    }
}
