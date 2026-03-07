package kr.java.documind.global.security.filter;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import kr.java.documind.global.exception.BadRequestException;
import kr.java.documind.global.exception.TooManyRequestsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    public static final String HEADER_API_KEY = "Api-Key";
    public static final String HEADER_REMAINING_TOKEN = "Remaining-Token";
    public static final String HEADER_RETRY_AFTER = "Retry-After";

    private final ProxyManager<String> proxyManager;

    @Value("${app.rate-limit.capacity:50}")
    private int capacity;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // API 경로가 아닌 경우 필터를 적용하지 않음
        return !request.getRequestURI().startsWith("/api/logs/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String apiKey = request.getHeader(HEADER_API_KEY);

        if (!StringUtils.hasText(apiKey)) {
            throw new BadRequestException(HEADER_API_KEY + " 헤더가 누락되었습니다.");
        }

        Bucket bucket = proxyManager.builder().build(apiKey, this::createBucketConfiguration);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.addHeader(HEADER_REMAINING_TOKEN, String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
        } else {
            long nanosToWaitForRefill = probe.getNanosToWaitForRefill();

            // 1초 미만의 대기 시간도 무조건 1초로 올림 처리하여 소수점 버림 방지
            long waitForRefillSeconds =
                    TimeUnit.NANOSECONDS.toSeconds(nanosToWaitForRefill + 999_999_999);

            log.warn("API Key: {}의 요청 한도를 초과했습니다. {}초 후에 다시 시도해주세요.", apiKey, waitForRefillSeconds);

            throw new TooManyRequestsException("요청 한도를 초과했습니다.", waitForRefillSeconds);
        }
    }

    private BucketConfiguration createBucketConfiguration() {
        return BucketConfiguration.builder()
                .addLimit(
                        limit ->
                                limit.capacity(capacity)
                                        .refillGreedy(capacity, Duration.ofSeconds(1)))
                .build();
    }
}
