package br.com.rinos.app.backend.module.membership.repository;
import java.util.Optional; import java.util.UUID; import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock; import org.springframework.data.jpa.repository.Query; import org.springframework.data.repository.query.Param;
import br.com.rinos.app.backend.module.membership.entity.AccountMembershipEntity;
import jakarta.persistence.LockModeType;
public interface AccountMembershipRepository extends JpaRepository<AccountMembershipEntity,Long>{
 Optional<AccountMembershipEntity> findByAccountIdAndUserIdAndCurrentMarker(Long accountId,Long userId,Integer marker);
 Optional<AccountMembershipEntity> findByPublicId(UUID publicId);
 @Query("select membership.id from AccountMembershipEntity membership where membership.publicId=:publicId")
 Optional<Long> findIdByPublicId(@Param("publicId") UUID publicId);
 @Query("select membership.accountId from AccountMembershipEntity membership where membership.id=:id")
 Optional<Long> findAccountIdById(@Param("id") Long id);
 java.util.List<AccountMembershipEntity> findByAccountIdAndCurrentMarkerOrderById(Long accountId,Integer marker);
 java.util.List<AccountMembershipEntity> findByUserIdAndCurrentMarkerOrderByAccountId(Long userId,Integer marker);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select membership from AccountMembershipEntity membership where membership.id=:id")
 Optional<AccountMembershipEntity> findByIdForUpdate(@Param("id") Long id);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select membership from AccountMembershipEntity membership where membership.publicId=:publicId")
 Optional<AccountMembershipEntity> findByPublicIdForUpdate(@Param("publicId") UUID publicId);
}
