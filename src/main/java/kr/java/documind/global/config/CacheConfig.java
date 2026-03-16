package kr.java.documind.global.config;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.util.List;
import kr.java.documind.domain.member.model.dto.ProjectSummary;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        JavaType projectSummaryListType =
                objectMapper
                        .getTypeFactory()
                        .constructCollectionType(List.class, ProjectSummary.class);

        RedisSerializer<Object> projectSummarySerializer =
                new RedisSerializer<Object>() {
                    @Override
                    public byte[] serialize(Object t) throws SerializationException {
                        try {
                            return objectMapper.writeValueAsBytes(t);
                        } catch (Exception e) {
                            throw new SerializationException("Redis 직렬화 에러", e);
                        }
                    }

                    @Override
                    public Object deserialize(byte[] bytes) throws SerializationException {
                        if (bytes == null || bytes.length == 0) return null;
                        try {
                            return objectMapper.readValue(bytes, projectSummaryListType);
                        } catch (Exception e) {
                            throw new SerializationException("Redis 역직렬화 에러", e);
                        }
                    }
                };

        RedisCacheConfiguration projectSelectorConfig =
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofSeconds(60))
                        .serializeKeysWith(
                                RedisSerializationContext.SerializationPair.fromSerializer(
                                        new StringRedisSerializer()))
                        .serializeValuesWith(
                                RedisSerializationContext.SerializationPair.fromSerializer(
                                        projectSummarySerializer));

        return RedisCacheManager.builder(connectionFactory)
                .withCacheConfiguration("projectSelector", projectSelectorConfig)
                .build();
    }
}
