package kr.java.documind.domain.patchnote.model.dto;

/**
 * 문서 LLM 분석 결과.
 *
 * <p>{@code extract-document-summary.st} 프롬프트의 응답을 파싱한 결과물이다.
 *
 * @param title 피드 표시용 제목 (검색에 최적화된 명확한 키워드 포함)
 * @param summary 핵심 내용 요약 (3~5문장)
 * @param categoryFromLlm LLM이 분류한 카테고리 문자열 ("NEW" / "CHANGE" / "FIX" / "MAINTENANCE")
 * @param affectsPlayer 유저에게 직접 영향을 주는 변경인지 여부 (프롬프트의 {@code isUserFacing} 필드 매핑).
 *     LLM 파싱 실패 시 {@code true}로 fallback하여 reranking 시 보수적으로 처리한다.
 */
public record DocumentSummaryResult(
        String title, String summary, String categoryFromLlm, boolean affectsPlayer) {}
