package kr.java.documind.global.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PromptUtil {

    private static final String PROMPT_CLASSPATH_PREFIX = "prompts/";

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String render(String filename, Map<String, Object> variables) {
        String template = load(filename);
        return substitute(template, variables);
    }

    private String load(String filename) {
        return cache.computeIfAbsent(
                filename,
                key -> {
                    String path = PROMPT_CLASSPATH_PREFIX + key;
                    try {
                        ClassPathResource resource = new ClassPathResource(path);
                        byte[] bytes = resource.getInputStream().readAllBytes();
                        log.debug("[PromptUtil] 프롬프트 파일 로드 완료: {}", path);
                        return new String(bytes, StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        throw new IllegalStateException("프롬프트 파일을 로드할 수 없습니다: " + path, e);
                    }
                });
    }

    private String substitute(String template, Map<String, Object> variables) {
        String result = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            String value = entry.getValue() != null ? String.valueOf(entry.getValue()) : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }
}
