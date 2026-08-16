package br.com.rinos.app.backend.module.membership.repository;
import java.util.*; import jakarta.persistence.LockModeType; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param;
import br.com.rinos.app.backend.module.membership.entity.MembershipInvitationEntity;
public interface MembershipInvitationRepository extends JpaRepository<MembershipInvitationEntity,Long>{
 Optional<MembershipInvitationEntity> findByAccountIdAndNormalizedEmailAndPendingMarker(Long accountId,String email,Integer marker);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("SELECT invitation FROM MembershipInvitationEntity invitation WHERE invitation.publicId=:publicId")
 Optional<MembershipInvitationEntity> findByPublicIdForUpdate(@Param("publicId") UUID publicId);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("""
   SELECT invitation FROM MembershipInvitationEntity invitation
   WHERE invitation.status=:status AND invitation.expiresAt<=:cutoff
   ORDER BY invitation.expiresAt, invitation.id
   """)
 List<MembershipInvitationEntity> findExpiredBatchForUpdate(
   @Param("status") br.com.rinos.app.api.module.membership.enums.MembershipInvitationStatus status,
   @Param("cutoff") java.time.Instant cutoff,org.springframework.data.domain.Pageable pageable);
}
