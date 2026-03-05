package kr.java.documind.domain.issue.service.fingerprint;

import kr.java.documind.domain.issue.model.enums.FingerprintQuality;
import lombok.Builder;
import lombok.Getter;

/**
 * 핑거프린트 생성 결과
 *
 * <p>SHA-256 해시값과 품질 등급, 사용된 전략 정보를 포함
 */
@Getter
@Builder
public class FingerprintResult {

    /** SHA-256 해시값 (64자 hex string) */
    private String fingerprint;

    /** 핑거프린트 품질 등급 */
    private FingerprintQuality quality;

    /** 사용된 전략 설명 (예: "Full Stacktrace", "Exception Type + Message") */
    private String strategy;

    /**
     * 수동 검토가 필요한지 확인
     *
     * @return LOW, VERY_LOW, FALLBACK 품질인 경우 true
     */
    public boolean requiresReview() {
        return quality.requiresReview();
    }
}
