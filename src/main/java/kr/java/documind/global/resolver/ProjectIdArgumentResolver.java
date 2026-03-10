package kr.java.documind.global.resolver;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.auth.exception.ProjectNotFoundException;
import kr.java.documind.domain.auth.model.repository.ProjectRepository;
import kr.java.documind.domain.auth.web.ProjectContextHolder;
import kr.java.documind.global.annotation.ProjectId;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerMapping;

/**
 * {@link ProjectId} 어노테이션이 붙은 {@code UUID} 파라미터를 프로젝트 내부 ID로 해석한다.
 *
 * <p>우선 {@code ProjectAccessInterceptor}가 설정한 {@code ProjectRequestContext}에서 읽는다(DB 조회 없음). 컨텍스트가
 * 없는 경우(인터셉터 미적용 엔드포인트 등)에는 DB를 직접 조회하는 폴백을 사용한다.
 */
@Component
@RequiredArgsConstructor
public class ProjectIdArgumentResolver implements HandlerMethodArgumentResolver {

    private final ProjectRepository projectRepository;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(ProjectId.class)
                && parameter.getParameterType().equals(UUID.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public UUID resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {

        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);

        // ① 인터셉터가 설정한 컨텍스트에서 읽기 (DB 추가 조회 없음)
        Optional<UUID> fromContext = ProjectContextHolder.get(request).map(ctx -> ctx.projectId());
        if (fromContext.isPresent()) {
            return fromContext.get();
        }

        // ② 폴백: URL path variable에서 publicId 추출 후 DB 조회
        Map<String, String> uriTemplateVars =
                (Map<String, String>)
                        webRequest.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, 0);
        String publicId = uriTemplateVars != null ? uriTemplateVars.get("publicId") : null;

        return projectRepository
                .findByPublicId(publicId)
                .orElseThrow(ProjectNotFoundException::new)
                .getId();
    }
}
