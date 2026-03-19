package kr.java.documind.domain.patchnote.model.dto;

import java.time.OffsetDateTime;
import kr.java.documind.domain.patchnote.model.enums.FeedMode;
import kr.java.documind.domain.patchnote.model.enums.PatchType;
import kr.java.documind.global.enums.SourceType;

/**
 * Pending Item 피드 조회 쿼리 파라미터.
 *
 * <p>모든 필드는 nullable이며 null이면 해당 필터는 적용되지 않는다. {@code mode}는 null이면 {@link FeedMode#PENDING}
 * (기본값)으로 동작한다.
 *
 * @param sourceType 소스 타입 필터 (DOCUMENT / ISSUE, null=전체)
 * @param patchType 패치 타입 필터 (NEW / CHANGE / FIX / MAINTENANCE, null=전체)
 * @param from 소스 생성일 범위 시작 (포함, null=제한없음)
 * @param to 소스 생성일 범위 종료 (미포함, null=제한없음)
 * @param keyword 검색어 — title / summary / choseong 대상 (null=전체)
 * @param mode 조회 모드 (null=PENDING 기본값)
 */
public record FeedQuery(
        SourceType sourceType,
        PatchType patchType,
        OffsetDateTime from,
        OffsetDateTime to,
        String keyword,
        FeedMode mode) {

    /** mode가 null이면 {@link FeedMode#PENDING}으로 정규화한다. */
    public FeedQuery {
        if (mode == null) {
            mode = FeedMode.PENDING;
        }
    }

    public boolean includeExcluded() {
        return mode.isIncludeExcluded();
    }

    public boolean includeCompleted() {
        return mode.isIncludeCompleted();
    }
}
