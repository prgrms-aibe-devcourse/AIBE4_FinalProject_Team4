package kr.java.documind.domain.member.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.java.documind.domain.auth.model.dto.ConflictingMemberInfo;
import kr.java.documind.domain.auth.model.dto.HeaderInfo;
import kr.java.documind.domain.auth.model.enums.GlobalRole;
import kr.java.documind.domain.auth.model.enums.OAuthProvider;
import kr.java.documind.domain.member.model.dto.CompanyDetail;
import kr.java.documind.domain.member.model.dto.MemberProfileDetail;
import kr.java.documind.domain.member.model.entity.Member;
import kr.java.documind.domain.member.model.enums.AccountStatus;
import kr.java.documind.domain.member.model.repository.MemberRepository;
import kr.java.documind.global.exception.UnauthorizedException;
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
public class MemberService {

    private final MemberRepository memberRepository;
    private final FileStore fileStore;

    public record ProfilePageData(MemberProfileDetail profileDetail) {}

    public record CompanyPageData(CompanyDetail companyDetail) {}

    public ProfilePageData getProfilePageData(Member member) {
        return new ProfilePageData(buildProfileDetail(member));
    }

    public CompanyPageData getCompanyPageData(Member member) {
        return new CompanyPageData(buildCompanyDetail(member));
    }

    public HeaderInfo getHeaderInfo(UUID memberId) {
        return buildHeaderInfo(getMemberWithCompany(memberId));
    }

    public HeaderInfo getHeaderInfo(Member member) {
        return buildHeaderInfo(member);
    }

    public Optional<ConflictingMemberInfo> findConflictingMemberInfo(
            String email, OAuthProvider currentProvider) {
        if (email == null || email.endsWith("@oauth.placeholder")) {
            return Optional.empty();
        }
        return memberRepository
                .findActiveByEmailAndDifferentProvider(
                        email, currentProvider, AccountStatus.DELETED)
                .map(
                        m ->
                                new ConflictingMemberInfo(
                                        m.getProvider(),
                                        m.getNickname(),
                                        m.getEmail(),
                                        resolveUrl(m.getProfileKey()),
                                        m.getGlobalRole()));
    }

    public boolean existsByProviderAndProviderId(OAuthProvider provider, String providerId) {
        return memberRepository.findByProviderAndProviderId(provider, providerId).isPresent();
    }

    public Map<Long, Member> findCeosByCompanyIds(List<Long> companyIds) {
        if (companyIds.isEmpty()) {
            return Map.of();
        }
        return memberRepository.findByCompanyIdInAndGlobalRole(companyIds, GlobalRole.CEO).stream()
                .collect(Collectors.toMap(m -> m.getCompany().getId(), Function.identity()));
    }

    public Member getMember(UUID id) {
        return memberRepository
                .findById(id)
                .orElseThrow(() -> new UnauthorizedException("인증된 회원을 찾을 수 없습니다."));
    }

    public Member getMemberWithCompany(UUID id) {
        return memberRepository
                .findWithCompanyById(id)
                .orElseThrow(() -> new UnauthorizedException("인증된 회원을 찾을 수 없습니다."));
    }

    @Transactional
    public Member findOrCreateOAuthMember(
            OAuthProvider provider,
            String providerId,
            String email,
            String name,
            String nickname,
            GlobalRole role) {

        return memberRepository
                .findByProviderAndProviderId(provider, providerId)
                .orElseGet(
                        () -> {
                            String resolvedName =
                                    Optional.ofNullable(name)
                                            .filter(n -> !n.isBlank())
                                            .orElse("사용자");
                            String resolvedNickname =
                                    Optional.ofNullable(nickname)
                                            .filter(n -> !n.isBlank())
                                            .orElse(resolvedName);

                            Member member =
                                    Member.createByOAuth(
                                            email,
                                            resolvedName,
                                            resolvedNickname,
                                            provider,
                                            providerId,
                                            role);
                            Member saved = memberRepository.save(member);
                            log.info(
                                    "[MemberService] 신규 OAuth 회원 가입: memberId={} provider={}"
                                            + " role={} emailPlaceholder={}",
                                    saved.getId(),
                                    provider,
                                    role,
                                    saved.isEmailPlaceholder());
                            return saved;
                        });
    }

    @Transactional
    public void updateMemberProfile(UUID memberId, String nickname, String position) {
        String validPosition = (position != null && !position.isBlank()) ? position.strip() : null;
        Member member = getMemberWithCompany(memberId);
        member.updateProfile(nickname, null, validPosition);
        log.info("[MemberService] 프로필 변경: memberId={}", memberId);
    }

    @Transactional
    public String uploadMemberProfileImage(UUID memberId, MultipartFile file) {
        Member member = getMemberWithCompany(memberId);
        String oldKey = member.getProfileKey();

        String newKey = fileStore.save(file).storedKey();
        fileStore.deleteOnRollback(newKey);
        member.updateProfile(null, newKey, null);
        if (oldKey != null) {
            fileStore.deleteOnCommit(oldKey);
        }
        log.info("[MemberService] 프로필 이미지 업로드: memberId={} key={}", memberId, newKey);
        return fileStore.getAccessUrl(newKey);
    }

    private HeaderInfo buildHeaderInfo(Member member) {
        String profileImageUrl = resolveUrl(member.getProfileKey());
        String companyProfileUrl =
                member.getCompany() != null
                        ? resolveUrl(member.getCompany().getProfileKey())
                        : null;
        return HeaderInfo.from(member, profileImageUrl, companyProfileUrl);
    }

    private MemberProfileDetail buildProfileDetail(Member member) {
        return MemberProfileDetail.from(member, resolveUrl(member.getProfileKey()));
    }

    private CompanyDetail buildCompanyDetail(Member member) {
        if (member.getCompany() == null) {
            return null;
        }
        return CompanyDetail.from(
                member.getCompany(), resolveUrl(member.getCompany().getProfileKey()));
    }

    private String resolveUrl(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return fileStore.getAccessUrl(key);
    }
}
