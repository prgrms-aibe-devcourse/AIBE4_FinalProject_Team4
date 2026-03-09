package kr.java.documind.global.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 이슈 심각도 계산 설정값
 *
 * <p>application.yml의 issue.severity 설정을 바인딩
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "issue.severity")
public class SeverityProperties {

    private FrequencyConfig frequency = new FrequencyConfig();
    private PlayerCountConfig playerCount = new PlayerCountConfig();
    private BusinessImpactConfig businessImpact = new BusinessImpactConfig();
    private BlockingConfig blocking = new BlockingConfig();
    private CrashConfig crash = new CrashConfig();

    /** 발생 빈도 기반 점수 설정 */
    @Getter
    @Setter
    public static class FrequencyConfig {
        private List<Threshold> thresholds = new ArrayList<>();
    }

    /** 영향받은 플레이어 수 기반 점수 설정 */
    @Getter
    @Setter
    public static class PlayerCountConfig {
        private List<Threshold> thresholds = new ArrayList<>();
    }

    /** 비즈니스 임팩트 키워드 점수 설정 */
    @Getter
    @Setter
    public static class BusinessImpactConfig {
        private Map<String, Integer> keywords = new HashMap<>();
    }

    /** 게임 진행 차단 점수 설정 */
    @Getter
    @Setter
    public static class BlockingConfig {
        private Map<String, Integer> keywords = new HashMap<>();
        private Map<String, Integer> errorTypes = new HashMap<>();
    }

    /** 크래시 심각도 점수 설정 */
    @Getter
    @Setter
    public static class CrashConfig {
        private Map<String, Integer> errorTypes = new HashMap<>();
    }

    /** 점수 임계값 */
    @Getter
    @Setter
    public static class Threshold {
        private long count;
        private int score;
    }
}
