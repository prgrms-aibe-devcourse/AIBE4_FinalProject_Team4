package kr.java.documind.domain.patchnote.event;

import java.util.UUID;
import kr.java.documind.domain.patchnote.model.enums.PendingItemStatus;

/** 이슈 patchnote 파이프라인 성공 알림 이벤트. TODO: 테스트용 이벤트. 알림 기능 구현 완료되면 변경될 예정 */
public record IssuePendingItemCreatedEvent(
        Long issueId, UUID projectId, UUID actorId, String title, PendingItemStatus status) {}
