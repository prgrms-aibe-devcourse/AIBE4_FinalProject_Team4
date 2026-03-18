package kr.java.documind.domain.patchnote.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import kr.java.documind.domain.chatbot.infrastructure.ChatModelResolver;
import kr.java.documind.domain.chatbot.model.vo.ResolvedChatModel;
import kr.java.documind.domain.patchnote.model.dto.DraftResult;
import kr.java.documind.domain.patchnote.model.dto.DraftStreamRequest;
import kr.java.documind.domain.patchnote.model.dto.RagContext;
import kr.java.documind.domain.patchnote.model.dto.TokenEstimation;
import kr.java.documind.domain.patchnote.model.entity.PendingItem;
import kr.java.documind.domain.patchnote.model.enums.PendingItemStatus;
import kr.java.documind.domain.patchnote.model.repository.PatchNoteRepository;
import kr.java.documind.domain.patchnote.model.repository.PendingItemRepository;
import kr.java.documind.domain.patchnote.util.SourceTagParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

@Slf4j
@Service
@RequiredArgsConstructor
public class PatchNoteDraftService {

    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1_000L;

    /**
     * 프로젝트별 초안 생성 진행 여부를 추적하는 in-memory 락.
     *
     * <p>동일 프로젝트에서 두 명의 사용자가 동시에 초안을 생성하면 LLM 호출이 중복되고
     * 버전 충돌 위험이 높아진다. {@code ConcurrentHashMap.newKeySet()}으로 thread-safe Set을 구성하여
     * 동시 생성을 방지한다. 앱 재시작 시 락은 자동으로 초기화된다.
     */
    private final Set<UUID> activeGenerations = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final PendingItemRepository pendingItemRepository;

    private final PatchNoteRepository patchNoteRepository;

    private final PatchNoteRagService patchNoteRagService;

    private final PatchNotePromptService patchNotePromptService;

    private final ChatModelResolver chatModelResolver;

    private final SourceTagParser sourceTagParser;

    private final ObjectMapper objectMapper;

    /** AsyncConfig에서 {@code @Bean("taskExecutor")}로 노출된 커스텀 스레드 풀. */
    private final Executor taskExecutor;

    public SseEmitter streamDraft(UUID projectId, DraftStreamRequest request) {

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        // ── 버전 중복 확인 (overwrite 모드에서는 스킵) ─────────────────────────
        if (!request.overwrite()
                && patchNoteRepository.existsByVersionAndNotDeleted(
                        projectId,
                        request.majorVersion(),
                        request.minorVersion(),
                        request.patchVersion())) {

            sendError(
                    emitter,
                    "이미 존재하는 버전입니다. v%d.%d.%d"
                            .formatted(
                                    request.majorVersion(),
                                    request.minorVersion(),
                                    request.patchVersion()));

            return emitter;
        }

        // ── PENDING 항목 조회 ──────────────────────────────────────────────────
        List<PendingItem> items =
                pendingItemRepository.findByProjectIdAndStatus(
                        projectId, PendingItemStatus.PENDING);

        if (items.isEmpty()) {

            sendError(emitter, "패치노트에 포함할 대기 중인 항목이 없습니다.");

            return emitter;
        }

        // ── 동시 생성 락 ───────────────────────────────────────────────────────
        if (!activeGenerations.add(projectId)) {

            sendError(emitter, "현재 다른 사용자가 초안을 생성 중입니다. 잠시 후 다시 시도해주세요.");

            return emitter;
        }

        AtomicBoolean aborted = new AtomicBoolean(false);

        emitter.onCompletion(() -> aborted.set(true));

        emitter.onTimeout(() -> aborted.set(true));

        emitter.onError(e -> aborted.set(true));

        // AsyncConfig에 등록된 커스텀 ThreadPoolTaskExecutor 사용 (ForkJoinPool 사용 금지)
        CompletableFuture.runAsync(
                () -> generateDraft(emitter, projectId, items, request, aborted),
                taskExecutor);

        return emitter;
    }

