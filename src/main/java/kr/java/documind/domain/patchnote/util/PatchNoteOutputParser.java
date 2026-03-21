package kr.java.documind.domain.patchnote.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.java.documind.domain.patchnote.model.dto.PatchNoteDraftResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    public PatchNoteDraftResponse parse(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            log.warn("LLM 출력이 비어 있음 — 빈 PatchNoteDraftResponse 반환");
            return empty();
        }

        String json = extractJson(rawOutput.strip());

        try {
            PatchNoteDraftResponse response =
                    objectMapper.readValue(json, PatchNoteDraftResponse.class);

            // null sections를 빈 목록으로 정규화
            if (response.sections() == null) {
                return empty();
            }

            log.debug("LLM 출력 파싱 성공 — 섹션 수: {}", response.sections().size());
            return response;

        } catch (JsonProcessingException e) {
            log.warn(
                    "LLM 출력 JSON 파싱 실패 — 빈 응답 반환. " + "원시 출력 앞 {}자: [{}], 오류: {}",
                    LOG_PREVIEW_LENGTH,
                    rawOutput.substring(0, Math.min(LOG_PREVIEW_LENGTH, rawOutput.length())),
                    e.getMessage());
            return empty();
        }
    }

    private String extractJson(String text) {
        // 1. 마크다운 코드 펜스 시도
        Matcher fenceMatcher = CODE_FENCE.matcher(text);
        if (fenceMatcher.find()) {
            return fenceMatcher.group(1).strip();
        }

        // 2. { ... } 구간 추출
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
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
