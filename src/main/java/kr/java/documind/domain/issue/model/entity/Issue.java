package kr.java.documind.domain.issue.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import kr.java.documind.domain.issue.model.enums.FingerprintQuality;
import kr.java.documind.domain.issue.model.enums.IssueStatus;
import kr.java.documind.domain.logprocessor.model.enums.LogSeverity;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 이슈 엔티티
 *
 * <p>동일한 fingerprint를 가진 로그들을 그룹핑한 이슈
 */
@Entity(name = "issue")
@Table(name = "issue")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Issue {

    @Id private UUID issueId;

    @Column(nullable = false)
    private UUID projectId;

    @Column(nullable = false, length = 64, unique = true)
    private String fingerprint; // SHA-256 해시

    @Column(nullable = false, length = 500)
    private String title; // 이슈 제목 (예외 타입 또는 메시지)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssueStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LogSeverity severity; // 첫 발생 시 severity

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FingerprintQuality fingerprintQuality;

    @Column(nullable = false)
    private Long occurrenceCount; // 발생 횟수

    @Column(nullable = false)
    private OffsetDateTime firstOccurredAt; // 첫 발생 시각

    @Column(nullable = false)
    private OffsetDateTime lastOccurredAt; // 마지막 발생 시각

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    // 비즈니스 로직

    /**
     * 이슈 발생 횟수 증가
     *
     * @param occurredAt 로그 발생 시각
     */
    public void incrementOccurrence(OffsetDateTime occurredAt) {
        this.occurrenceCount++;
        this.lastOccurredAt = occurredAt;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 이슈 상태 변경
     *
     * @param newStatus 새로운 상태
     */
    public void changeStatus(IssueStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 수동 검토가 필요한 이슈인지 확인
     *
     * @return REQUIRES_REVIEW 상태이면 true
     */
    public boolean requiresReview() {
        return this.status == IssueStatus.REQUIRES_REVIEW;
    }
}
