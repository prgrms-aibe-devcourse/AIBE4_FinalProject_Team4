package kr.java.documind.domain.issue.service.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kr.java.documind.domain.issue.model.enums.IssueStatus;
import kr.java.documind.global.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 이슈 상태 전환 규칙 검증기 테스트
 *
 * <p>상태 전환 규칙 위반 차단 테스트
 */
@DisplayName("IssueWorkflowValidator 테스트")
class IssueWorkflowValidatorTest {

    private IssueWorkflowValidator validator;

    @BeforeEach
    void setUp() {
        validator = new IssueWorkflowValidator();
    }

    @Nested
    @DisplayName("허용되는 상태 전환")
    class ValidTransitions {

        @Test
        @DisplayName("TODO → IN_PROGRESS 전환 성공")
        void todoToInProgress() {
            // given
            IssueStatus currentStatus = IssueStatus.TODO;
            IssueStatus newStatus = IssueStatus.IN_PROGRESS;

            // when & then
            validator.validateStatusTransition(currentStatus, newStatus);
            assertThat(validator.canTransition(currentStatus, newStatus)).isTrue();
        }

        @Test
        @DisplayName("IN_PROGRESS → RESOLVED 전환 성공")
        void inProgressToResolved() {
            // given
            IssueStatus currentStatus = IssueStatus.IN_PROGRESS;
            IssueStatus newStatus = IssueStatus.RESOLVED;

            // when & then
            validator.validateStatusTransition(currentStatus, newStatus);
            assertThat(validator.canTransition(currentStatus, newStatus)).isTrue();
        }

        @Test
        @DisplayName("RESOLVED → IN_PROGRESS 전환 성공 (재작업)")
        void resolvedToInProgress() {
            // given
            IssueStatus currentStatus = IssueStatus.RESOLVED;
            IssueStatus newStatus = IssueStatus.IN_PROGRESS;

            // when & then
            validator.validateStatusTransition(currentStatus, newStatus);
            assertThat(validator.canTransition(currentStatus, newStatus)).isTrue();
        }
    }

    @Nested
    @DisplayName("차단되는 상태 전환")
    class InvalidTransitions {

        @Test
        @DisplayName("TODO → RESOLVED 직접 전환 차단")
        void todoToResolvedBlocked() {
            // given
            IssueStatus currentStatus = IssueStatus.TODO;
            IssueStatus newStatus = IssueStatus.RESOLVED;

            // when & then
            assertThatThrownBy(() -> validator.validateStatusTransition(currentStatus, newStatus))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("허용되지 않는 상태 전환입니다");

            assertThat(validator.canTransition(currentStatus, newStatus)).isFalse();
        }

        @Test
        @DisplayName("RESOLVED → TODO 전환 차단")
        void resolvedToTodoBlocked() {
            // given
            IssueStatus currentStatus = IssueStatus.RESOLVED;
            IssueStatus newStatus = IssueStatus.TODO;

            // when & then
            assertThatThrownBy(() -> validator.validateStatusTransition(currentStatus, newStatus))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("허용되지 않는 상태 전환입니다");

            assertThat(validator.canTransition(currentStatus, newStatus)).isFalse();
        }

        @Test
        @DisplayName("IN_PROGRESS → TODO 역행 차단")
        void inProgressToTodoBlocked() {
            // given
            IssueStatus currentStatus = IssueStatus.IN_PROGRESS;
            IssueStatus newStatus = IssueStatus.TODO;

            // when & then
            assertThatThrownBy(() -> validator.validateStatusTransition(currentStatus, newStatus))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("허용되지 않는 상태 전환입니다");

            assertThat(validator.canTransition(currentStatus, newStatus)).isFalse();
        }

        @Test
        @DisplayName("동일 상태로 변경 차단")
        void sameStatusBlocked() {
            // given
            IssueStatus currentStatus = IssueStatus.TODO;
            IssueStatus newStatus = IssueStatus.TODO;

            // when & then
            assertThatThrownBy(() -> validator.validateStatusTransition(currentStatus, newStatus))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("현재 상태와 동일한 상태로는 변경할 수 없습니다");

            assertThat(validator.canTransition(currentStatus, newStatus)).isFalse();
        }
    }

    @Nested
    @DisplayName("RECOMMENDED/REJECTED 상태 차단")
    class RecommendationStatusBlocked {

        @Test
        @DisplayName("RECOMMENDED 상태에서 변경 차단")
        void fromRecommendedBlocked() {
            // given
            IssueStatus currentStatus = IssueStatus.RECOMMENDED;
            IssueStatus newStatus = IssueStatus.TODO;

            // when & then
            assertThatThrownBy(() -> validator.validateStatusTransition(currentStatus, newStatus))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("추천 관련 상태는 IssueRecommendationService를 사용하세요");

            assertThat(validator.canTransition(currentStatus, newStatus)).isFalse();
        }

        @Test
        @DisplayName("REJECTED 상태에서 변경 차단")
        void fromRejectedBlocked() {
            // given
            IssueStatus currentStatus = IssueStatus.REJECTED;
            IssueStatus newStatus = IssueStatus.TODO;

            // when & then
            assertThatThrownBy(() -> validator.validateStatusTransition(currentStatus, newStatus))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("추천 관련 상태는 IssueRecommendationService를 사용하세요");

            assertThat(validator.canTransition(currentStatus, newStatus)).isFalse();
        }

        @Test
        @DisplayName("RECOMMENDED 상태로 직접 변경 차단")
        void toRecommendedBlocked() {
            // given
            IssueStatus currentStatus = IssueStatus.TODO;
            IssueStatus newStatus = IssueStatus.RECOMMENDED;

            // when & then
            assertThatThrownBy(() -> validator.validateStatusTransition(currentStatus, newStatus))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("추천 관련 상태로는 직접 변경할 수 없습니다");

            assertThat(validator.canTransition(currentStatus, newStatus)).isFalse();
        }

        @Test
        @DisplayName("REJECTED 상태로 직접 변경 차단")
        void toRejectedBlocked() {
            // given
            IssueStatus currentStatus = IssueStatus.TODO;
            IssueStatus newStatus = IssueStatus.REJECTED;

            // when & then
            assertThatThrownBy(() -> validator.validateStatusTransition(currentStatus, newStatus))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("추천 관련 상태로는 직접 변경할 수 없습니다");

            assertThat(validator.canTransition(currentStatus, newStatus)).isFalse();
        }
    }
}
