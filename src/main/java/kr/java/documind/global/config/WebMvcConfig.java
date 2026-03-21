package kr.java.documind.global.config;

import java.util.List;
import kr.java.documind.domain.auth.web.interceptor.ProjectAccessInterceptor;
import kr.java.documind.domain.auth.web.resolver.CurrentProjectArgumentResolver;
import kr.java.documind.global.resolver.ProjectIdArgumentResolver;
import kr.java.documind.global.security.interceptor.QueryRateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final ProjectAccessInterceptor projectAccessInterceptor;
    private final QueryRateLimitInterceptor queryRateLimitInterceptor;
    private final CurrentProjectArgumentResolver currentProjectArgumentResolver;
    private final ProjectIdArgumentResolver projectIdArgumentResolver;

    @Qualifier("taskExecutor")
    private final AsyncTaskExecutor taskExecutor;

    /**
     * {@code Flux<ServerSentEvent>} 반환 시 Spring MVC가 비동기 처리를 위해 사용하는 실행자.
     *
     * <p>설정하지 않으면 {@code SimpleAsyncTaskExecutor}(스레드를 매번 생성, 프로덕션 부적합)가 사용된다. {@code AsyncConfig}에
     * 등록된 커스텀 {@code ThreadPoolTaskExecutor}를 재사용한다.
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(taskExecutor);
    }

    /**
     * 프로젝트 인가 인터셉터를 {@code /projects/**} 및 {@code /api/projects/**} 패턴에 등록한다.
     *
     * <p>인터셉터가 {@code ProjectRequestContext}를 설정하면, 이후 ArgumentResolver들이 DB 추가 조회 없이 context에서 값을
     * 읽는다.
     *
     * <p>{@code QueryRateLimitInterceptor}는 {@code ProjectAccessInterceptor} 이후에 등록한다
     * (ProjectRequestContext 의존).
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(projectAccessInterceptor)
                .addPathPatterns("/projects/**", "/api/projects/**");
        registry.addInterceptor(queryRateLimitInterceptor).addPathPatterns("/api/projects/**");
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
