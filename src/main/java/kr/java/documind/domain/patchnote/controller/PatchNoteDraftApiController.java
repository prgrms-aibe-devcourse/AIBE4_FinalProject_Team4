package kr.java.documind.domain.patchnote.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.java.documind.domain.auth.model.dto.ProjectRequestContext;
import kr.java.documind.domain.patchnote.model.dto.DraftStreamRequest;
import kr.java.documind.domain.patchnote.service.PatchNoteDraftService;
import kr.java.documind.global.annotation.CurrentProject;
import kr.java.documind.global.annotation.RequireProjectMember;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 패치노트 초안 생성 API.
 *
 * <p>SSE 스트리밍 방식으로 LLM 초안을 실시간 전달한다.
 *
 * <p>{@code Flux<ServerSentEvent<String>>} 반환 타입을 사용한다. 서비스 레이어의 {@code onErrorResume}이 모든 예외를
 * {@code error} 이벤트로 변환하므로, 전역 예외 핸들러({@code GlobalApiExceptionHandler})가 {@code text/event-stream}
 * 응답에 JSON을 쓰는 상황이 발생하지 않는다.
 *
 * <p>모든 엔드포인트는 {@code @RequireProjectMember}를 통해 프로젝트 멤버만 접근 가능하다.
 */
@Tag(name = "PatchNote Draft", description = "패치노트 초안 SSE 스트리밍 생성 API")
@RestController
@RequestMapping("/api/projects/{publicId}/patch-note/drafts")
@RequiredArgsConstructor
@RequireProjectMember
public class PatchNoteDraftApiController {

    private final PatchNoteDraftService patchNoteDraftService;

    @Operation(
            summary = "패치노트 초안 SSE 스트리밍 생성",
            description =
                    "PENDING 상태의 피드 항목을 기반으로 LLM이 패치노트 초안을 실시간 스트리밍합니다. "
                            + "progress → sources → token(N) → done 이벤트 순서로 전달됩니다. "
                            + "버전 중복 또는 항목 없음 시 error 이벤트를 전송하고 스트림을 종료합니다.")
    @PatchNoteDraftSwaggerDocs.StreamDraftDocs
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamDraft(
            @CurrentProject ProjectRequestContext ctx, @RequestBody DraftStreamRequest request) {

        return patchNoteDraftService.streamDraft(ctx.projectId(), request);
    }
}
