package kr.java.documind.global.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.ReadOffset;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisStreamConfig {

    @Value("${redis.stream.key}")
    private String streamKey;

    @Value("${redis.stream.group}")
    private String consumerGroup;

    private final RedisConnectionFactory redisConnectionFactory;

    /** Consumer Group 초기화 Scheduled 폴링 방식에서도 Consumer Group이 필요하므로 초기화 */
    @PostConstruct
    public void initConsumerGroup() {
        createConsumerGroupIfNotExists();
    }

    private void createConsumerGroupIfNotExists() {
        // try-with-resources로 Connection을 안전하게 닫음
        try (var connection = redisConnectionFactory.getConnection()) {
            connection
                    .streamCommands()
                    .xGroupCreate(
                            streamKey.getBytes(), consumerGroup, ReadOffset.from("0-0"), true);
            log.info("Redis Stream consumer group created: {}", consumerGroup);
        } catch (Exception e) {
            // Root Cause의 메시지까지 확인하여 "already exists" 에러 감지
            String rootMessage = NestedExceptionUtils.getMostSpecificCause(e).getMessage();

            if (rootMessage != null && rootMessage.contains("BUSYGROUP")) {
                log.debug("Consumer group already exists: {}", consumerGroup);
            } else {
                log.error("Failed to create Redis Stream consumer group", e);
            }
        }
    }
}
