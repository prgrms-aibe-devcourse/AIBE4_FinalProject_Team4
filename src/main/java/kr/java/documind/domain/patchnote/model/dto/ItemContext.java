package kr.java.documind.domain.patchnote.model.dto;

import java.util.List;
import kr.java.documind.domain.patchnote.model.enums.PatchType;

public record ItemContext(
        String ref,
        PatchType patchType,
        String title,
        String summary,
        List<RagEvidence> evidences,
        List<String> allowedSourceRefs) {}
