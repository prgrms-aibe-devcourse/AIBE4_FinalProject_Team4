package kr.java.documind.global.resolver;

import java.util.Map;
import java.util.UUID;
import kr.java.documind.global.annotation.ProjectId;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerMapping;

@Component
public class ProjectIdArgumentResolver implements HandlerMethodArgumentResolver {

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

        // TODO: Project 엔티티 생성 후 projectRepository.findByPublicId(publicId) 변환 로직으로 교체
        return UUID.fromString("00000000-0000-0000-0000-000000000000");
    }
}
