package kr.java.documind.domain.issue.service.fingerprint;

import java.nio.charset.StandardCharsets;
import java.util.List;
import kr.java.documind.domain.issue.model.enums.FingerprintQuality;
import kr.java.documind.domain.logprocessor.model.entity.GameLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

/**
 * 5-tier 핑거프린트 생성 전략 구현체
 *
 * <p>스택트레이스 및 에러 메시지 가용성에 따라 최적의 전략을 선택하여 SHA-256 해시 생성
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FingerprintGenerator {

    private final MessageNormalizer messageNormalizer;
    private final StackFrameFilter stackFrameFilter;

    /** 전체 스택트레이스 사용 최소 프레임 수 */
    private static final int FULL_STACK_MIN_FRAMES = 3;

    /** 부분 스택트레이스 사용 최소 프레임 수 */
    private static final int PARTIAL_STACK_MIN_FRAMES = 1;

    /** 부분 스택트레이스에서 사용할 상위 프레임 수 */
    private static final int PARTIAL_STACK_TOP_FRAMES = 3;

    /**
     * GameLog로부터 핑거프린트 생성
     *
     * @param gameLog 게임 로그 엔티티
     * @return 핑거프린트 생성 결과
     */
    public FingerprintResult generate(GameLog gameLog) {
        String archive = gameLog.getArchive();

        // archive에서 스택트레이스 추출 시도
        String stackTrace = extractStackTrace(archive);
        String exceptionType = extractExceptionType(archive);
        String message = extractMessage(archive);

        // 5-tier 전략 적용
        if (stackTrace != null && !stackTrace.isEmpty()) {
            List<String> appFrames = stackFrameFilter.extractAppFrames(stackTrace);

            // Strategy 1: Full Stacktrace (HIGH)
            if (appFrames.size() >= FULL_STACK_MIN_FRAMES) {
                return generateFullStacktraceFingerprint(appFrames, exceptionType);
            }

            // Strategy 2: Partial Stacktrace (MEDIUM)
            if (appFrames.size() >= PARTIAL_STACK_MIN_FRAMES) {
                return generatePartialStacktraceFingerprint(appFrames, exceptionType);
            }
        }

        // Strategy 3: Exception Type + Message (LOW)
        if (exceptionType != null && message != null) {
            return generateExceptionMessageFingerprint(exceptionType, message);
        }

        // Strategy 4: Message Only (VERY_LOW)
        if (message != null && !message.isEmpty()) {
            return generateMessageOnlyFingerprint(message);
        }

        // Strategy 5: Fallback
        return generateFallbackFingerprint(gameLog);
    }

    /**
     * Strategy 1: 전체 스택트레이스 기반 핑거프린트 (HIGH quality)
     *
     * <p>정규화된 애플리케이션 프레임 전체를 사용
     */
    private FingerprintResult generateFullStacktraceFingerprint(
            List<String> appFrames, String exceptionType) {
        StringBuilder input = new StringBuilder();

        if (exceptionType != null) {
            input.append(exceptionType).append("\n");
        }

        appFrames.forEach(frame -> input.append(frame).append("\n"));

        String fingerprint = sha256(input.toString());
        log.debug(
                "Generated HIGH quality fingerprint using {} app frames: {}",
                appFrames.size(),
                fingerprint);

        return FingerprintResult.builder()
                .fingerprint(fingerprint)
                .quality(FingerprintQuality.HIGH)
                .strategy("Full Stacktrace")
                .build();
    }

    /**
     * Strategy 2: 부분 스택트레이스 기반 핑거프린트 (MEDIUM quality)
     *
     * <p>상위 N개 프레임만 사용
     */
    private FingerprintResult generatePartialStacktraceFingerprint(
            List<String> appFrames, String exceptionType) {
        StringBuilder input = new StringBuilder();

        if (exceptionType != null) {
            input.append(exceptionType).append("\n");
        }

        int frameCount = Math.min(appFrames.size(), PARTIAL_STACK_TOP_FRAMES);
        for (int i = 0; i < frameCount; i++) {
            input.append(appFrames.get(i)).append("\n");
        }

        String fingerprint = sha256(input.toString());
        log.debug(
                "Generated MEDIUM quality fingerprint using top {} frames: {}",
                frameCount,
                fingerprint);

        return FingerprintResult.builder()
                .fingerprint(fingerprint)
                .quality(FingerprintQuality.MEDIUM)
                .strategy("Partial Stacktrace")
                .build();
    }

    /**
     * Strategy 3: 예외 타입 + 메시지 기반 핑거프린트 (LOW quality)
     *
     * <p>수동 검토 필요
     */
    private FingerprintResult generateExceptionMessageFingerprint(
            String exceptionType, String message) {
        String normalizedMessage = messageNormalizer.normalize(message);
        String input = exceptionType + "\n" + normalizedMessage;
        String fingerprint = sha256(input);

        log.debug("Generated LOW quality fingerprint (Exception + Message): {}", fingerprint);

        return FingerprintResult.builder()
                .fingerprint(fingerprint)
                .quality(FingerprintQuality.LOW)
                .strategy("Exception Type + Message")
                .build();
    }

    /**
     * Strategy 4: 메시지만 기반 핑거프린트 (VERY_LOW quality)
     *
     * <p>수동 검토 필수
     */
    private FingerprintResult generateMessageOnlyFingerprint(String message) {
        String normalizedMessage = messageNormalizer.normalize(message);
        String fingerprint = sha256(normalizedMessage);

        log.debug("Generated VERY_LOW quality fingerprint (Message Only): {}", fingerprint);

        return FingerprintResult.builder()
                .fingerprint(fingerprint)
                .quality(FingerprintQuality.VERY_LOW)
                .strategy("Message Only")
                .build();
    }

    /**
     * Strategy 5: 폴백 핑거프린트 (FALLBACK quality)
     *
     * <p>severity + eventCategory 기반 (최후의 수단)
     */
    private FingerprintResult generateFallbackFingerprint(GameLog gameLog) {
        String input = gameLog.getSeverity() + "_" + gameLog.getEventCategory();
        String fingerprint = sha256(input);

        log.warn("Generated FALLBACK quality fingerprint (no stacktrace/message): {}", fingerprint);

        return FingerprintResult.builder()
                .fingerprint(fingerprint)
                .quality(FingerprintQuality.FALLBACK)
                .strategy("Fallback (Severity + Category)")
                .build();
    }

    /**
     * archive에서 스택트레이스 추출
     *
     * <p>간단한 구현: "at " 패턴이 포함된 라인들을 스택트레이스로 간주
     */
    private String extractStackTrace(String archive) {
        if (archive == null || !archive.contains("at ")) {
            return null;
        }

        StringBuilder stackTrace = new StringBuilder();
        String[] lines = archive.split("\\r?\\n");

        for (String line : lines) {
            if (line.trim().startsWith("at ")) {
                stackTrace.append(line).append("\n");
            }
        }

        return stackTrace.length() > 0 ? stackTrace.toString() : null;
    }

    /**
     * archive에서 예외 타입 추출
     *
     * <p>첫 줄에서 예외 클래스명 추출 시도 (예: "java.lang.NullPointerException: ...")
     */
    private String extractExceptionType(String archive) {
        if (archive == null || archive.isEmpty()) {
            return null;
        }

        String firstLine = archive.split("\\r?\\n")[0].trim();

        // 예외 타입 패턴: 패키지명.클래스명
        if (firstLine.contains(":")) {
            String typePart = firstLine.substring(0, firstLine.indexOf(":")).trim();
            if (typePart.contains(".")) {
                return typePart;
            }
        }

        return null;
    }

    /**
     * archive에서 에러 메시지 추출
     *
     * <p>첫 줄의 콜론(:) 이후 텍스트를 메시지로 간주
     */
    private String extractMessage(String archive) {
        if (archive == null || archive.isEmpty()) {
            return null;
        }

        String firstLine = archive.split("\\r?\\n")[0].trim();

        if (firstLine.contains(":")) {
            return firstLine.substring(firstLine.indexOf(":") + 1).trim();
        }

        return firstLine;
    }

    /**
     * SHA-256 해시 생성
     *
     * @param input 입력 문자열
     * @return 64자 hex 문자열
     */
    private String sha256(String input) {
        return DigestUtils.sha256Hex(input.getBytes(StandardCharsets.UTF_8));
    }
}
