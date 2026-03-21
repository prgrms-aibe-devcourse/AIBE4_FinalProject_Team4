package kr.java.documind.domain.patchnote.service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.java.documind.domain.chatbot.infrastructure.ChatModelResolver;
import kr.java.documind.domain.chatbot.model.vo.ResolvedChatModel;
import kr.java.documind.domain.patchnote.infrastructure.PatchNoteSseConverter;
import kr.java.documind.domain.patchnote.model.dto.DraftResult;
import kr.java.documind.domain.patchnote.model.dto.DraftStreamRequest;
import kr.java.documind.domain.patchnote.model.dto.PatchNoteDraftResponse;
import kr.java.documind.domain.patchnote.model.dto.RagContext;
import kr.java.documind.domain.patchnote.model.entity.PendingItem;
import kr.java.documind.domain.patchnote.model.enums.PendingItemStatus;
import kr.java.documind.domain.patchnote.model.repository.PatchNoteRepository;
import kr.java.documind.domain.patchnote.model.repository.PendingItemRepository;
import kr.java.documind.domain.patchnote.util.PatchNoteOutputParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
@RequiredArgsConstructor
public class PatchNoteDraftService {

    /** LLM 스트리밍 최대 대기 시간. GitHub Models 등 응답이 느린 API 대응. */
    private static final Duration LLM_STREAM_TIMEOUT = Duration.ofMinutes(5);

    /** 프로젝트당 동시 초안 생성 방지 락. */
    private final Set<UUID> activeGenerations =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final PendingItemRepository pendingItemRepository;
    private final PatchNoteRepository patchNoteRepository;
    private final PatchNoteRagService patchNoteRagService;
    private final PatchNotePromptService patchNotePromptService;
    private final EvidenceReducer evidenceReducer;
    private final PatchNoteOutputParser outputParser;
    private final PatchNoteRefValidator refValidator;
    private final PatchNoteRenderer renderer;
    private final ChatModelResolver chatModelResolver;
    private final PatchNoteSseConverter sseConverter;

    /**
     * 패치노트 초안을 SSE 스트리밍으로 생성한다.
     *
     * <p>모든 예외는 {@code onErrorResume}으로 흡수해 {@code error} 이벤트로 변환한다. 전역 {@code
     * GlobalApiExceptionHandler}가 {@code text/event-stream} 응답에 JSON을 쓰는 상황이 발생하지 않는다.
     */
    public Flux<ServerSentEvent<String>> streamDraft(UUID projectId, DraftStreamRequest request) {

        // ── 버전 중복 확인 (overwrite 모드에서는 스킵) ─────────────────────────
        if (!request.overwrite()
                && patchNoteRepository.existsByVersionAndNotDeleted(
                        projectId,
                        request.majorVersion(),
                        request.minorVersion(),
                        request.patchVersion())) {
            return Flux.just(
                    sseConverter.errorEvent(
                            "이미 존재하는 버전입니다. v%d.%d.%d"
                                    .formatted(
                                            request.majorVersion(),
                                            request.minorVersion(),
                                            request.patchVersion())));
        }

        // ── PENDING 항목 조회 ──────────────────────────────────────────────────
        List<PendingItem> items =
                pendingItemRepository.findByProjectIdAndStatus(
                        projectId, PendingItemStatus.PENDING);
        if (items.isEmpty()) {
            return Flux.just(sseConverter.errorEvent("패치노트에 포함할 대기 중인 항목이 없습니다."));
        }

        // ── 동시 생성 락 ───────────────────────────────────────────────────────
        if (!activeGenerations.add(projectId)) {
            return Flux.just(sseConverter.errorEvent("현재 다른 사용자가 초안을 생성 중입니다. 잠시 후 다시 시도해주세요."));
        }

        return buildPipelineFlux(projectId, items, request)
                .doFinally(signal -> activeGenerations.remove(projectId))
                .onErrorResume(
                        e -> {
                            log.error("패치노트 초안 생성 실패 — projectId={}", projectId, e);
                            return Flux.just(
                                    sseConverter.errorEvent(
                                            "초안 생성 중 오류가 발생했습니다: " + e.getMessage()));
                        });
    }

