package kr.java.documind.domain.logprocessor.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.java.documind.domain.logprocessor.config.SamplingConfig;
import kr.java.documind.domain.logprocessor.model.dto.request.RawLogRequest;
import kr.java.documind.domain.logprocessor.model.entity.GameLog;
import kr.java.documind.domain.logprocessor.model.enums.BackpressureState;
import kr.java.documind.domain.logprocessor.model.enums.EventCategory;
import kr.java.documind.domain.logprocessor.model.enums.LogSeverity;
import kr.java.documind.domain.logprocessor.model.repository.LogJdbcRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("로그 샘플링 통합 테스트 (HyperLogLog)")
class LogSamplingIntegrationTest {

    @Autowired private LogBufferService logBufferService;

    @Autowired private LogSamplingService logSamplingService;

    @Autowired private SamplingConfig samplingConfig;

    @Autowired private RedisTemplate<String, String> redisTemplate;

    @MockBean private LogJdbcRepository logJdbcRepository;

    @MockBean private IssueGroupingBatchService issueGroupingBatchService;

    @MockBean private BackpressureManager backpressureManager;

    private static final String TEST_FINGERPRINT = "integration-test-fingerprint";

    @BeforeEach
    void setUp() {
        // Redis HyperLogLog 키 초기화
        Set<String> keys = redisTemplate.keys("sampling:" + TEST_FINGERPRINT + ":hll:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        // Mock 초기화
        reset(logJdbcRepository, issueGroupingBatchService, backpressureManager);

        // BackpressureManager 기본 상태: NORMAL
        when(backpressureManager.getState()).thenReturn(BackpressureState.NORMAL);
    }

    @Test
    @DisplayName("샘플링 비활성화 시 모든 로그가 버퍼에 추가된다")
    void samplingDisabled_allLogsBuffered() throws InterruptedException {
        // given
        samplingConfig.setEnabled(false);

        RawLogRequest request = createTestLogRequest();

        // when
        for (int i = 0; i < 100; i++) {
            logBufferService.addFromDto(request);
        }

        // 버퍼가 flush될 때까지 대기
        Thread.sleep(1500);

        // then
        // logJdbcRepository.saveAll()이 호출되었는지 확인
        verify(logJdbcRepository, atLeastOnce()).saveAll(anyList());
        // incrementOccurrenceOnly()는 호출되지 않음 (샘플링 안 함)
        verify(issueGroupingBatchService, never()).incrementOccurrenceOnly(any(GameLog.class));
    }

    @Test
    @DisplayName("샘플링 활성화 시 임계값 초과 후 샘플링이 적용된다")
    void samplingEnabled_appliesSamplingAfterThreshold() throws InterruptedException {
        // given
        samplingConfig.setEnabled(true);
        samplingConfig.setThreshold(10);
        samplingConfig.setRate(0.0); // 0% 저장, 100% 샘플링

        // when: 50개 unique 로그 전송
        for (int i = 0; i < 50; i++) {
            RawLogRequest request = createTestLogRequest();
            logBufferService.addFromDto(request);
        }

        // 버퍼가 flush될 때까지 대기
        Thread.sleep(1500);

        // then
        // incrementOccurrenceOnly()가 호출되었는지 확인 (샘플링된 로그)
        // HyperLogLog 근사값으로 임계값 10 초과 후 약 40개 샘플링 (±2 허용)
        verify(issueGroupingBatchService, atLeast(38)).incrementOccurrenceOnly(any(GameLog.class));
        verify(issueGroupingBatchService, atMost(42)).incrementOccurrenceOnly(any(GameLog.class));

        // Redis HyperLogLog 카운트 확인 (근사값)
        long count = logSamplingService.getCurrentCount(TEST_FINGERPRINT);
        assertThat(count).isBetween(48L, 52L);
    }

    @Test
    @DisplayName("샘플링된 로그는 DB에 저장되지 않지만 occurrence_count는 증가한다")
    void sampledLogs_notSavedButOccurrenceIncremented() throws InterruptedException {
        // given
        samplingConfig.setEnabled(true);
        samplingConfig.setThreshold(5);
        samplingConfig.setRate(0.0); // 0% 저장, 100% 샘플링

        // when: 20개 unique 로그 전송 (처음 5개 저장, 나머지 15개 샘플링)
        for (int i = 0; i < 20; i++) {
            RawLogRequest request = createTestLogRequest();
            logBufferService.addFromDto(request);
        }

        // 버퍼가 flush될 때까지 대기
        Thread.sleep(1500);

        // then
        // 샘플링된 약 15개 로그에 대해 incrementOccurrenceOnly() 호출 (HyperLogLog 근사값)
        verify(issueGroupingBatchService, atLeast(13)).incrementOccurrenceOnly(any(GameLog.class));
        verify(issueGroupingBatchService, atMost(17)).incrementOccurrenceOnly(any(GameLog.class));

        // DB 저장은 처음 약 5개만 (HyperLogLog 근사값)
        verify(logJdbcRepository, atLeastOnce()).saveAll(argThat(logs -> logs.size() <= 7));
    }

    /**
     * 테스트용 RawLogRequest 생성
     *
     * @return RawLogRequest (기본 ERROR severity)
     */
    private RawLogRequest createTestLogRequest() {
        return createTestLogRequest(LogSeverity.ERROR);
    }

    /**
     * 테스트용 RawLogRequest 생성 (severity 지정)
     *
     * @param severity 로그 심각도
     * @return RawLogRequest
     */
    private RawLogRequest createTestLogRequest(LogSeverity severity) {
        Map<String, Object> resource = new HashMap<>();
        resource.put("environment", "test");

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("action", "test_action");

        return new RawLogRequest(
                UUID.randomUUID(),
                "test-session",
                "test-user",
                severity,
                EventCategory.GAMEPLAY,
                "Test log message",
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                "test-trace",
                "test-span",
                resource,
                attributes);
    }

    @Test
    @DisplayName("서버 과부하 시 Fingerprint 임계값 미만이어도 샘플링된다")
    void serverOverload_samplesEvenBelowThreshold() throws InterruptedException {
        // given
        samplingConfig.setEnabled(true);
        samplingConfig.setBackpressureEnabled(true);
        samplingConfig.setThreshold(1000); // Fingerprint 임계값 높게
        samplingConfig.setBackpressureRate(0.0); // 100% 샘플링

        // 서버 FATAL 상태
        when(backpressureManager.getState()).thenReturn(BackpressureState.CRITICAL);

        // when: 10개 로그 전송 (Fingerprint 임계값 미만)
        for (int i = 0; i < 10; i++) {
            RawLogRequest request = createTestLogRequest();
            logBufferService.addFromDto(request);
        }

        // 버퍼가 flush될 때까지 대기
        Thread.sleep(1500);

        // then: Fingerprint 임계값 미만이지만 서버 과부하로 샘플링 적용
        verify(issueGroupingBatchService, times(10)).incrementOccurrenceOnly(any(GameLog.class));

        // DB 저장 없음 (모두 샘플링됨)
        verify(logJdbcRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("하이브리드: Fingerprint 초과 OR 서버 과부하 시 샘플링")
    void hybrid_fingerprintOrBackpressure() throws InterruptedException {
        // given
        samplingConfig.setEnabled(true);
        samplingConfig.setBackpressureEnabled(true);
        samplingConfig.setThreshold(5);
        samplingConfig.setRate(0.0); // Fingerprint 샘플링
        samplingConfig.setBackpressureRate(0.0); // 서버 부하 샘플링

        // 서버 정상 상태에서 시작
        when(backpressureManager.getState()).thenReturn(BackpressureState.NORMAL);

        // when: Fingerprint 임계값 초과 (10개 전송)
        for (int i = 0; i < 10; i++) {
            RawLogRequest request = createTestLogRequest();
            logBufferService.addFromDto(request);
        }

        Thread.sleep(1500);

        // then: Fingerprint 초과로 약 5개 샘플링 (HyperLogLog 근사값)
        verify(issueGroupingBatchService, atLeast(3)).incrementOccurrenceOnly(any(GameLog.class));
    }

    @Test
    @DisplayName("INFO 로그는 항상 Severity 비율로 샘플링된다")
    void infoLogs_sampledBySeverity() throws InterruptedException {
        // given
        samplingConfig.setEnabled(true);
        samplingConfig.getSeverityRates().put("INFO", 0.0); // 0% 저장, 100% 샘플링

        // when: 20개 INFO 로그 전송
        for (int i = 0; i < 20; i++) {
            RawLogRequest request = createTestLogRequest(LogSeverity.INFO);
            logBufferService.addFromDto(request);
        }

        Thread.sleep(1500);

        // then: 모두 샘플링됨 (Severity 기준)
        verify(issueGroupingBatchService, times(20)).incrementOccurrenceOnly(any(GameLog.class));

        // DB 저장 없음
        verify(logJdbcRepository, never()).saveAll(anyList());
    }
}
