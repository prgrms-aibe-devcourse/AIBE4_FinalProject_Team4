package kr.java.documind.domain.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import kr.java.documind.domain.auth.model.dto.HeaderInfo;
import kr.java.documind.domain.member.model.entity.Member;
import kr.java.documind.domain.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentUserViewContextService {
    private static final String ATTR_MEMBER = "currentUser.memberWithCompany";
    private static final String ATTR_HEADER = "currentUser.headerInfo";

    private final MemberService memberService;

    public Member getCurrentMember(UUID memberId, HttpServletRequest request) {
        Object cached = request.getAttribute(ATTR_MEMBER);
        if (cached instanceof Member member) {
            return member;
        }

        Member member = memberService.getMemberWithCompany(memberId);
        request.setAttribute(ATTR_MEMBER, member);
        return member;
    }

    public HeaderInfo getHeaderInfo(UUID memberId, HttpServletRequest request) {
        Object cached = request.getAttribute(ATTR_HEADER);
        if (cached instanceof HeaderInfo headerInfo) {
            return headerInfo;
        }

        Member member = getCurrentMember(memberId, request);
        HeaderInfo headerInfo = memberService.getHeaderInfo(member);
        request.setAttribute(ATTR_HEADER, headerInfo);
        return headerInfo;
    }
}
