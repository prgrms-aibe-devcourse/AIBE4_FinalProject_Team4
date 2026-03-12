package kr.java.documind.domain.issue.service.workflow;

import kr.java.documind.domain.issue.model.enums.IssueStatus;
import kr.java.documind.global.exception.BadRequestException;
import org.springframework.stereotype.Component;

/**
 * 이슈 상태 전환 규칙 검증기
 *
 * <p>허용되는 상태 전환:
 *
 * <ul>
 *   <li>TODO → IN_PROGRESS
 *   <li>IN_PROGRESS → RESOLVED
 *   <li>RESOLVED → IN_PROGRESS (재작업 필요 시)
 * </ul>
 *
 * <p>RECOMMENDED, REJECTED 상태는 IssueRecommendationService에서만 처리
 */
@Component
public class IssueWorkflowValidator {

    /**
     * 상태 전환 가능 여부 검증
     *
     * @param currentStatus 현재 상태
     * @param newStatus 변경하려는 상태
     * @throws BadRequestException 허용되지 않는 전환인 경우
     */
    public void validateStatusTransition(IssueStatus currentStatus, IssueStatus newStatus) {
        // RECOMMENDED, REJECTED는 일반 상태 변경 API에서 사용 불가
        if (currentStatus == IssueStatus.RECOMMENDED || currentStatus == IssueStatus.REJECTED) {
            throw new BadRequestException(
                    "추천 관련 상태는 IssueRecommendationService를 사용하세요. 현재 상태: "
                            + currentStatus.getValue());
        }

        if (newStatus == IssueStatus.RECOMMENDED || newStatus == IssueStatus.REJECTED) {
            throw new BadRequestException(
                    "추천 관련 상태로는 직접 변경할 수 없습니다. 대상 상태: " + newStatus.getValue());
        }

        if (currentStatus == newStatus) {
            throw new BadRequestException(
                    "현재 상태와 동일한 상태로는 변경할 수 없습니다: " + currentStatus.getValue());
        }

        boolean isValidTransition =
                switch (currentStatus) {
                    case TODO -> newStatus == IssueStatus.IN_PROGRESS;
                    case IN_PROGRESS -> newStatus == IssueStatus.RESOLVED;
                    case RESOLVED -> newStatus == IssueStatus.IN_PROGRESS; // 재작업 필요 시
                    default -> false;
                };

        if (!isValidTransition) {
            throw new BadRequestException(
                    String.format(
                            "허용되지 않는 상태 전환입니다: %s → %s",
                            currentStatus.getValue(), newStatus.getValue()));
        }
    }

    /**
     * 상태 전환 가능 여부 확인 (예외 없이 boolean 반환)
     *
     * @param currentStatus 현재 상태
     * @param newStatus 변경하려는 상태
     * @return 전환 가능 여부
     */
    public boolean canTransition(IssueStatus currentStatus, IssueStatus newStatus) {
        // RECOMMENDED, REJECTED는 일반 상태 변경 불가
        if (currentStatus == IssueStatus.RECOMMENDED || currentStatus == IssueStatus.REJECTED) {
            return false;
        }

        if (newStatus == IssueStatus.RECOMMENDED || newStatus == IssueStatus.REJECTED) {
            return false;
        }

        if (currentStatus == newStatus) {
            return false;
        }

        return switch (currentStatus) {
            case TODO -> newStatus == IssueStatus.IN_PROGRESS;
            case IN_PROGRESS -> newStatus == IssueStatus.RESOLVED;
            case RESOLVED -> newStatus == IssueStatus.IN_PROGRESS;
            default -> false;
        };
    }
}
