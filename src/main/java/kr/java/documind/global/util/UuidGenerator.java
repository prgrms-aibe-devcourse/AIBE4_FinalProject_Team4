package kr.java.documind.global.util;

import com.github.f4b6a3.uuid.UuidCreator;
import java.util.UUID;

/** 시간 순서가 보장되는 고유 식별자(UUID v7) 생성 유틸리티 */
public class UuidGenerator {

    private UuidGenerator() {
        // 인스턴스화 방지
        throw new IllegalStateException("Utility class");
    }

    /**
     * 표준 UUID v7 명세(RFC 9562)에 따라 UUID를 생성한다
     */
    public static UUID generateV7() {
        return UuidCreator.getTimeOrderedEpoch();
    }
}
