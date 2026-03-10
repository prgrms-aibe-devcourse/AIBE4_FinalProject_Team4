package kr.java.documind.domain.auth.web.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;
import kr.java.documind.domain.auth.exception.DeletedProjectException;
import kr.java.documind.domain.auth.exception.ProjectAccessDeniedException;
import kr.java.documind.domain.auth.exception.ProjectNotFoundException;
import kr.java.documind.domain.auth.model.dto.ProjectMemberProjection;
import kr.java.documind.domain.auth.model.dto.ProjectRequestContext;
import kr.java.documind.domain.member.model.enums.ProjectStatus;
import kr.java.documind.domain.auth.model.repository.ProjectRepository;
import kr.java.documind.domain.auth.web.ProjectContextHolder;
import kr.java.documind.global.security.jwt.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectAccessInterceptor implements HandlerInterceptor {

    private final ProjectRepository projectRepository;

    @Override
    @SuppressWarnings("unchecked")
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        Map<String, String> pathVars =
                (Map<String, String>)
                        request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        String publicId = pathVars != null ? pathVars.get("publicId") : null;

        if (publicId == null || publicId.isBlank()) {
            return true;
        }

        UUID memberId = extractMemberId();
        if (memberId == null) {
            return true;
        }

        ProjectMemberProjection projection =
                projectRepository
                        .findProjectWithMemberContext(publicId, memberId)
                        .orElseThrow(ProjectNotFoundException::new);

        if (projection.getProjectStatus() == ProjectStatus.DELETED) {
            throw new DeletedProjectException();
        }

        ProjectRequestContext ctx = ProjectRequestContext.from(projection, memberId);

        if (!ctx.isProjectMember()) {
            throw new ProjectAccessDeniedException();
        }

        ProjectContextHolder.set(request, ctx);

        log.debug(
                "[ProjectAccessInterceptor] publicId={} memberId={} isProjectMember={} isProjectManager={}",
                publicId,
                memberId,
                ctx.isProjectMember(),
                ctx.isProjectManager());

        return true;
    }

    private UUID extractMemberId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        if (auth.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails.getMemberId();
        }
        return null;
    }
}
