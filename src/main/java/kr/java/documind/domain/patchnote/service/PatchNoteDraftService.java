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
import kr.java.documind.domain.patchnote.model.dto.PatchNoteDraftResponse;
import kr.java.documind.domain.patchnote.model.dto.RagContext;
import kr.java.documind.domain.patchnote.model.entity.PendingItem;
import kr.java.documind.domain.patchnote.model.enums.PendingItemStatus;
import kr.java.documind.domain.patchnote.model.repository.PatchNoteRepository;
import kr.java.documind.domain.patchnote.model.repository.PendingItemRepository;
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
        try {
            CompletableFuture.runAsync(
                    () -> generateDraft(emitter, projectId, items, request, aborted), taskExecutor);
        } catch (Exception e) {
            activeGenerations.remove(projectId);
            sendError(emitter, "초안 생성을 시작할 수 없습니다: " + e.getMessage());
            completeQuietly(emitter);
        }

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

            // ── Step 1–2. RAG 컨텍스트 빌드 ──────────────────────────────────
            sendProgress(emitter, "BUILDING_CONTEXT");

            RagContext ragContext = patchNoteRagService.buildContext(projectId, items);

            if (aborted.get()) return;

            if (!ragContext.sourceRefs().isEmpty()) {
                sendSources(emitter, ragContext.sourceRefs());
            }

            // ── Step 3. 컨텍스트 오버플로우 처리 — 실제 증거 축소 ────────────
            if (ragContext.tokenEstimation().exceeded()) {
                int beforePercent =
                        (int) Math.round(ragContext.tokenEstimation().usageRatio() * 100);
                ragContext = evidenceReducer.reduce(ragContext);
                int afterPercent =
                        (int) Math.round(ragContext.tokenEstimation().usageRatio() * 100);

                log.info(
                        "컨텍스트 감소 완료 — projectId={}, 감소 전: {}%, 감소 후: {}%",
                        projectId, beforePercent, afterPercent);

                // 실제 감소가 완료된 후에 이벤트 전송
                sendContextOverflow(emitter, beforePercent, afterPercent);
            }

            // ── Step 4. 프롬프트 빌드 ─────────────────────────────────────────
            String systemPrompt = patchNotePromptService.buildSystemPrompt(ragContext);
            String userPrompt =
                    patchNotePromptService.buildUserPrompt(
                            request.majorVersion(),
                            request.minorVersion(),
                            request.patchVersion(),
                            request.additionalPrompt());

            ResolvedChatModel resolvedModel =
                    chatModelResolver.resolveForPatchNote(request.modelAlias());

            if (aborted.get()) return;

            // ── Step 5. LLM 단일 호출 (구조화 JSON 수신) ─────────────────────
            sendProgress(emitter, "GENERATING");

            String rawOutput =
                    ChatClient.create(resolvedModel.chatModel())
                            .prompt()
                            .system(systemPrompt)
                            .user(userPrompt)
                            .options(resolvedModel.chatOptions())
                            .call()
                            .content();

            if (aborted.get()) return;

            // ── Step 6. JSON 파싱 (fail-safe) ─────────────────────────────────
            PatchNoteDraftResponse parsed = outputParser.parse(rawOutput);

            // ── Step 7. 소스 REF 검증 (환각 REF 제거) ────────────────────────
            PatchNoteDraftResponse validated = refValidator.validate(parsed, ragContext);

            // ── Step 8. 서버 사이드 렌더링 ────────────────────────────────────
            DraftResult result = renderer.render(validated);

            // ── Step 9. 완료 전송 ─────────────────────────────────────────────
            sendDone(emitter, result);
            completeQuietly(emitter);

        } catch (Exception e) {
            log.error("패치노트 초안 생성 실패 — projectId={}", projectId, e);
            if (!aborted.get()) {
                sendError(emitter, "초안 생성 중 오류가 발생했습니다: " + e.getMessage());
            }
        } finally {
            // 정상/비정상 종료 모두에서 락 해제
            activeGenerations.remove(projectId);
        }
    }

    private void sendProgress(SseEmitter emitter, String step) {
        send(emitter, SseEmitter.event().name("progress").data(toJson(Map.of("step", step))));
    }

    private void sendSources(SseEmitter emitter, List<String> refs) {
        send(emitter, SseEmitter.event().name("sources").data(toJson(Map.of("refs", refs))));
    }

    private void sendContextOverflow(SseEmitter emitter, int beforePercent, int afterPercent) {
        send(
                emitter,
                SseEmitter.event()
                        .name("context_overflow")
                        .data(
                                toJson(
                                        Map.of(
                                                "beforePercent", beforePercent,
                                                "afterPercent", afterPercent,
                                                "recommendedPercent", 100))));
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

    private void sendError(SseEmitter emitter, String message) {
        send(emitter, SseEmitter.event().name("error").data(toJson(Map.of("message", message))));
        completeQuietly(emitter);
    }

    private void send(SseEmitter emitter, SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException e) {
            // 클라이언트 연결 끊김 시 정상적인 상황
            log.debug("SSE 전송 실패 (클라이언트 연결 끊김): {}", e.getMessage());
        }
    }

    private void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception e) {
            log.debug("SseEmitter.complete() 호출 중 예외 (클라이언트 연결 끊김으로 무시)", e);
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
