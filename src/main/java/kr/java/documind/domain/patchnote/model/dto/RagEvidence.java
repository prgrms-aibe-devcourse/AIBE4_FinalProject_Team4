package kr.java.documind.domain.patchnote.model.dto;

/**
 * RAG 컨텍스트 내 단일 증거 블록.
 *
 * <p>벡터 검색 청크 하나 또는 diff evidence 텍스트 하나를 표현한다.
 * {@link ItemContext}가 이 목록을 보유하며, {@link PatchNotePromptService}가
 * 이를 구조화된 증거 블록으로 렌더링한다.
 *
 * <h3>role 값</h3>
 * <ul>
 *   <li>{@code diff_change}  — diff 기반 변경 텍스트 (이전↔현재)
 *   <li>{@code resolution}   — 이슈 해결 정보 청크
 *   <li>{@code background}   — 이슈 배경 정보 청크
 *   <li>{@code combined}     — 배경+해결 병합 청크 (background_resolution)
 *   <li>{@code chunk}        — 문서 청크 또는 역할 미지정 청크
 * </ul>
 *
 * @param sourceRef       이 증거가 속한 소스 REF (예: {@code "ISSUE-42"}, {@code "DOC-17-0"})
 * @param role            증거 역할 (위 값 중 하나)
 * @param text            증거 텍스트 (이미 최대 길이 제한 적용된 상태)
 * @param score           관련성 점수 (벡터 유사도 또는 diff score, 0.0~1.0)
 * @param playerVisible   플레이어 직접 영향 여부 ({@code affects_player} 메타데이터)
 * @param numericChange   수치 변경 포함 여부 ({@code has_numeric_change} 메타데이터)
 * @param releaseSpecific 이번 릴리스 변경사항에서 직접 파생된 증거 여부
 */
public record RagEvidence(
        String sourceRef,
        String role,
        String text,
        double score,
        boolean playerVisible,
        boolean numericChange,
        boolean releaseSpecific) {}
