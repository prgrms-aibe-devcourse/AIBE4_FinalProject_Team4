package kr.java.documind.domain.patchnote.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 이슈 삭제 이벤트.
 *
 * <p>이슈가 삭제될 때 발행되며, 패치노트 도메인이 구독하여
 * 연관된 pending_item을 sourceDeleted 상태로 처리한다.
 */
public record IssueDeletedEvent(
        Long issueId,
        UUID projectId,
        UUID actorId,
        Instant occurredAt) {}
