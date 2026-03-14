package kr.java.documind.domain.member.service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import kr.java.documind.domain.auth.exception.DeletedProjectException;
import kr.java.documind.domain.auth.exception.ProjectNotFoundException;
import kr.java.documind.domain.auth.model.entity.Project;
import kr.java.documind.domain.auth.model.entity.ProjectApiKey;
import kr.java.documind.domain.auth.model.enums.ApiKeyType;
import kr.java.documind.domain.auth.model.enums.ProjectRole;
import kr.java.documind.domain.auth.model.repository.ProjectApiKeyRepository;
import kr.java.documind.domain.auth.model.repository.ProjectRepository;
import kr.java.documind.domain.member.model.dto.ApiKeyIssueResponse;
import kr.java.documind.domain.member.model.dto.ProfileImageResponse;
import kr.java.documind.domain.member.model.dto.ProjectApiKeyInfo;
import kr.java.documind.domain.member.model.dto.ProjectCreateResponse;
import kr.java.documind.domain.member.model.dto.ProjectMemberRow;
import kr.java.documind.domain.member.model.dto.ProjectSettingPageData;
import kr.java.documind.domain.member.model.dto.ProjectSummary;
import kr.java.documind.domain.member.model.entity.Member;
import kr.java.documind.domain.member.model.entity.ProjectMember;
import kr.java.documind.domain.member.model.enums.AccountStatus;
import kr.java.documind.domain.member.model.enums.ApiKeyStatus;
import kr.java.documind.domain.member.model.enums.CompanyStatus;
import kr.java.documind.domain.member.model.repository.ProjectMemberRepository;
import kr.java.documind.global.exception.BadRequestException;
import kr.java.documind.global.exception.ForbiddenException;
import kr.java.documind.global.exception.NotFoundException;
import kr.java.documind.global.storage.FileStore;
import kr.java.documind.global.util.HmacApiKeyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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

    @CacheEvict(cacheNames = "projectSelector", key = "#memberId")
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
    @CacheEvict(cacheNames = "projectSelector", allEntries = true)
    public ProfileImageResponse uploadProjectProfileImage(
            String publicId, UUID memberId, MultipartFile file) {

        Project project =
                projectRepository
                        .findByPublicId(publicId)
                        .orElseThrow(ProjectNotFoundException::new);

        String oldKey = project.getProfileKey();
        String newKey = fileStore.save(file).storedKey();

        project.updateInfo(null, newKey);

        if (oldKey != null) {
            fileStore.deleteOnCommit(oldKey);
        }
        fileStore.deleteOnRollback(newKey);

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

        String projectProfileUrl =
                project.getProfileKey() != null
                        ? fileStore.getAccessUrl(project.getProfileKey())
                        : null;
        ProjectSummary projectSummary =
                new ProjectSummary(project.getPublicId(), project.getName(), projectProfileUrl);

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

        ProjectApiKeyInfo apiKeyInfo = null;
        if (currentPm.isManager()) {
            apiKeyInfo = getProjectApiKeysForSetting(project);
        }

        return new ProjectSettingPageData(
                projectSummary,
                currentPm.getProjectRole(),
                currentMember.isCeo(),
                members,
                apiKeyInfo);
    }

    private ProjectApiKeyInfo getProjectApiKeysForSetting(Project project) {
        ProjectApiKeyInfo.KeyMetadata ingestKey =
                findCurrentKeyMetadata(project, ApiKeyType.INGEST);
        ProjectApiKeyInfo.KeyMetadata queryKey = findCurrentKeyMetadata(project, ApiKeyType.QUERY);

        return new ProjectApiKeyInfo(ingestKey, queryKey);
    }

    private ProjectApiKeyInfo.KeyMetadata findCurrentKeyMetadata(
            Project project, ApiKeyType keyType) {
        Optional<ProjectApiKey> keyOpt =
                projectApiKeyRepository
                        .findFirstByProjectAndKeyTypeAndApiKeyStatusInOrderByCreatedAtDesc(
                                project,
                                keyType,
                                List.of(ApiKeyStatus.ACTIVE, ApiKeyStatus.SUSPENDED));

        if (keyOpt.isPresent()) {
            ProjectApiKey key = keyOpt.get();
            String masked = HmacApiKeyUtil.maskApiKey(key.getKeyPrefix(), key.getKeyLast4());
            return new ProjectApiKeyInfo.KeyMetadata(
                    true, key.getKeyType(), masked, key.getApiKeyStatus());
        } else {
            return new ProjectApiKeyInfo.KeyMetadata(false, keyType, null, null);
        }
    }

    @Cacheable(cacheNames = "projectSelector", key = "#memberId")
    public List<ProjectSummary> getProjectSelectorList(UUID memberId) {
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
    @CacheEvict(cacheNames = "projectSelector", allEntries = true)
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
    public void changeProjectRole(
            String publicId, UUID actorMemberId, UUID targetMemberId, ProjectRole newRole) {
        Project project =
                projectRepository
                        .findByPublicId(publicId)
                        .orElseThrow(ProjectNotFoundException::new);

        Member targetMember = memberService.getMember(targetMemberId);

        if (targetMember.isCeo()) {
            throw new ForbiddenException("대표(CEO)의 프로젝트 권한은 변경할 수 없습니다.");
        }

        ProjectMember targetPm =
                projectMemberRepository
                        .findByProjectAndMember(project, targetMember)
                        .orElseThrow(() -> new NotFoundException("대상이 프로젝트 멤버가 아닙니다."));

        if (targetPm.getStatus() == AccountStatus.DELETED) {
            throw new NotFoundException("대상이 프로젝트 멤버가 아닙니다.");
        }

        if (targetPm.isManager() && newRole == ProjectRole.MEMBER) {
            if (projectMemberRepository.countByProjectAndProjectRoleAndStatus(
                            project, ProjectRole.MANAGER, AccountStatus.ACTIVE)
                    <= 1) {
                throw new BadRequestException("프로젝트에는 최소 한 명 이상의 관리자가 필요합니다.");
            }
        }

        targetPm.changeRole(newRole);
        log.info(
                "[ProjectService] 멤버 권한 변경: actorId={}, targetId={}, projectId={}, newRole={}",
                actorMemberId,
                targetMemberId,
                project.getId(),
                newRole);
    }

    @Transactional
    public void removeProjectMember(String publicId, UUID actorMemberId, UUID targetMemberId) {
        if (actorMemberId.equals(targetMemberId)) {
            throw new BadRequestException("자기 자신을 제거할 수 없습니다. '프로젝트 나가기'를 이용해주세요.");
        }

        Project project =
                projectRepository
                        .findByPublicId(publicId)
                        .orElseThrow(ProjectNotFoundException::new);

        Member targetMember = memberService.getMember(targetMemberId);

        if (targetMember.isCeo()) {
            throw new ForbiddenException("대표(CEO)는 프로젝트에서 제거할 수 없습니다.");
        }

        ProjectMember targetPm =
                projectMemberRepository
                        .findByProjectAndMember(project, targetMember)
                        .orElseThrow(() -> new NotFoundException("대상이 프로젝트 멤버가 아닙니다."));

        if (targetPm.getStatus() == AccountStatus.DELETED) {
            throw new NotFoundException("대상이 프로젝트 멤버가 아닙니다.");
        }

        if (targetPm.isManager()) {
            if (projectMemberRepository.countByProjectAndProjectRoleAndStatus(
                            project, ProjectRole.MANAGER, AccountStatus.ACTIVE)
                    <= 1) {
                throw new BadRequestException("프로젝트에는 최소 한 명 이상의 관리자가 필요합니다.");
            }
        }

        targetPm.softDelete();
        log.info(
                "[ProjectService] 멤버 제거: actorId={}, targetId={}, projectId={}",
                actorMemberId,
                targetMemberId,
                project.getId());
    }

    @Transactional
    @CacheEvict(cacheNames = "projectSelector", key = "#memberId")
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

        if (pm.isManager()) {
            if (projectMemberRepository.countByProjectAndProjectRoleAndStatus(
                            project, ProjectRole.MANAGER, AccountStatus.ACTIVE)
                    <= 1) {
                throw new BadRequestException("프로젝트에는 최소 한 명 이상의 관리자가 필요합니다. 나갈 수 없습니다.");
            }
        }

        pm.softDelete();
        log.info("[ProjectService] 프로젝트 나가기: memberId={} publicId={}", memberId, publicId);
    }

    @Transactional
    @CacheEvict(cacheNames = "projectSelector", allEntries = true)
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

        if (project.isDeleted()) {
            throw new DeletedProjectException();
        }

        // INGEST, QUERY 타입에 대해 각각 기존 키 폐기 및 신규 키 생성
        ApiKeyIssueResponse.IssuedKey issuedIngestKey =
                createNewApiKeyForType(project, ApiKeyType.INGEST);
        ApiKeyIssueResponse.IssuedKey issuedQueryKey =
                createNewApiKeyForType(project, ApiKeyType.QUERY);

        log.info(
                "[ProjectService] 전체 API Key 재발급 (INGEST & QUERY): memberId={} publicId={}",
                memberId,
                publicId);
        return new ApiKeyIssueResponse(issuedIngestKey, issuedQueryKey);
    }

    @Transactional
    public ApiKeyIssueResponse reissueApiKey(String publicId, UUID memberId, ApiKeyType keyType) {
        Project project =
                projectRepository
                        .findByPublicId(publicId)
                        .orElseThrow(ProjectNotFoundException::new);

        if (project.isDeleted()) {
            throw new DeletedProjectException();
        }

        ApiKeyIssueResponse.IssuedKey newIssuedKey = createNewApiKeyForType(project, keyType);

        // 응답 구조에 맞게 반환. 재발급하지 않은 키 타입은 null로 채움
        ApiKeyIssueResponse.IssuedKey ingestKey =
                (keyType == ApiKeyType.INGEST) ? newIssuedKey : null;
        ApiKeyIssueResponse.IssuedKey queryKey =
                (keyType == ApiKeyType.QUERY) ? newIssuedKey : null;

        log.info(
                "[ProjectService] API Key 재발급 ({}): memberId={} publicId={}",
                keyType,
                memberId,
                publicId);
        return new ApiKeyIssueResponse(ingestKey, queryKey);
    }

    private ApiKeyIssueResponse.IssuedKey createNewApiKeyForType(
            Project project, ApiKeyType keyType) {
        projectApiKeyRepository.revokeAllByProjectAndKeyType(
                project,
                keyType,
                ApiKeyStatus.REVOKED,
                List.of(ApiKeyStatus.ACTIVE, ApiKeyStatus.SUSPENDED));

        String plainKey = HmacApiKeyUtil.generatePlainKey(keyType.getPrefix());
        String hmacHash = HmacApiKeyUtil.computeHmac(plainKey, hmacSecret);
        String keyPrefix = HmacApiKeyUtil.extractPrefix(plainKey);
        String keyLast4 = HmacApiKeyUtil.extractLast4(plainKey);

        ProjectApiKey newApiKey =
                ProjectApiKey.create(project, hmacHash, keyPrefix, keyLast4, keyType);
        projectApiKeyRepository.save(newApiKey);

        String maskedKey = HmacApiKeyUtil.maskApiKey(plainKey);

        return new ApiKeyIssueResponse.IssuedKey(keyType, plainKey, maskedKey);
    }

    @Transactional
    public void toggleApiKeyStatus(
            String publicId, UUID memberId, ApiKeyType keyType, ApiKeyStatus newStatus) {
        if (newStatus != ApiKeyStatus.ACTIVE && newStatus != ApiKeyStatus.SUSPENDED) {
            throw new BadRequestException("ACTIVE 또는 SUSPENDED 상태만 설정할 수 있습니다.");
        }

        Project project =
                projectRepository
                        .findByPublicId(publicId)
                        .orElseThrow(ProjectNotFoundException::new);

        if (project.isDeleted()) {
            throw new DeletedProjectException();
        }

        // 특정 타입의 키만 찾아서 상태 변경
        ProjectApiKey key =
                projectApiKeyRepository
                        .findFirstByProjectAndKeyTypeAndApiKeyStatusInOrderByCreatedAtDesc(
                                project,
                                keyType,
                                List.of(ApiKeyStatus.ACTIVE, ApiKeyStatus.SUSPENDED))
                        .orElseThrow(
                                () -> new NotFoundException(keyType + " 타입의 활성화된 API 키가 없습니다."));

        if (newStatus == ApiKeyStatus.ACTIVE) {
            key.activate();
        } else {
            key.suspend();
        }

        log.info(
                "[ProjectService] API Key 상태 변경: memberId={} publicId={} keyType={} status={}",
                memberId,
                publicId,
                keyType,
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
