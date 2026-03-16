package kr.java.documind.domain.issue.model.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

/**
 * 이슈 댓글 엔티티
 *
 * <p>이슈에 대한 협업 커뮤니케이션을 위한 댓글
 */
@Entity(name = "comment")
@Table(name = "comment")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "issue_id", nullable = false)
    private Long issueId;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * 멘션된 사용자 ID 목록 (파싱 결과 저장)
     *
     * <p>댓글 본문에서 @닉네임으로 멘션된 사용자들의 UUID 배열
     *
     * <p>저장 이유: 매번 파싱하지 않고, 알림 발송 시 즉시 사용
     */
    @Type(JsonBinaryType.class)
    @Column(name = "mentioned_member_ids", columnDefinition = "jsonb")
    private List<UUID> mentionedMemberIds;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * 댓글 생성 (정적 팩토리 메서드)
     *
     * @param issueId 이슈 ID
     * @param memberId 작성자 ID
     * @param content 댓글 내용
     * @param mentionedMemberIds 멘션된 사용자 ID 목록
     * @return Comment 엔티티
     */
    public static Comment create(
            Long issueId, UUID memberId, String content, List<UUID> mentionedMemberIds) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return Comment.builder()
                .issueId(issueId)
                .memberId(memberId)
                .content(content)
                .mentionedMemberIds(mentionedMemberIds)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * 댓글 내용 수정
     *
     * @param newContent 새로운 댓글 내용
     * @param newMentionedMemberIds 새로운 멘션 대상자 목록
     */
    public void updateContent(String newContent, List<UUID> newMentionedMemberIds) {
        this.content = newContent;
        this.mentionedMemberIds = newMentionedMemberIds;
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
