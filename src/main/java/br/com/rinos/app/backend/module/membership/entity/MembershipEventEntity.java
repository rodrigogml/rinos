package br.com.rinos.app.backend.module.membership.entity;
import java.time.Instant; import jakarta.persistence.*;
@Entity @Table(name="membership_event")
public class MembershipEventEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="idMembershipEvent") private Long id;
 @Column(name="eventType",nullable=false,updatable=false) private String eventType;
 @Column(name="idAccount",nullable=false,updatable=false) private Long accountId;
 @Column(name="idAccountMembership",updatable=false) private Long membershipId;
 @Column(name="idMembershipInvitation",updatable=false) private Long invitationId;
 @Column(name="actorUserId",updatable=false) private Long actorUserId;
 @Column(name="systemOrigin",updatable=false) private String systemOrigin;
 @Column(name="correlationId",nullable=false,updatable=false) private String correlationId;
 @Column(name="safeResultCode",nullable=false,updatable=false) private String safeResultCode;
 @Column(name="occurredAt",nullable=false,updatable=false) private Instant occurredAt;
 protected MembershipEventEntity(){}
 public MembershipEventEntity(String eventType,Long accountId,Long membershipId,String systemOrigin,
     String correlationId,String safeResultCode,Instant occurredAt){this.eventType=eventType;this.accountId=accountId;
   this.membershipId=membershipId;this.systemOrigin=systemOrigin;this.correlationId=correlationId;
   this.safeResultCode=safeResultCode;this.occurredAt=occurredAt;}
 public MembershipEventEntity(String eventType,Long accountId,Long membershipId,Long invitationId,Long actorUserId,
     String correlationId,String safeResultCode,Instant occurredAt){this.eventType=eventType;this.accountId=accountId;
   this.membershipId=membershipId;this.invitationId=invitationId;this.actorUserId=actorUserId;this.correlationId=correlationId;
   this.safeResultCode=safeResultCode;this.occurredAt=occurredAt;}
 public MembershipEventEntity(String eventType,Long accountId,Long membershipId,Long invitationId,String systemOrigin,
     String correlationId,String safeResultCode,Instant occurredAt){this.eventType=eventType;this.accountId=accountId;
   this.membershipId=membershipId;this.invitationId=invitationId;this.systemOrigin=systemOrigin;this.correlationId=correlationId;
   this.safeResultCode=safeResultCode;this.occurredAt=occurredAt;}
}
