package kr.java.documind.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.auth.model.enums.GlobalRole;
import kr.java.documind.domain.auth.model.enums.OAuthProvider;
import kr.java.documind.domain.member.model.entity.Member;
import kr.java.documind.domain.member.model.repository.MemberRepository;
import kr.java.documind.global.exception.UnauthorizedException;
import kr.java.documind.global.storage.FileStore;
import kr.java.documind.global.storage.FileStoreResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
@DisplayName("MemberService 단위 테스트")
class MemberServiceTest {

    @InjectMocks private MemberService memberService;

    @Mock private MemberRepository memberRepository;
    @Mock private FileStore fileStore;

    // ── 공통 픽스처 헬퍼 ─────────────────────────────────────────────────────

    private Member createMember(GlobalRole role) {
        return Member.createByOAuth(
                "test@example.com",
                "Test User",
                "TestNick",
                OAuthProvider.GOOGLE,
                "google-123",
                role);
    }

    // ── getMember() ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getMember()")
    class GetMember {

        @Test
        @DisplayName("기능: 존재하는 회원 ID → 회원 반환")
        void getMember_존재하는회원_반환성공() {
            // Given
            UUID memberId = UUID.randomUUID();
            Member expected = createMember(GlobalRole.EMPLOYEE);
            given(memberRepository.findById(memberId)).willReturn(Optional.of(expected));

            // When
            Member result = memberService.getMember(memberId);

            // Then
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("예외: 존재하지 않는 회원 ID → UnauthorizedException 발생")
        void getMember_존재하지않는회원_UnauthorizedException발생() {
            // Given
            UUID memberId = UUID.randomUUID();
            given(memberRepository.findById(memberId)).willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> memberService.getMember(memberId))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("인증된 회원을 찾을 수 없습니다");
        }
    }

    // ── getMemberWithCompany() ───────────────────────────────────────────────

    @Nested
    @DisplayName("getMemberWithCompany()")
    class GetMemberWithCompany {

        @Test
        @DisplayName("기능: 존재하는 회원 ID → 회사 포함 회원 반환")
        void getMemberWithCompany_존재하는회원_반환성공() {
            // Given
            UUID memberId = UUID.randomUUID();
            Member expected = createMember(GlobalRole.CEO);
            given(memberRepository.findWithCompanyById(memberId)).willReturn(Optional.of(expected));

            // When
            Member result = memberService.getMemberWithCompany(memberId);

            // Then
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("예외: 존재하지 않는 회원 ID → UnauthorizedException 발생")
        void getMemberWithCompany_존재하지않는회원_UnauthorizedException발생() {
            // Given
            UUID memberId = UUID.randomUUID();
            given(memberRepository.findWithCompanyById(memberId)).willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> memberService.getMemberWithCompany(memberId))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("인증된 회원을 찾을 수 없습니다");
        }
    }

    // ── findOrCreateOAuthMember() ────────────────────────────────────────────

    @Nested
    @DisplayName("findOrCreateOAuthMember()")
    class FindOrCreateOAuthMember {

