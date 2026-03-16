package kr.java.documind.domain.issue.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import kr.java.documind.domain.issue.model.enums.ErrorType;
import kr.java.documind.domain.issue.model.enums.IssueSeverity;
import kr.java.documind.domain.issue.model.enums.IssueStatus;
import kr.java.documind.domain.issue.model.enums.IssueType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 이슈 엔티티 (ERD 기준)
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "issue_type", length = 50)
    private IssueType issueType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private IssueStatus status = IssueStatus.TODO;

    @Column(length = 50)
    private String priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private IssueSeverity severity = IssueSeverity.LOW;

    @Column(name = "severity_score", nullable = false)
    @Builder.Default
    private Integer severityScore = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_type", nullable = false, length = 100)
    @Builder.Default
    private ErrorType errorType = ErrorType.UNKNOWN;

    @Column(name = "stack_key", length = 255)
    private String stackKey;

    @Column(name = "occurrence_count", nullable = false)
    @Builder.Default
    private Integer occurrenceCount = 1;

    @Column(name = "resolution_note", columnDefinition = "TEXT")
    private String resolutionNote;

    @Column(name = "first_occurred_at", nullable = false)
    private OffsetDateTime firstOccurredAt;

    @Column(name = "last_occurred_at", nullable = false)
    private OffsetDateTime lastOccurredAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 비즈니스 로직
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 이슈 발생 횟수 증가
     *
     * <p>Out-of-order 로그 처리: lastOccurredAt은 더 최근 시간으로만 업데이트
     *
     * @param occurredAt 로그 발생 시각
     */
    public void incrementOccurrence(OffsetDateTime occurredAt) {
        this.occurrenceCount++;
        // Out-of-order 로그 대비: 더 최근 시간만 업데이트
        if (this.lastOccurredAt == null || occurredAt.isAfter(this.lastOccurredAt)) {
            this.lastOccurredAt = occurredAt;
        }
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    /**
     * 이슈 상태 변경
     *
     * @param newStatus 새로운 상태
     */
    public void changeStatus(IssueStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);

        // RESOLVED 상태로 변경 시 resolved_at 설정
        if (newStatus == IssueStatus.RESOLVED) {
            this.resolvedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
        // RESOLVED에서 다른 상태로 되돌릴 시 resolved_at 초기화
        else if (this.resolvedAt != null) {
            this.resolvedAt = null;
        }
    }

    /**
     * 담당자 할당
     *
     * @param assigneeId 담당자 ID
     */
    public void assignTo(UUID assigneeId) {
        this.assigneeId = assigneeId;
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    /**
     * 심각도 업데이트 (DM-43)
     *
     * @param severity 심각도 등급
     * @param score 심각도 점수 (0-100)
     */
    public void updateSeverity(IssueSeverity severity, Integer score) {
        this.severity = severity;
        this.severityScore = score;
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    /**
     * 이슈 제목 수정
     *
     * @param title 새로운 제목
     */
    public void updateTitle(String title) {
        this.title = title;
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    /**
     * 이슈 설명 수정
     *
     * @param description 새로운 설명
     */
    public void updateDescription(String description) {
        this.description = description;
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    /**
     * 우선순위 설정
     *
     * @param priority 우선순위
     */
    public void setPriority(String priority) {
        this.priority = priority;
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    /**
     * 해결 노트 작성
     *
     * @param resolutionNote 해결 방법/원인
     */
    public void writeResolutionNote(String resolutionNote) {
        this.resolutionNote = resolutionNote;
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    /** 이슈 추천 승인 (RECOMMENDED → TODO) */
    public void approve() {
        if (this.status != IssueStatus.RECOMMENDED) {
            throw new IllegalStateException(
                    "이슈 추천 상태(RECOMMENDED)에서만 승인할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = IssueStatus.TODO;
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    /** 이슈 추천 거부 (RECOMMENDED → REJECTED) */
    public void reject() {
        if (this.status != IssueStatus.RECOMMENDED) {
            throw new IllegalStateException(
                    "이슈 추천 상태(RECOMMENDED)에서만 거부할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = IssueStatus.REJECTED;
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
