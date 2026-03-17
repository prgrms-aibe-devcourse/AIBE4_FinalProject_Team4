package kr.java.documind.domain.issue.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 분포 분석 개별 항목
 *
 * @param name 항목명 (예: "Windows", "v1.2.3", "Galaxy S21")
 * @param count 발생 횟수
 * @param percentage 비율 (0-100)
 */
@Schema(description = "분포 분석 개별 항목")
public record DistributionItem(
        @Schema(description = "항목명", example = "Windows") String name,
        @Schema(description = "발생 횟수", example = "45") Integer count,
        @Schema(description = "비율 (%)", example = "45") Integer percentage) {

    public static DistributionItem of(String name, Integer count, Integer total) {
        int percentage = total > 0 ? (int) Math.round((count * 100.0) / total) : 0;
        return new DistributionItem(name, count, percentage);
    }
}
