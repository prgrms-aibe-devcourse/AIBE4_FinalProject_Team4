package kr.java.documind.domain.patchnote.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.java.documind.domain.patchnote.model.dto.IssueChunkingSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

@DisplayName("IssueChunkingService 단위 테스트")
class IssueChunkingServiceTest {

    // 외부 의존성 없는 순수 로직 — 실제 인스턴스 사용
    private final IssueChunkHeuristicAnalyzer heuristicAnalyzer = new IssueChunkHeuristicAnalyzer();
    private final IssueChunkDocumentBuilder documentBuilder = new IssueChunkDocumentBuilder();
    private final IssueChunkingService chunkingService =
            new IssueChunkingService(heuristicAnalyzer, documentBuilder);

    private static final Long ISSUE_ID = 1L;
    private static final UUID PROJECT_ID = UUID.randomUUID();

    /** resolutionNote가 없는 기본 소스 */
    private IssueChunkingSource sourceWithoutResolution(String description) {
        return new IssueChunkingSource(
                ISSUE_ID, PROJECT_ID, "테스트 이슈", description, null, "HIGH", "BUG", null, List.of());
    }

    /** resolutionNote가 있는 소스 */
    private IssueChunkingSource sourceWithResolution(String resolutionNote) {
        return new IssueChunkingSource(
                ISSUE_ID,
                PROJECT_ID,
                "테스트 이슈",
                "충분한 설명입니다",
                resolutionNote,
                "HIGH",
                "BUG",
                null,
                List.of());
    }

    @Nested
    @DisplayName("buildChunks() - 청크 수 규칙")
    class ChunkCountRules {

        @Test
        @DisplayName("청크 생성: resolutionNote=null → background 청크 1개만 생성")
        void buildChunks_resolutionNote없음_background1개생성() {
            // Given
            IssueChunkingSource source = sourceWithoutResolution("충분한 설명입니다");

            // When
            List<Document> chunks = chunkingService.buildChunks(source);

            // Then
            assertThat(chunks).hasSize(1);
            assertThat(chunks.get(0).getMetadata()).containsEntry("chunk_role", "background");
        }

        @Test
        @DisplayName("청크 생성: resolutionNote=공백 → background 청크 1개만 생성")
        void buildChunks_resolutionNote공백_background1개생성() {
            // Given
            IssueChunkingSource source =
                    new IssueChunkingSource(
                            ISSUE_ID,
                            PROJECT_ID,
                            "테스트 이슈",
                            "충분한 설명입니다",
                            "   ",
                            "HIGH",
                            "BUG",
                            null,
                            List.of());

            // When
            List<Document> chunks = chunkingService.buildChunks(source);

            // Then
            assertThat(chunks).hasSize(1);
        }

        @Test
        @DisplayName("청크 생성: resolutionNote 있고 500자 이하 → background+resolution+merged 3개 생성")
        void buildChunks_resolutionNote있고500자이하_3개청크생성() {
            // Given
            String shortResolution = "A".repeat(50); // 50자
            IssueChunkingSource source = sourceWithResolution(shortResolution);

            // When
            List<Document> chunks = chunkingService.buildChunks(source);

            // Then
            assertThat(chunks).hasSize(3);
        }

        @Test
        @DisplayName("청크 생성: resolutionNote 501자 초과 → background+resolution 2개만 생성 (merged 없음)")
        void buildChunks_resolutionNote500자초과_2개청크생성() {
            // Given
            String longResolution = "A".repeat(501); // 501자
            IssueChunkingSource source = sourceWithResolution(longResolution);

            // When
            List<Document> chunks = chunkingService.buildChunks(source);

            // Then
            assertThat(chunks).hasSize(2);
        }

        @Test
        @DisplayName("청크 생성: resolutionNote 정확히 500자 → merged 포함 3개 생성")
        void buildChunks_resolutionNote정확히500자_3개청크생성() {
            // Given
            String exactly500 = "A".repeat(500);
            IssueChunkingSource source = sourceWithResolution(exactly500);

            // When
            List<Document> chunks = chunkingService.buildChunks(source);

            // Then
            assertThat(chunks).hasSize(3);
        }
    }

    @Nested
    @DisplayName("buildChunks() - chunk_role 및 chunk_contains_resolution 메타데이터")
    class ChunkRoleMetadata {

