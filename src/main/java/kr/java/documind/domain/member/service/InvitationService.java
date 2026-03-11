package kr.java.documind.domain.member.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.auth.exception.AlreadyProjectMemberException;
import kr.java.documind.domain.auth.exception.ProjectNotFoundException;
import kr.java.documind.domain.auth.model.entity.Project;
import kr.java.documind.domain.auth.model.repository.ProjectRepository;
import kr.java.documind.domain.member.event.InvitationCreatedEvent;
import kr.java.documind.domain.member.exception.InvalidInviteTokenException;
import kr.java.documind.domain.member.exception.InviteEmailMismatchException;
import kr.java.documind.domain.member.model.dto.InviteSendRequest;
import kr.java.documind.domain.member.model.dto.InviteViewData;
import kr.java.documind.domain.member.model.entity.Company;
import kr.java.documind.domain.member.model.entity.Invitation;
import kr.java.documind.domain.member.model.entity.Member;
import kr.java.documind.domain.member.model.entity.ProjectMember;
import kr.java.documind.domain.member.model.enums.InvitationStatus;
import kr.java.documind.domain.member.model.repository.InvitationRepository;
import kr.java.documind.domain.member.model.repository.ProjectMemberRepository;
import kr.java.documind.global.exception.BadRequestException;
import kr.java.documind.global.exception.ForbiddenException;
import kr.java.documind.global.util.HmacApiKeyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class InvitationService {

    private static final String INVITE_KEY_PREFIX = "invite:";

    private final InvitationRepository invitationRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final MemberService memberService;
    private final StringRedisTemplate redisTemplate;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.invitation.hmac-secret}")
    private String hmacSecret;

    @Value("${app.invitation.expiration-hours}")
    private long expirationHours;

    @Transactional
    public void sendInvitation(String publicId, UUID inviterMemberId, InviteSendRequest request) {

        Project project =
                projectRepository
                        .findByPublicId(publicId)
                        .orElseThrow(ProjectNotFoundException::new);

        Member inviter = memberService.getMember(inviterMemberId);

        validateInvitationRequest(inviter, project, request.targetEmail());

        invitationRepository
                .findAllByProjectAndTargetEmailIgnoreCaseAndStatus(
                        project, request.targetEmail(), InvitationStatus.PENDING)
                .forEach(Invitation::revoke);

        String rawToken = HmacApiKeyUtil.generatePlainKey();
        String tokenHash = HmacApiKeyUtil.computeHmac(rawToken, hmacSecret);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusHours(expirationHours);

        Invitation invitation =
                Invitation.create(
                        project, inviter, request.targetEmail(), request.targetRole(), expiresAt);
        invitationRepository.save(invitation);

        saveTokenToRedis(tokenHash, invitation.getId(), now, expiresAt);

        eventPublisher.publishEvent(
                new InvitationCreatedEvent(
                        invitation.getId(),
                        inviter.getName(),
                        project.getName(),
                        request.targetEmail(),
                        rawToken,
                        expiresAt));

        log.info(
                "[InvitationService] 초대 생성: projectId={} inviterMemberId={} targetEmail={} role={}",
                project.getId(),
                inviterMemberId,
                request.targetEmail(),
                request.targetRole());
    }

    @Transactional
    public InviteViewData getInviteViewData(String rawToken, UUID memberId) {
        InviteResolution resolution = resolveToken(rawToken);
        Invitation invitation = resolution.invitation();
        Project project = invitation.getProject();

        validateProjectNotDeleted(project);

        Member member = memberService.getMemberWithCompany(memberId);
        validateMemberEmailMatch(member, invitation.getTargetEmail());
        validateNotAlreadyMember(project, member);

        boolean hasDifferentCompany = checkDifferentCompany(member, project);

        return new InviteViewData(
                rawToken,
                project.getName(),
                project.getPublicId(),
                invitation.getInviter().getName(),
                invitation.getTargetRole(),
                hasDifferentCompany,
                hasDifferentCompany && member.isCeo(),
                hasDifferentCompany ? member.getCompany().getName() : null,
                invitation.getExpiresAt());
    }

    @Transactional
    public String acceptInvitation(String rawToken, UUID memberId, boolean forceLeaveCompany) {
        log.info(
                "[InvitationService] acceptInvitation 호출 시작: tokenHashPrefix={}..., memberId={}",
                rawToken.substring(0, Math.min(5, rawToken.length())),
                memberId);

        InviteResolution resolution = resolveToken(rawToken);
        Invitation invitation = resolution.invitation();
        String tokenHash = resolution.tokenHash();

        String publicId = invitation.getProject().getPublicId();
        Project project =
                projectRepository
                        .findByPublicIdWithCompany(publicId)
                        .orElseThrow(ProjectNotFoundException::new);

        validateProjectNotDeleted(project);

        Member member = memberService.getMemberWithCompany(memberId);
        validateMemberEmailMatch(member, invitation.getTargetEmail());

        if (member.getCompany() == null) {
            member.assignCompany(project.getCompany());
            log.info(
                    "[InvitationService] 회사 할당: memberId={} company={}",
                    member.getId(),
                    project.getCompany().getName());
        } else if (checkDifferentCompany(member, project)) {
            handleCompanySwitch(member, project.getCompany(), forceLeaveCompany);
        }

        joinProject(project, member, invitation);

        invitation.use(member);
        redisTemplate.delete(INVITE_KEY_PREFIX + tokenHash);

        log.info(
                "[InvitationService] 초대 수락 완료: invitationId={} memberId={} projectPublicId={}",
                invitation.getId(),
                memberId,
                project.getPublicId());

        return project.getPublicId();
    }

    private void validateInvitationRequest(Member inviter, Project project, String targetEmail) {
        if (inviter.getEmail() != null && inviter.getEmail().equalsIgnoreCase(targetEmail)) {
            throw new BadRequestException("자신에게 초대를 보낼 수 없습니다.");
        }
        if (invitationRepository.existsActiveMemberByProjectAndEmail(project, targetEmail)) {
            throw new BadRequestException("이미 해당 프로젝트에 참여 중인 멤버입니다.");
        }
    }

    private void saveTokenToRedis(
            String tokenHash, UUID invitationId, LocalDateTime now, LocalDateTime expiresAt) {
        long ttlSeconds = ChronoUnit.SECONDS.between(now, expiresAt);
        redisTemplate
                .opsForValue()
                .set(
                        INVITE_KEY_PREFIX + tokenHash,
                        invitationId.toString(),
                        Duration.ofSeconds(ttlSeconds));
    }

    private void validateProjectNotDeleted(Project project) {
        if (project.isDeleted()) {
            throw new InvalidInviteTokenException("삭제된 프로젝트의 초대 링크입니다.");
        }
    }

    private void validateMemberEmailMatch(Member member, String targetEmail) {
        if (member.isEmailPlaceholder() || !member.getEmail().equalsIgnoreCase(targetEmail)) {
            throw new InviteEmailMismatchException(targetEmail);
        }
    }

    private void validateNotAlreadyMember(Project project, Member member) {
        Optional<ProjectMember> existingPm =
                projectMemberRepository.findByProjectAndMember(project, member);
        if (existingPm.isPresent() && existingPm.get().isActive()) {
            throw new AlreadyProjectMemberException(project.getPublicId());
        }
    }

    private boolean checkDifferentCompany(Member member, Project project) {
        Company memberCompany = member.getCompany();
        Company projectCompany = project.getCompany();
        return memberCompany != null && !memberCompany.getId().equals(projectCompany.getId());
    }

    private void handleCompanySwitch(
            Member member, Company targetCompany, boolean forceLeaveCompany) {
        if (member.isCeo()) {
            throw new ForbiddenException("CEO는 현재 회사를 탈퇴하고 다른 프로젝트에 참여할 수 없습니다.");
        }
        if (!forceLeaveCompany) {
            throw new BadRequestException("다른 회사에 소속되어 있습니다. 현재 회사를 탈퇴 후 참여해 주세요.");
        }
        Company oldCompany = member.getCompany();
        member.assignCompany(targetCompany);
        log.info(
                "[InvitationService] 회사 전환: memberId={} {} → {}",
                member.getId(),
                oldCompany.getName(),
                targetCompany.getName());
    }

    private void joinProject(Project project, Member member, Invitation invitation) {
        Optional<ProjectMember> existingPm =
                projectMemberRepository.findByProjectAndMember(project, member);

        if (existingPm.isPresent()) {
            ProjectMember pm = existingPm.get();
            if (pm.isActive()) {
                return;
            }
            pm.activate();
            pm.changeRole(invitation.getTargetRole());
            log.info("[InvitationService] ProjectMember 재활성화: status={} → ACTIVE", pm.getStatus());
        } else {
            projectMemberRepository.save(
                    ProjectMember.create(project, member, invitation.getTargetRole()));
        }
    }

    private InviteResolution resolveToken(String rawToken) {
        String tokenHash = HmacApiKeyUtil.computeHmac(rawToken, hmacSecret);
        String invitationIdStr;

        try {
            invitationIdStr = redisTemplate.opsForValue().get(INVITE_KEY_PREFIX + tokenHash);
        } catch (Exception e) {
            log.error("[InvitationService] Redis 장애 — 토큰 검증 불가", e);
            throw new InvalidInviteTokenException(
                    "일시적인 오류가 발생했습니다. 초대 링크가 만료되었을 수 있습니다. 운영자에게 재초대를 요청해 주세요.");
        }

        if (invitationIdStr == null) {
            throw new InvalidInviteTokenException("초대 링크가 만료되었습니다. 운영자에게 재초대를 요청해 주세요.");
        }

        Invitation invitation =
                invitationRepository
                        .findById(UUID.fromString(invitationIdStr))
                        .orElseThrow(() -> new InvalidInviteTokenException("유효하지 않은 초대 링크입니다."));

        if (invitation.isPending() && invitation.isExpired()) {
            invitation.expire();
            redisTemplate.delete(INVITE_KEY_PREFIX + tokenHash);
            throw new InvalidInviteTokenException("초대 링크가 만료되었습니다. 운영자에게 재초대를 요청해 주세요.");
        }

        return switch (invitation.getStatus()) {
            case PENDING -> new InviteResolution(invitation, tokenHash);
            case EXPIRED -> throw new InvalidInviteTokenException(
                    "초대 링크가 만료되었습니다. 운영자에게 재초대를 요청해 주세요.");
            case REVOKED, USED -> throw new InvalidInviteTokenException(
                    "만료되었거나 철회된 초대 링크입니다. 운영자에게 재초대를 요청해 주세요.");
        };
    }

    private record InviteResolution(Invitation invitation, String tokenHash) {}
}
