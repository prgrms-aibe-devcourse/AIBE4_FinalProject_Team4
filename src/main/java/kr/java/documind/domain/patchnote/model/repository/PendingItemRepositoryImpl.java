package kr.java.documind.domain.patchnote.model.repository;

import static kr.java.documind.domain.patchnote.model.entity.QPendingItem.pendingItem;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.patchnote.model.entity.PendingItem;
import kr.java.documind.domain.patchnote.model.enums.PatchType;
import kr.java.documind.domain.patchnote.model.enums.PendingItemStatus;
import kr.java.documind.global.enums.SourceType;
import org.springframework.util.StringUtils;

public class PendingItemRepositoryImpl implements PendingItemRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public PendingItemRepositoryImpl(EntityManager em) {
        this.queryFactory = new JPAQueryFactory(em);
    }

    @Override
    public List<PendingItem> findFeed(
            UUID projectId,
            SourceType sourceType,
            PatchType patchType,
            OffsetDateTime from,
            OffsetDateTime to,
            String keyword,
            boolean includeExcluded,
            boolean includeCompleted) {

        return queryFactory
                .selectFrom(pendingItem)
                .where(
                        projectIdEq(projectId),
                        statusIn(includeExcluded, includeCompleted),
                        sourceTypeEq(sourceType),
                        patchTypeEq(patchType),
                        sourceCreatedAtGoe(from),
                        sourceCreatedAtLt(to),
                        keywordContains(keyword))
                .orderBy(pendingItem.sourceCreatedAt.desc(), pendingItem.id.desc())
                .fetch();
    }

    private BooleanExpression projectIdEq(UUID projectId) {
        return pendingItem.projectId.eq(projectId);
    }

    private BooleanExpression statusIn(boolean includeExcluded, boolean includeCompleted) {
        List<PendingItemStatus> statuses = new ArrayList<>();
        statuses.add(PendingItemStatus.PENDING);
        if (includeExcluded) statuses.add(PendingItemStatus.EXCLUDED);
        if (includeCompleted) statuses.add(PendingItemStatus.COMPLETED);
        return pendingItem.status.in(statuses);
    }

    private BooleanExpression sourceTypeEq(SourceType sourceType) {
        return sourceType != null ? pendingItem.sourceType.eq(sourceType) : null;
    }

    private BooleanExpression patchTypeEq(PatchType patchType) {
        return patchType != null ? pendingItem.patchType.eq(patchType) : null;
    }

    private BooleanExpression sourceCreatedAtGoe(OffsetDateTime from) {
        return from != null ? pendingItem.sourceCreatedAt.goe(from) : null;
    }

    private BooleanExpression sourceCreatedAtLt(OffsetDateTime to) {
        return to != null ? pendingItem.sourceCreatedAt.lt(to) : null;
    }

    private BooleanExpression keywordContains(String keyword) {
        if (!StringUtils.hasText(keyword)) return null;
        // 추후 pg_bigm Native Query로 교체 시 이 메서드만 수정하면 됨
        return pendingItem
                .title
                .containsIgnoreCase(keyword)
                .or(pendingItem.summary.containsIgnoreCase(keyword))
                .or(pendingItem.choseong.contains(keyword));
    }
}
