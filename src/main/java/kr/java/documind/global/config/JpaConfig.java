package kr.java.documind.global.config;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaConfig {

    /**
     * JPA Auditing용 DateTimeProvider
     *
     * <p>BaseEntity의 createdAt, updatedAt이 OffsetDateTime 타입이므로 UTC 기준 OffsetDateTime 반환
     *
     * <p>타임존 정책: 이슈 추적 시스템은 UTC 사용 (로그 연관성)
     */
    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now(ZoneOffset.UTC));
    }
}
