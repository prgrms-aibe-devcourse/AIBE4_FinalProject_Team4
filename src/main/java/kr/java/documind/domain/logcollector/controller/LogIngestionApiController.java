package kr.java.documind.domain.logcollector.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import kr.java.documind.domain.logcollector.model.dto.LogBatchRequest;
import kr.java.documind.domain.logcollector.model.dto.RawLogRequest;
import kr.java.documind.domain.logcollector.service.LogIngestionService;
import kr.java.documind.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Log Ingestion", description = "로그 수집 API")
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogIngestionApiController {

    private final LogIngestionService logIngestionService;

    /**
     * 클라이언트로부터 로그를 수신하여 비동기 적재한다
     *
     * @param projectId 필터(Api-Key 검증)에서 파악하여 넘겨준 신뢰할 수 있는 프로젝트 ID
     * @param request 클라이언트가 보낸 원본 로그 데이터 (내부의 projectId는 무시됨)
     */
    @Operation(
            summary = "게임 로그 다건(배치) 수집",
            description = "게임 클라이언트로부터 여러 개의 로그를 한 번에 수신하여 비동기 큐에 적재합니다. (헤더에 Api-Key 필요)")
    @PostMapping
    public ApiResponse<Void> ingestLogBatch(
            @RequestAttribute("projectId") UUID projectId,
            @Valid @RequestBody LogBatchRequest request) {

        logIngestionService.ingestLogBatchToStream(projectId, request);

        return ApiResponse.success(request.logs().size() + "건의 로그 수집이 성공적으로 접수되었습니다.");
    }

    private boolean isRawLogRequestEmpty(RawLogRequest request) {
        if (request == null) {
            return true;
        }
        return Stream.of(
                        request.sessionId(),
                        request.userId(),
                        request.severity(),
                        request.eventCategory(),
                        request.occurredAt(),
                        request.traceId(),
                        request.spanId(),
                        request.archive(),
                        request.resource(),
                        request.attributes())
                .allMatch(Objects::isNull);
    }
}
