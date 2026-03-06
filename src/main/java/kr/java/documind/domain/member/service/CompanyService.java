package kr.java.documind.domain.member.service;

import java.io.IOException;
import java.util.UUID;
import kr.java.documind.domain.member.model.entity.Company;
import kr.java.documind.domain.member.model.entity.Member;
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

    private final CompanyRepository companyRepository;
    private final MemberService memberService;
    private final FileStore fileStore;

    @Transactional
    public void registerCompany(UUID memberId, String name) {
        Member member = memberService.getMemberWithCompany(memberId);

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

        if (company.getProfileKey() != null) {
            fileStore.delete(company.getProfileKey());
        }

        try {
            String newKey = fileStore.save(file);
            company.updateProfileKey(newKey);
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