        @Test
        @DisplayName("기능: 동일 provider+providerId 기존 회원 존재 → DB 저장 없이 기존 회원 반환")
        void findOrCreateOAuthMember_기존회원존재_기존회원반환() {
            // Given
            Member existing = createMember(GlobalRole.EMPLOYEE);
            given(memberRepository.findByProviderAndProviderId(OAuthProvider.GOOGLE, "google-123"))
                    .willReturn(Optional.of(existing));

            // When
            Member result =
                    memberService.findOrCreateOAuthMember(
                            OAuthProvider.GOOGLE,
                            "google-123",
                            "test@example.com",
                            "Test",
                            "TestNick",
                            GlobalRole.EMPLOYEE);

            // Then
            assertThat(result).isEqualTo(existing);
            then(memberRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("기능: 신규 provider+providerId → 새 회원 생성 후 저장")
        void findOrCreateOAuthMember_신규회원_저장후반환() {
            // Given
            given(memberRepository.findByProviderAndProviderId(OAuthProvider.GITHUB, "github-456"))
                    .willReturn(Optional.empty());
            given(memberRepository.save(any(Member.class))).willAnswer(inv -> inv.getArgument(0));

            // When
            Member result =
                    memberService.findOrCreateOAuthMember(
                            OAuthProvider.GITHUB,
                            "github-456",
                            "new@example.com",
                            "New User",
                            "NewNick",
                            GlobalRole.EMPLOYEE);

            // Then
            assertThat(result).isNotNull();
            then(memberRepository).should().save(any(Member.class));
        }

        @Test
        @DisplayName("경계값: 빈 name → 기본값 '사용자'로 생성")
        void findOrCreateOAuthMember_빈이름_기본이름으로생성() {
            // Given
            given(memberRepository.findByProviderAndProviderId(any(), anyString()))
                    .willReturn(Optional.empty());
            given(memberRepository.save(any(Member.class))).willAnswer(inv -> inv.getArgument(0));

            // When
            Member result =
                    memberService.findOrCreateOAuthMember(
                            OAuthProvider.GOOGLE,
                            "google-blank",
                            "blank@example.com",
                            "  ",
                            "  ",
                            GlobalRole.EMPLOYEE);

            // Then
            assertThat(result.getName()).isEqualTo("사용자");
        }
    }

    // ── updateMemberProfile() ────────────────────────────────────────────────

    @Nested
    @DisplayName("updateMemberProfile()")
    class UpdateMemberProfile {

        @Test
        @DisplayName("기능: 유효한 닉네임·포지션 → 프로필 정상 변경")
        void updateMemberProfile_유효한닉네임포지션_프로필변경성공() {
            // Given
            UUID memberId = UUID.randomUUID();
            Member member = createMember(GlobalRole.EMPLOYEE);
            given(memberRepository.findWithCompanyById(memberId)).willReturn(Optional.of(member));

            // When
            memberService.updateMemberProfile(memberId, "UpdatedNick", "SENIOR");

            // Then
            assertThat(member.getNickname()).isEqualTo("UpdatedNick");
            assertThat(member.getPosition()).isEqualTo("SENIOR");
        }

        @Test
        @DisplayName("경계값: 공백 포지션 → 포지션 변경 미적용")
        void updateMemberProfile_공백포지션_변경미적용() {
            // Given
            UUID memberId = UUID.randomUUID();
            Member member = createMember(GlobalRole.EMPLOYEE);
            String originalPosition = member.getPosition();
            given(memberRepository.findWithCompanyById(memberId)).willReturn(Optional.of(member));

            // When
            memberService.updateMemberProfile(memberId, "UpdatedNick", "   ");

            // Then
            assertThat(member.getPosition()).isEqualTo(originalPosition);
        }
    }

    // ── uploadMemberProfileImage() ───────────────────────────────────────────

    @Nested
    @DisplayName("uploadMemberProfileImage()")
    class UploadMemberProfileImage {

        @Test
        @DisplayName("기능: 정상 업로드 → 새 키 저장 및 접근 URL 반환")
        void uploadMemberProfileImage_정상업로드_URL반환() {
            // Given
            UUID memberId = UUID.randomUUID();
            Member member = createMember(GlobalRole.EMPLOYEE);
            given(memberRepository.findWithCompanyById(memberId)).willReturn(Optional.of(member));
            MultipartFile file = mock(MultipartFile.class);
            given(fileStore.save(file)).willReturn(new FileStoreResult("new-key", "jpg"));
            given(fileStore.getAccessUrl("new-key")).willReturn("https://cdn.example.com/new-key");

            // When
            String url = memberService.uploadMemberProfileImage(memberId, file);

            // Then
            assertThat(url).isEqualTo("https://cdn.example.com/new-key");
            assertThat(member.getProfileKey()).isEqualTo("new-key");
        }

        @Test
        @DisplayName("기능: 기존 프로필 키 존재 → 커밋 후 기존 키 삭제 예약")
        void uploadMemberProfileImage_기존키존재_커밋후삭제예약() {
            // Given
            UUID memberId = UUID.randomUUID();
            Member member = createMember(GlobalRole.EMPLOYEE);
            member.updateProfile(null, "old-key", null); // 기존 키 설정
            given(memberRepository.findWithCompanyById(memberId)).willReturn(Optional.of(member));
            MultipartFile file = mock(MultipartFile.class);
            given(fileStore.save(file)).willReturn(new FileStoreResult("new-key", "jpg"));
            given(fileStore.getAccessUrl("new-key")).willReturn("https://cdn.example.com/new-key");

            // When
            memberService.uploadMemberProfileImage(memberId, file);

            // Then
            then(fileStore).should().deleteOnCommit("old-key");
        }
    }
}
