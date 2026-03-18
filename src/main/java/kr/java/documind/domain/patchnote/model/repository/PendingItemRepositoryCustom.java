package kr.java.documind.domain.patchnote.model.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.patchnote.model.entity.PendingItem;
import kr.java.documind.domain.patchnote.model.enums.PatchType;
import kr.java.documind.global.enums.SourceType;

public interface PendingItemRepositoryCustom {

    List<PendingItem> findFeed(
            UUID projectId,
            SourceType sourceType,
            PatchType patchType,
            OffsetDateTime from,
            OffsetDateTime to,
            String keyword,
            boolean includeExcluded,
            boolean includeCompleted);
}
