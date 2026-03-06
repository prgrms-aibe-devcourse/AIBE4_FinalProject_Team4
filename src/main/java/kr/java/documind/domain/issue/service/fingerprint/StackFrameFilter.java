package kr.java.documind.domain.issue.service.fingerprint;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 스택 트레이스 프레임 필터링 및 정규화 컴포넌트
 *
 * <p>애플리케이션 프레임만 추출하고 라이브러리 프레임을 제거하여 핑거프린트 생성에 사용
 */
@Slf4j
@Component
public class StackFrameFilter {

    // 스택 트레이스 프레임 파싱 패턴
    // 예: "at com.game.service.PlayerService.loadPlayer(PlayerService.java:42)"
    private static final Pattern FRAME_PATTERN =
            Pattern.compile("at\\s+([a-zA-Z0-9_.]+)\\.([a-zA-Z0-9_<>$]+)\\(([^:)]+):?(\\d+)?\\)");

    // 애플리케이션 프레임 패턴 (프로젝트별 수정 필요)
    private static final List<String> APP_PACKAGE_PREFIXES =
            List.of(
                    "kr.java.documind", // 현재 프로젝트
                    "com.game", // 게임 프로젝트 예시
                    "com.studio",
                    "com.gameco");

    // 제외할 라이브러리 프레임 패턴
    private static final List<String> LIBRARY_PACKAGE_PREFIXES =
            List.of(
                    "java.",
                    "javax.",
                    "jdk.",
                    "sun.",
                    "org.springframework.",
                    "org.hibernate.",
                    "org.apache.",
                    "com.fasterxml.",
                    "io.netty.",
                    "reactor.",
                    "kotlin.");

    /**
     * 스택 트레이스에서 애플리케이션 프레임만 추출
     *
     * @param stackTrace 전체 스택 트레이스 문자열
     * @return 정규화된 애플리케이션 프레임 리스트
     */
    public List<String> extractAppFrames(String stackTrace) {
        if (stackTrace == null || stackTrace.isEmpty()) {
            return List.of();
        }

        List<String> appFrames = new ArrayList<>();
        String[] lines = stackTrace.split("\\r?\\n");

        for (String line : lines) {
            Matcher matcher = FRAME_PATTERN.matcher(line.trim());
            if (!matcher.find()) {
                continue;
            }

            String packageName = matcher.group(1);

            // 라이브러리 프레임 제외
            if (isLibraryFrame(packageName)) {
                continue;
            }

            // 애플리케이션 프레임만 수집
            if (isAppFrame(packageName)) {
                String normalizedFrame = normalizeFrame(matcher);
                appFrames.add(normalizedFrame);
            }
        }

        log.trace("Extracted {} app frames from {} total lines", appFrames.size(), lines.length);
        return appFrames;
    }

    /**
     * 스택 트레이스 프레임 정규화
     *
     * <p>정규화 규칙: - 라인 번호를 10 단위로 반올림 (42 -> 40) - 람다 표현식 정리 ($lambda$0 제거) - 파일 경로 정규화 (절대 경로 제거)
     *
     * @param matcher 프레임 패턴 매칭 결과
     * @return 정규화된 프레임 문자열
     */
    private String normalizeFrame(Matcher matcher) {
        String packageName = matcher.group(1);
        String methodName = matcher.group(2);
        String fileName = matcher.group(3);
        String lineNumber = matcher.group(4);

        // 람다 표현식 정리
        methodName = methodName.replaceAll("\\$lambda\\$\\d+", "\\$lambda");

        // 라인 번호 10단위 반올림 (있는 경우만)
        String normalizedLine = "";
        if (lineNumber != null && !lineNumber.isEmpty()) {
            int line = Integer.parseInt(lineNumber);
            int rounded = (line / 10) * 10;
            normalizedLine = ":" + rounded;
        }

        return String.format("%s.%s(%s%s)", packageName, methodName, fileName, normalizedLine);
    }

    /**
     * 애플리케이션 프레임인지 확인
     *
     * @param packageName 패키지명
     * @return 애플리케이션 프레임이면 true
     */
    private boolean isAppFrame(String packageName) {
        return APP_PACKAGE_PREFIXES.stream().anyMatch(packageName::startsWith);
    }

    /**
     * 라이브러리 프레임인지 확인
     *
     * @param packageName 패키지명
     * @return 라이브러리 프레임이면 true
     */
    private boolean isLibraryFrame(String packageName) {
        return LIBRARY_PACKAGE_PREFIXES.stream().anyMatch(packageName::startsWith);
    }
}
