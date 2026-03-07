package kr.java.documind.global.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.redisson.Bucket4jRedisson;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.command.CommandAsyncExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class Bucket4jConfig {

    private final RedissonClient redissonClient;

    @Value("${app.rate-limit.expiration-hours:1}")
    private long expirationHours;

    /** 분산 환경에서 버킷 상태를 관리하는 ProxyManager를 Bean으로 등록 */
    @Bean
    public ProxyManager<String> proxyManager() {
        // 인터페이스 다운캐스팅 위험 방어 로직
        if (!(redissonClient instanceof Redisson)) {
            throw new IllegalArgumentException("Bucket4j 공식 가이드에 따라 실제 Redisson 구현체가 필요합니다.");
        }

        // Redis 명령어를 직접 실행하기 위해 RedissonClient를 실제 구현체인 Redisson으로 캐스팅하여 내부 Async 엔진을 추출
        CommandAsyncExecutor commandExecutor = ((Redisson) redissonClient).getCommandExecutor();

        return Bucket4jRedisson.casBasedBuilder(commandExecutor)
                // 버킷이 다시 채워지는 시간에 기반한 유동적 만료 전략
                .expirationAfterWrite(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(
                                Duration.ofHours(expirationHours)))
                .build();
    }
}
