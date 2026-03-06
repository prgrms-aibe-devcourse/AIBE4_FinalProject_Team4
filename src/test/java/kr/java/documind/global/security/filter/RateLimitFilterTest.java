package kr.java.documind.global.security.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.rate-limit.capacity=5")
@AutoConfigureMockMvc
@DisplayName("RateLimitFilter 통합 테스트")
class RateLimitFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Rate Limit: 동시 요청으로 한도 초과 시 → 429 에러 반환")
    void doFilterInternal_ConcurrentRequestsExceedLimit_Returns429() throws Exception {
        // Given: 50개의 동시 요청 준비 (현재 설정된 한도: 5)
        String testApiKey = "test-project-uuid-001";
        int totalRequests = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(30);
        CountDownLatch latch = new CountDownLatch(totalRequests);
        AtomicInteger tooManyRequestsCount = new AtomicInteger(0);

        // When: 50개의 요청 동시 실행
        for (int i = 0; i < totalRequests; i++) {
            executorService.submit(() -> {
                try {
                    int statusCode = mockMvc.perform(get("/api/v1/test-rate-limit")
                            .header("Api-Key", testApiKey))
                        .andReturn().getResponse().getStatus();

                    if (statusCode == 429) {
                        tooManyRequestsCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(); // 모든 스레드의 작업이 끝날 때까지 대기

        // Then: 429 에러가 최소 1번 이상 발생해야 함
        assertThat(tooManyRequestsCount.get()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Rate Limit: 순차 요청으로 한도 초과 시 → 429 에러 및 헤더/JSON 반환")
    void doFilterInternal_SequentialRequestsExceedLimit_Returns429() throws Exception {
        // Given: 5번의 요청은 성공할 것을 가정 (독립적인 API Key 사용)
        String testApiKey = "test-project-uuid-002";

        // When: 5번의 요청 순차 실행
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/v1/test-rate-limit").header("Api-Key", testApiKey))
                .andExpect(status().isOk());
        }

        // Then: 6번째 요청은 GlobalApiExceptionHandler를 타고 429 에러와 커스텀 헤더, JSON 포맷을 반환해야 함
        mockMvc.perform(get("/api/v1/test-rate-limit").header("Api-Key", testApiKey))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string(RateLimitFilter.HEADER_REMAINING_TOKEN, "0"))
            .andExpect(header().exists(RateLimitFilter.HEADER_RETRY_AFTER))
            .andExpect(jsonPath("$.error.message").value("요청 한도를 초과했습니다."));
    }

    @Test
    @DisplayName("Rate Limit: Api-Key 헤더 누락 시 → 400 에러 및 JSON 반환")
    void doFilterInternal_MissingApiKeyHeader_Returns400() throws Exception {
        // When & Then: Api-Key 헤더를 실수로 빼먹고 요청을 보냈을 때
        mockMvc.perform(get("/api/v1/test-rate-limit"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.message").value(RateLimitFilter.HEADER_API_KEY + " 헤더가 누락되었습니다."));
    }
}
