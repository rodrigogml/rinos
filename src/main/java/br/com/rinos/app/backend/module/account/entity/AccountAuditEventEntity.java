package br.com.rinos.app.backend.module.account.entity;
import java.time.Instant; import jakarta.persistence.*;
@Entity @Table(name="account_auditEvent")
public class AccountAuditEventEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="idAccountAuditEvent") private Long id;
 @Column(name="eventType",nullable=false,updatable=false) private String eventType;
 @Column(name="idAccount",updatable=false) private Long accountId; @Column(name="idTenant",updatable=false) private Long tenantId;
 @Column(name="actorUserId",updatable=false) private Long actorUserId; @Column(name="systemOrigin",updatable=false) private String systemOrigin;
 @Column(name="correlationId",nullable=false,updatable=false) private String correlationId;
 @Column(name="safeResultCode",nullable=false,updatable=false) private String safeResultCode;
 @Column(name="details",columnDefinition="JSON",updatable=false) private String details;
 @Column(name="occurredAt",nullable=false,updatable=false) private Instant occurredAt;
 protected AccountAuditEventEntity(){}
 public AccountAuditEventEntity(String eventType,Long accountId,Long tenantId,Long actorUserId,String correlationId,String safeResultCode,Instant occurredAt){this.eventType=eventType;this.accountId=accountId;this.tenantId=tenantId;this.actorUserId=actorUserId;this.correlationId=correlationId;this.safeResultCode=safeResultCode;this.occurredAt=occurredAt;}
 public Long getId(){return id;}
}
