package kr.java.documind.domain.issue.model.enums;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("IssueSeverity 테스트")
class IssueSeverityTest {

    @Nested
    @DisplayName("fromScore() - 점수 기반 등급 자동 판별")
    class FromScoreTest {

        @ParameterizedTest
        @CsvSource({
            "90, CRITICAL",
            "95, CRITICAL",
            "100, CRITICAL",
            "60, HIGH",
            "75, HIGH",
            "89, HIGH",
            "30, MEDIUM",
            "45, MEDIUM",
            "59, MEDIUM",
            "0, LOW",
            "15, LOW",
            "29, LOW"
        })
        @DisplayName("점수 범위에 따라 올바른 등급 반환")
        void shouldReturnCorrectSeverity(int score, IssueSeverity expected) {
            // When
            IssueSeverity result = IssueSeverity.fromScore(score);

            // Then
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("경계값 테스트 - 89점은 HIGH")
        void shouldReturnHighFor89Points() {
            // When
            IssueSeverity result = IssueSeverity.fromScore(89);

            // Then
            assertThat(result).isEqualTo(IssueSeverity.HIGH);
        }

        @Test
        @DisplayName("경계값 테스트 - 90점은 CRITICAL")
        void shouldReturnCriticalFor90Points() {
            // When
            IssueSeverity result = IssueSeverity.fromScore(90);

            // Then
            assertThat(result).isEqualTo(IssueSeverity.CRITICAL);
        }

        @Test
        @DisplayName("경계값 테스트 - 59점은 MEDIUM")
        void shouldReturnMediumFor59Points() {
            // When
            IssueSeverity result = IssueSeverity.fromScore(59);

            // Then
            assertThat(result).isEqualTo(IssueSeverity.MEDIUM);
        }

        @Test
        @DisplayName("경계값 테스트 - 60점은 HIGH")
        void shouldReturnHighFor60Points() {
            // When
            IssueSeverity result = IssueSeverity.fromScore(60);

            // Then
            assertThat(result).isEqualTo(IssueSeverity.HIGH);
        }

        @Test
        @DisplayName("점수가 0 미만이면 예외 발생")
        void shouldThrowExceptionForNegativeScore() {
            // When & Then
            assertThatThrownBy(() -> IssueSeverity.fromScore(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Score must be between 0 and 100");
        }

        @Test
        @DisplayName("점수가 100 초과하면 예외 발생")
        void shouldThrowExceptionForScoreOver100() {
            // When & Then
            assertThatThrownBy(() -> IssueSeverity.fromScore(101))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Score must be between 0 and 100");
        }
    }

    @Nested
    @DisplayName("fromString() - 문자열 변환")
    class FromStringTest {

        @Test
        @DisplayName("대문자 문자열을 올바르게 변환")
        void shouldConvertUpperCaseString() {
            // When
            IssueSeverity result = IssueSeverity.fromString("CRITICAL");

            // Then
            assertThat(result).isEqualTo(IssueSeverity.CRITICAL);
        }

        @Test
        @DisplayName("소문자 문자열을 올바르게 변환 (대소문자 무시)")
        void shouldConvertLowerCaseString() {
            // When
            IssueSeverity result = IssueSeverity.fromString("high");

            // Then
            assertThat(result).isEqualTo(IssueSeverity.HIGH);
        }

        @Test
        @DisplayName("공백이 포함된 문자열을 올바르게 변환")
        void shouldConvertStringWithWhitespace() {
            // When
            IssueSeverity result = IssueSeverity.fromString("  MEDIUM  ");

            // Then
            assertThat(result).isEqualTo(IssueSeverity.MEDIUM);
        }

        @Test
        @DisplayName("null이면 MEDIUM 반환 (기본값)")
        void shouldReturnMediumForNull() {
            // When
            IssueSeverity result = IssueSeverity.fromString(null);

            // Then
            assertThat(result).isEqualTo(IssueSeverity.MEDIUM);
        }

        @Test
        @DisplayName("빈 문자열이면 MEDIUM 반환 (기본값)")
        void shouldReturnMediumForEmptyString() {
            // When
            IssueSeverity result = IssueSeverity.fromString("");

            // Then
            assertThat(result).isEqualTo(IssueSeverity.MEDIUM);
        }

        @Test
        @DisplayName("지원하지 않는 문자열이면 예외 발생")
        void shouldThrowExceptionForUnsupportedString() {
            // When & Then
            assertThatThrownBy(() -> IssueSeverity.fromString("UNKNOWN"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown issue severity");
        }
    }

    @Nested
    @DisplayName("Getter 메서드")
    class GetterTest {

        @Test
        @DisplayName("CRITICAL의 최소/최대 점수 확인")
        void shouldReturnCorrectScoreRangeForCritical() {
            // When
            int minScore = IssueSeverity.CRITICAL.getMinScore();
            int maxScore = IssueSeverity.CRITICAL.getMaxScore();

            // Then
            assertThat(minScore).isEqualTo(90);
            assertThat(maxScore).isEqualTo(100);
        }

        @Test
        @DisplayName("HIGH의 최소/최대 점수 확인")
        void shouldReturnCorrectScoreRangeForHigh() {
            // When
            int minScore = IssueSeverity.HIGH.getMinScore();
            int maxScore = IssueSeverity.HIGH.getMaxScore();

            // Then
            assertThat(minScore).isEqualTo(60);
            assertThat(maxScore).isEqualTo(89);
        }
    }

    @Nested
    @DisplayName("JSON 직렬화")
    class JsonSerializationTest {

        @Test
        @DisplayName("getValue()는 대문자 문자열 반환")
        void shouldReturnUpperCaseValue() {
            // When
            String value = IssueSeverity.CRITICAL.getValue();

            // Then
            assertThat(value).isEqualTo("CRITICAL");
        }

        @Test
        @DisplayName("toString()은 getValue()와 동일")
        void shouldReturnSameValueAsToString() {
            // When
            String value = IssueSeverity.HIGH.getValue();
            String toString = IssueSeverity.HIGH.toString();

            // Then
            assertThat(value).isEqualTo(toString);
        }
    }
}
