package kr.java.documind.global.config;

import java.util.List;
import kr.java.documind.domain.member.web.interceptor.ProjectAccessInterceptor;
import kr.java.documind.domain.member.web.resolver.CurrentProjectArgumentResolver;
import kr.java.documind.global.resolver.ProjectIdArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final ProjectAccessInterceptor projectAccessInterceptor;
    private final CurrentProjectArgumentResolver currentProjectArgumentResolver;
    private final ProjectIdArgumentResolver projectIdArgumentResolver;

    /**
     * 프로젝트 인가 인터셉터를 {@code /projects/**} 및 {@code /api/projects/**} 패턴에 등록한다.
     *
     * <p>인터셉터가 {@code ProjectRequestContext}를 설정하면, 이후 ArgumentResolver들이 DB 추가 조회 없이 context에서 값을
     * 읽는다.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(projectAccessInterceptor)
                .addPathPatterns("/projects/**", "/api/projects/**");
    }

    /**
     * ArgumentResolver 등록 순서가 중요하다.
     *
     * <ol>
     *   <li>{@link CurrentProjectArgumentResolver} — {@code @CurrentProject ProjectRequestContext}
     *   <li>{@link ProjectIdArgumentResolver} — {@code @ProjectId UUID}
     * </ol>
     */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentProjectArgumentResolver);
        resolvers.add(projectIdArgumentResolver);
    }
}
