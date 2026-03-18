package kr.java.documind.domain.patchnote.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.archive.vector.infrastructure.VectorStoreManager;
import kr.java.documind.domain.patchnote.exception.PendingItemUpsertFailedException;
import kr.java.documind.domain.patchnote.model.dto.PendingItemCreateDto;
import kr.java.documind.domain.patchnote.model.entity.PendingItem;
import kr.java.documind.domain.patchnote.model.enums.PatchType;
import kr.java.documind.domain.patchnote.model.enums.PendingItemStatus;
import kr.java.documind.domain.patchnote.model.repository.PendingItemRepository;
import kr.java.documind.global.enums.SourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("PendingItemUpsertService 단위 테스트")
class PendingItemUpsertServiceTest {

    @Mock private PendingItemRepository pendingItemRepository;
    @Mock private VectorStoreManager vectorStoreManager;

    @InjectMocks private PendingItemUpsertService pendingItemUpsertService;

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final Long SOURCE_ID = 42L;
    private static final OffsetDateTime NOW = OffsetDateTime.now(ZoneOffset.UTC);

    @BeforeEach
    void injectSelf() {
        // @Lazy @Autowired self 필드는 단위 테스트에서 수동 주입
        ReflectionTestUtils.setField(pendingItemUpsertService, "self", pendingItemUpsertService);
    }

    private PendingItemCreateDto buildDto(PendingItemStatus status) {
        return new PendingItemCreateDto(
                PROJECT_ID,
                SOURCE_ID,
                SourceType.ISSUE,
                "패치노트 제목",
                "패치노트 요약입니다",
                "ㅍㅊㄴㅌ",
                PatchType.FIX,
                status,
                NOW);
    }

    @Nested
    @DisplayName("upsertPendingItem()")
    class UpsertPendingItem {

        @Test
        @DisplayName("upsert: 기존 항목 없음 → 신규 PendingItem 저장")
        void upsertPendingItem_기존항목없음_새항목저장() {
            // Given
            PendingItemCreateDto dto = buildDto(PendingItemStatus.PENDING);
            given(
                            pendingItemRepository.findByProjectIdAndSourceTypeAndSourceId(
                                    PROJECT_ID, SourceType.ISSUE, SOURCE_ID))
                    .willReturn(Optional.empty());

            // When
            pendingItemUpsertService.upsertPendingItem(dto);

            // Then
            then(pendingItemRepository).should().save(any(PendingItem.class));
        }

