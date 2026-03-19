package kr.java.documind.domain.patchnote.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.patchnote.model.dto.FeedQuery;
import kr.java.documind.domain.patchnote.model.dto.PendingItemDetail;
import kr.java.documind.domain.patchnote.model.dto.PendingItemSummary;
import kr.java.documind.domain.patchnote.model.entity.PendingItem;
import kr.java.documind.domain.patchnote.model.enums.FeedMode;
import kr.java.documind.domain.patchnote.model.enums.PatchType;
import kr.java.documind.domain.patchnote.model.enums.PendingItemStatus;
import kr.java.documind.domain.patchnote.model.repository.PendingItemRepository;
import kr.java.documind.global.enums.SourceType;
import kr.java.documind.global.exception.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PendingItemQueryService 단위 테스트")
class PendingItemQueryServiceTest {

    @Mock private PendingItemRepository pendingItemRepository;

    @InjectMocks private PendingItemQueryService pendingItemQueryService;

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID OTHER_PROJECT_ID = UUID.randomUUID();
    private static final Long ITEM_ID = 42L;
    private static final Long SOURCE_ID = 7L;
    private static final String PUBLIC_ID = "proj-abc123";
    private static final OffsetDateTime NOW = OffsetDateTime.now(ZoneOffset.UTC);

    private PendingItem buildItem(UUID projectId, SourceType sourceType, boolean sourceDeleted) {
        PendingItem item = PendingItem.create(
                projectId,
                SOURCE_ID,
                sourceType,
                "패치 제목",
                "패치 요약",
                "ㅍㅊ",
                PatchType.FIX,
                PendingItemStatus.PENDING,
                NOW);
        if (sourceDeleted) {
            item.markSourceDeleted();
        }
        return item;
    }

    // ─────────────────────────────────────────────
    // getFeed
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("getFeed()")
    class GetFeed {

        @Test
        @DisplayName("피드 조회: mode=null → PENDING으로 정규화, includeExcluded=false/includeCompleted=false 전달")
        void getFeed_mode_null_PENDING으로정규화() {
            // Given — mode=null → FeedQuery compact constructor가 FeedMode.PENDING으로 정규화
            FeedQuery query = new FeedQuery(null, null, null, null, null, null);
            given(pendingItemRepository.findFeed(
                            eq(PROJECT_ID), isNull(), isNull(), isNull(), isNull(), isNull(),
                            eq(false), eq(false)))
                    .willReturn(List.of());

            // When
            List<PendingItemSummary> result =
                    pendingItemQueryService.getFeed(PROJECT_ID, query);

            // Then
            assertThat(result).isEmpty();
            then(pendingItemRepository)
                    .should()
                    .findFeed(PROJECT_ID, null, null, null, null, null, false, false);
        }

        @Test
        @DisplayName("피드 조회: FeedMode.EXCLUDED → includeExcluded=true, includeCompleted=false 전달")
        void getFeed_mode_EXCLUDED_includeExcluded_true() {
            // Given
            FeedQuery query = new FeedQuery(null, null, null, null, null, FeedMode.EXCLUDED);
            given(pendingItemRepository.findFeed(
                            eq(PROJECT_ID), isNull(), isNull(), isNull(), isNull(), isNull(),
                            eq(true), eq(false)))
                    .willReturn(List.of());

            // When
            pendingItemQueryService.getFeed(PROJECT_ID, query);

            // Then
            then(pendingItemRepository)
                    .should()
                    .findFeed(PROJECT_ID, null, null, null, null, null, true, false);
        }

        @Test
        @DisplayName("피드 조회: FeedMode.COMPLETED → includeExcluded=false, includeCompleted=true 전달")
        void getFeed_mode_COMPLETED_includeCompleted_true() {
            // Given
            FeedQuery query = new FeedQuery(null, null, null, null, null, FeedMode.COMPLETED);
            given(pendingItemRepository.findFeed(
                            eq(PROJECT_ID), isNull(), isNull(), isNull(), isNull(), isNull(),
                            eq(false), eq(true)))
                    .willReturn(List.of());

            // When
            pendingItemQueryService.getFeed(PROJECT_ID, query);

            // Then
            then(pendingItemRepository)
                    .should()
                    .findFeed(PROJECT_ID, null, null, null, null, null, false, true);
        }

