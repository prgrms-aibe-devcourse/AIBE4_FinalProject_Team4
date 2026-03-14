package kr.java.documind.domain.logprocessor.model.repository;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import kr.java.documind.domain.issue.model.dto.response.AffectedPlayerResponse;
import kr.java.documind.domain.issue.model.dto.response.OccurrenceTrendResponse;
import kr.java.documind.domain.logprocessor.model.entity.QGameLog;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/**
 * GameLog Custom Repository 구현체
 *
 * <p>QueryDSL을 사용한 복잡한 쿼리 구현
 */
@Repository
@RequiredArgsConstructor
public class GameLogRepositoryImpl implements GameLogRepositoryCustom {

    private final EntityManager entityManager;

    @Override
    public Page<AffectedPlayerResponse> findAffectedPlayersByFingerprint(
            String fingerprint, Pageable pageable) {

        JPAQueryFactory queryFactory = new JPAQueryFactory(entityManager);
        QGameLog gameLog = QGameLog.gameLog;

        // 집계 쿼리
        JPAQuery<Tuple> query =
                queryFactory
                        .select(
                                gameLog.userId,
                                gameLog.count(),
                                gameLog.occurredAt.min(),
                                gameLog.occurredAt.max())
                        .from(gameLog)
                        .where(gameLog.fingerprint.eq(fingerprint).and(gameLog.userId.isNotNull()))
                        .groupBy(gameLog.userId)
                        .orderBy(gameLog.count().desc(), gameLog.occurredAt.max().desc());

        // 전체 개수 조회 (페이징용)
        long total = query.fetchCount();

        // 페이징 적용하여 데이터 조회
        List<Tuple> results =
                query.offset(pageable.getOffset()).limit(pageable.getPageSize()).fetch();

        // Tuple → DTO 변환
        List<AffectedPlayerResponse> content =
                results.stream()
                        .map(
                                tuple ->
                                        new AffectedPlayerResponse(
                                                tuple.get(gameLog.userId),
                                                tuple.get(gameLog.count()),
                                                tuple.get(gameLog.occurredAt.min()),
                                                tuple.get(gameLog.occurredAt.max())))
                        .toList();

        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public List<OccurrenceTrendResponse> findOccurrenceTrendByFingerprint(
            String fingerprint, OffsetDateTime startDate, OffsetDateTime endDate) {

        JPAQueryFactory queryFactory = new JPAQueryFactory(entityManager);
        QGameLog gameLog = QGameLog.gameLog;

        // DATE(occurred_at) 함수를 사용하여 날짜별로 그룹화
        List<Tuple> results =
                queryFactory
                        .select(
                                Expressions.stringTemplate(
                                        "TO_CHAR({0}, 'YYYY-MM-DD')", gameLog.occurredAt),
                                gameLog.count())
                        .from(gameLog)
                        .where(
                                gameLog.fingerprint
                                        .eq(fingerprint)
                                        .and(gameLog.occurredAt.goe(startDate))
                                        .and(gameLog.occurredAt.lt(endDate)))
                        .groupBy(
                                Expressions.stringTemplate(
                                        "TO_CHAR({0}, 'YYYY-MM-DD')", gameLog.occurredAt))
                        .orderBy(
                                Expressions.stringTemplate(
                                                "TO_CHAR({0}, 'YYYY-MM-DD')", gameLog.occurredAt)
                                        .asc())
                        .fetch();

        // Tuple → DTO 변환
        return results.stream()
                .map(
                        tuple -> {
                            String dateStr = tuple.get(0, String.class);
                            Long count = tuple.get(1, Long.class);
                            LocalDate date = LocalDate.parse(dateStr);
                            return new OccurrenceTrendResponse(date, count);
                        })
                .toList();
    }
}
