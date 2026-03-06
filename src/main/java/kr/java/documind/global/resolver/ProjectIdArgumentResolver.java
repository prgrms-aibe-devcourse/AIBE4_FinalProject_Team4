package kr.java.documind.global.resolver;

import java.util.Map;
import java.util.UUID;
import kr.java.documind.domain.member.model.repository.ProjectRepository;
import kr.java.documind.global.annotation.ProjectId;
import kr.java.documind.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerMapping;

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
        Map<String, String> uriTemplateVars =
                (Map<String, String>)
                        webRequest.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, 0);

        String publicId = uriTemplateVars != null ? uriTemplateVars.get("publicId") : null;

        return projectRepository
                .findByPublicId(publicId)
                .orElseThrow(() -> new NotFoundException("프로젝트를 찾을 수 없습니다."))
                .getId();
    }
}
