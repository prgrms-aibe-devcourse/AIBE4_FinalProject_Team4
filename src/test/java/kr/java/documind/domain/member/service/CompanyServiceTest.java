package kr.java.documind.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.auth.model.enums.GlobalRole;
import kr.java.documind.domain.auth.model.enums.OAuthProvider;
import kr.java.documind.domain.member.exception.CompanyNotFoundException;
import kr.java.documind.domain.member.model.entity.Company;
import kr.java.documind.domain.member.model.entity.Member;
import kr.java.documind.domain.member.model.enums.CompanyStatus;
import kr.java.documind.domain.member.model.repository.CompanyRepository;
import kr.java.documind.global.exception.ConflictException;
import kr.java.documind.global.exception.NotFoundException;
import kr.java.documind.global.storage.FileStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CompanyService 단위 테스트")
class CompanyServiceTest {

    @InjectMocks private CompanyService companyService;

    @Mock private CompanyRepository companyRepository;
    @Mock private MemberService memberService;
    @Mock private FileStore fileStore;

    // ── 공통 픽스처 헬퍼 ─────────────────────────────────────────────────────

    private Member createCeoWithoutCompany() {
        return Member.createByOAuth(
                "ceo@example.com",
                "CEO User",
                "CeoNick",
                OAuthProvider.GOOGLE,
                "google-ceo",
                GlobalRole.CEO);
    }

    private Member createCeoWithCompany() {
        Member member = createCeoWithoutCompany();
        member.assignCompany(Company.create("ExistingCo"));
        return member;
    }

    // ── approveCompany() ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("approveCompany()")
    class ApproveCompany {

        @Test
        @DisplayName("기능: 존재하는 회사 → APPROVED 상태로 변경")
        void approveCompany_존재하는회사_APPROVED상태로변경() {
            // Given
            UUID adminId = UUID.randomUUID();
            Long companyId = 1L;
            Company company = Company.create("TestCo");
            given(companyRepository.findByIdAndDeletedAtIsNull(companyId))
                    .willReturn(Optional.of(company));

            // When
            companyService.approveCompany(adminId, companyId);

            // Then
            assertThat(company.getStatus()).isEqualTo(CompanyStatus.APPROVED);
        }

        @Test
        @DisplayName("예외: 존재하지 않는 회사 → CompanyNotFoundException 발생")
        void approveCompany_존재하지않는회사_CompanyNotFoundException발생() {
            // Given
            UUID adminId = UUID.randomUUID();
            Long companyId = 999L;
            given(companyRepository.findByIdAndDeletedAtIsNull(companyId))
                    .willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> companyService.approveCompany(adminId, companyId))
                    .isInstanceOf(CompanyNotFoundException.class)
                    .hasMessageContaining("회사 정보를 찾을 수 없습니다");
        }
    }

    // ── rejectCompany() ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("rejectCompany()")
    class RejectCompany {

        @Test
        @DisplayName("기능: 존재하는 회사 → SUSPENDED 상태로 변경")
        void rejectCompany_존재하는회사_SUSPENDED상태로변경() {
            // Given
            UUID adminId = UUID.randomUUID();
            Long companyId = 2L;
            Company company = Company.create("TestCo");
            given(companyRepository.findByIdAndDeletedAtIsNull(companyId))
                    .willReturn(Optional.of(company));

            // When
            companyService.rejectCompany(adminId, companyId);

            // Then
            assertThat(company.getStatus()).isEqualTo(CompanyStatus.SUSPENDED);
        }

        @Test
        @DisplayName("예외: 존재하지 않는 회사 → CompanyNotFoundException 발생")
        void rejectCompany_존재하지않는회사_CompanyNotFoundException발생() {
            // Given
            UUID adminId = UUID.randomUUID();
            Long companyId = 999L;
            given(companyRepository.findByIdAndDeletedAtIsNull(companyId))
                    .willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> companyService.rejectCompany(adminId, companyId))
                    .isInstanceOf(CompanyNotFoundException.class);
        }
    }

    // ── registerCompany() ────────────────────────────────────────────────────

    @Nested
    @DisplayName("registerCompany()")
    class RegisterCompany {

        @Test
        @DisplayName("기능: 소속 회사 없는 CEO → PENDING 상태 회사 생성 및 연결")
        void registerCompany_소속회사없는CEO_PENDING회사생성() {
            // Given
            UUID memberId = UUID.randomUUID();
            Member member = createCeoWithoutCompany();
            given(memberService.getMemberWithCompany(memberId)).willReturn(member);
            given(companyRepository.save(any(Company.class))).willAnswer(inv -> inv.getArgument(0));

            // When
            companyService.registerCompany(memberId, "NewCo");

            // Then
            assertThat(member.getCompany()).isNotNull();
            assertThat(member.getCompany().getStatus()).isEqualTo(CompanyStatus.PENDING);
            then(companyRepository).should().save(any(Company.class));
        }

        @Test
        @DisplayName("예외: 이미 소속 회사 있는 회원 → ConflictException 발생")
        void registerCompany_이미소속회사있는회원_ConflictException발생() {
            // Given
            UUID memberId = UUID.randomUUID();
            Member member = createCeoWithCompany();
            given(memberService.getMemberWithCompany(memberId)).willReturn(member);

            // When & Then
            assertThatThrownBy(() -> companyService.registerCompany(memberId, "AnotherCo"))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("이미 소속 회사가 있습니다");
        }
    }

    // ── updateCompanyName() ──────────────────────────────────────────────────

    @Nested
    @DisplayName("updateCompanyName()")
    class UpdateCompanyName {

        @Test
        @DisplayName("기능: 소속 회사 있는 CEO → 회사명 변경 성공")
        void updateCompanyName_소속회사있는CEO_회사명변경성공() {
            // Given
            UUID memberId = UUID.randomUUID();
            Member member = createCeoWithCompany();
            given(memberService.getMemberWithCompany(memberId)).willReturn(member);

            // When
            companyService.updateCompanyName(memberId, "UpdatedCo");

            // Then
            assertThat(member.getCompany().getName()).isEqualTo("UpdatedCo");
        }

        @Test
        @DisplayName("예외: 소속 회사 없는 회원 → NotFoundException 발생")
        void updateCompanyName_소속회사없는회원_NotFoundException발생() {
            // Given
            UUID memberId = UUID.randomUUID();
            Member member = createCeoWithoutCompany();
            given(memberService.getMemberWithCompany(memberId)).willReturn(member);

            // When & Then
            assertThatThrownBy(() -> companyService.updateCompanyName(memberId, "UpdatedCo"))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("소속 회사 정보가 없습니다");
        }
    }
}
