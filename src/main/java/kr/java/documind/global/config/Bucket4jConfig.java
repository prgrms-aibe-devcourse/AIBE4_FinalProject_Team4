package kr.java.documind.global.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.redisson.cas.RedissonBasedProxyManager;
import lombok.RequiredArgsConstructor;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.command.CommandAsyncExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class Bucket4jConfig {

    private final RedissonClient redissonClient;

    @Value("${app.rate-limit.expiration-hours:1}")
    private long expirationHours;

    /** 분산 환경에서 버킷 상태를 관리하는 ProxyManager를 Bean으로 등록 */
    @Bean
    public ProxyManager<String> proxyManager() {
        // Redis 명령어를 직접 실행하기 위해 RedissonClient를 실제 구현체인 Redisson으로 캐스팅하여 내부 Async 엔진을 추출
        CommandAsyncExecutor commandExecutor = ((Redisson) redissonClient).getCommandExecutor();

        return RedissonBasedProxyManager.builderFor(commandExecutor)
            // 버킷의 만료 시간을 설정하여 미사용 API Key로 인한 Redis 메모리 낭비 방지
            .withExpirationStrategy(
                ExpirationAfterWriteStrategy.fixedTimeToLive(Duration.ofHours(expirationHours))
            )
            .build();
    }
}
