package br.com.rinos.app.backend.module.membership.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.rinos.app.backend.module.membership.entity.MembershipInvitationRateWindowEntity;

public interface MembershipInvitationRateWindowRepository
    extends JpaRepository<MembershipInvitationRateWindowEntity, Long> {

  @Modifying
  @Query(value = """
      INSERT IGNORE INTO membership_invitationRateWindow
        (dimensionType, dimensionKey, activeMarker, windowStartedAt, windowEndsAt, eventCount, version)
      VALUES (:type, :key, TRUE, CURRENT_TIMESTAMP(6),
        TIMESTAMPADD(MICROSECOND, :duration, CURRENT_TIMESTAMP(6)), 0, 0)
      """, nativeQuery = true)
  int createActiveIfAbsent(
      @Param("type") String type, @Param("key") byte[] key, @Param("duration") long duration);

  @Modifying
  @Query(value = """
      UPDATE membership_invitationRateWindow SET activeMarker = NULL
      WHERE dimensionType = :type AND dimensionKey = :key AND activeMarker = TRUE
        AND windowEndsAt <= CURRENT_TIMESTAMP(6)
      """, nativeQuery = true)
  int closeExpired(@Param("type") String type, @Param("key") byte[] key);

  @Modifying
  @Query(value = """
      UPDATE membership_invitationRateWindow SET eventCount = eventCount + 1
      WHERE dimensionType = :type AND dimensionKey = :key AND activeMarker = TRUE
        AND eventCount < :limit
      """, nativeQuery = true)
  int incrementBelowLimit(
      @Param("type") String type, @Param("key") byte[] key, @Param("limit") int limit);

  @Query(value = """
      SELECT * FROM membership_invitationRateWindow
      WHERE dimensionType = :type AND dimensionKey = :key AND activeMarker = TRUE
      """, nativeQuery = true)
  Optional<MembershipInvitationRateWindowEntity> findActive(
      @Param("type") String type, @Param("key") byte[] key);
}
