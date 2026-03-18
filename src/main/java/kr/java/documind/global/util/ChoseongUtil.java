package kr.java.documind.global.util;

import org.springframework.stereotype.Component;

@Component
public class ChoseongUtil {

    private static final char HANGUL_BASE = 0xAC00; // '가'
    private static final char HANGUL_END = 0xD7A3; // '힣'
    private static final int JUNG_COUNT = 21; // 중성 개수
    private static final int JONG_COUNT = 28; // 종성 개수

    private static final char[] CHOSEONG = {
        'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ',
        'ㅎ'
    };

    public String extract(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        try {
            StringBuilder sb = new StringBuilder();
            for (char c : text.toCharArray()) {
                if (c >= HANGUL_BASE && c <= HANGUL_END) {
                    int index = (c - HANGUL_BASE) / (JUNG_COUNT * JONG_COUNT);
                    sb.append(CHOSEONG[index]);
                } else if (Character.isLetterOrDigit(c)) {
                    sb.append(Character.toLowerCase(c));
                }
            }
            return sb.toString();
        } catch (Exception e) {
            // 어떤 예외가 발생해도 빈 문자열 반환
            return "";
        }
    }
}
