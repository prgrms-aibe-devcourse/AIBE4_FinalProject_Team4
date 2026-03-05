package kr.java.documind.domain.logprocessor.model.repository;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.IntStream;
import kr.java.documind.domain.logprocessor.model.entity.GameLog;
import kr.java.documind.domain.logprocessor.model.enums.EventCategory;
import kr.java.documind.domain.logprocessor.model.enums.LogSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LogJdbcRepository 단위 테스트")
class LogJdbcRepositoryTest {

    @Mock private JdbcTemplate jdbcTemplate;

    @Mock private ObjectMapper objectMapper;

    @InjectMocks private LogJdbcRepository logJdbcRepository;

    private List<GameLog> testLogs;

    @BeforeEach
    void setUp() throws Exception {
        // batchSize 필드 초기화
        ReflectionTestUtils.setField(logJdbcRepository, "batchSize", 1000);

        // 테스트용 로그 데이터 생성
        testLogs = createTestLogs(100);

        // ObjectMapper Mock 설정
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    }

    @Test
    @DisplayName("JDBC Batch Insert: 1000개 로그를 배치로 삽입한다")
    void saveAll_batchInsert_success() {
        // given
        List<GameLog> logs = createTestLogs(1000);
        when(jdbcTemplate.batchUpdate(anyString(), any(BatchPreparedStatementSetter.class)))
                .thenReturn(new int[1000]);

        // when
        logJdbcRepository.saveAll(logs);

        // then
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<BatchPreparedStatementSetter> setterCaptor =
                ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);

        verify(jdbcTemplate, times(1)).batchUpdate(sqlCaptor.capture(), setterCaptor.capture());

        // SQL 검증
        assertThat(sqlCaptor.getValue()).contains("INSERT INTO game_log");
        assertThat(sqlCaptor.getValue()).contains("?::jsonb"); // JSONB 타입 검증

