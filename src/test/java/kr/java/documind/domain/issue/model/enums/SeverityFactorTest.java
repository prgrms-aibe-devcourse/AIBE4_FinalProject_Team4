package kr.java.documind.domain.issue.model.enums;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SeverityFactor 테스트")
class SeverityFactorTest {

    @Test
    @DisplayName("CRASH 최대 점수는 50점")
    void crashMaxScoreShouldBe50() {
        // When
        int maxScore = SeverityFactor.CRASH.getMaxScore();

        // Then
        assertThat(maxScore).isEqualTo(50);
    }

    @Test
    @DisplayName("PLAYER_COUNT 최대 점수는 20점")
    void playerCountMaxScoreShouldBe20() {
        // When
        int maxScore = SeverityFactor.PLAYER_COUNT.getMaxScore();

        // Then
        assertThat(maxScore).isEqualTo(20);
    }

    @Test
    @DisplayName("BUSINESS_IMPACT 최대 점수는 30점")
    void businessImpactMaxScoreShouldBe30() {
        // When
        int maxScore = SeverityFactor.BUSINESS_IMPACT.getMaxScore();

        // Then
        assertThat(maxScore).isEqualTo(30);
    }

    @Test
    @DisplayName("BLOCKING 최대 점수는 20점")
    void blockingMaxScoreShouldBe20() {
        // When
        int maxScore = SeverityFactor.BLOCKING.getMaxScore();

        // Then
        assertThat(maxScore).isEqualTo(20);
    }

    @Test
    @DisplayName("FREQUENCY 최대 점수는 20점")
    void frequencyMaxScoreShouldBe20() {
        // When
        int maxScore = SeverityFactor.FREQUENCY.getMaxScore();

        // Then
        assertThat(maxScore).isEqualTo(20);
    }

    @Test
    @DisplayName("전체 최대 점수 합계는 140점")
    void totalMaxScoreShouldBe140() {
        // When
        int totalMaxScore = SeverityFactor.getTotalMaxScore();

        // Then
        assertThat(totalMaxScore).isEqualTo(140);
    }

    @Test
    @DisplayName("모든 요소는 한글 설명을 가짐")
    void allFactorsShouldHaveDescription() {
        // When & Then
        for (SeverityFactor factor : SeverityFactor.values()) {
            assertThat(factor.getDescription())
                    .isNotNull()
                    .isNotEmpty()
                    .matches(".*[가-힣]+.*"); // 한글 포함 확인
        }
    }

    @Test
    @DisplayName("CRASH의 설명은 '게임 크래시 여부'")
    void crashDescriptionShouldBeCorrect() {
        // When
        String description = SeverityFactor.CRASH.getDescription();

        // Then
        assertThat(description).isEqualTo("게임 크래시 여부");
    }
}
