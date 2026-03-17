package kr.java.documind.domain.issue.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 이슈 분포 분석 데이터
 *
 * <p>OS, 앱 버전, 디바이스별 발생 분포
 */
@Schema(description = "이슈 분포 분석 데이터")
public record DistributionDataResponse(
        @Schema(description = "운영체제별 분포") List<DistributionItem> os,
        @Schema(description = "앱 버전별 분포") List<DistributionItem> version,
        @Schema(description = "디바이스별 분포") List<DistributionItem> device) {}
