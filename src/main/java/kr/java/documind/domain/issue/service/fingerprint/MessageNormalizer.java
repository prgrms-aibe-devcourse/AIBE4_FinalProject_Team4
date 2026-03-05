package kr.java.documind.domain.issue.service.fingerprint;

import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 에러 메시지 정규화 컴포넌트
 *
 * <p>동적 데이터를 플레이스홀더로 치환하여 동일한 원인의 에러가 같은 핑거프린트를 갖도록 함
 */
@Slf4j
@Component
public class MessageNormalizer {

    // 숫자 패턴
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b\\d+\\b");

    // UUID 패턴 (8-4-4-4-12)
    private static final Pattern UUID_PATTERN =
            Pattern.compile(
                    "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b");

    // IP 주소 패턴 (IPv4)
    private static final Pattern IP_PATTERN = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");

    // ISO 8601 타임스탬프 패턴
    private static final Pattern TIMESTAMP_PATTERN =
            Pattern.compile(
                    "\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}:\\d{2})?");

    // 절대 파일 경로 패턴 (Windows/Unix)
    private static final Pattern FILE_PATH_PATTERN =
            Pattern.compile(
                    "(?:[A-Za-z]:\\\\|/)(?:[^\\\\/:*?\"<>|\\r\\n]+[\\\\/])*([^\\\\/:*?\"<>|\\r\\n]+)");

    // 16진수 메모리 주소 패턴 (예: @a1b2c3d4)
    private static final Pattern HEX_ADDRESS_PATTERN = Pattern.compile("@[0-9a-fA-F]+");

    // 플레이어 ID 패턴 (예: player_12345)
    private static final Pattern PLAYER_ID_PATTERN = Pattern.compile("\\bplayer_\\d+\\b");

    // 게임 아이템 ID 패턴 (예: item_weapon_123, item_potion_456)
    private static final Pattern ITEM_ID_PATTERN = Pattern.compile("\\bitem_([a-zA-Z]+)_\\d+\\b");

    /**
     * 에러 메시지를 정규화
     *
     * <p>적용 순서: 1. UUID 2. IP 주소 3. 타임스탬프 4. 파일 경로 5. 플레이어 ID 6. 아이템 ID 7. 16진수 주소 8. 일반 숫자
     *
     * @param message 원본 메시지
     * @return 정규화된 메시지
     */
    public String normalize(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        String normalized = message;

        // 1. UUID 치환 (숫자 패턴보다 먼저 처리)
        normalized = UUID_PATTERN.matcher(normalized).replaceAll("UUID");

        // 2. IP 주소 치환
        normalized = IP_PATTERN.matcher(normalized).replaceAll("IP");

        // 3. 타임스탬프 치환
        normalized = TIMESTAMP_PATTERN.matcher(normalized).replaceAll("TIMESTAMP");

        // 4. 파일 경로 치환 (경로는 제거하고 파일명만 유지)
        normalized =
                FILE_PATH_PATTERN
                        .matcher(normalized)
                        .replaceAll(
                                matchResult -> {
                                    String filename = matchResult.group(1);
                                    return "PATH/" + filename;
                                });

        // 5. 플레이어 ID 치환
        normalized = PLAYER_ID_PATTERN.matcher(normalized).replaceAll("player_N");

        // 6. 게임 아이템 ID 치환 (타입은 유지, 숫자만 치환)
        normalized =
                ITEM_ID_PATTERN
                        .matcher(normalized)
                        .replaceAll(
                                matchResult -> {
                                    String itemType = matchResult.group(1);
                                    return "item_" + itemType + "_N";
                                });

        // 7. 16진수 메모리 주소 치환
        normalized = HEX_ADDRESS_PATTERN.matcher(normalized).replaceAll("@ADDR");

        // 8. 일반 숫자 치환 (마지막에 처리)
        normalized = NUMBER_PATTERN.matcher(normalized).replaceAll("N");

        log.trace("Message normalized: '{}' -> '{}'", message, normalized);
        return normalized;
    }
}
