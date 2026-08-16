package br.com.rinos.app.backend.module.membership.repository;
import org.springframework.data.jpa.repository.JpaRepository; import br.com.rinos.app.backend.module.membership.entity.MembershipEventEntity;
public interface MembershipEventRepository extends JpaRepository<MembershipEventEntity,Long>{}
