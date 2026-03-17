package kr.java.documind.global.security.interceptor;

import static kr.java.documind.global.security.filter.RateLimitFilter.HEADER_API_KEY;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import kr.java.documind.domain.auth.model.dto.ProjectRequestContext;
import kr.java.documind.domain.auth.web.ProjectContextHolder;
import kr.java.documind.global.annotation.QueryRateLimit;
import kr.java.documind.global.exception.TooManyRequestsException;
import kr.java.documind.global.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 로그 탐색기 쿼리 API에 대한 Rate Limit 인터셉터.
 *
 * <p>{@link QueryRateLimit} 어노테이션이 붙은 핸들러에만 적용된다. {@code ProjectAccessInterceptor}가 먼저 실행된 뒤 {@link
 * ProjectContextHolder}에서 projectId를 읽어 버킷 키로 사용하므로, WebMvcConfig에서 반드시 ProjectAccessInterceptor 다음
 * 순서로 등록해야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryRateLimitInterceptor implements HandlerInterceptor {

    public static final String HEADER_REMAINING_QUERY_TOKEN = "Remaining-Query-Token";

    private final ProxyManager<String> proxyManager;

    @Value("${app.rate-limit.query.capacity:20}")
    private int capacity;

    @Value("${app.rate-limit.query.redis-prefix:rate-limit:query}")
    private String rateLimitPrefix;

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        if (handlerMethod.getMethodAnnotation(QueryRateLimit.class) == null) {
            return true;
        }

        UUID projectId = resolveProjectId(request);

        String bucketKey = rateLimitPrefix + projectId;
        Bucket bucket = proxyManager.builder().build(bucketKey, this::createBucketConfiguration);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.addHeader(
                    HEADER_REMAINING_QUERY_TOKEN, String.valueOf(probe.getRemainingTokens()));
            return true;
        }

        long nanosToWait = probe.getNanosToWaitForRefill();
        long retryAfterSeconds = TimeUnit.NANOSECONDS.toSeconds(nanosToWait + 999_999_999L);

        log.warn("[QueryRateLimit] 쿼리 한도 초과. retryAfter={}s", retryAfterSeconds);

        throw new TooManyRequestsException(
                "쿼리 요청 한도를 초과했습니다. " + retryAfterSeconds + "초 후에 다시 시도해주세요.", retryAfterSeconds);
    }

    private UUID resolveProjectId(HttpServletRequest request) {
        // 내부 웹 UI 접근: JWT를 통해 이미 파싱된 컨텍스트 확인
        Optional<ProjectRequestContext> ctxOpt = ProjectContextHolder.get(request);
        if (ctxOpt.isPresent()) {
            return ctxOpt.get().projectId();
        }

        // 외부 API 접근: 컨텍스트가 없다면 헤더의 API Key 검증
        String apiKey = request.getHeader(HEADER_API_KEY);
        if (StringUtils.hasText(apiKey)) {
            // TODO: 향후 외부 시스템 연동을 위해 API-KEY 기반 조회 기능이 필요할 경우 하이브리드 인증 로직을 구현
            // 현재는 보안을 유지하기 위해 API Key 조회를 원천 차단함
            log.warn("API Key를 통한 로그 조회 시도 차단됨");
            throw new UnauthorizedException("현재 API Key를 통한 로그 조회가 허용되지 않습니다. 웹 콘솔을 이용해 주세요.");
        }

        // 세션도 없고 API Key도 없는 경우
        throw new UnauthorizedException("로그 조회를 위한 인증 정보가 존재하지 않습니다.");
    }

    private BucketConfiguration createBucketConfiguration() {
        return BucketConfiguration.builder()
                .addLimit(
                        limit ->
                                limit.capacity(capacity)
                                        .refillGreedy(capacity, Duration.ofMinutes(1)))
                .build();
    }
}
