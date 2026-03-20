package kr.java.documind.domain.patchnote.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SourceTagParser {

    /** {@code {{source:REF}}} 패턴. REF는 영숫자, 하이픈 허용. */
    private static final Pattern SOURCE_TAG_PATTERN =
            Pattern.compile("\\{\\{source:([A-Za-z0-9\\-]+)\\}\\}");

    public String clean(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        // 태그 제거 후 연속 공백을 단일 공백으로 정규화
        return SOURCE_TAG_PATTERN.matcher(content).replaceAll("").stripTrailing();
    }

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
