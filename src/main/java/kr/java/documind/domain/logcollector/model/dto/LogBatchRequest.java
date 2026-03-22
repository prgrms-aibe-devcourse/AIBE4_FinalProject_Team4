package kr.java.documind.domain.logcollector.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record LogBatchRequest(
        @NotEmpty(message = "로그 배열은 비어있을 수 없습니다.") @Valid List<RawLogRequest> logs) {}
