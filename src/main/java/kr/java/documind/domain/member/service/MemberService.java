package kr.java.documind.domain.member.service;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.member.model.dto.CompanyDetail;
import kr.java.documind.domain.member.model.dto.HeaderInfo;
import kr.java.documind.domain.member.model.dto.MemberProfileDetail;
import kr.java.documind.domain.member.model.entity.Member;
import kr.java.documind.domain.member.model.enums.AccountStatus;
import kr.java.documind.domain.member.model.enums.GlobalRole;
import kr.java.documind.domain.member.model.enums.OAuthProvider;
import kr.java.documind.domain.member.model.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;

    public HeaderInfo getHeaderInfo(UUID memberId) {
        Member member = getMemberWithCompany(memberId);
        return HeaderInfo.from(member);
    }

    public MemberProfileDetail getProfileDetail(UUID memberId) {
        Member member = getMemberWithCompany(memberId);
        return MemberProfileDetail.from(member);
    }

    public CompanyDetail getCompanyDetail(UUID memberId) {
        Member member = getMemberWithCompany(memberId);
        if (member.getCompany() == null) {
            return null;
        }
        return CompanyDetail.from(member.getCompany());
    }

    public Optional<OAuthProvider> findConflictingProvider(
            String email, OAuthProvider currentProvider) {
        if (email == null || email.endsWith("@oauth.placeholder")) {
            return Optional.empty();
        }
        return memberRepository
                .findActiveByEmailAndDifferentProvider(
                        email, currentProvider, AccountStatus.DELETED)
                .map(Member::getProvider);
    }

    public Member getMemberWithCompany(UUID id) {
        return memberRepository
                .findWithCompanyById(id)
                .orElseThrow(() -> new NoSuchElementException("인증된 회원을 찾을 수 없습니다."));
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
                                    "신규 OAuth 회원 가입: memberId={} provider={} role={} emailPlaceholder={}",
                                    saved.getId(),
                                    provider,
                                    role,
                                    saved.isEmailPlaceholder());
                            return saved;
                        });
    }
}
