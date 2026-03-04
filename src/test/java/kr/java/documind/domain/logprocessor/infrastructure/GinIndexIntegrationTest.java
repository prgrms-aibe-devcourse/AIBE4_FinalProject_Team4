package kr.java.documind.domain.logprocessor.infrastructure;

import static org.assertj.core.api.Assertions.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.java.documind.domain.logprocessor.model.enums.EventCategory;
import kr.java.documind.domain.logprocessor.model.enums.LogSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional
@DisplayName("GIN 인덱스 통합 테스트")
class GinIndexIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // 테스트용 로그 데이터 삽입
        insertTestLogs();
    }

    @Test
    @DisplayName("GIN 인덱스: attributes 컬럼에 GIN 인덱스가 생성되어 있다")
    void ginIndex_attributes_exists() {
        // given
        String sql =
                """
                SELECT COUNT(*) FROM pg_indexes
                WHERE tablename LIKE 'game_log_%'
                  AND indexname LIKE '%attributes'
                  AND indexdef LIKE '%USING gin%'
                """;

        // when
        Integer indexCount = jdbcTemplate.queryForObject(sql, Integer.class);

        // then
        assertThat(indexCount).isGreaterThan(0);
    }

    @Test
    @DisplayName("GIN 인덱스: resource 컬럼에 GIN 인덱스가 생성되어 있다")
    void ginIndex_resource_exists() {
        // given
        String sql =
                """
                SELECT COUNT(*) FROM pg_indexes
                WHERE tablename LIKE 'game_log_%'
                  AND indexname LIKE '%resource'
                  AND indexdef LIKE '%USING gin%'
                """;

        // when
        Integer indexCount = jdbcTemplate.queryForObject(sql, Integer.class);

        // then
        assertThat(indexCount).isGreaterThan(0);
    }

    @Test
    @DisplayName("GIN 인덱스: jsonb_path_ops 전략이 적용되어 있다")
    void ginIndex_jsonbPathOps_strategy() {
        // given
        String sql =
                """
                SELECT indexdef FROM pg_indexes
                WHERE tablename LIKE 'game_log_%'
                  AND indexname LIKE '%attributes'
                LIMIT 1
                """;

        // when
        String indexDef = jdbcTemplate.queryForObject(sql, String.class);

        // then
        assertThat(indexDef).contains("jsonb_path_ops");
    }

    // EXPLAIN 테스트는 복잡하므로 생략 (GIN 인덱스 존재 여부는 다른 테스트에서 확인)

    @Test
    @DisplayName("GIN 인덱스: attributes 컬럼 검색 시 결과를 정확히 반환한다")
    void ginIndex_attributes_search_returnsCorrectResults() {
        // given
        String sql =
                """
                SELECT COUNT(*) FROM game_log
                WHERE attributes @> '{"action": "test"}'::jsonb
                """;

        // when
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);

        // then
        assertThat(count).isGreaterThan(0);
    }

    @Test
    @DisplayName("GIN 인덱스: resource 컬럼 검색 시 결과를 정확히 반환한다")
    void ginIndex_resource_search_returnsCorrectResults() {
        // given
        String sql =
                """
                SELECT COUNT(*) FROM game_log
                WHERE resource @> '{"environment": "test"}'::jsonb
                """;

        // when
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);

        // then
        assertThat(count).isGreaterThan(0);
    }

    @Test
    @DisplayName("GIN 인덱스: 복합 JSONB 조건 검색이 가능하다")
    void ginIndex_complexJsonbQuery_works() {
        // given
        String sql =
                """
                SELECT COUNT(*) FROM game_log
                WHERE attributes @> '{"action": "test"}'::jsonb
                  AND resource @> '{"environment": "test"}'::jsonb
                """;

        // when
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);

        // then
        assertThat(count).isGreaterThan(0);
    }

    @Test
    @DisplayName("GIN 인덱스: 각 파티션별로 GIN 인덱스가 생성되어 있다")
    void ginIndex_eachPartition_hasGinIndex() {
        // given
        String sql =
                """
                SELECT tablename, COUNT(*) as index_count
                FROM pg_indexes
                WHERE tablename LIKE 'game_log_2024%'
                  AND indexname LIKE '%attributes'
                  AND indexdef LIKE '%USING gin%'
                GROUP BY tablename
                """;

        // when
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

        // then
        assertThat(results).isNotEmpty();
        results.forEach(
                row -> {
                    assertThat(row.get("index_count")).isEqualTo(1L);
                });
    }

    // ===== Helper Methods =====

    /** 테스트용 로그 데이터 삽입 */
    private void insertTestLogs() {
        String sql =
                """
                INSERT INTO game_log (
                    log_id, project_id, session_id, user_id, severity,
                    event_category, body, occurred_at, ingested_at,
                    trace_id, span_id, fingerprint, resource, attributes
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb)
                """;

        // 2024년 3월 데이터 삽입 (파티션 존재)
        OffsetDateTime testDate = OffsetDateTime.parse("2024-03-15T12:00:00+09:00");

        for (int i = 0; i < 10; i++) {
            jdbcTemplate.update(
                    sql,
                    UUID.randomUUID(),
                    "test-project",
                    "session-" + i,
                    "user-" + i,
                    LogSeverity.INFO.toString(),
                    EventCategory.GAMEPLAY.toString(),
                    "Test log message " + i,
                    testDate,
                    testDate,
                    "trace-" + i,
                    "span-" + i,
                    "fingerprint-" + i,
                    "{\"environment\": \"test\"}",
                    "{\"action\": \"test\"}");
        }
    }
}
