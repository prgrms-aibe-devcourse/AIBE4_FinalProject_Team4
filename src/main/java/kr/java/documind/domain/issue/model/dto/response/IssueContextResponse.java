package kr.java.documind.domain.issue.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * 이슈 발생 맥락 정보
 *
 * <p>이슈가 발생한 환경과 게임 상태 정보
 */
@Schema(description = "이슈 발생 맥락 정보")
public record IssueContextResponse(
        @Schema(description = "가장 빈번한 환경 정보") EnvironmentInfo mostFrequentEnvironment,
        @Schema(description = "대표 게임 상태 예시") GameStateExample gameStateExample,
        @Schema(description = "공통 속성 (반복 패턴)") Map<String, String> commonAttributes) {

    /** 환경 정보 */
    public record EnvironmentInfo(
            @Schema(description = "OS", example = "iOS 16.0") String os,
            @Schema(description = "디바이스", example = "iPhone 14 Pro") String device,
            @Schema(description = "앱 버전", example = "1.2.3") String appVersion,
            @Schema(description = "발생 비율", example = "65") Integer percentage) {}

    /** 게임 상태 예시 */
    public record GameStateExample(
            @Schema(description = "플레이어 레벨", example = "42") String playerLevel,
            @Schema(description = "현재 스테이지", example = "던전 5-3") String currentStage,
            @Schema(description = "보유 재화", example = "골드 15,000") String currency,
            @Schema(description = "기타 상태 정보") Map<String, Object> additionalState) {}
}
