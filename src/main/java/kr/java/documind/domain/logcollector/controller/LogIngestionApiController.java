package kr.java.documind.domain.logcollector.controller;

import java.util.UUID;

import kr.java.documind.domain.logcollector.model.dto.RawLogRequest;
import kr.java.documind.domain.logcollector.service.LogIngestionService;
import kr.java.documind.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
    @PostMapping
    public ApiResponse<Void> ingestLog(
            @RequestAttribute("projectId") UUID projectId,
            @RequestBody RawLogRequest request) {

        logIngestionService.ingestLogToStream(projectId, request);

        return ApiResponse.success("로그 수집이 성공적으로 접수되었습니다.");
    }
}
