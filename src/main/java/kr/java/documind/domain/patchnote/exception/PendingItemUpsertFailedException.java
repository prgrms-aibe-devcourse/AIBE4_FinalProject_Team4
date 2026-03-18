package kr.java.documind.domain.patchnote.exception;

public class PendingItemUpsertFailedException extends RuntimeException {

    public PendingItemUpsertFailedException(Long sourceId) {
        super("pending_item upsert가 3회 재시도 후 최종 실패하였습니다. sourceId: " + sourceId);
    }
}
