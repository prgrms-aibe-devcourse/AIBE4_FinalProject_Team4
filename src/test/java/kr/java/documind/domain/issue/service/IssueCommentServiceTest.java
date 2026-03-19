package kr.java.documind.domain.issue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.issue.model.dto.request.CommentCreateRequest;
import kr.java.documind.domain.issue.model.dto.request.CommentUpdateRequest;
import kr.java.documind.domain.issue.model.dto.response.CommentResponse;
import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.entity.IssueComment;
import kr.java.documind.domain.issue.model.enums.ErrorType;
import kr.java.documind.domain.issue.model.enums.IssuePriority;
import kr.java.documind.domain.issue.model.enums.IssueSeverity;
import kr.java.documind.domain.issue.model.enums.IssueStatus;
import kr.java.documind.domain.issue.model.enums.IssueType;
import kr.java.documind.domain.issue.model.repository.CommentRepository;
import kr.java.documind.domain.issue.model.repository.IssueRepository;
import kr.java.documind.domain.issue.service.workflow.IssueHistoryService;
import kr.java.documind.domain.auth.model.enums.GlobalRole;
import kr.java.documind.domain.auth.model.enums.OAuthProvider;
import kr.java.documind.domain.member.model.entity.Member;
import kr.java.documind.domain.member.model.enums.AccountStatus;
import kr.java.documind.domain.member.model.repository.MemberRepository;
import kr.java.documind.domain.member.model.repository.ProjectMemberRepository;
import kr.java.documind.global.exception.BadRequestException;
import kr.java.documind.global.exception.ForbiddenException;
import kr.java.documind.global.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("IssueCommentService 단위 테스트")
class IssueCommentServiceTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private IssueRepository issueRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private IssueHistoryService issueHistoryService;

    @InjectMocks
    private IssueCommentService issueCommentService;

    private Long issueId;
    private UUID projectId;
    private UUID authorId;
    private UUID mentionedMemberId;
    private Issue issue;
    private Member author;
    private Member mentionedMember;

    @BeforeEach
    void setUp() {
        issueId = 101L;
        projectId = UUID.randomUUID();
        authorId = UUID.randomUUID();
        mentionedMemberId = UUID.randomUUID();

        issue = Issue.builder()
                .projectId(projectId)
                .fingerprint("test-fingerprint")
                .title("NullPointerException")
                .stackKey("PlayerService.java:loadPlayer:42")
                .issueType(IssueType.BUG)
                .errorType(ErrorType.NULL_POINTER)
                .status(IssueStatus.TODO)
                .priority(IssuePriority.P2)
                .severity(IssueSeverity.MEDIUM)
                .severityScore(50)
                .occurrenceCount(1)
                .firstOccurredAt(OffsetDateTime.now(java.time.ZoneOffset.UTC))
                .lastOccurredAt(OffsetDateTime.now(java.time.ZoneOffset.UTC))
                .build();
        ReflectionTestUtils.setField(issue, "id", issueId);

        author = Member.createByOAuth(
                "author@example.com",
                "작성자",
                "작성자",
                OAuthProvider.GOOGLE,
                "google-123",
                GlobalRole.EMPLOYEE
        );
        ReflectionTestUtils.setField(author, "id", authorId);
        ReflectionTestUtils.setField(author, "profileKey", "profile/avatar.png");

        mentionedMember = Member.createByOAuth(
                "mentioned@example.com",
                "김철수",
                "김철수",
                OAuthProvider.GOOGLE,
                "google-456",
                GlobalRole.EMPLOYEE
        );
        ReflectionTestUtils.setField(mentionedMember, "id", mentionedMemberId);
        ReflectionTestUtils.setField(mentionedMember, "profileKey", "profile/avatar2.png");
    }

    @Test
    @DisplayName("댓글 생성 성공: @멘션 포함")
    void createCommentWithMentions() {
        // Given
        String content = "@김철수 메모리 누수 지점이 의심됩니다.";
        List<UUID> mentionIds = List.of(mentionedMemberId);
        CommentCreateRequest request = new CommentCreateRequest(content, mentionIds);

        when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));
        when(projectMemberRepository.existsByProject_IdAndMember_IdAndStatus(
                        eq(projectId), eq(mentionedMemberId), eq(AccountStatus.ACTIVE)))
                .thenReturn(true);
        when(memberRepository.findById(authorId)).thenReturn(Optional.of(author));
        when(memberRepository.findAllById(anyList())).thenReturn(List.of(mentionedMember));

        IssueComment savedComment = IssueComment.create(issueId, authorId, content, mentionIds);
        ReflectionTestUtils.setField(savedComment, "id", 1L);
        when(commentRepository.save(any(IssueComment.class))).thenReturn(savedComment);

        // When
        CommentResponse response =
                issueCommentService.createComment(issueId, projectId, authorId, request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.content()).isEqualTo(content);
        assertThat(response.author().nickname()).isEqualTo("작성자");
        assertThat(response.mentionedMembers()).hasSize(1);
        assertThat(response.mentionedMembers().get(0).nickname()).isEqualTo("김철수");

        verify(commentRepository, times(1)).save(any(IssueComment.class));
        verify(issueHistoryService, times(1)).saveCommentAdded(eq(issueId), eq(authorId), eq(content));
    }

    @Test
    @DisplayName("댓글 생성 성공: 멘션 없음")
    void createCommentWithoutMentions() {
        // Given
        String content = "확인 완료했습니다.";
        CommentCreateRequest request = new CommentCreateRequest(content, List.of());

        when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));
        when(memberRepository.findById(authorId)).thenReturn(Optional.of(author));

        IssueComment savedComment = IssueComment.create(issueId, authorId, content, List.of());
        ReflectionTestUtils.setField(savedComment, "id", 1L);
        when(commentRepository.save(any(IssueComment.class))).thenReturn(savedComment);

        // When
        CommentResponse response =
                issueCommentService.createComment(issueId, projectId, authorId, request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.content()).isEqualTo(content);
        assertThat(response.mentionedMembers()).isEmpty();

        verify(commentRepository, times(1)).save(any(IssueComment.class));
    }

    @Test
    @DisplayName("댓글 생성 실패: 이슈가 존재하지 않음")
    void createCommentFailWhenIssueNotFound() {
        // Given
        CommentCreateRequest request = new CommentCreateRequest("댓글", List.of());
        when(issueRepository.findById(issueId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() ->
                        issueCommentService.createComment(issueId, projectId, authorId, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("이슈를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("댓글 생성 실패: 프로젝트 소유권 불일치")
    void createCommentFailWhenProjectMismatch() {
        // Given
        UUID wrongProjectId = UUID.randomUUID();
        CommentCreateRequest request = new CommentCreateRequest("댓글", List.of());
        when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));

        // When & Then
        assertThatThrownBy(() ->
                        issueCommentService.createComment(issueId, wrongProjectId, authorId, request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("해당 프로젝트의 이슈가 아닙니다");
    }

    @Test
    @DisplayName("댓글 생성 시 프로젝트 멤버가 아닌 멘션 무시")
    void createCommentIgnoreNonProjectMemberMentions() {
        // Given
        UUID nonMemberId = UUID.randomUUID();
        CommentCreateRequest request = new CommentCreateRequest("댓글", List.of(nonMemberId));

        when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));
        when(projectMemberRepository.existsByProject_IdAndMember_IdAndStatus(
                        eq(projectId), eq(nonMemberId), eq(AccountStatus.ACTIVE)))
                .thenReturn(false);
        when(memberRepository.findById(authorId)).thenReturn(Optional.of(author));

        IssueComment savedComment = IssueComment.create(issueId, authorId, "댓글", List.of());
        ReflectionTestUtils.setField(savedComment, "id", 1L);
        when(commentRepository.save(any(IssueComment.class))).thenReturn(savedComment);

        // When
        CommentResponse response =
                issueCommentService.createComment(issueId, projectId, authorId, request);

        // Then
        assertThat(response.mentionedMembers()).isEmpty(); // 무시됨
        verify(commentRepository, times(1)).save(any(IssueComment.class));
    }

    @Test
    @DisplayName("댓글 수정 성공: 멘션 변경")
    void updateCommentWithMentionChange() {
        // Given
        Long commentId = 1L;
        String originalContent = "@김철수 확인 부탁드립니다.";
        List<UUID> originalMentions = List.of(mentionedMemberId);

        UUID newMentionId = UUID.randomUUID();
        String newContent = "@박영희 함께 검토 부탁드립니다.";
        List<UUID> newMentions = List.of(newMentionId);

        IssueComment existingComment = IssueComment.create(issueId, authorId, originalContent, originalMentions);
        ReflectionTestUtils.setField(existingComment, "id", commentId);

        Member newMentionedMember = Member.createByOAuth(
                "park@example.com",
                "박영희",
                "박영희",
                OAuthProvider.GOOGLE,
                "google-789",
                GlobalRole.EMPLOYEE
        );
        ReflectionTestUtils.setField(newMentionedMember, "id", newMentionId);

        CommentUpdateRequest request = new CommentUpdateRequest(newContent, newMentions);

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(existingComment));
        when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));
        when(projectMemberRepository.existsByProject_IdAndMember_IdAndStatus(
                        eq(projectId), eq(newMentionId), eq(AccountStatus.ACTIVE)))
                .thenReturn(true);
        when(memberRepository.findById(authorId)).thenReturn(Optional.of(author));
        when(memberRepository.findAllById(anyList())).thenReturn(List.of(newMentionedMember));

        // When
        CommentResponse response =
                issueCommentService.updateComment(commentId, issueId, projectId, authorId, request);

        // Then
        assertThat(response.content()).isEqualTo(newContent);
        assertThat(response.mentionedMembers()).hasSize(1);
        assertThat(response.mentionedMembers().get(0).nickname()).isEqualTo("박영희");
    }

    @Test
    @DisplayName("댓글 수정 실패: 작성자 본인이 아님")
    void updateCommentFailWhenNotAuthor() {
        // Given
        Long commentId = 1L;
        UUID otherUserId = UUID.randomUUID();
        IssueComment comment = IssueComment.create(issueId, authorId, "댓글", List.of());
        ReflectionTestUtils.setField(comment, "id", commentId);

        CommentUpdateRequest request = new CommentUpdateRequest("수정", List.of());

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));

        // When & Then
        assertThatThrownBy(() ->
                        issueCommentService.updateComment(commentId, issueId, projectId, otherUserId, request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("자신이 작성한 댓글만 수정할 수 있습니다");
    }

    @Test
    @DisplayName("댓글 수정 실패: 다른 이슈의 댓글")
    void updateCommentFailWhenWrongIssue() {
        // Given
        Long commentId = 1L;
        Long wrongIssueId = 999L;
        IssueComment comment = IssueComment.create(issueId, authorId, "댓글", List.of());
        ReflectionTestUtils.setField(comment, "id", commentId);

        CommentUpdateRequest request = new CommentUpdateRequest("수정", List.of());

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));

        // When & Then
        assertThatThrownBy(() ->
                        issueCommentService.updateComment(commentId, wrongIssueId, projectId, authorId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("해당 이슈의 댓글이 아닙니다");
    }

    @Test
    @DisplayName("댓글 삭제 성공")
    void deleteCommentSuccess() {
        // Given
        Long commentId = 1L;
        IssueComment comment = IssueComment.create(issueId, authorId, "댓글", List.of());
        ReflectionTestUtils.setField(comment, "id", commentId);

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));

        // When
        issueCommentService.deleteComment(commentId, issueId, projectId, authorId);

        // Then
        verify(commentRepository, times(1)).deleteByIdAndIssueId(commentId, issueId);
    }

    @Test
    @DisplayName("댓글 삭제 실패: 작성자 본인이 아님")
    void deleteCommentFailWhenNotAuthor() {
        // Given
        Long commentId = 1L;
        UUID otherUserId = UUID.randomUUID();
        IssueComment comment = IssueComment.create(issueId, authorId, "댓글", List.of());
        ReflectionTestUtils.setField(comment, "id", commentId);

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));

        // When & Then
        assertThatThrownBy(() ->
                        issueCommentService.deleteComment(commentId, issueId, projectId, otherUserId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("자신이 작성한 댓글만 삭제할 수 있습니다");
    }

    @Test
    @DisplayName("댓글 목록 조회 성공")
    void getCommentsSuccess() {
        // Given
        List<IssueComment> comments = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            IssueComment comment = IssueComment.create(issueId, authorId, "댓글 " + i, List.of());
            ReflectionTestUtils.setField(comment, "id", (long) i);
            comments.add(comment);
        }

        when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));
        when(commentRepository.findByIssueIdOrderByCreatedAtAsc(issueId)).thenReturn(comments);
        lenient().when(memberRepository.findById(authorId)).thenReturn(Optional.of(author));

        // When
        var response = issueCommentService.getComments(
                issueId, projectId, org.springframework.data.domain.PageRequest.of(0, 20));

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(5);
        assertThat(response.getTotalElements()).isEqualTo(5);
    }

    @Test
    @DisplayName("댓글 목록 조회 실패: 프로젝트 소유권 불일치")
    void getCommentsFailWhenProjectMismatch() {
        // Given
        UUID wrongProjectId = UUID.randomUUID();
        when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));

        // When & Then
        assertThatThrownBy(() ->
                        issueCommentService.getComments(
                                issueId, wrongProjectId, org.springframework.data.domain.PageRequest.of(0, 20)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("해당 프로젝트의 이슈가 아닙니다");
    }
}
