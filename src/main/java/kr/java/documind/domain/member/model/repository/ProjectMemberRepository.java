package kr.java.documind.domain.member.model.repository;

import kr.java.documind.domain.member.model.entity.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {}
