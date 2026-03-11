package kr.java.documind.domain.issue.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import kr.java.documind.domain.issue.model.enums.IssueSeverity;
import kr.java.documind.domain.issue.model.enums.SeverityFactor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SeverityScore VO 테스트")
class SeverityScoreTest {

    @Nested
    @DisplayName("불변성 테스트")
    class ImmutabilityTest {

        @Test
        @DisplayName("scoreBreakdown Map은 수정 불가능")
        void scoreBreakdownShouldBeUnmodifiable() {
            // Given
            Map<SeverityFactor, Integer> breakdown =
                    Map.of(
                            SeverityFactor.CRASH, 50,
                            SeverityFactor.PLAYER_COUNT, 20);

            SeverityScore score =
                    SeverityScore.builder()
                            .severity(IssueSeverity.CRITICAL)
                            .totalScore(100)
                            .rawScore(140)
                            .scoreBreakdown(breakdown)
                            .reason("테스트")
                            .build();

            // When & Then
            assertThatThrownBy(() -> score.getScoreBreakdown().put(SeverityFactor.FREQUENCY, 10))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("getScoreByFactor() 테스트")
    class GetScoreByFactorTest {

        @Test
        @DisplayName("존재하는 요소의 점수를 올바르게 반환")
        void shouldReturnCorrectScoreForExistingFactor() {
            // Given
            Map<SeverityFactor, Integer> breakdown =
                    Map.of(
                            SeverityFactor.CRASH, 50,
                            SeverityFactor.BUSINESS_IMPACT, 30);

            SeverityScore score =
                    SeverityScore.builder()
                            .severity(IssueSeverity.CRITICAL)
                            .totalScore(80)
                            .rawScore(80)
                            .scoreBreakdown(breakdown)
                            .reason("테스트")
                            .build();

            // When
            int crashScore = score.getScoreByFactor(SeverityFactor.CRASH);
            int businessScore = score.getScoreByFactor(SeverityFactor.BUSINESS_IMPACT);

            // Then
            assertThat(crashScore).isEqualTo(50);
            assertThat(businessScore).isEqualTo(30);
        }

        @Test
        @DisplayName("존재하지 않는 요소는 0 반환")
        void shouldReturn0ForNonExistingFactor() {
            // Given
            Map<SeverityFactor, Integer> breakdown = Map.of(SeverityFactor.CRASH, 50);

            SeverityScore score =
                    SeverityScore.builder()
                            .severity(IssueSeverity.HIGH)
                            .totalScore(50)
                            .rawScore(50)
                            .scoreBreakdown(breakdown)
                            .reason("테스트")
                            .build();

            // When
            int frequencyScore = score.getScoreByFactor(SeverityFactor.FREQUENCY);

            // Then
            assertThat(frequencyScore).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("isCapped() 테스트")
    class IsCappedTest {

        @Test
        @DisplayName("rawScore가 100 초과하면 true 반환")
        void shouldReturnTrueWhenRawScoreExceeds100() {
            // Given
            SeverityScore score =
                    SeverityScore.builder()
                            .severity(IssueSeverity.CRITICAL)
                            .totalScore(100)
                            .rawScore(140)
                            .scoreBreakdown(Map.of())
                            .reason("캡핑됨")
                            .build();

            // When
            boolean isCapped = score.isCapped();

            // Then
            assertThat(isCapped).isTrue();
        }

        @Test
        @DisplayName("rawScore가 100 이하면 false 반환")
        void shouldReturnFalseWhenRawScoreIs100OrLess() {
            // Given
            SeverityScore score =
                    SeverityScore.builder()
                            .severity(IssueSeverity.HIGH)
                            .totalScore(75)
                            .rawScore(75)
                            .scoreBreakdown(Map.of())
                            .reason("캡핑 안됨")
                            .build();

            // When
            boolean isCapped = score.isCapped();

            // Then
            assertThat(isCapped).isFalse();
        }

        @Test
        @DisplayName("rawScore가 정확히 100이면 false 반환")
        void shouldReturnFalseWhenRawScoreIsExactly100() {
            // Given
            SeverityScore score =
                    SeverityScore.builder()
                            .severity(IssueSeverity.CRITICAL)
                            .totalScore(100)
                            .rawScore(100)
                            .scoreBreakdown(Map.of())
                            .reason("경계값")
                            .build();

            // When
            boolean isCapped = score.isCapped();

            // Then
            assertThat(isCapped).isFalse();
        }
    }

    @Nested
    @DisplayName("toString() 테스트")
    class ToStringTest {

        @Test
        @DisplayName("toString()은 주요 정보를 포함")
        void shouldIncludeKeyInformation() {
            // Given
            SeverityScore score =
                    SeverityScore.builder()
                            .severity(IssueSeverity.CRITICAL)
                            .totalScore(100)
                            .rawScore(140)
                            .scoreBreakdown(Map.of())
                            .reason("VIP 12명이 결제 실패")
                            .build();

            // When
            String result = score.toString();

            // Then
            assertThat(result)
                    .contains("CRITICAL")
                    .contains("100")
                    .contains("140")
                    .contains("VIP 12명이 결제 실패");
        }
    }

    @Nested
    @DisplayName("Builder 패턴 테스트")
    class BuilderTest {

        @Test
        @DisplayName("Builder로 정상적으로 객체 생성")
        void shouldBuildObjectSuccessfully() {
            // Given
            Map<SeverityFactor, Integer> breakdown =
                    Map.of(
                            SeverityFactor.CRASH, 50,
                            SeverityFactor.PLAYER_COUNT, 20,
                            SeverityFactor.BUSINESS_IMPACT, 30,
                            SeverityFactor.BLOCKING, 20,
                            SeverityFactor.FREQUENCY, 20);

            // When
            SeverityScore score =
                    SeverityScore.builder()
                            .severity(IssueSeverity.CRITICAL)
                            .totalScore(100)
                            .rawScore(140)
                            .scoreBreakdown(breakdown)
                            .reason("CRITICAL: 5가지 요소 모두 최대 점수")
                            .build();

            // Then
            assertThat(score.getSeverity()).isEqualTo(IssueSeverity.CRITICAL);
            assertThat(score.getTotalScore()).isEqualTo(100);
            assertThat(score.getRawScore()).isEqualTo(140);
            assertThat(score.getScoreBreakdown()).hasSize(5);
            assertThat(score.getReason()).contains("CRITICAL");
        }
    }
}
