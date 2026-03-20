package kr.java.documind.domain.archive.vector.event;

import kr.java.documind.domain.archive.vector.model.enums.EmbeddingStatus;

public record EmbeddingStatusUpdateEvent(
        Long sourceId, EmbeddingStatus status, boolean excludeFromPatchNote) {

    /** PROCESSING / FAILED 등 패치노트와 무관한 상태 전환용 생성자 */
    public EmbeddingStatusUpdateEvent(Long sourceId, EmbeddingStatus status) {
        this(sourceId, status, false);
    }
}
