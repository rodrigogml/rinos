package br.com.rinos.app.backend.module.membership.entity;
import java.time.Instant; import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode; import org.hibernate.type.SqlTypes;
import br.com.rinos.app.api.module.membership.enums.*; import jakarta.persistence.*;
@Entity @Table(name="membership_invitation")
public class MembershipInvitationEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="idMembershipInvitation") private Long id;
 @JdbcTypeCode(SqlTypes.BINARY) @Column(name="publicId",columnDefinition="BINARY(16)",nullable=false,updatable=false) private UUID publicId;
 @Column(name="idAccount",nullable=false,updatable=false) private Long accountId;
 @Column(name="inviterMembershipId",nullable=false,updatable=false) private Long inviterMembershipId;
 @Column(name="normalizedEmail",nullable=false,updatable=false,length=320) private String normalizedEmail;
 @Enumerated(EnumType.STRING) @Column(name="proposedRoleType",nullable=false) private MembershipRoleType proposedRoleType;
 @Column(name="proofDigest",columnDefinition="BINARY(32)",nullable=false,updatable=false) private byte[] proofDigest;
 @Column(name="proofKeyId",nullable=false,updatable=false,length=100) private String proofKeyId;
 @Enumerated(EnumType.STRING) @Column(name="status",nullable=false) private MembershipInvitationStatus status;
 @Column(name="pendingMarker") private Integer pendingMarker;
 @Column(name="expiresAt",nullable=false,updatable=false) private Instant expiresAt;
 @Column(name="consumedByUserId") private Long consumedByUserId; @Column(name="consumedAt") private Instant consumedAt;
 @Column(name="sendCount",nullable=false) private int sendCount;
 @Version @Column(name="version",nullable=false) private long version;
 protected MembershipInvitationEntity(){}
 public MembershipInvitationEntity(UUID publicId,Long accountId,Long inviterMembershipId,String normalizedEmail,
   MembershipRoleType role,byte[] proofDigest,String proofKeyId,Instant expiresAt){this.publicId=publicId;this.accountId=accountId;
   this.inviterMembershipId=inviterMembershipId;this.normalizedEmail=normalizedEmail;this.proposedRoleType=role;
   this.proofDigest=proofDigest.clone();this.proofKeyId=proofKeyId;this.expiresAt=expiresAt;this.status=MembershipInvitationStatus.PENDING;
   this.pendingMarker=1;this.sendCount=1;}
 public void accept(long userId,Instant at){status=MembershipInvitationStatus.ACCEPTED;pendingMarker=null;consumedByUserId=userId;consumedAt=at;}
 public void decline(long userId,Instant at){status=MembershipInvitationStatus.DECLINED;pendingMarker=null;consumedByUserId=userId;consumedAt=at;}
 public void expire(){status=MembershipInvitationStatus.EXPIRED;pendingMarker=null;}
 public void revoke(){status=MembershipInvitationStatus.REVOKED;pendingMarker=null;}
 public void supersede(){status=MembershipInvitationStatus.SUPERSEDED;pendingMarker=null;}
 public Long getId(){return id;} public UUID getPublicId(){return publicId;} public Long getAccountId(){return accountId;}
 public Long getInviterMembershipId(){return inviterMembershipId;} public String getNormalizedEmail(){return normalizedEmail;}
 public MembershipRoleType getProposedRoleType(){return proposedRoleType;} public byte[] getProofDigest(){return proofDigest.clone();}
 public String getProofKeyId(){return proofKeyId;} public MembershipInvitationStatus getStatus(){return status;}
 public Instant getExpiresAt(){return expiresAt;} public long getVersion(){return version;}
}