    /**
     * RAG 컨텍스트 빌드 → LLM 스트리밍 → 후처리 → done 이벤트를 하나의 Flux로 연결한다.
     *
     * <p>{@code Flux.create}로 명령형 로직과 반응형 LLM 스트리밍을 브리징한다. {@code
     * subscribeOn(Schedulers.boundedElastic())}으로 블로킹 작업(RAG, blockLast)을 HTTP 요청 스레드가 아닌 전용 스레드에서
     * 실행한다.
     */
    private Flux<ServerSentEvent<String>> buildPipelineFlux(
            UUID projectId, List<PendingItem> items, DraftStreamRequest request) {

        return Flux.<ServerSentEvent<String>>create(
                        sink -> {
                            try {
                                // ── Step 1–2. RAG 컨텍스트 빌드 ─────────────────
                                sink.next(sseConverter.progressEvent("BUILDING_CONTEXT"));
                                RagContext ragContext =
                                        patchNoteRagService.buildContext(projectId, items);

                                if (!ragContext.sourceRefs().isEmpty()) {
                                    sink.next(sseConverter.sourcesEvent(ragContext.sourceRefs()));
                                }

                                // ── Step 3. 컨텍스트 오버플로우 처리 ────────────
                                if (ragContext.tokenEstimation().exceeded()) {
                                    int beforePercent =
                                            (int)
                                                    Math.round(
                                                            ragContext
                                                                            .tokenEstimation()
                                                                            .usageRatio()
                                                                    * 100);
                                    ragContext = evidenceReducer.reduce(ragContext);
                                    int afterPercent =
                                            (int)
                                                    Math.round(
                                                            ragContext
                                                                            .tokenEstimation()
                                                                            .usageRatio()
                                                                    * 100);
                                    log.info(
                                            "컨텍스트 감소 완료 — projectId={}, 감소 전: {}%, 감소 후: {}%",
                                            projectId, beforePercent, afterPercent);
                                    sink.next(
                                            sseConverter.contextOverflowEvent(
                                                    beforePercent, afterPercent));
                                }

                                // ── Step 4. 프롬프트 빌드 ───────────────────────
                                String systemPrompt =
                                        patchNotePromptService.buildSystemPrompt(ragContext);
                                String userPrompt =
                                        patchNotePromptService.buildUserPrompt(
                                                request.majorVersion(),
                                                request.minorVersion(),
                                                request.patchVersion(),
                                                request.additionalPrompt());

                                ResolvedChatModel resolvedModel =
                                        chatModelResolver.resolveForPatchNote(request.modelAlias());

                                sink.next(sseConverter.progressEvent("GENERATING"));

                                // ── Step 5. LLM 스트리밍 호출 ──────────────────
                                // boundedElastic 스레드에서 blockLast() 블로킹 허용.
                                // doOnNext는 LLM HTTP 응답 스레드에서 실행되며,
                                // FluxSink는 스레드 안전하므로 sink.next() 호출이 안전하다.
                                final StringBuilder rawOutput = new StringBuilder();
                                final RagContext finalRagContext = ragContext;

                                ChatClient.create(resolvedModel.chatModel())
                                        .prompt()
                                        .system(systemPrompt)
                                        .user(userPrompt)
                                        .options(resolvedModel.chatOptions())
                                        .stream()
                                        .chatClientResponse()
                                        .doOnNext(
                                                response -> {
                                                    String text = extractText(response);
                                                    if (text != null && !text.isEmpty()) {
                                                        rawOutput.append(text);
                                                        sink.next(sseConverter.tokenEvent(text));
                                                    }
                                                })
                                        .blockLast(LLM_STREAM_TIMEOUT);

                                // ── Step 6. JSON 파싱 (fail-safe) ───────────────
                                PatchNoteDraftResponse parsed =
                                        outputParser.parse(rawOutput.toString());

                                // ── Step 7. 소스 REF 검증 (환각 REF 제거) ───────
                                PatchNoteDraftResponse validated =
                                        refValidator.validate(parsed, finalRagContext);

                                // ── Step 8. 서버 사이드 렌더링 ──────────────────
                                DraftResult result = renderer.render(validated);

                                // ── Step 9. 완료 전송 ────────────────────────────
                                sink.next(sseConverter.doneEvent(result));
                                sink.complete();

                            } catch (Exception e) {
                                sink.error(e);
                            }
                        })
                // 블로킹 RAG 작업과 blockLast()를 boundedElastic 스레드에서 실행
                .subscribeOn(Schedulers.boundedElastic());
    }

    private String extractText(ChatClientResponse response) {
        return Optional.ofNullable(response.chatResponse())
                .map(r -> r.getResult())
                .map(r -> r.getOutput())
                .map(r -> r.getText())
                .filter(text -> !text.isEmpty())
                .orElse(null);
    }
}
