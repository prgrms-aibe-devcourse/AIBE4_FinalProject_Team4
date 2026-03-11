package kr.java.documind.domain.logprocessor.service.coldstorage;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("ColdStorageService 테이블명 검증 테스트 (격리)")
class ColdStorageServiceValidationTest {

    @Autowired private ColdStorageService coldStorageService;

    @Test
    @DisplayName("올바른 파티션 테이블명은 검증 통과")
    void validPartitionName_shouldPass() throws Exception {
        // given
        String validTableName = "game_log_2024_w10";

        // when & then - private 메서드를 직접 호출하여 검증만 테스트 (DB/S3 호출 안 함)
        assertThatCode(() -> invokeValidateMethod(validTableName)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("유효한 주차 범위 테스트 (w01 ~ w53)")
    void validWeekRange_shouldPass() throws Exception {
        // given
        String[] validWeeks = {"game_log_2024_w01", "game_log_2024_w10", "game_log_2024_w53"};

        // when & then
        for (String tableName : validWeeks) {
            assertThatCode(() -> invokeValidateMethod(tableName))
                    .as("Valid week: " + tableName)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("유효하지 않은 주차는 검증 실패 (w00, w54, w99)")
    void invalidWeekRange_shouldFail() throws Exception {
        // given
        String[] invalidWeeks = {"game_log_2024_w00", "game_log_2024_w54", "game_log_2024_w99"};

        // when & then
        for (String tableName : invalidWeeks) {
            assertThatThrownBy(() -> invokeValidateMethod(tableName))
                    .as("Invalid week: " + tableName)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid partition table name");
        }
    }

    /** private validatePartitionTableName() 메서드를 리플렉션으로 호출 (격리 테스트용) */
    private void invokeValidateMethod(String tableName) throws Exception {
        Method validateMethod =
                ColdStorageService.class.getDeclaredMethod(
                        "validatePartitionTableName", String.class);
        validateMethod.setAccessible(true);
        validateMethod.invoke(coldStorageService, tableName);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "game_log_2024_w10; DROP TABLE users; --", // SQL 인젝션 시도
                "game_log_2024_w10 UNION SELECT * FROM passwords", // UNION 공격
                "game_log_2024_w10' OR '1'='1", // OR 조건 주입
                "game_log_2024_w10\" --", // 따옴표 이스케이프 우회
                "game_log_2024_w10; DELETE FROM game_log; --", // 삭제 공격
                "game_log_2024_w10; UPDATE users SET role='admin'; --", // 업데이트 공격
                "game_log_2024_w99", // 잘못된 주차 (99주)
                "game_log_2024_w1", // 주차 포맷 오류 (w01이어야 함)
                "game_log_2024_m10", // 잘못된 접두사 (w가 아님)
                "gamelogs_2024_w10", // 테이블명 오타
                "../../../etc/passwd", // 경로 탐색 공격
                "", // 빈 문자열
                "null", // 문자열 "null"
                "game_log_2024_w10\n; DROP TABLE users; --", // 개행 문자 포함
                "game_log_2024_w10\u0000; DROP TABLE users; --" // NULL 바이트 공격
            })
    @DisplayName("SQL 인젝션 시도는 검증 실패")
    void sqlInjectionAttempt_shouldFail(String maliciousTableName) {
        // given
        LocalDate weekStartDate = LocalDate.of(2024, 3, 4);

        // when & then
        assertThatThrownBy(
                        () ->
                                coldStorageService.archivePartitionToS3(
                                        maliciousTableName, weekStartDate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid partition table name");
    }

    @Test
    @DisplayName("null 테이블명은 검증 실패")
    void nullTableName_shouldFail() {
        // given
        String nullTableName = null;
        LocalDate weekStartDate = LocalDate.of(2024, 3, 4);

        // when & then
        assertThatThrownBy(
                        () -> coldStorageService.archivePartitionToS3(nullTableName, weekStartDate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or empty");
    }

    @Test
    @DisplayName("복원 기능도 동일한 검증 적용")
    void restoreValidation_shouldWork() {
        // given
        String maliciousTableName = "game_log_2024_w10; DROP TABLE users; --";
        LocalDate weekStartDate = LocalDate.of(2024, 3, 4);

        // when & then
        assertThatThrownBy(
                        () -> coldStorageService.restoreFromS3(maliciousTableName, weekStartDate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid partition table name");
    }
}
