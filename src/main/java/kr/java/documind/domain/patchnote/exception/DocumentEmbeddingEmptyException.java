package kr.java.documind.domain.patchnote.exception;

public class DocumentEmbeddingEmptyException extends RuntimeException {

    public DocumentEmbeddingEmptyException(Long sourceId) {
        super("임베딩 완료 후 벡터 스토어에서 문서 청크를 찾을 수 없습니다. sourceId: " + sourceId);
    }
}
