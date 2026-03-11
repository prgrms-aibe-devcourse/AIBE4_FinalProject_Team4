package kr.java.documind.domain.chatbot.model.dto.response;

public record ReferenceResponse(
        Long documentId,
        String documentName,
        String extension,
        String version,
        Integer pageNumber,
        String chunkText) {}
