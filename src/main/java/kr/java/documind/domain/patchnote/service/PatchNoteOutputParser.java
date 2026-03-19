package kr.java.documind.domain.patchnote.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.java.documind.domain.patchnote.model.dto.PatchNoteDraftResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * LLM 원시 출력 문자열을 {@link PatchNoteDraftResponse}로 안전하게 파싱하는 서비스.
 *
 * <h3>파싱 전략</h3>
 * <ol>
 *   <li>마크다운 코드 펜스({@code ```json ... ```}) 감지 → 내부 JSON 추출
 *   <li>코드 펜스가 없으면 첫 번째 {@code {}}와 마지막 {@code {}} 사이 문자열 추출
 *   <li>추출 실패 시 원문 그대로 파싱 시도
 *   <li>모든 경우에 {@link JsonProcessingException} 발생 시 빈 응답 반환 (절대 예외 전파 없음)
 * </ol>
 *
 * <p>LLM이 잘못된 JSON을 반환하거나 응답이 비어 있어도 서비스 흐름이 중단되지 않도록
 * fail-safe 설계를 따른다. 파싱 실패 내역은 WARN 레벨로 로깅한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PatchNoteOutputParser {

    /** 마크다운 코드 펜스 패턴 — ```json ... ``` 또는 ``` ... ```. */
    private static final Pattern CODE_FENCE =
            Pattern.compile("```(?:json)?\\s*([\\s\\S]+?)\\s*```", Pattern.DOTALL);

    /** 로그에 출력할 원시 출력 최대 길이 (너무 긴 오류 로그 방지). */
    private static final int LOG_PREVIEW_LENGTH = 300;

    private final ObjectMapper objectMapper;

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * LLM 원시 출력을 {@link PatchNoteDraftResponse}로 파싱한다.
     *
     * <p>파싱에 실패하면 빈 섹션 목록을 가진 응답을 반환한다. 예외는 전파되지 않는다.
     *
     * @param rawOutput LLM이 반환한 원시 문자열 (null 허용)
     * @return 파싱된 응답, 실패 시 {@code new PatchNoteDraftResponse(null, List.of(), null)}
     */
    public PatchNoteDraftResponse parse(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            log.warn("LLM 출력이 비어 있음 — 빈 PatchNoteDraftResponse 반환");
            return empty();
        }

        String json = extractJson(rawOutput.strip());

        try {
            PatchNoteDraftResponse response = objectMapper.readValue(json, PatchNoteDraftResponse.class);

            // null sections를 빈 목록으로 정규화
            if (response.sections() == null) {
                return empty();
            }

            log.debug("LLM 출력 파싱 성공 — 섹션 수: {}", response.sections().size());
            return response;

        } catch (JsonProcessingException e) {
            log.warn(
                    "LLM 출력 JSON 파싱 실패 — 빈 응답 반환. "
                            + "원시 출력 앞 {}자: [{}], 오류: {}",
                    LOG_PREVIEW_LENGTH,
                    rawOutput.substring(0, Math.min(LOG_PREVIEW_LENGTH, rawOutput.length())),
                    e.getMessage());
            return empty();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // JSON 추출
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 텍스트에서 JSON 객체 문자열을 추출한다.
     *
     * <ol>
     *   <li>마크다운 코드 펜스 내부 우선 추출
     *   <li>첫 번째 {@code {}~마지막 {@code }} 구간 fallback 추출
     *   <li>위 두 방법 모두 실패 시 원문 반환 (이후 파싱에서 실패 처리)
     * </ol>
     */
    private String extractJson(String text) {
        // 1. 마크다운 코드 펜스 시도
        Matcher fenceMatcher = CODE_FENCE.matcher(text);
        if (fenceMatcher.find()) {
            return fenceMatcher.group(1).strip();
        }

        // 2. { ... } 구간 추출
        int start = text.indexOf('{');
        int end   = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }

        // 3. 원문 그대로 (파싱에서 처리)
        return text;
    }

    private PatchNoteDraftResponse empty() {
        return new PatchNoteDraftResponse(null, List.of(), null);
    }
}