        @Test
        @DisplayName("피드 조회: 결과 → PendingItemSummary 목록 반환")
        void getFeed_결과있을때_summary목록반환() {
            // Given
            PendingItem item = buildItem(PROJECT_ID, SourceType.ISSUE, false);
            FeedQuery query = new FeedQuery(null, null, null, null, null, FeedMode.PENDING);
            given(pendingItemRepository.findFeed(
                            eq(PROJECT_ID), isNull(), isNull(), isNull(), isNull(), isNull(),
                            eq(false), eq(false)))
                    .willReturn(List.of(item));

            // When
            List<PendingItemSummary> result =
                    pendingItemQueryService.getFeed(PROJECT_ID, query);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).title()).isEqualTo("패치 제목");
        }
    }

    // ─────────────────────────────────────────────
    // getDetail
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("getDetail()")
    class GetDetail {

        @Test
        @DisplayName("상세 조회: ISSUE 소스 → /projects/{publicId}/issues/{sourceId}/analysis")
        void getDetail_ISSUE_sourceLink() {
            // Given
            PendingItem item = buildItem(PROJECT_ID, SourceType.ISSUE, false);
            given(pendingItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item));

            // When
            PendingItemDetail detail =
                    pendingItemQueryService.getDetail(PROJECT_ID, ITEM_ID, PUBLIC_ID);

            // Then
            assertThat(detail.sourceLink())
                    .isEqualTo("/projects/" + PUBLIC_ID + "/issues/" + SOURCE_ID + "/analysis");
        }

        @Test
        @DisplayName("상세 조회: DOCUMENT 소스 → /projects/{publicId}/documents/{sourceId}")
        void getDetail_DOCUMENT_sourceLink() {
            // Given
            PendingItem item = buildItem(PROJECT_ID, SourceType.DOCUMENT, false);
            given(pendingItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item));

            // When
            PendingItemDetail detail =
                    pendingItemQueryService.getDetail(PROJECT_ID, ITEM_ID, PUBLIC_ID);

            // Then
            assertThat(detail.sourceLink())
                    .isEqualTo("/projects/" + PUBLIC_ID + "/documents/" + SOURCE_ID);
        }

        @Test
        @DisplayName("상세 조회: sourceDeleted=true → sourceLink=null")
        void getDetail_sourceDeleted_sourceLinkNull() {
            // Given
            PendingItem item = buildItem(PROJECT_ID, SourceType.ISSUE, true);
            given(pendingItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item));

            // When
            PendingItemDetail detail =
                    pendingItemQueryService.getDetail(PROJECT_ID, ITEM_ID, PUBLIC_ID);

            // Then
            assertThat(detail.sourceLink()).isNull();
            assertThat(detail.sourceDeleted()).isTrue();
        }

        @Test
        @DisplayName("상세 조회: 항목 없음 → NotFoundException")
        void getDetail_항목없음_NotFoundException() {
            // Given
            given(pendingItemRepository.findById(ITEM_ID)).willReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(
                            () -> pendingItemQueryService.getDetail(PROJECT_ID, ITEM_ID, PUBLIC_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining(String.valueOf(ITEM_ID));
        }

        @Test
        @DisplayName("상세 조회: 다른 프로젝트 항목 → NotFoundException")
        void getDetail_다른프로젝트_NotFoundException() {
            // Given — OTHER_PROJECT_ID 소속 항목을 PROJECT_ID로 조회
            PendingItem item = buildItem(OTHER_PROJECT_ID, SourceType.ISSUE, false);
            given(pendingItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item));

            // When / Then
            assertThatThrownBy(
                            () -> pendingItemQueryService.getDetail(PROJECT_ID, ITEM_ID, PUBLIC_ID))
                    .isInstanceOf(NotFoundException.class);
        }
    }
}
