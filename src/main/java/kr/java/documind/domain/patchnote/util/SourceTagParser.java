package kr.java.documind.domain.patchnote.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/**
 * LLM 출력의 {@code {{source:REF}}} 태그 파서.
 *
 * <p>LLM은 패치노트 컨텐츠 내에 출처 태그를 {@code {{source:ISSUE-245}}} 형식으로 삽입한다.
 * 이 클래스는 해당 태그를 제거하거나 추출하는 유틸리티를 제공한다.
 *
 * <p><b>주의:</b> 이 클래스는 Spring 컨텍스트에 등록되지 않는 순수 유틸리티이다.
 * 인라인 태그 삽입은 {@link kr.java.documind.domain.patchnote.service.PatchNoteRenderer}가 담당한다.
 * 이 클래스는 독립적인 유틸리티 목적으로만 사용한다.
 */
public class SourceTagParser {

    /** {@code {{source:REF}}} 패턴. REF는 영숫자, 하이픈 허용. */
    private static final Pattern SOURCE_TAG_PATTERN =
            Pattern.compile("\\{\\{source:([A-Za-z0-9\\-]+)\\}\\}");

    /**
     * 컨텐츠에서 {@code {{source:REF}}} 태그를 모두 제거한 텍스트를 반환한다.
     *
     * <p>태그 주변 공백은 정리하되 단락 구조는 유지한다.
     *
     * @param content LLM 출력 텍스트 (null 허용 → 빈 문자열 반환)
     * @return 태그 제거된 정제 텍스트
     */
    public String clean(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        // 태그 제거 후 연속 공백을 단일 공백으로 정규화
        return SOURCE_TAG_PATTERN.matcher(content).replaceAll("").stripTrailing();
    }

    /**
     * 컨텐츠에 포함된 소스 REF 목록을 등장 순서대로 추출한다.
     *
     * <p>동일한 REF가 여러 번 등장해도 한 번만 포함한다(중복 제거, 첫 등장 순서 유지).
     *
     * @param content LLM 출력 텍스트 (null 허용 → 빈 목록 반환)
     * @return 소스 REF 목록 (예: ["ISSUE-245", "DOC-1024"])
     */
    public List<String> extractRefs(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        List<String> refs = new ArrayList<>();
        Matcher matcher = SOURCE_TAG_PATTERN.matcher(content);

        while (matcher.find()) {
            String ref = matcher.group(1);
            if (!refs.contains(ref)) {
                refs.add(ref);
            }
        }

        return List.copyOf(refs);
    }
}
