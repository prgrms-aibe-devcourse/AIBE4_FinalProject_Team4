package kr.java.documind.domain.patchnote.model.dto;

/**
 * 벡터 스토어에서 조회한 청크 데이터 (순서 보존).
 *
 * @param chunkIndex 청크 순서 인덱스 (metadata.chunk_index 기준, 없으면 조회 순번)
 * @param content    청크 텍스트
 */
public record DocumentChunkWithMeta(int chunkIndex, String content) {}
