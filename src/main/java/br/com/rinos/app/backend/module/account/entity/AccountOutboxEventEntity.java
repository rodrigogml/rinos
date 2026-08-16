package br.com.rinos.app.backend.module.account.entity;
import java.time.Instant; import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode; import org.hibernate.type.SqlTypes;
import br.com.rinos.app.backend.module.account.enums.AccountOutboxStatus; import jakarta.persistence.*;
@Entity @Table(name="account_outboxEvent")
public class AccountOutboxEventEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="idAccountOutboxEvent") private Long id;
 @JdbcTypeCode(SqlTypes.BINARY) @Column(name="eventId",columnDefinition="BINARY(16)",nullable=false,updatable=false) private UUID eventId;
 @Column(name="aggregateType",nullable=false,updatable=false) private String aggregateType;
 @Column(name="aggregateId",nullable=false,updatable=false) private Long aggregateId;
 @Column(name="eventType",nullable=false,updatable=false) private String eventType;
 @Column(name="payload",columnDefinition="JSON",nullable=false,updatable=false) private String payload;
 @Enumerated(EnumType.STRING) @Column(name="status",nullable=false) private AccountOutboxStatus status;
 @Column(name="attemptCount",nullable=false) private int attemptCount;
 @Column(name="createdAt",insertable=false,updatable=false) private Instant createdAt;
 protected AccountOutboxEventEntity(){}
 public AccountOutboxEventEntity(UUID eventId,Long aggregateId,String eventType,String payload){this.eventId=eventId;this.aggregateType="ACCOUNT";this.aggregateId=aggregateId;this.eventType=eventType;this.payload=payload;this.status=AccountOutboxStatus.PENDING;}
 public Long getId(){return id;} public UUID getEventId(){return eventId;} public Long getAggregateId(){return aggregateId;} public AccountOutboxStatus getStatus(){return status;}
}
