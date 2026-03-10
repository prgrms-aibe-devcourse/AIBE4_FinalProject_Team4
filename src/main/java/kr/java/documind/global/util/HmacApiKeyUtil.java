package kr.java.documind.global.util;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class HmacApiKeyUtil {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String KEY_PREFIX = "dm_";

    /** 랜덤 바이트 수: 256비트 */
    private static final int RAW_BYTES = 32;

    /** key_prefix 컬럼 길이(VARCHAR(32))와 동일하게 저장 */
    private static final int PREFIX_MAX_LEN = 32;

    /** key_last4 컬럼 길이 */
    private static final int LAST4_LEN = 4;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private HmacApiKeyUtil() {}

    public static String generatePlainKey() {
        byte[] bytes = new byte[RAW_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String computeHmac(String plainKey, String hmacSecret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(
                    new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(plainKey.getBytes(StandardCharsets.UTF_8));
            return toHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA-256 해시 생성 실패", e);
        }
    }

    public static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    public static String extractPrefix(String plainKey) {
        return plainKey.substring(0, Math.min(PREFIX_MAX_LEN, plainKey.length()));
    }

    public static String extractLast4(String plainKey) {
        int len = plainKey.length();
        return plainKey.substring(Math.max(0, len - LAST4_LEN));
    }

    public static String maskApiKey(String plainKey) {
        if (plainKey == null || plainKey.length() <= 8) {
            return "****";
        }
        String prefix = extractPrefix(plainKey);
        String last4 = extractLast4(plainKey);
        return prefix.substring(0, Math.min(12, prefix.length())) + "****" + last4;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
