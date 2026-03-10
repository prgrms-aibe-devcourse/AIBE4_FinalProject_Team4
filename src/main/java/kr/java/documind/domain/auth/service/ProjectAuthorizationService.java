package kr.java.documind.domain.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import kr.java.documind.domain.auth.exception.ProjectAccessDeniedException;
import kr.java.documind.domain.auth.model.dto.ProjectRequestContext;
import kr.java.documind.domain.auth.web.ProjectContextHolder;
import kr.java.documind.global.exception.ForbiddenException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component("projectAuthz")
public class ProjectAuthorizationService {

    public boolean isProjectMember() {
        return ProjectContextHolder.get(currentRequest())
                .map(ProjectRequestContext::isProjectMember)
                .orElse(false);
    }

    public boolean isProjectManager() {
        return ProjectContextHolder.get(currentRequest())
                .map(ProjectRequestContext::isProjectManager)
                .orElse(false);
    }

    public void requireMember(ProjectRequestContext ctx) {
        if (!ctx.isProjectMember()) {
            throw new ProjectAccessDeniedException();
        }
    }

    public void requireManager(ProjectRequestContext ctx) {
        if (!ctx.isProjectManager()) {
            throw new ForbiddenException("해당 기능은 프로젝트 관리자만 사용할 수 있습니다.");
        }
    }

    private HttpServletRequest currentRequest() {
        return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                .getRequest();
    }
}
