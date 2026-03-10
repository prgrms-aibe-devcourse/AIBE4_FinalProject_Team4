package kr.java.documind.domain.member.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.java.documind.domain.member.exception.CompanyNotFoundException;
import kr.java.documind.domain.member.model.dto.AdminCompanyCard;
import kr.java.documind.domain.auth.model.dto.HeaderInfo;
import kr.java.documind.domain.member.model.entity.Company;
import kr.java.documind.domain.member.model.entity.Member;
import kr.java.documind.domain.member.model.enums.AccountStatus;
import kr.java.documind.domain.member.model.enums.CompanyStatus;
import kr.java.documind.domain.member.model.repository.CompanyRepository;
import kr.java.documind.global.exception.ConflictException;
import kr.java.documind.global.exception.NotFoundException;
import kr.java.documind.global.exception.StorageException;
import kr.java.documind.global.storage.FileStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CompanyService {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private final CompanyRepository companyRepository;
    private final MemberService memberService;
    private final FileStore fileStore;

    public record AdminPageData(
            HeaderInfo headerInfo,
            List<AdminCompanyCard> pendingCompanies,
            List<AdminCompanyCard> approvedCompanies,
            List<AdminCompanyCard> suspendedCompanies,
            long pendingCount,
            long approvedCount,
            long suspendedCount) {}

    public AdminPageData getAdminCompanyPageData(UUID adminMemberId) {
        HeaderInfo headerInfo = memberService.getHeaderInfo(adminMemberId);
        List<AdminCompanyCard> pending = buildAdminCards(CompanyStatus.PENDING);
        List<AdminCompanyCard> approved = buildAdminCards(CompanyStatus.APPROVED);
        List<AdminCompanyCard> suspended = buildAdminCards(CompanyStatus.SUSPENDED);
        return new AdminPageData(
                headerInfo,
                pending,
                approved,
                suspended,
                pending.size(),
                approved.size(),
                suspended.size());
    }

    @Transactional
    public void approveCompany(UUID adminMemberId, Long companyId) {
        Company company =
                companyRepository
                        .findByIdAndDeletedAtIsNull(companyId)
                        .orElseThrow(CompanyNotFoundException::new);
        company.approve();
        log.info("[CompanyService] 회사 승인: adminId={} companyId={}", adminMemberId, companyId);
    }

    @Transactional
    public void rejectCompany(UUID adminMemberId, Long companyId) {
        Company company =
                companyRepository
                        .findByIdAndDeletedAtIsNull(companyId)
                        .orElseThrow(CompanyNotFoundException::new);
        company.reject();
        log.info("[CompanyService] 회사 거부: adminId={} companyId={}", adminMemberId, companyId);
    }

    private List<AdminCompanyCard> buildAdminCards(CompanyStatus status) {
        List<Company> companies =
                companyRepository.findByStatusAndDeletedAtIsNullOrderByCreatedAtDesc(status);
        if (companies.isEmpty()) {
            return List.of();
        }
        // 회사 ID 목록으로 CEO를 단일 쿼리로 일괄 조회 (N+1 방지)
        List<Long> companyIds = companies.stream().map(Company::getId).toList();
        Map<Long, Member> ceoByCompanyId = memberService.findCeosByCompanyIds(companyIds);

        return companies.stream()
                .map(company -> toAdminCompanyCard(company, ceoByCompanyId.get(company.getId())))
                .toList();
    }

    private AdminCompanyCard toAdminCompanyCard(Company company, Member ceo) {
        String companyProfileUrl = resolveUrl(company.getProfileKey());
        String appliedAt = formatDate(company.getCreatedAt());
        String updatedAt = formatDate(company.getUpdatedAt());

        if (ceo == null || ceo.getAccountStatus() == AccountStatus.DELETED) {
            return new AdminCompanyCard(
                    company.getId(),
                    company.getName(),
                    companyProfileUrl,
                    company.getStatus(),
                    appliedAt,
                    updatedAt,
                    null,
                    null,
                    null,
                    null,
                    null,
                    true);
        }

        return new AdminCompanyCard(
                company.getId(),
                company.getName(),
                companyProfileUrl,
                company.getStatus(),
                appliedAt,
                updatedAt,
                ceo.getName(),
                ceo.getEmail(),
                ceo.getAccountStatus(),
                ceo.getPosition(),
                resolveUrl(ceo.getProfileKey()),
                false);
    }

    private String resolveUrl(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return fileStore.getAccessUrl(key);
    }

    private String formatDate(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "-";
        }
        return dateTime.format(DATE_FORMATTER);
    }

    @Transactional
    public void registerCompany(UUID memberId, String name) {
        Member member = memberService.getMemberWithCompany(memberId);

        // 비즈니스 규칙: CEO가 이미 회사를 보유한 경우 등록 불가
        if (member.getCompany() != null) {
            throw new ConflictException("이미 소속 회사가 있습니다.");
        }

        Company company = Company.create(name);
        companyRepository.save(company);
        member.assignCompany(company);
        log.info(
                "[CompanyService] 회사 등록: memberId={} companyId={} name={}",
                memberId,
                company.getId(),
                name);
    }

    @Transactional
    public void updateCompanyName(UUID memberId, String name) {
        Company company = getCompanyByMember(memberId);
        company.updateName(name);
        log.info(
                "[CompanyService] 회사명 변경: memberId={} companyId={} name={}",
                memberId,
                company.getId(),
                name);
    }

    @Transactional
    public String uploadCompanyProfileImage(UUID memberId, MultipartFile file) {
        Company company = getCompanyByMember(memberId);
        String oldKey = company.getProfileKey();

        try {
            // 1. 새 파일 먼저 업로드
            String newKey = fileStore.save(file);
            // 2. 롤백 시 newKey 자동 삭제 (orphan 방지)
            fileStore.registerRollback(newKey);
            // 3. DB 업데이트
            company.updateProfileKey(newKey);
            // 4. 커밋 확정 후 기존 파일 삭제 (롤백 시에는 삭제하지 않음)
            if (oldKey != null) {
                fileStore.registerDeleteAfterCommit(oldKey);
            }
            log.info(
                    "[CompanyService] 회사 프로필 이미지 업로드: memberId={} companyId={} key={}",
                    memberId,
                    company.getId(),
                    newKey);
            return fileStore.getAccessUrl(newKey);
        } catch (IOException e) {
            throw new StorageException("회사 프로필 이미지 저장 중 오류가 발생했습니다.", e);
        }
    }

    private Company getCompanyByMember(UUID memberId) {
        Member member = memberService.getMemberWithCompany(memberId);
        if (member.getCompany() == null) {
            throw new NotFoundException("소속 회사 정보가 없습니다.");
        }
        return member.getCompany();
    }
}
