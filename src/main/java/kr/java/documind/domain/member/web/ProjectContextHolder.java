package kr.java.documind.domain.member.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import kr.java.documind.domain.member.model.dto.ProjectRequestContext;

public final class ProjectContextHolder {

    private static final String ATTR_KEY = ProjectRequestContext.class.getName();

    private ProjectContextHolder() {}

    public static void set(HttpServletRequest request, ProjectRequestContext ctx) {
        request.setAttribute(ATTR_KEY, ctx);
    }

    public static Optional<ProjectRequestContext> get(HttpServletRequest request) {
        Object attr = request.getAttribute(ATTR_KEY);
        return Optional.ofNullable((ProjectRequestContext) attr);
    }

    public static ProjectRequestContext require(HttpServletRequest request) {
        return get(request)
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "ProjectRequestContext가 요청에 존재하지 않습니다. "
                                                + "ProjectAccessInterceptor가 해당 URL에 등록되어 있는지 확인하세요."));
    }
}
