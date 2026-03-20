package kr.java.documind.domain.patchnote.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import kr.java.documind.domain.patchnote.model.dto.PatchNoteDraftResponse;
import kr.java.documind.domain.patchnote.model.dto.PatchNoteItemResponse;
import kr.java.documind.domain.patchnote.model.dto.PatchNoteSectionResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PatchNoteOutputParser 단위 테스트")
class PatchNoteOutputParserTest {

    @Mock private ObjectMapper objectMapper;
    @InjectMocks private PatchNoteOutputParser parser;

    // ─────────────────────────────────────────────────────────────────────────
    // 헬퍼
    // ─────────────────────────────────────────────────────────────────────────

    private PatchNoteDraftResponse singleSectionResponse() {
        PatchNoteItemResponse item = new PatchNoteItemResponse("버그 수정", List.of("ISSUE-1"));
        PatchNoteSectionResponse section = new PatchNoteSectionResponse("FIX", List.of(item));
        return new PatchNoteDraftResponse(null, List.of(section), null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // parse()
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("parse()")
    class Parse {

        @Test
        @DisplayName("null 입력 → 빈 응답 반환")
        void parse_null입력_빈응답반환() {
            // When
            PatchNoteDraftResponse result = parser.parse(null);

            // Then
            assertThat(result.sections()).isEmpty();
        }

        @Test
        @DisplayName("공백 입력 → 빈 응답 반환")
        void parse_공백입력_빈응답반환() {
            // When
            PatchNoteDraftResponse result = parser.parse("   ");

            // Then
            assertThat(result.sections()).isEmpty();
        }

        @Test
        @DisplayName("마크다운 코드 펜스(```json) 내부 JSON 추출 후 파싱")
        void parse_코드펜스JSON_추출후파싱() throws JsonProcessingException {
            // Given
            String raw = "```json\n{\"sections\":[]}\n```";
            PatchNoteDraftResponse expected = new PatchNoteDraftResponse(null, List.of(), null);
            given(objectMapper.readValue("{\"sections\":[]}", PatchNoteDraftResponse.class))
                    .willReturn(expected);

            // When
            PatchNoteDraftResponse result = parser.parse(raw);

            // Then
            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("마크다운 코드 펜스(``` 언어 표기 없음) 내부 JSON 추출 후 파싱")
        void parse_코드펜스언어표기없음_추출후파싱() throws JsonProcessingException {
            // Given
            String raw = "```\n{\"sections\":[]}\n```";
            PatchNoteDraftResponse expected = new PatchNoteDraftResponse(null, List.of(), null);
            given(objectMapper.readValue("{\"sections\":[]}", PatchNoteDraftResponse.class))
                    .willReturn(expected);

            // When
            PatchNoteDraftResponse result = parser.parse(raw);

            // Then
            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("코드 펜스 없이 { } 구간 추출 후 파싱")
        void parse_코드펜스없이_중괄호추출후파싱() throws JsonProcessingException {
            // Given
            String json = "{\"sections\":[]}";
            String raw = "여기에 텍스트가 있고 " + json + " 이후에 더 있음";
            PatchNoteDraftResponse expected = new PatchNoteDraftResponse(null, List.of(), null);
            given(objectMapper.readValue(json, PatchNoteDraftResponse.class)).willReturn(expected);

            // When
            PatchNoteDraftResponse result = parser.parse(raw);

            // Then
            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("유효한 JSON → 섹션 포함 응답 반환")
        void parse_유효한JSON_섹션포함응답반환() throws JsonProcessingException {
            // Given
            String json = "{\"sections\":[{\"sectionType\":\"FIX\",\"items\":[]}]}";
            PatchNoteDraftResponse expected = singleSectionResponse();
            given(objectMapper.readValue(json, PatchNoteDraftResponse.class)).willReturn(expected);

            // When
            PatchNoteDraftResponse result = parser.parse(json);

            // Then
            assertThat(result.sections()).hasSize(1);
        }

        @Test
        @DisplayName("sections null 반환 → 빈 응답 정규화")
        void parse_sectionsNull_빈응답정규화() throws JsonProcessingException {
            // Given
            String json = "{\"sections\":null}";
            PatchNoteDraftResponse nullSections = new PatchNoteDraftResponse(null, null, null);
            given(objectMapper.readValue(json, PatchNoteDraftResponse.class))
                    .willReturn(nullSections);

            // When
            PatchNoteDraftResponse result = parser.parse(json);

            // Then
            assertThat(result.sections()).isEmpty();
        }

        @Test
        @DisplayName("JSON 파싱 실패(잘못된 JSON) → 빈 응답 반환, 예외 전파 없음")
        void parse_잘못된JSON_빈응답반환_예외전파없음() throws JsonProcessingException {
            // Given
            String raw = "{invalid-json}";
            given(objectMapper.readValue(eq("{invalid-json}"), eq(PatchNoteDraftResponse.class)))
                    .willThrow(new com.fasterxml.jackson.core.JsonParseException(null, "오류"));

            // When — 예외가 전파되면 테스트 실패
            PatchNoteDraftResponse result = parser.parse(raw);

            // Then
            assertThat(result.sections()).isEmpty();
        }

        @Test
        @DisplayName("중괄호 없는 순수 텍스트 → 파싱 실패로 빈 응답 반환")
        void parse_중괄호없는텍스트_빈응답반환() throws JsonProcessingException {
            // Given
            String raw = "이것은 JSON이 아닙니다";
            given(objectMapper.readValue(any(String.class), eq(PatchNoteDraftResponse.class)))
                    .willThrow(new com.fasterxml.jackson.core.JsonParseException(null, "오류"));

            // When
            PatchNoteDraftResponse result = parser.parse(raw);

            // Then
            assertThat(result.sections()).isEmpty();
        }
    }
}
