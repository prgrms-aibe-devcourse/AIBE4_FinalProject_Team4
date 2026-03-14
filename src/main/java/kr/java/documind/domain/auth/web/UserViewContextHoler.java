package kr.java.documind.domain.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import kr.java.documind.domain.auth.model.dto.UserViewContext;

public final class UserViewContextHoler {
    private static final String ATTR_KEY = UserViewContext.class.getName();

    private UserViewContextHoler() {}

    public static void set(HttpServletRequest request, UserViewContext ctx) {
        request.setAttribute(ATTR_KEY, ctx);
    }

    public static Optional<UserViewContext> get(HttpServletRequest request) {
        Object attr = request.getAttribute(ATTR_KEY);
        return Optional.ofNullable((UserViewContext) attr);
    }

    public static UserViewContext require(HttpServletRequest request) {
        return get(request)
                .orElseThrow(() -> new IllegalStateException("UserViewContext가 요청에 존재하지 않습니다. "));
    }
}