        @Test
        @DisplayName("청크 생성: 3개 청크의 chunk_role이 순서대로 background/resolution/background_resolution")
        void buildChunks_3개청크시_각chunk_role확인() {
            // Given
            IssueChunkingSource source = sourceWithResolution("50자 이내의 해결 방법입니다 한글로");

            // When
            List<Document> chunks = chunkingService.buildChunks(source);

            // Then
            assertThat(chunks).hasSize(3);
            assertThat(chunks.get(0).getMetadata()).containsEntry("chunk_role", "background");
            assertThat(chunks.get(1).getMetadata()).containsEntry("chunk_role", "resolution");
            assertThat(chunks.get(2).getMetadata())
                    .containsEntry("chunk_role", "background_resolution");
        }

        @Test
        @DisplayName(
                "청크 생성: chunk_contains_resolution — background=false, resolution=true, merged=true")
        void buildChunks_chunk_contains_resolution_metadata확인() {
            // Given
            IssueChunkingSource source = sourceWithResolution("해결 방법입니다");

            // When
            List<Document> chunks = chunkingService.buildChunks(source);

            // Then
            assertThat(chunks.get(0).getMetadata())
                    .containsEntry("chunk_contains_resolution", false);
            assertThat(chunks.get(1).getMetadata())
                    .containsEntry("chunk_contains_resolution", true);
            assertThat(chunks.get(2).getMetadata())
                    .containsEntry("chunk_contains_resolution", true);
        }
    }

    @Nested
    @DisplayName("buildChunks() - 분석 메타데이터 (has_numeric_change, affects_player)")
    class AnalysisMetadata {

        @Test
        @DisplayName("청크 생성: description에 수치 변경 패턴 포함 → 모든 청크 has_numeric_change=true")
        void buildChunks_수치변경감지_hasNumericChange_true() {
            // Given — "50% 증가" 패턴
            IssueChunkingSource source =
                    new IssueChunkingSource(
                            ISSUE_ID,
                            PROJECT_ID,
                            "테스트 이슈",
                            "데미지가 50% 증가했습니다",
                            null,
                            "HIGH",
                            "BALANCE",
                            null,
                            List.of());

            // When
            List<Document> chunks = chunkingService.buildChunks(source);

            // Then
            assertThat(chunks)
                    .allSatisfy(
                            chunk ->
                                    assertThat(chunk.getMetadata())
                                            .containsEntry("has_numeric_change", true));
        }

        @Test
        @DisplayName("청크 생성: description에 플레이어 영향 키워드 포함 → 모든 청크 affects_player=true")
        void buildChunks_플레이어영향감지_affectsPlayer_true() {
            // Given — "로그인" 키워드
            IssueChunkingSource source =
                    new IssueChunkingSource(
                            ISSUE_ID,
                            PROJECT_ID,
                            "테스트 이슈",
                            "로그인 시 오류가 발생합니다",
                            null,
                            "HIGH",
                            "BUG",
                            null,
                            List.of());

            // When
            List<Document> chunks = chunkingService.buildChunks(source);

            // Then
            assertThat(chunks)
                    .allSatisfy(
                            chunk ->
                                    assertThat(chunk.getMetadata())
                                            .containsEntry("affects_player", true));
        }

        @Test
        @DisplayName("청크 생성: 수치/플레이어 키워드 없음 → has_numeric_change=false, affects_player=false")
        void buildChunks_수치와플레이어없음_false메타데이터() {
            // Given
            IssueChunkingSource source = sourceWithoutResolution("일반적인 서버 내부 오류입니다");

            // When
            List<Document> chunks = chunkingService.buildChunks(source);

            // Then
            Map<String, Object> metadata = chunks.get(0).getMetadata();
            assertThat(metadata).containsEntry("has_numeric_change", false);
            assertThat(metadata).containsEntry("affects_player", false);
        }
    }

    @Nested
    @DisplayName("buildChunks() - 공통 메타데이터")
    class CommonMetadata {

        @Test
        @DisplayName("청크 생성: 모든 청크의 source_type=ISSUE, source_id=issueId")
        void buildChunks_source_type_항상ISSUE() {
            // Given
            IssueChunkingSource source = sourceWithoutResolution("충분한 설명입니다");

            // When
            List<Document> chunks = chunkingService.buildChunks(source);

            // Then
            assertThat(chunks)
                    .allSatisfy(
                            chunk -> {
                                assertThat(chunk.getMetadata())
                                        .containsEntry("source_type", "ISSUE");
                                assertThat(chunk.getMetadata())
                                        .containsEntry("source_id", ISSUE_ID);
                            });
        }

