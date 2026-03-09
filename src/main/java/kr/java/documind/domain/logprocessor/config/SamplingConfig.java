package kr.java.documind.domain.logprocessor.config;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 로그 샘플링 설정
 *
 * <p>대량 발생 로그에 대해 샘플링 저장 전략 적용
 *
 * <p>샘플링 전략: 1. Severity 기반 (DEBUG/INFO는 항상 샘플링, ERROR/CRITICAL은 보존) 2. Fingerprint 기반 (동일 에러 대량 발생
 * 시) 3. 서버 부하 기반 (DB 지연, 메모리 부족 시)
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "sampling")
public class SamplingConfig {

    /** 샘플링 활성화 여부 */
    private boolean enabled = false;

    /** 샘플링 윈도우 (초) */
    private int windowSeconds = 60;

    /** Severity별 샘플링 비율 (0.0 ~ 1.0, 1.0 = 100% 저장) */
    private Map<String, Double> severityRates = new HashMap<>();

    /** Fingerprint별 샘플링 임계값 (이 값 초과 시 샘플링 시작) */
    private int threshold = 100;

    /** Fingerprint 기반 샘플링 비율 (0.0 ~ 1.0, 1.0 = 100% 저장) */
    private double rate = 0.1;

    /** 서버 부하 기반 샘플링 활성화 여부 */
    private boolean backpressureEnabled = true;

    /** 서버 과부하 시 샘플링 비율 (0.0 ~ 1.0, Fingerprint보다 공격적) */
    private double backpressureRate = 0.05;

    /**
     * Severity별 샘플링 비율 조회
     *
     * @param severity LogSeverity
     * @return 샘플링 비율 (기본값: 1.0 = 100% 저장)
     */
    public double getSeverityRate(String severity) {
        return severityRates.getOrDefault(severity, 1.0);
    }
}
