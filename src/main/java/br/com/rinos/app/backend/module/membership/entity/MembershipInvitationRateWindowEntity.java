package br.com.rinos.app.backend.module.membership.entity;
import java.time.Instant; import br.com.rinos.app.backend.module.membership.enums.MembershipInvitationRateDimension; import jakarta.persistence.*;
@Entity @Table(name="membership_invitationRateWindow")
public class MembershipInvitationRateWindowEntity{
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="idMembershipInvitationRateWindow") private Long id;
 @Enumerated(EnumType.STRING) @Column(name="dimensionType",nullable=false,updatable=false) private MembershipInvitationRateDimension dimensionType;
 @Column(name="dimensionKey",columnDefinition="VARBINARY(320)",nullable=false,updatable=false) private byte[] dimensionKey;
 @Column(name="activeMarker") private Boolean activeMarker; @Column(name="windowStartedAt",nullable=false,updatable=false) private Instant windowStartedAt;
 @Column(name="windowEndsAt",nullable=false,updatable=false) private Instant windowEndsAt; @Column(name="eventCount",nullable=false) private int eventCount;
 @Version @Column(name="version",nullable=false) private long version; protected MembershipInvitationRateWindowEntity(){}
 public Instant getWindowEndsAt(){return windowEndsAt;} public int getEventCount(){return eventCount;}
}