        @Test
        @DisplayName("청크 생성: chunk_index 순서대로 0,1,2 / total_chunks=3")
        void buildChunks_chunk_index_순서확인() {
            // Given
            IssueChunkingSource source = sourceWithResolution("짧은 해결 방법");

            // When
            List<Document> chunks = chunkingService.buildChunks(source);

            // Then
            assertThat(chunks).hasSize(3);
            assertThat(chunks.get(0).getMetadata()).containsEntry("chunk_index", 0);
            assertThat(chunks.get(1).getMetadata()).containsEntry("chunk_index", 1);
            assertThat(chunks.get(2).getMetadata()).containsEntry("chunk_index", 2);
            assertThat(chunks)
                    .allSatisfy(
                            chunk ->
                                    assertThat(chunk.getMetadata())
                                            .containsEntry("total_chunks", 3));
        }

        @Test
        @DisplayName("청크 생성: resolutionNote 없을 때 total_chunks=1 metadata 확인")
        void buildChunks_background1개_total_chunks_1() {
            // Given
            IssueChunkingSource source = sourceWithoutResolution("충분한 설명입니다");

            // When
            List<Document> chunks = chunkingService.buildChunks(source);

            // Then
            assertThat(chunks).hasSize(1);
            assertThat(chunks.get(0).getMetadata()).containsEntry("total_chunks", 1);
        }

        @Test
        @DisplayName("청크 생성: resolutionNote 500자 초과 시 total_chunks=2 metadata 확인")
        void buildChunks_background_resolution2개_total_chunks_2() {
            // Given
            IssueChunkingSource source = sourceWithResolution("A".repeat(501));

            // When
            List<Document> chunks = chunkingService.buildChunks(source);

            // Then
            assertThat(chunks).hasSize(2);
            assertThat(chunks)
                    .allSatisfy(
                            chunk ->
                                    assertThat(chunk.getMetadata())
                                            .containsEntry("total_chunks", 2));
        }

        @Test
        @DisplayName("청크 생성: project_id, severity, error_type 메타데이터 포함")
        void buildChunks_필수메타데이터_포함() {
            // Given
            IssueChunkingSource source = sourceWithoutResolution("충분한 설명입니다");

            // When
            List<Document> chunks = chunkingService.buildChunks(source);

            // Then
            Map<String, Object> metadata = chunks.get(0).getMetadata();
            assertThat(metadata).containsEntry("project_id", PROJECT_ID);
            assertThat(metadata).containsEntry("severity", "HIGH");
            assertThat(metadata).containsEntry("error_type", "BUG");
        }
    }

    @Nested
    @DisplayName("buildChunks() - has_resolution / issue_title / null 처리")
    class AdditionalMetadata {

        @Test
        @DisplayName("청크 생성: resolutionNote 있을 때 has_resolution=true (모든 청크 공통)")
        void buildChunks_resolutionNote있을때_has_resolution_true() {
            // Given
            IssueChunkingSource source = sourceWithResolution("짧은 해결 방법");

            // When
            List<Document> chunks = chunkingService.buildChunks(source);

            // Then
            assertThat(chunks)
                    .allSatisfy(
                            chunk ->
                                    assertThat(chunk.getMetadata())
                                            .containsEntry("has_resolution", true));
        }

        @Test
        @DisplayName("청크 생성: resolutionNote 없을 때 has_resolution=false")
        void buildChunks_resolutionNote없을때_has_resolution_false() {
            // Given
            IssueChunkingSource source = sourceWithoutResolution("충분한 설명입니다");

            // When
            List<Document> chunks = chunkingService.buildChunks(source);

            // Then
            assertThat(chunks.get(0).getMetadata()).containsEntry("has_resolution", false);
        }

        @Test
        @DisplayName("청크 생성: issue_title 메타데이터가 소스 title과 일치")
        void buildChunks_issue_title_metadata포함() {
            // Given
            IssueChunkingSource source = sourceWithoutResolution("충분한 설명입니다");

            // When
            List<Document> chunks = chunkingService.buildChunks(source);

            // Then
            assertThat(chunks.get(0).getMetadata()).containsEntry("issue_title", "테스트 이슈");
        }

        @Test
        @DisplayName("청크 생성: severity=null → metadata에 \"UNKNOWN\" 저장")
        void buildChunks_severity_null_UNKNOWN저장() {
            // Given
            IssueChunkingSource source =
                    new IssueChunkingSource(
                            ISSUE_ID,
                            PROJECT_ID,
                            "테스트 이슈",
                            "충분한 설명입니다",
                            null,
                            null,
                            "BUG",
                            null,
                            List.of());

            // When
            List<Document> chunks = chunkingService.buildChunks(source);

            // Then
            assertThat(chunks.get(0).getMetadata()).containsEntry("severity", "UNKNOWN");
        }

