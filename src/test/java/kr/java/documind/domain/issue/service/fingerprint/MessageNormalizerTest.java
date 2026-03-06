package kr.java.documind.domain.issue.service.fingerprint;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MessageNormalizer 테스트")
class MessageNormalizerTest {

    private MessageNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new MessageNormalizer();
    }

    @Test
    @DisplayName("숫자를 N으로 치환")
    void normalizeNumbers() {
        // given
        String message = "Failed to load player 12345 with score 9999";

        // when
        String result = normalizer.normalize(message);

        // then
        assertThat(result).isEqualTo("Failed to load player N with score N");
    }

    @Test
    @DisplayName("UUID를 UUID로 치환")
    void normalizeUuid() {
        // given
        String message =
                "Player not found: 550e8400-e29b-41d4-a716-446655440000 in session"
                        + " 7c9e6679-7425-40de-944b-e07fc1f90ae7";

        // when
        String result = normalizer.normalize(message);

        // then
        assertThat(result).isEqualTo("Player not found: UUID in session UUID");
    }

    @Test
    @DisplayName("IP 주소를 IP로 치환")
    void normalizeIpAddress() {
        // given
        String message = "Connection timeout from 192.168.1.100 to 10.0.0.5";

        // when
        String result = normalizer.normalize(message);

        // then
        assertThat(result).isEqualTo("Connection timeout from IP to IP");
    }

    @Test
    @DisplayName("ISO 8601 타임스탬프를 TIMESTAMP로 치환")
    void normalizeTimestamp() {
        // given
        String message = "Error occurred at 2024-03-05T10:30:45.123Z and 2024-03-05 15:20:30+09:00";

        // when
        String result = normalizer.normalize(message);

        // then
        assertThat(result).isEqualTo("Error occurred at TIMESTAMP and TIMESTAMP");
    }

    @Test
    @DisplayName("파일 경로를 PATH/filename으로 치환 (Unix)")
    void normalizeFilePathUnix() {
        // given
        String message = "Failed to load /home/user/game/config/settings.json";

        // when
        String result = normalizer.normalize(message);

        // then
        assertThat(result).isEqualTo("Failed to load PATH/settings.json");
    }

    @Test
    @DisplayName("파일 경로를 PATH/filename으로 치환 (Windows)")
    void normalizeFilePathWindows() {
        // given
        String message = "Cannot open C:\\Users\\Player\\Documents\\save_file.dat";

        // when
        String result = normalizer.normalize(message);

        // then
        assertThat(result).isEqualTo("Cannot open PATH/save_file.dat");
    }

    @Test
    @DisplayName("16진수 메모리 주소를 @ADDR로 치환")
    void normalizeHexAddress() {
        // given
        String message = "NullPointerException at @a1b2c3d4 and @5e6f7890";

        // when
        String result = normalizer.normalize(message);

        // then
        assertThat(result).isEqualTo("NullPointerException at @ADDR and @ADDR");
    }

    @Test
    @DisplayName("플레이어 ID를 player_N으로 치환")
    void normalizePlayerId() {
        // given
        String message = "Player player_12345 killed by player_67890";

        // when
        String result = normalizer.normalize(message);

        // then
        assertThat(result).isEqualTo("Player player_N killed by player_N");
    }

    @Test
    @DisplayName("게임 아이템 ID를 item_TYPE_N으로 치환 (타입 유지)")
    void normalizeItemId() {
        // given
        String message = "Dropped item_weapon_123 and picked item_potion_456";

        // when
        String result = normalizer.normalize(message);

        // then
        assertThat(result).isEqualTo("Dropped item_weapon_N and picked item_potion_N");
    }

    @Test
    @DisplayName("복합 메시지 정규화 (모든 패턴 포함)")
    void normalizeComplexMessage() {
        // given
        String message =
                "Player player_12345 (550e8400-e29b-41d4-a716-446655440000) from 192.168.1.100"
                        + " failed to load item_weapon_999 at 2024-03-05T10:30:45Z in"
                        + " C:\\Game\\items\\config.xml with error @a1b2c3d4";

        // when
        String result = normalizer.normalize(message);

        // then
        assertThat(result)
                .isEqualTo(
                        "Player player_N (UUID) from IP failed to load item_weapon_N at TIMESTAMP in"
                                + " PATH/config.xml with error @ADDR");
    }

    @Test
    @DisplayName("null 또는 빈 메시지는 그대로 반환")
    void normalizeNullOrEmpty() {
        assertThat(normalizer.normalize(null)).isNull();
        assertThat(normalizer.normalize("")).isEmpty();
    }

    @Test
    @DisplayName("동일한 에러 원인은 동일한 정규화 결과 생성")
    void normalizeSameErrorProducesSameResult() {
        // given
        String error1 = "Player player_11111 failed to load item_weapon_123";
        String error2 = "Player player_99999 failed to load item_weapon_987";

        // when
        String result1 = normalizer.normalize(error1);
        String result2 = normalizer.normalize(error2);

        // then
        assertThat(result1).isEqualTo(result2);
        assertThat(result1).isEqualTo("Player player_N failed to load item_weapon_N");
    }
}
