package kr.java.documind.domain.issue.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 근본 원인 분석 응답
 *
 * <p>이슈의 근본 원인 및 해결 방법 제시
 */
@Schema(description = "근본 원인 분석 응답")
public record RootCauseAnalysisResponse(
        @Schema(description = "에러 타입", example = "NULL_POINTER") String errorType,
        @Schema(description = "에러 설명", example = "NullPointerException") String errorDescription,
        @Schema(description = "가능한 원인 목록") List<String> possibleCauses,
        @Schema(description = "권장 해결책 목록") List<String> solutions,
        @Schema(description = "발견된 패턴 목록") List<PatternInfo> patterns,
        @Schema(description = "핵심 코드 위치", example = "UserService.java:42") String hotspot,
        @Schema(description = "유사 해결 사례") SimilarResolution similarResolution) {

    /** 패턴 정보 */
    @Schema(description = "패턴 정보")
    public record PatternInfo(
            @Schema(description = "패턴 타입", example = "TIME") String type, // TIME, USER, ENVIRONMENT
            @Schema(description = "패턴 설명", example = "주로 02:00~04:00에 발생 (전체의 73%)")
                    String description) {}

    /** 유사 해결 사례 */
    @Schema(description = "유사 해결 사례")
    public record SimilarResolution(
            @Schema(description = "유사 이슈 ID", example = "123") Long issueId,
            @Schema(description = "유사 이슈 제목", example = "NullPointerException in UserService")
                    String issueTitle,
            @Schema(description = "해결 방법", example = "Optional 적용으로 해결") String resolutionNote) {}
}
