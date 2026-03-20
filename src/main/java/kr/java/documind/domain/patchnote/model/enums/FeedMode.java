package kr.java.documind.domain.patchnote.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Pending Item 피드 조회 모드.
 *
 * <ul>
 *   <li>{@link #PENDING} — PENDING 항목만 표시 (기본 워크플로우 뷰)
 *   <li>{@link #EXCLUDED} — PENDING + EXCLUDED 표시 (제외 항목 탐색기 모드)
 *   <li>{@link #COMPLETED} — PENDING + COMPLETED 표시 (완료 항목 탐색기 모드)
 * </ul>
 */
@Getter
@RequiredArgsConstructor
public enum FeedMode {

    /** 기본 모드: PENDING 항목만 표시 */
    PENDING(false, false),

    /** 탐색기 모드: PENDING + EXCLUDED 표시 (제외 항목 복원 작업용) */
    EXCLUDED(true, false),

    /** 탐색기 모드: PENDING + COMPLETED 표시 (패치노트 반영 이력 확인용) */
    COMPLETED(false, true);

    private final boolean includeExcluded;
    private final boolean includeCompleted;
}
