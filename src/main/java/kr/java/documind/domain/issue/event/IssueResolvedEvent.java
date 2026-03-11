package kr.java.documind.domain.issue.event;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 이슈 해결 이벤트
 *
 * <p>이슈가 RESOLVED 상태로 변경되었을 때 발행되어 AI 패치노트 생성을 트리거
 */
public record IssueResolvedEvent(
        Long issueId,
        UUID projectId,
        String title,
        String description,
        String fingerprint,
        OffsetDateTime resolvedAt,
        UUID resolvedBy) {}
