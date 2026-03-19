package kr.java.documind.domain.notification.model.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.notification.model.entity.Notification;
import kr.java.documind.domain.notification.model.entity.QNotification;
import kr.java.documind.global.entity.QDomainSource;
import kr.java.documind.global.enums.SourceType;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationRepositoryImpl implements NotificationRepositoryCustom {

    private final JPAQueryFactory qf;

    public NotificationRepositoryImpl(EntityManager entityManager) {
        this.qf = new JPAQueryFactory(entityManager);
    }

    @Override
    public List<Notification> findCursorPage(
            UUID projectId, UUID receiverId, Long cursorId, SourceType sourceType, int limit) {

        QNotification n = QNotification.notification;
        QDomainSource ds = QDomainSource.domainSource;

        return qf.selectFrom(n)
                .leftJoin(n.source, ds)
                .fetchJoin()
                .where(
                        projectIdEq(projectId), // 동적 쿼리 처리
                        receiverIdEq(receiverId),
                        cursorIdLt(cursorId),
                        sourceTypeEq(sourceType, ds))
                .orderBy(n.id.desc())
                .limit(limit + 1L)
                .fetch();
    }

    // --- BooleanExpression 헬퍼 메서드 ---

    private BooleanExpression projectIdEq(UUID projectId) {
        // 파라미터가 null이면 null을 반환하여 where 절에서 조건 생략
        return projectId != null ? QNotification.notification.projectId.eq(projectId) : null;
    }

    private BooleanExpression receiverIdEq(UUID receiverId) {
        return receiverId != null ? QNotification.notification.receiverId.eq(receiverId) : null;
    }

    private BooleanExpression cursorIdLt(Long cursorId) {
        return cursorId != null ? QNotification.notification.id.lt(cursorId) : null;
    }

    private BooleanExpression sourceTypeEq(SourceType sourceType, QDomainSource ds) {
        return sourceType != null ? ds.sourceType.eq(sourceType) : null;
    }
}