        @Test
        @DisplayName("upsert: 기존 PENDING 항목 존재 → refresh 호출, save 미호출")
        void upsertPendingItem_기존항목존재_refresh호출() {
            // Given
            PendingItemCreateDto dto = buildDto(PendingItemStatus.PENDING);
            PendingItem existing =
                    PendingItem.create(
                            PROJECT_ID,
                            SOURCE_ID,
                            SourceType.ISSUE,
                            "이전 제목",
                            "이전 요약",
                            "ㅇㅈ",
                            PatchType.FIX,
                            PendingItemStatus.PENDING,
                            NOW.minusDays(1));
            given(
                            pendingItemRepository.findByProjectIdAndSourceTypeAndSourceId(
                                    PROJECT_ID, SourceType.ISSUE, SOURCE_ID))
                    .willReturn(Optional.of(existing));

            // When
            pendingItemUpsertService.upsertPendingItem(dto);

            // Then — title이 갱신되어야 하고, save는 호출되지 않음 (dirty checking)
            assertThat(existing.getTitle()).isEqualTo("패치노트 제목");
            then(pendingItemRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("upsert: 기존 EXCLUDED 항목 refresh → status는 EXCLUDED 유지")
        void upsertPendingItem_EXCLUDED항목_status유지refresh() {
            // Given
            PendingItemCreateDto dto = buildDto(PendingItemStatus.PENDING);
            PendingItem excluded =
                    PendingItem.create(
                            PROJECT_ID,
                            SOURCE_ID,
                            SourceType.ISSUE,
                            "이전 제목",
                            "이전 요약",
                            "ㅇㅈ",
                            PatchType.FIX,
                            PendingItemStatus.EXCLUDED,
                            NOW.minusDays(1));
            given(
                            pendingItemRepository.findByProjectIdAndSourceTypeAndSourceId(
                                    PROJECT_ID, SourceType.ISSUE, SOURCE_ID))
                    .willReturn(Optional.of(excluded));

            // When
            pendingItemUpsertService.upsertPendingItem(dto);

            // Then — title 갱신, status는 EXCLUDED 유지
            assertThat(excluded.getTitle()).isEqualTo("패치노트 제목");
            assertThat(excluded.getStatus()).isEqualTo(PendingItemStatus.EXCLUDED);
        }

        @Test
        @DisplayName("upsert: 기존 COMPLETED 항목 refresh → status는 COMPLETED 유지")
        void upsertPendingItem_COMPLETED항목_status유지refresh() {
            // Given
            PendingItemCreateDto dto = buildDto(PendingItemStatus.PENDING);
            PendingItem completed =
                    PendingItem.create(
                            PROJECT_ID,
                            SOURCE_ID,
                            SourceType.ISSUE,
                            "이전 제목",
                            "이전 요약",
                            "ㅇㅈ",
                            PatchType.FIX,
                            PendingItemStatus.PENDING,
                            NOW.minusDays(1));
            completed.complete(); // PENDING → COMPLETED
            given(
                            pendingItemRepository.findByProjectIdAndSourceTypeAndSourceId(
                                    PROJECT_ID, SourceType.ISSUE, SOURCE_ID))
                    .willReturn(Optional.of(completed));

            // When
            pendingItemUpsertService.upsertPendingItem(dto);

            // Then — 제목은 갱신, COMPLETED 상태 유지
            assertThat(completed.getTitle()).isEqualTo("패치노트 제목");
            assertThat(completed.getStatus()).isEqualTo(PendingItemStatus.COMPLETED);
        }
    }

    @Nested
    @DisplayName("recoverUpsert()")
    class RecoverUpsert {

        @Test
        @DisplayName("recover: 3회 실패 후 벡터 정리 수행 → PendingItemUpsertFailedException 발생")
        void recoverUpsert_호출시_벡터정리후PendingItemUpsertFailedException발생() {
            // Given
            PendingItemCreateDto dto = buildDto(PendingItemStatus.PENDING);
            DataAccessException cause = new DataIntegrityViolationException("DB 오류");

            // When & Then
            assertThatThrownBy(() -> pendingItemUpsertService.recoverUpsert(cause, dto))
                    .isInstanceOf(PendingItemUpsertFailedException.class);
            then(vectorStoreManager).should().deleteBySourceId(SOURCE_ID, SourceType.ISSUE);
        }

        @Test
        @DisplayName("recover: 벡터 정리 실패해도 PendingItemUpsertFailedException 발생")
        void recoverUpsert_벡터정리실패해도_PendingItemUpsertFailedException발생() {
            // Given
            PendingItemCreateDto dto = buildDto(PendingItemStatus.PENDING);
            DataAccessException cause = new DataIntegrityViolationException("DB 오류");
            willThrow(new RuntimeException("벡터 삭제 실패"))
                    .given(vectorStoreManager)
                    .deleteBySourceId(SOURCE_ID, SourceType.ISSUE);

            // When & Then
            assertThatThrownBy(() -> pendingItemUpsertService.recoverUpsert(cause, dto))
                    .isInstanceOf(PendingItemUpsertFailedException.class);
        }
    }

    @Nested
    @DisplayName("saveVectorThenUpsert()")
    class SaveVectorThenUpsert {

        @Test
        @DisplayName("벡터+upsert: 벡터 저장 실패 시 upsertPendingItem 미호출")
        void saveVectorThenUpsert_벡터저장실패시_upsert미호출() {
            // Given
            PendingItemCreateDto dto = buildDto(PendingItemStatus.PENDING);
            willThrow(new RuntimeException("벡터 저장 실패"))
                    .given(vectorStoreManager)
                    .insertChunks(any(), any(), any());

            // When & Then
            assertThatThrownBy(
                            () ->
                                    pendingItemUpsertService.saveVectorThenUpsert(
                                            SOURCE_ID, List.of(), List.of(), dto))
                    .isInstanceOf(RuntimeException.class);
            then(pendingItemRepository)
                    .should(never())
                    .findByProjectIdAndSourceTypeAndSourceId(any(), any(), any());
        }

        @Test
        @DisplayName("벡터+upsert: 벡터 저장 성공 후 upsertPendingItem 호출")
        void saveVectorThenUpsert_벡터저장성공_upsert호출() {
            // Given
            PendingItemCreateDto dto = buildDto(PendingItemStatus.PENDING);
            given(
                            pendingItemRepository.findByProjectIdAndSourceTypeAndSourceId(
                                    PROJECT_ID, SourceType.ISSUE, SOURCE_ID))
                    .willReturn(Optional.empty());

            // When
            pendingItemUpsertService.saveVectorThenUpsert(SOURCE_ID, List.of(), List.of(), dto);

            // Then
            then(vectorStoreManager).should().insertChunks(SOURCE_ID, List.of(), List.of());
            then(pendingItemRepository).should().save(any(PendingItem.class));
        }
    }
}
