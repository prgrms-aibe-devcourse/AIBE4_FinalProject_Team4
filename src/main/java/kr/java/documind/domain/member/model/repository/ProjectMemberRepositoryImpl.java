package kr.java.documind.domain.member.model.repository;

import static kr.java.documind.domain.member.model.entity.QProjectMember.projectMember;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.member.model.enums.AccountStatus;

public class ProjectMemberRepositoryImpl implements ProjectMemberRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public ProjectMemberRepositoryImpl(EntityManager em) {
        this.queryFactory = new JPAQueryFactory(em);
    }

    @Override
    public List<UUID> findValidMemberIds(
            UUID projectId, List<UUID> memberIds, AccountStatus status) {
        return queryFactory
                .select(projectMember.member.id)
                .from(projectMember)
                .where(
                        projectMember.project.id.eq(projectId),
                        projectMember.member.id.in(memberIds),
                        projectMember.status.eq(status))
                .fetch();
    }
}