        // 배치 크기 검증
        BatchPreparedStatementSetter setter = setterCaptor.getValue();
        assertThat(setter.getBatchSize()).isEqualTo(1000);
    }

    @Test
    @DisplayName("JDBC Batch Insert: 2500개 로그를 1000개씩 3번 배치로 나누어 삽입한다")
    void saveAll_largeBatch_splitIntoMultipleBatches() {
        // given
        List<GameLog> logs = createTestLogs(2500);
        when(jdbcTemplate.batchUpdate(anyString(), any(BatchPreparedStatementSetter.class)))
                .thenReturn(new int[1000]);

        // when
        logJdbcRepository.saveAll(logs);

        // then
        // 1000개 + 1000개 + 500개 = 3번 호출
        ArgumentCaptor<BatchPreparedStatementSetter> setterCaptor =
                ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);
        verify(jdbcTemplate, times(3)).batchUpdate(anyString(), setterCaptor.capture());

        List<BatchPreparedStatementSetter> setters = setterCaptor.getAllValues();
        assertThat(setters.get(0).getBatchSize()).isEqualTo(1000); // 첫 번째 배치
        assertThat(setters.get(1).getBatchSize()).isEqualTo(1000); // 두 번째 배치
        assertThat(setters.get(2).getBatchSize()).isEqualTo(500); // 마지막 배치
    }

    @Test
    @DisplayName("Deadlock 재시도: Deadlock 발생 시 배치를 절반(500개)으로 나누어 재시도한다")
    void saveAll_deadlock_retryWithHalfBatchSize() {
        // given
        List<GameLog> logs = createTestLogs(1000);

        // 첫 시도(1000개): Deadlock 발생
        // 재시도(500개씩 2번): 성공
        when(jdbcTemplate.batchUpdate(anyString(), any(BatchPreparedStatementSetter.class)))
                .thenThrow(new PessimisticLockingFailureException("Deadlock detected"))
                .thenReturn(new int[500])
                .thenReturn(new int[500]);

        // when
        logJdbcRepository.saveAll(logs);

        // then
        // 총 3번 호출: 1회 실패 + 2회 성공 (500개씩)
        verify(jdbcTemplate, times(3))
                .batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
    }

    @Test
    @DisplayName("Deadlock 재시도: 배치 크기가 최소값(10) 미만이 되면 예외를 던진다")
    void saveAll_deadlock_throwExceptionWhenBatchSizeBelowMinimum() {
        // given
        List<GameLog> logs = createTestLogs(100);

        // 모든 시도에서 Deadlock 발생 (1000 → 500 → 250 → 125 → 62 → 31 → 15 → 7 ← 최소값 미만)
        when(jdbcTemplate.batchUpdate(anyString(), any(BatchPreparedStatementSetter.class)))
                .thenThrow(new PessimisticLockingFailureException("Deadlock detected"));

        // when & then
        assertThatThrownBy(() -> logJdbcRepository.saveAll(logs))
                .isInstanceOf(PessimisticLockingFailureException.class)
                .hasMessageContaining("Deadlock detected");
    }

    @Test
    @DisplayName("Deadlock 재시도: 재귀적으로 배치를 절반씩 줄여가며 재시도한다")
    void saveAll_deadlock_recursiveRetryWithDecreasingBatchSize() {
        // given
        List<GameLog> logs = createTestLogs(1000);

        // 1000 → Deadlock
        // 500 → Deadlock
        // 250 → 성공
        when(jdbcTemplate.batchUpdate(anyString(), any(BatchPreparedStatementSetter.class)))
                .thenThrow(new PessimisticLockingFailureException("Deadlock"))
                .thenThrow(new PessimisticLockingFailureException("Deadlock"))
                .thenReturn(new int[250])
                .thenReturn(new int[250])
                .thenReturn(new int[250])
                .thenReturn(new int[250]);

        // when
        logJdbcRepository.saveAll(logs);

        // then
        // 총 6번 호출: 2회 실패 + 4회 성공 (250개씩 4번)
        verify(jdbcTemplate, times(6))
                .batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
    }

    @Test
    @DisplayName("JDBC Batch Insert: 빈 리스트를 저장해도 예외가 발생하지 않는다")
    void saveAll_emptyList_noException() {
        // given
        List<GameLog> emptyLogs = Collections.emptyList();

        // when & then
        assertThatCode(() -> logJdbcRepository.saveAll(emptyLogs)).doesNotThrowAnyException();

        // batchUpdate가 호출되지 않음
        verify(jdbcTemplate, never())
                .batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
    }

    @Test
    @DisplayName("JDBC Batch Insert: PreparedStatement 파라미터가 올바르게 설정된다")
    void saveAll_preparedStatementParameters_setCorrectly() throws SQLException {
        // given
        GameLog log = createTestLogs(1).get(0);
        PreparedStatement mockPs = mock(PreparedStatement.class);

        ArgumentCaptor<BatchPreparedStatementSetter> setterCaptor =
                ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);

        when(jdbcTemplate.batchUpdate(anyString(), setterCaptor.capture())).thenReturn(new int[1]);

        // when
        logJdbcRepository.saveAll(List.of(log));

        // then
        BatchPreparedStatementSetter setter = setterCaptor.getValue();
        setter.setValues(mockPs, 0);

        // 파라미터 설정 검증
        verify(mockPs).setObject(1, log.getLogId()); // log_id
        verify(mockPs).setObject(2, log.getProjectId()); // project_id (UUID)
        verify(mockPs).setString(3, log.getSessionId()); // session_id
        verify(mockPs).setString(5, log.getSeverity().toString()); // severity
        verify(mockPs).setString(6, log.getEventCategory().toString()); // event_category
        verify(mockPs).setObject(8, log.getOccurredAt()); // occurred_at
        verify(mockPs).setString(13, "{}"); // resource (JSON)
        verify(mockPs).setString(14, "{}"); // attributes (JSON)
    }

    // ===== Helper Methods =====

    /**
     * 테스트용 GameLog 리스트 생성
     *
     * @param count 생성할 로그 개수
     * @return GameLog 리스트
     */
    private List<GameLog> createTestLogs(int count) {
        OffsetDateTime now = OffsetDateTime.now();
        return IntStream.range(0, count)
                .mapToObj(
                        i ->
                                GameLog.builder()
                                        .logId(UUID.randomUUID())
                                        .projectId(UUID.randomUUID())
                                        .sessionId("session-" + i)
                                        .userId("user-" + i)
                                        .severity(LogSeverity.INFO)
                                        .eventCategory(EventCategory.GAMEPLAY)
                                        .archive("Test log message " + i)
                                        .occurredAt(now)
                                        .ingestedAt(now)
                                        .traceId("trace-" + i)
                                        .spanId("span-" + i)
                                        .fingerprint("fingerprint-" + i)
                                        .resource(Map.of("key", "value"))
                                        .attributes(Map.of("action", "test"))
                                        .createdAt(now)
                                        .updatedAt(now)
                                        .build())
                .toList();
    }
}
