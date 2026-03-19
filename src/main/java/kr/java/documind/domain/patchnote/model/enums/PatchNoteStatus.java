package kr.java.documind.domain.patchnote.model.enums;

public enum PatchNoteStatus {

    /** 초안 상태 — 생성 직후의 유일한 활성 상태. */
    DRAFT,

    /**
     * @deprecated 현재 비즈니스 흐름에서 사용되지 않는다. 패치노트는 DRAFT에서 바로 DELETED로 전환된다.
     *             향후 외부 공개 기능 추가 시 활용 가능하나, 코드에서 이 값으로 전환하는 경로는 없다.
     */
    @Deprecated
    PUBLISHED,

    /** soft delete 상태. */
    DELETED
}