        @Test
        @DisplayName("청크 생성: issueType=null → error_type metadata에 \"UNKNOWN\" 저장")
        void buildChunks_issueType_null_UNKNOWN저장() {
            // Given
            IssueChunkingSource source =
                    new IssueChunkingSource(
                            ISSUE_ID,
                            PROJECT_ID,
                            "테스트 이슈",
                            "충분한 설명입니다",
                            null,
                            "HIGH",
                            null,
                            null,
                            List.of());

            // When
            List<Document> chunks = chunkingService.buildChunks(source);

            // Then
            assertThat(chunks.get(0).getMetadata()).containsEntry("error_type", "UNKNOWN");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 이슈 댓글 관련 테스트 — 댓글 기능 구현 후 활성화
    // ──────────────────────────────────────────────────────────────────────────

    // TODO: 이슈 댓글 기능 구현 후 활성화
    //
    // @Test
    // @DisplayName("청크 생성: 15자 이상 댓글 → comment 청크 추가")
    // void buildChunks_의미있는댓글_comment청크추가() {
    //     // Given
    //     IssueCommentChunkSource comment = new IssueCommentChunkSource(
    //             100L, "이 부분에서 NPE가 발생했습니다 (15자 이상)", Instant.now());
    //     IssueChunkingSource source = new IssueChunkingSource(
    //             ISSUE_ID, PROJECT_ID, "테스트 이슈", "충분한 설명입니다",
    //             null, "HIGH", "BUG", null, List.of(comment));
    //
    //     // When
    //     List<Document> chunks = chunkingService.buildChunks(source);
    //
    //     // Then
    //     assertThat(chunks).hasSize(2); // background + comment
    //     assertThat(chunks.get(1).getMetadata()).containsEntry("chunk_role", "comment");
    //     assertThat(chunks.get(1).getMetadata()).containsEntry("comment_id", 100L);
    // }

    // TODO: 이슈 댓글 기능 구현 후 활성화
    //
    // @Test
    // @DisplayName("청크 생성: 14자 이하 댓글 → comment 청크 미추가")
    // void buildChunks_짧은댓글_comment청크미추가() {
    //     // Given — 14자 댓글 (MIN_COMMENT_LENGTH=15 미만)
    //     IssueCommentChunkSource shortComment = new IssueCommentChunkSource(
    //             101L, "짧은댓글123456", Instant.now()); // 10자
    //     IssueChunkingSource source = new IssueChunkingSource(
    //             ISSUE_ID, PROJECT_ID, "테스트 이슈", "충분한 설명입니다",
    //             null, "HIGH", "BUG", null, List.of(shortComment));
    //
    //     // When
    //     List<Document> chunks = chunkingService.buildChunks(source);
    //
    //     // Then
    //     assertThat(chunks).hasSize(1); // background만
    // }

    // TODO: 이슈 댓글 기능 구현 후 활성화
    //
    // @Test
    // @DisplayName("청크 생성: 공백만 있는 댓글 → comment 청크 미추가")
    // void buildChunks_공백댓글_comment청크미추가() {
    //     // Given
    //     IssueCommentChunkSource blankComment = new IssueCommentChunkSource(
    //             102L, "               ", Instant.now()); // 공백 15자
    //     IssueChunkingSource source = new IssueChunkingSource(
    //             ISSUE_ID, PROJECT_ID, "테스트 이슈", "충분한 설명입니다",
    //             null, "HIGH", "BUG", null, List.of(blankComment));
    //
    //     // When
    //     List<Document> chunks = chunkingService.buildChunks(source);
    //
    //     // Then
    //     assertThat(chunks).hasSize(1);
    // }

    // TODO: 이슈 댓글 기능 구현 후 활성화
    //
    // @Test
    // @DisplayName("청크 생성: comment 청크 metadata에 comment_id, comment_index 포함")
    // void buildChunks_comment청크_metadata검증() {
    //     // Given
    //     IssueCommentChunkSource comment = new IssueCommentChunkSource(
    //             200L, "이 버그는 서버 재시작 후 재현됩니다", Instant.now());
    //     IssueChunkingSource source = new IssueChunkingSource(
    //             ISSUE_ID, PROJECT_ID, "테스트 이슈", "충분한 설명입니다",
    //             null, "HIGH", "BUG", null, List.of(comment));
    //
    //     // When
    //     List<Document> chunks = chunkingService.buildChunks(source);
    //
    //     // Then
    //     Document commentChunk = chunks.get(1);
    //     assertThat(commentChunk.getMetadata()).containsEntry("comment_id", 200L);
    //     assertThat(commentChunk.getMetadata()).containsEntry("comment_index", 0);
    // }
}