    private void generateDraft(
            SseEmitter emitter,
            UUID projectId,
            List<PendingItem> items,
            DraftStreamRequest request,
            AtomicBoolean aborted) {

        try {

            if (aborted.get()) return;

            sendProgress(emitter, "BUILDING_CONTEXT");

            RagContext ragContext = patchNoteRagService.buildContext(projectId, items);

            if (aborted.get()) return;

            if (!ragContext.sourceRefs().isEmpty()) {

                sendSources(emitter, ragContext.sourceRefs());
            }

            String systemPrompt = patchNotePromptService.buildSystemPrompt(ragContext);

            String userPrompt =
                    patchNotePromptService.buildUserPrompt(
                            request.majorVersion(),
                            request.minorVersion(),
                            request.patchVersion(),
                            request.additionalPrompt());

            ResolvedChatModel resolvedModel = chatModelResolver.resolve(request.modelAlias());

            if (aborted.get()) return;

            sendProgress(emitter, "GENERATING");

            // ── 컨텍스트 초과 경고 (GENERATING 이벤트 이후 전송 — 오버레이 사라진 뒤 노출) ──
            TokenEstimation estimation = ragContext.tokenEstimation();
            if (estimation.exceeded()) {
                int currentPercent = (int) Math.round(estimation.usageRatio() * 100);
                sendContextOverflow(emitter, currentPercent);
            }

            StringBuilder rawContent = new StringBuilder();

            ChatClient.create(resolvedModel.chatModel())
                    .prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .options(resolvedModel.chatOptions())
                    .stream()
                    .content()
                    .takeWhile(token -> !aborted.get())
                    .doOnNext(
                            token -> {
                                rawContent.append(token);

                                sendToken(emitter, token);
                            })
                    .doOnComplete(
                            () -> {
                                if (!aborted.get()) {

                                    finalizeDraft(emitter, rawContent.toString(), ragContext);
                                }
                            })
                    .doOnError(
                            e -> {
                                log.error("LLM 스트리밍 오류 - projectId={}", projectId, e);

                                sendError(emitter, "LLM 생성 중 오류가 발생했습니다: " + e.getMessage());
                            })
                    .blockLast();

        } catch (Exception e) {

            log.error("패치노트 초안 생성 실패 - projectId={}", projectId, e);

            if (!aborted.get()) {

                sendError(emitter, "초안 생성 중 오류가 발생했습니다: " + e.getMessage());
            }

        } finally {

            // 정상/비정상 종료 모두에서 락 해제
            activeGenerations.remove(projectId);
        }
    }

    private void finalizeDraft(SseEmitter emitter, String rawContent, RagContext ragContext) {

        String cleanedContent = sourceTagParser.clean(rawContent);

        List<String> extractedRefs = sourceTagParser.extractRefs(rawContent);

        List<String> finalRefs = extractedRefs.isEmpty() ? ragContext.sourceRefs() : extractedRefs;

        DraftResult result = new DraftResult(cleanedContent, finalRefs);

        sendDone(emitter, result);

        try {

            emitter.complete();

        } catch (Exception e) {

            log.debug("SseEmitter.complete() 호출 중 예외 (클라이언트 연결 끊김으로 무시)", e);
        }
    }

    private void sendProgress(SseEmitter emitter, String step) {

        send(emitter, SseEmitter.event().name("progress").data(toJson(Map.of("step", step))));
    }

    private void sendSources(SseEmitter emitter, List<String> refs) {

        send(emitter, SseEmitter.event().name("sources").data(toJson(Map.of("refs", refs))));
    }

    private void sendToken(SseEmitter emitter, String content) {

        send(emitter, SseEmitter.event().name("token").data(toJson(Map.of("content", content))));
    }

    private void sendDone(SseEmitter emitter, DraftResult result) {

        send(
                emitter,
                SseEmitter.event()
                        .name("done")
                        .data(
                                toJson(
                                        Map.of(
                                                "cleanedContent", result.cleanedContent(),
                                                "sourceRefs", result.sourceRefs()))));
    }

    /**
     * 컨텍스트 초과 경고 이벤트.
     *
     * <p>RAG 토큰 추정량이 모델 한도를 초과할 때 전송된다. 이 이벤트는 에러가 아니라 경고이며,
     * LLM 호출은 이미 시작된다. 프론트엔드는 모달로 사용자에게 안내하고 스트리밍을 계속 수신한다.
     *
     * @param currentPercent 현재 사용량 백분율 (예: 150 → 150%)
     */
    private void sendContextOverflow(SseEmitter emitter, int currentPercent) {

        send(
                emitter,
                SseEmitter.event()
                        .name("context_overflow")
                        .data(
                                toJson(
                                        Map.of(
                                                "currentPercent", currentPercent,
                                                "recommendedPercent", 100))));
    }

    private void sendError(SseEmitter emitter, String message) {

        send(emitter, SseEmitter.event().name("error").data(toJson(Map.of("message", message))));

        try {

            emitter.complete();

        } catch (Exception ignored) {

            // 이미 닫힌 경우 무시
        }
    }

    private void send(SseEmitter emitter, SseEventBuilder event) {

        try {

            emitter.send(event);

        } catch (IOException e) {

            // 클라이언트 연결 끊김 시 정상적인 상황
            log.debug("SSE 전송 실패 (클라이언트 연결 끊김): {}", e.getMessage());
        }
    }

    private String toJson(Object value) {

        try {

            return objectMapper.writeValueAsString(value);

        } catch (JsonProcessingException e) {

            log.warn("SSE 이벤트 JSON 직렬화 실패", e);

            return "{}";
        }
    }
}
