package kr.java.documind.domain.member.model.repository;

import java.util.UUID;
import kr.java.documind.domain.member.model.entity.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {}
