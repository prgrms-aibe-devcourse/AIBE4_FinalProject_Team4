package kr.java.documind.domain.logprocessor.model.dto;

import kr.java.documind.domain.logprocessor.model.entity.GameLog;
import org.springframework.data.redis.connection.stream.RecordId;

/**
 * 로그 버퍼 처리를 위한 래퍼 레코드
 *
 * <p>GameLog와 Redis RecordId, 재시도 횟수를 함께 관리
 */
public record LogWrapper(GameLog log, RecordId recordId, int retryCount) {

    /**
     * retryCount를 포함하지 않는 생성자 (기존 호환성 유지)
     *
     * @param log GameLog 엔티티
     * @param recordId Redis Stream RecordId (없을 경우 null)
     */
    public LogWrapper(GameLog log, RecordId recordId) {
        this(log, recordId, 0);
    }
}
