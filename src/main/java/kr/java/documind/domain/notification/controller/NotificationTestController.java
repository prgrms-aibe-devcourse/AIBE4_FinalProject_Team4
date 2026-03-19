package kr.java.documind.domain.notification.controller;

import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.issue.model.enums.IssueSeverity;
import kr.java.documind.domain.notification.event.IssueNotificationEvent;
import kr.java.documind.domain.notification.model.enums.NotificationEventType;
import kr.java.documind.global.annotation.ProjectId;
import kr.java.documind.global.entity.DomainSource;
import kr.java.documind.global.enums.SourceType;
import kr.java.documind.global.repository.DomainSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Profile({"dev", "test"})
@RestController
@RequestMapping("/api/test/projects/{publicId}/notifications")
@RequiredArgsConstructor
public class NotificationTestController {

    private final ApplicationEventPublisher eventPublisher;
    private final DomainSourceRepository domainSourceRepository; // JpaRepository 주입

    @PostMapping("/trigger/issue")
    @Transactional // 🚨 메인 트랜잭션 경계 설정 (AFTER_COMMIT 트리거용)
    public ResponseEntity<String> triggerIssueNotification(
            @ProjectId UUID projectId, @RequestParam UUID receiverId) {

        // 1. 선행 작업: 유효한 DomainSource 영속화 (FK 제약조건 해결)
        // 팩토리 메서드를 통해 SourceType.ISSUE 타입의 엔티티를 생성합니다.
        DomainSource dummySource = DomainSource.create(SourceType.ISSUE); // [cite: 11]

        // Repository를 통해 DB에 영속화.
        // @Transactional 내부이므로 쓰기 지연(Write-behind)이 발생하지만,
        // 엔티티의 ID(BIGSERIAL) 채번을 위해 DB에 즉시 INSERT 쿼리가 날아가거나 영속성 컨텍스트에 ID가 할당됩니다.
        domainSourceRepository.save(dummySource); //

        Long validSourceId = dummySource.getId();

        // 2. 확보된 유효한 ID를 사용하여 알림 이벤트 DTO 조립
        List<UUID> receivers = List.of(receiverId);

        IssueNotificationEvent event =
                new IssueNotificationEvent(
                        projectId,
                        receivers,
                        validSourceId, // 🚨 방금 채번된 유효한 ID 삽입
                        NotificationEventType.ISSUE_STATUS_CHANGED,
                        "테스트 알림",
                        "이것은 테스트 알림입니다.",
                        "/test/url",
                        true,
                        IssueSeverity.HIGH);

        // 3. 메인 트랜잭션 내 이벤트 발행
        eventPublisher.publishEvent(event);

        log.info("[Test API] 가짜 이슈 알림 이벤트 발행 완료. Source ID: {}", validSourceId);

        return ResponseEntity.ok(
                String.format(
                        "가짜 이슈 알림 발행 완료 (생성된 Source ID: %d, 수신자: %d명)",
                        validSourceId, receivers.size()));
    }
}
