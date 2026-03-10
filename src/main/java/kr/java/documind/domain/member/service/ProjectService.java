package kr.java.documind.domain.member.service;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.auth.exception.DeletedProjectException;
import kr.java.documind.domain.auth.exception.ProjectNotFoundException;
import kr.java.documind.domain.member.model.dto.ApiKeyIssueResponse;
import kr.java.documind.domain.member.model.dto.ProfileImageResponse;
import kr.java.documind.domain.member.model.dto.ProjectApiKeyInfo;
import kr.java.documind.domain.member.model.dto.ProjectCreateResponse;
import kr.java.documind.domain.member.model.dto.ProjectDetail;
import kr.java.documind.domain.member.model.dto.ProjectMemberRow;
import kr.java.documind.domain.member.model.dto.ProjectSettingPageData;
import kr.java.documind.domain.member.model.dto.ProjectSummary;
import kr.java.documind.domain.member.model.entity.Member;
import kr.java.documind.domain.auth.model.entity.Project;
import kr.java.documind.domain.auth.model.entity.ProjectApiKey;
import kr.java.documind.domain.member.model.entity.ProjectMember;
import kr.java.documind.domain.member.model.enums.AccountStatus;
import kr.java.documind.domain.member.model.enums.ApiKeyStatus;
import kr.java.documind.domain.member.model.enums.CompanyStatus;
import kr.java.documind.domain.auth.model.enums.ProjectRole;
import kr.java.documind.domain.auth.model.repository.ProjectApiKeyRepository;
import kr.java.documind.domain.member.model.repository.ProjectMemberRepository;
import kr.java.documind.domain.auth.model.repository.ProjectRepository;
import kr.java.documind.global.exception.BadRequestException;
import kr.java.documind.global.exception.ForbiddenException;
import kr.java.documind.global.exception.NotFoundException;
import kr.java.documind.global.exception.StorageException;
import kr.java.documind.global.storage.FileStore;
import kr.java.documind.global.util.HmacApiKeyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectApiKeyRepository projectApiKeyRepository;
    private final MemberService memberService;
    private final FileStore fileStore;
    private final PlatformTransactionManager txManager;

    @Value("${app.api-key.hmac-secret}")
    private String hmacSecret;

    public ProjectCreateResponse createProject(UUID memberId, String name) {
        Member member = memberService.getMemberWithCompany(memberId);

        if (member.getCompany() == null
                || member.getCompany().getStatus() != CompanyStatus.APPROVED) {
            throw new ForbiddenException("승인된 회사가 있어야 프로젝트를 생성할 수 있습니다.");
        }

        TransactionTemplate requiresNew = new TransactionTemplate(txManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        for (int attempt = 1; attempt <= PUBLIC_ID_MAX_RETRY; attempt++) {
            try {
                final String publicId = generatePublicId();
                return requiresNew.execute(
                        status -> {
                            Project project =
                                    Project.create(publicId, member.getCompany(), name, null);
                            projectRepository.saveAndFlush(project);
                            projectMemberRepository.save(
                                    ProjectMember.create(project, member, ProjectRole.MANAGER));
                            log.info(
                                    "[ProjectService] 프로젝트 생성: memberId={} publicId={} name={}",
                                    memberId,
                                    publicId,
                                    name);
                            return new ProjectCreateResponse(publicId);
                        });
            } catch (DataIntegrityViolationException e) {
                log.warn("[ProjectService] publicId 충돌, 재시도 ({}/{})", attempt, PUBLIC_ID_MAX_RETRY);
            }
        }
        throw new IllegalStateException("publicId 생성에 실패했습니다. 잠시 후 다시 시도해주세요.");
    }

    @Transactional
    public ProfileImageResponse uploadProjectProfileImage(
            String publicId, UUID memberId, MultipartFile file) {

        Project project =
                projectRepository
                        .findByPublicId(publicId)
                        .orElseThrow(ProjectNotFoundException::new);

        String oldKey = project.getProfileKey();

        String newKey;
        try {
            newKey = fileStore.save(file);
        } catch (IOException e) {
            throw new StorageException("이미지 업로드에 실패했습니다.", e);
        }

        project.updateInfo(null, newKey);

        if (oldKey != null) {
            fileStore.registerDeleteAfterCommit(oldKey);
        }
        fileStore.registerRollback(newKey);

        String url = fileStore.getAccessUrl(newKey);
        log.info(
                "[ProjectService] 프로젝트 이미지 업로드: memberId={} publicId={} key={}",
                memberId,
                publicId,
                newKey);
        return new ProfileImageResponse(url);
    }

    public ProjectSettingPageData getProjectSettingPageData(String publicId, UUID memberId) {
        Project project =
                projectRepository
                        .findByPublicId(publicId)
                        .orElseThrow(ProjectNotFoundException::new);

        if (project.isDeleted()) {
            throw new DeletedProjectException();
        }

        Member currentMember = memberService.getMemberWithCompany(memberId);

        ProjectMember currentPm =
                projectMemberRepository
                        .findByProjectAndMember(project, currentMember)
                        .orElseThrow(() -> new NotFoundException("프로젝트 멤버 정보를 찾을 수 없습니다."));

        var headerInfo = memberService.getHeaderInfo(currentMember);

        String projectProfileUrl =
                project.getProfileKey() != null
                        ? fileStore.getAccessUrl(project.getProfileKey())
                        : null;
        var projectDetail =
                new ProjectDetail(project.getPublicId(), project.getName(), projectProfileUrl);

        List<ProjectMemberRow> members =
                projectMemberRepository
                        .findByProjectAndStatusFetchMember(project, AccountStatus.ACTIVE)
                        .stream()
                        .map(
                                pm -> {
                                    Member m = pm.getMember();
                                    String mUrl =
                                            m.getProfileKey() != null
                                                    ? fileStore.getAccessUrl(m.getProfileKey())
                                                    : null;
                                    return new ProjectMemberRow(
                                            m.getId(),
                                            m.getName(),
                                            m.getNickname(),
                                            m.getEmail(),
                                            mUrl,
                                            pm.getProjectRole(),
                                            m.getId().equals(memberId),
                                            m.isCeo());
                                })
                        .toList();

        List<ProjectSummary> myProjects =
                projectMemberRepository
                        .findByMemberAndStatusFetchProject(currentMember, AccountStatus.ACTIVE)
                        .stream()
                        .filter(pm -> pm.getProject().isActive())
                        .map(
                                pm -> {
                                    Project p = pm.getProject();
                                    String pUrl =
                                            p.getProfileKey() != null
                                                    ? fileStore.getAccessUrl(p.getProfileKey())
                                                    : null;
                                    return new ProjectSummary(p.getPublicId(), p.getName(), pUrl);
                                })
                        .toList();

        ProjectApiKeyInfo apiKeyInfo = null;
        if (currentPm.isManager()) {
            Optional<ProjectApiKey> keyOpt =
                    projectApiKeyRepository
                            .findFirstByProjectAndApiKeyStatusNotOrderByCreatedAtDesc(
                                    project, ApiKeyStatus.REVOKED);
            if (keyOpt.isPresent()) {
                ProjectApiKey key = keyOpt.get();
                String masked = HmacApiKeyUtil.maskApiKey(key.getKeyPrefix() + "..." + key.getKeyLast4());
                apiKeyInfo = new ProjectApiKeyInfo(true, masked, key.getApiKeyStatus());
            } else {
                apiKeyInfo = new ProjectApiKeyInfo(false, null, null);
            }
        }

        return new ProjectSettingPageData(
                headerInfo,
                projectDetail,
                currentPm.getProjectRole(),
                currentMember.isCeo(),
                members,
                myProjects,
                apiKeyInfo);
    }

    public List<ProjectSummary> getDashboardProjects(UUID memberId) {
        return projectMemberRepository
                .findByMemberIdAndStatusFetchProject(memberId, AccountStatus.ACTIVE)
                .stream()
                .filter(pm -> pm.getProject().isActive())
                .map(
                        pm -> {
                            Project p = pm.getProject();
                            String pUrl =
                                    p.getProfileKey() != null
                                            ? fileStore.getAccessUrl(p.getProfileKey())
                                            : null;
                            return new ProjectSummary(p.getPublicId(), p.getName(), pUrl);
                        })
                .toList();
    }

    @Transactional
    public void updateProjectName(String publicId, UUID memberId, String name) {
        Project project =
                projectRepository
                        .findByPublicId(publicId)
                        .orElseThrow(ProjectNotFoundException::new);

        project.updateInfo(name, null);
        log.info(
                "[ProjectService] 프로젝트 이름 수정: memberId={} publicId={} name={}",
                memberId,
                publicId,
                name);
    }

    @Transactional
    public void leaveProject(String publicId, UUID memberId) {
        Project project =
                projectRepository
                        .findByPublicId(publicId)
                        .orElseThrow(ProjectNotFoundException::new);

        Member member = memberService.getMemberWithCompany(memberId);

        if (member.isCeo()) {
            throw new ForbiddenException("대표(CEO) 계정은 프로젝트에서 나갈 수 없습니다.");
        }

        ProjectMember pm =
                projectMemberRepository
                        .findByProjectAndMember(project, member)
                        .orElseThrow(() -> new NotFoundException("프로젝트 멤버를 찾을 수 없습니다."));

        pm.softDelete();
        log.info("[ProjectService] 프로젝트 나가기: memberId={} publicId={}", memberId, publicId);
    }

    @Transactional
    public void deleteProject(String publicId, UUID memberId) {
        Project project =
                projectRepository
                        .findByPublicId(publicId)
                        .orElseThrow(ProjectNotFoundException::new);

        projectMemberRepository
                .findAllByProjectAndStatusNot(project, AccountStatus.DELETED)
                .forEach(ProjectMember::softDelete);

        project.softDelete();
        log.info("[ProjectService] 프로젝트 삭제: memberId={} publicId={}", memberId, publicId);
    }

    @Transactional
    public ApiKeyIssueResponse issueApiKey(String publicId, UUID memberId) {
        Project project =
                projectRepository
                        .findByPublicId(publicId)
                        .orElseThrow(ProjectNotFoundException::new);

        // 기존 ACTIVE/SUSPENDED 키 일괄 REVOKE (회전)
        projectApiKeyRepository
                .findAllByProjectAndApiKeyStatusNot(project, ApiKeyStatus.REVOKED)
                .forEach(ProjectApiKey::revoke);

        // 새 키 생성
        String plainKey = HmacApiKeyUtil.generatePlainKey();
        String hmacHash = HmacApiKeyUtil.computeHmac(plainKey, hmacSecret);
        String keyPrefix = HmacApiKeyUtil.extractPrefix(plainKey);
        String keyLast4 = HmacApiKeyUtil.extractLast4(plainKey);

        projectApiKeyRepository.save(ProjectApiKey.create(project, hmacHash, keyPrefix, keyLast4));

        String masked = HmacApiKeyUtil.maskApiKey(plainKey);

        log.info("[ProjectService] API Key 발급: memberId={} publicId={}", memberId, publicId);
        return new ApiKeyIssueResponse(plainKey, masked);
    }

    @Transactional
    public void toggleApiKeyStatus(String publicId, UUID memberId, ApiKeyStatus newStatus) {
        if (newStatus != ApiKeyStatus.ACTIVE && newStatus != ApiKeyStatus.SUSPENDED) {
            throw new BadRequestException("ACTIVE 또는 SUSPENDED 상태만 설정할 수 있습니다.");
        }

        Project project =
                projectRepository
                        .findByPublicId(publicId)
                        .orElseThrow(ProjectNotFoundException::new);

        ProjectApiKey key =
                projectApiKeyRepository
                        .findFirstByProjectAndApiKeyStatusNotOrderByCreatedAtDesc(
                                project, ApiKeyStatus.REVOKED)
                        .orElseThrow(() -> new NotFoundException("활성화된 API 키가 없습니다."));

        if (newStatus == ApiKeyStatus.ACTIVE) {
            key.activate();
        } else {
            key.suspend();
        }

        log.info(
                "[ProjectService] API Key 상태 변경: memberId={} publicId={} status={}",
                memberId,
                publicId,
                newStatus);
    }

    private static final String PUBLIC_ID_CHARS =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int PUBLIC_ID_MIN_LEN = 8;
    private static final int PUBLIC_ID_MAX_LEN = 12;
    private static final int PUBLIC_ID_MAX_RETRY = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private String generatePublicId() {
        int length = PUBLIC_ID_MIN_LEN + RANDOM.nextInt(PUBLIC_ID_MAX_LEN - PUBLIC_ID_MIN_LEN + 1);
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(PUBLIC_ID_CHARS.charAt(RANDOM.nextInt(PUBLIC_ID_CHARS.length())));
        }
        return sb.toString();
    }
}
