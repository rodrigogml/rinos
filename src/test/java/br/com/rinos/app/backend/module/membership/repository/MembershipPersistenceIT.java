package br.com.rinos.app.backend.module.membership.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import br.com.rinos.app.api.module.account.dto.AccountCreationRequest;
import br.com.rinos.app.api.module.account.enums.AccountBootstrapResultStatus;
import br.com.rinos.app.api.module.account.vo.AccountBootstrapRequest;
import br.com.rinos.app.api.module.membership.enums.*;
import br.com.rinos.app.backend.module.account.entity.AccountEntity;
import br.com.rinos.app.backend.module.account.repository.*;
import br.com.rinos.app.backend.module.account.service.AccountCreationAcceptanceService;
import br.com.rinos.app.backend.module.membership.component.*;
import br.com.rinos.app.backend.module.membership.entity.AccountMembershipEntity;
import br.com.rinos.app.backend.module.membership.service.*;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.identity.service.*;
import br.com.rinos.app.config.AuthenticationKeyringPropertiesConfig;
import br.com.rinos.app.config.ApplicationPropertiesConfig;
import br.com.rinos.app.config.MembershipInvitationPropertiesConfig;
import br.eng.rodrigogml.rfw.mail.EmailDispatchService;
import br.eng.rodrigogml.rfw.mail.EmailMessage;
import br.com.rinos.app.testsupport.mysql.MySqlTestDatabase;

class MembershipPersistenceIT {
 private static MySqlTestDatabase database; private DataSource dataSource;
 @BeforeAll static void start(){database=MySqlTestDatabase.openIfAvailable().orElse(null);}
 @AfterAll static void stop(){if(database!=null)database.close();}
 @BeforeEach void reset() throws SQLException{
  Assumptions.assumeTrue(database!=null,"MySQL de teste indisponível"); dataSource=database.recreateSchema();
  new ResourceDatabasePopulator(new ClassPathResource("db/global/init/01-ddl.sql")).execute(dataSource);
  execute("INSERT INTO identity_user (email,normalizedEmail,status) VALUES ('founder@example.com','founder@example.com','ACTIVE'),('guest@example.com','guest@example.com','ACTIVE')");
 }
 @Test void invitation_shouldIssueAndBeConsumedExactlyOnce(){
  runner().run(context->{var setup=operationalAccount(context,"Convites");var invitations=context.getBean(MembershipInvitationService.class);
   var issued=invitations.issue(setup.membershipId(),setup.accountPublicId()," Guest@Example.com ",MembershipRoleType.COLLABORATOR,"203.0.113.10","invite",Instant.now());
   assertThat(issued.status()).isEqualTo(MembershipInvitationResultStatus.ISSUED);assertThat(issued.transientProof()).isNotBlank();
   assertThat(issued.toString()).doesNotContain(issued.transientProof(),issued.invitationPublicId().toString());
   var duplicate=invitations.issue(setup.membershipId(),setup.accountPublicId(),"guest@example.com",MembershipRoleType.COLLABORATOR,"203.0.113.10","duplicate",Instant.now());
   assertThat(duplicate.status()).isEqualTo(MembershipInvitationResultStatus.ALREADY_PENDING);assertThat(duplicate.transientProof()).isNull();
   var invalid=invitations.decide(2L,issued.invitationPublicId(),"wrong",true,"wrong",Instant.now());
   assertThat(invalid.status()).isEqualTo(MembershipInvitationResultStatus.REJECTED);
   var accepted=invitations.decide(2L,issued.invitationPublicId(),issued.transientProof(),true,"accept",Instant.now());
   assertThat(accepted.status()).isEqualTo(MembershipInvitationResultStatus.ACCEPTED);
   var replay=invitations.decide(2L,issued.invitationPublicId(),issued.transientProof(),true,"replay",Instant.now());
   assertThat(replay.status()).isEqualTo(MembershipInvitationResultStatus.REJECTED);
   assertThat(count("membership_invitation")).isOne();assertThat(count("membership_accountMembership")).isEqualTo(2);
   assertThat(count("membership_event")).isEqualTo(3);assertThat(count("membership_outboxEvent")).isEqualTo(3);
   assertThat(longValue("SELECT COUNT(*) FROM membership_outboxEvent WHERE secretCiphertext IS NOT NULL")).isZero();
  });
 }
 @Test void invitationDelivery_shouldDecryptOnlyForDispatchAndEraseTheEnvelope() throws Exception{
  runner().run(context->{var setup=operationalAccount(context,"Entrega");var service=context.getBean(MembershipInvitationService.class);
   var issued=service.issue(setup.membershipId(),setup.accountPublicId(),"guest@example.com",MembershipRoleType.COLLABORATOR,
     "203.0.113.10","delivery",Instant.now());
   assertThat(stringValue("SELECT payload FROM membership_outboxEvent WHERE eventType='INVITATION_ISSUED'"))
     .doesNotContain(issued.transientProof());
   assertThat(longValue("SELECT COUNT(*) FROM membership_outboxEvent WHERE secretCiphertext IS NOT NULL")).isOne();
   assertThat(context.getBean(MembershipInvitationDeliveryService.class).dispatchBatch(Instant.now(),"test-node")).isOne();
   var mail=context.getBean(EmailDispatchService.class);try{verify(mail).createMessage(any(),any(),any(),any(),any(),any(),any(),any(Object[].class));
     verify(mail).dispatch(any());}catch(Exception failure){throw new IllegalStateException(failure);}
   assertThat(stringValue("SELECT status FROM membership_outboxEvent WHERE eventType='INVITATION_ISSUED'"))
     .isEqualTo("PUBLISHED");
   assertThat(longValue("SELECT COUNT(*) FROM membership_outboxEvent WHERE secretCiphertext IS NOT NULL")).isZero();
  });
 }
 @Test void invitationRateLimit_shouldRollbackEveryDimensionWhenOneDimensionBlocks(){
  runner().run(context->{var setup=operationalAccount(context,"Limite");var service=context.getBean(MembershipInvitationService.class);
   for(int cycle=0;cycle<5;cycle++){var issued=service.issue(setup.membershipId(),setup.accountPublicId(),"guest@example.com",
     MembershipRoleType.COLLABORATOR,"203.0.113.10","rate-"+cycle,Instant.now());
    assertThat(issued.status()).isEqualTo(MembershipInvitationResultStatus.ISSUED);
    assertThat(service.revoke(setup.membershipId(),issued.invitationPublicId(),"revoke-"+cycle,Instant.now()).status())
      .isEqualTo(MembershipInvitationResultStatus.REVOKED);}
   var limited=service.issue(setup.membershipId(),setup.accountPublicId(),"guest@example.com",MembershipRoleType.COLLABORATOR,
     "203.0.113.10","rate-limited",Instant.now());
   assertThat(limited.status()).isEqualTo(MembershipInvitationResultStatus.RATE_LIMITED);
   assertThat(longValue("SELECT COUNT(*) FROM membership_invitationRateWindow WHERE activeMarker=TRUE")).isEqualTo(4);
   assertThat(longValue("SELECT SUM(eventCount) FROM membership_invitationRateWindow WHERE activeMarker=TRUE")).isEqualTo(20);
  });
 }
 @Test void invitationAcceptance_shouldSerializeConcurrentDevices(){
  runner().run(context->{var setup=operationalAccount(context,"Corrida convite");var service=context.getBean(MembershipInvitationService.class);
   var issued=service.issue(setup.membershipId(),setup.accountPublicId(),"guest@example.com",MembershipRoleType.ACCOUNTANT,"203.0.113.10","issue-race",Instant.now());
   var executor=Executors.newFixedThreadPool(2);try{Callable<MembershipInvitationResultStatus> call=()->service.decide(
     2L,issued.invitationPublicId(),issued.transientProof(),true,UUID.randomUUID().toString(),Instant.now()).status();
    var results=executor.invokeAll(List.of(call,call),15,TimeUnit.SECONDS).stream().map(f->{try{return f.get();}catch(Exception e){throw new IllegalStateException(e);}}).toList();
    assertThat(results).containsExactlyInAnyOrder(MembershipInvitationResultStatus.ACCEPTED,MembershipInvitationResultStatus.REJECTED);
    assertThat(count("membership_accountMembership")).isEqualTo(2);
   }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}finally{executor.shutdownNow();}
  });
 }
 @Test void invitationIssue_shouldConvergeOnOnePendingProof(){
  runner().run(context->{var setup=operationalAccount(context,"Corrida emissão");var service=context.getBean(MembershipInvitationService.class);
   var executor=Executors.newFixedThreadPool(2);try{Callable<MembershipInvitationResultStatus> call=()->service.issue(
     setup.membershipId(),setup.accountPublicId(),"guest@example.com",MembershipRoleType.EXTERNAL_PARTNER,
     "203.0.113.10",UUID.randomUUID().toString(),Instant.now()).status();
    var results=executor.invokeAll(List.of(call,call),15,TimeUnit.SECONDS).stream().map(f->{try{return f.get();}catch(Exception e){throw new IllegalStateException(e);}}).toList();
    assertThat(results).containsExactlyInAnyOrder(MembershipInvitationResultStatus.ISSUED,MembershipInvitationResultStatus.ALREADY_PENDING);
    assertThat(count("membership_invitation")).isOne();assertThat(count("membership_event")).isEqualTo(2);
   }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}finally{executor.shutdownNow();}
  });
 }
 @Test void invitationExpiry_shouldMaterializeTheTerminalStateAndAllowANewCycle(){
  runner().run(context->{var setup=operationalAccount(context,"Expiração");var service=context.getBean(MembershipInvitationService.class);
   Instant old=Instant.now().minus(java.time.Duration.ofDays(16));
   var first=service.issue(setup.membershipId(),setup.accountPublicId(),"guest@example.com",MembershipRoleType.COLLABORATOR,"203.0.113.10","old",old);
   assertThat(context.getBean(MembershipInvitationExpiryService.class).expireBatch(Instant.now())).isOne();
   var replacement=service.issue(setup.membershipId(),setup.accountPublicId(),"guest@example.com",MembershipRoleType.COLLABORATOR,"203.0.113.10","new",Instant.now());
   assertThat(first.status()).isEqualTo(MembershipInvitationResultStatus.ISSUED);
   assertThat(replacement.status()).isEqualTo(MembershipInvitationResultStatus.ISSUED);
   assertThat(replacement.invitationPublicId()).isNotEqualTo(first.invitationPublicId());assertThat(count("membership_invitation")).isEqualTo(2);
  });
 }
 @Test void invitationResendAndRevoke_shouldInvalidateEveryPreviousProof(){
  runner().run(context->{var setup=operationalAccount(context,"Reenvio");var service=context.getBean(MembershipInvitationService.class);
   var first=service.issue(setup.membershipId(),setup.accountPublicId(),"guest@example.com",MembershipRoleType.COLLABORATOR,"203.0.113.10","first",Instant.now());
   var replacement=service.resend(setup.membershipId(),first.invitationPublicId(),"203.0.113.10","resend",Instant.now());
   assertThat(replacement.status()).isEqualTo(MembershipInvitationResultStatus.REISSUED);
   assertThat(service.decide(2L,first.invitationPublicId(),first.transientProof(),true,"old-proof",Instant.now()).status())
     .isEqualTo(MembershipInvitationResultStatus.REJECTED);
   assertThat(service.revoke(setup.membershipId(),replacement.invitationPublicId(),"revoke",Instant.now()).status())
     .isEqualTo(MembershipInvitationResultStatus.REVOKED);
   assertThat(service.decide(2L,replacement.invitationPublicId(),replacement.transientProof(),true,"revoked",Instant.now()).status())
     .isEqualTo(MembershipInvitationResultStatus.REJECTED);
   assertThat(count("membership_invitation")).isEqualTo(2);assertThat(count("membership_accountMembership")).isOne();
   assertThat(longValue("SELECT COUNT(*) FROM membership_outboxEvent WHERE eventType='INVITATION_ISSUED' AND status='CANCELLED'")).isEqualTo(2);
   assertThat(longValue("SELECT COUNT(*) FROM membership_outboxEvent WHERE secretCiphertext IS NOT NULL")).isZero();
  });
 }
 @Test void membershipLifecycle_shouldPersistTransitionsAuditAndRevision(){
  runner().run(context->{var setup=operationalAccount(context,"Lifecycle");var repository=context.getBean(AccountMembershipRepository.class);
   var target=repository.saveAndFlush(new AccountMembershipEntity(UUID.randomUUID(),setup.accountId(),2L,
     MembershipRoleType.COLLABORATOR,MembershipOriginType.INVITATION,Instant.now()));
   var lifecycle=context.getBean(MembershipLifecycleService.class);
   var suspended=lifecycle.mutate(command(setup.membershipId(),target,MembershipMutationOperation.SUSPEND,null,0,"suspend"));
   assertThat(suspended.status()).isEqualTo(MembershipMutationResultStatus.CHANGED);
   assertThat(suspended.version()).isEqualTo(1);assertThat(suspended.contextRevision()).isOne();
   var conflict=lifecycle.mutate(command(setup.membershipId(),target,MembershipMutationOperation.REACTIVATE,null,0,"stale"));
   assertThat(conflict.status()).isEqualTo(MembershipMutationResultStatus.CONFLICT);
   var reactivated=lifecycle.mutate(command(setup.membershipId(),target,MembershipMutationOperation.REACTIVATE,null,1,"reactivate"));
   assertThat(reactivated.status()).isEqualTo(MembershipMutationResultStatus.CHANGED);
   var role=lifecycle.mutate(command(setup.membershipId(),target,MembershipMutationOperation.CHANGE_ROLE,
     MembershipRoleType.ACCOUNTANT,2,"role"));
   assertThat(role.status()).isEqualTo(MembershipMutationResultStatus.CHANGED);
   var removed=lifecycle.mutate(command(setup.membershipId(),target,MembershipMutationOperation.REMOVE,null,3,"remove"));
   assertThat(removed.status()).isEqualTo(MembershipMutationResultStatus.CHANGED);
   var persisted=repository.findById(target.getId()).orElseThrow();
   assertThat(persisted.getStatus()).isEqualTo(MembershipStatus.REMOVED);
   assertThat(repository.findByAccountIdAndUserIdAndCurrentMarker(setup.accountId(),2L,1)).isEmpty();
   assertThat(longValue("SELECT COUNT(*) FROM membership_event WHERE idAccountMembership="+target.getId())).isEqualTo(5);
   assertThat(longValue("SELECT COUNT(*) FROM membership_outboxEvent WHERE aggregateId="+target.getId())).isEqualTo(4);
  });
 }
 @Test void membershipLifecycle_shouldRejectLastAdministratorAtomicallyWhenContinuityDenies(){
  runner(MembershipContinuityDecision.deny()).run(context->{var setup=operationalAccount(context,"Continuidade");
   var repository=context.getBean(AccountMembershipRepository.class);var founder=repository.findById(setup.membershipId()).orElseThrow();
   var result=context.getBean(MembershipLifecycleService.class).mutate(command(setup.membershipId(),founder,
     MembershipMutationOperation.LEAVE,null,founder.getVersion(),"leave"));
   assertThat(result.status()).isEqualTo(MembershipMutationResultStatus.REJECTED);
   assertThat(result.safeReasonCode()).isEqualTo("MEMBERSHIP_ADMINISTRATIVE_CONTINUITY_REQUIRED");
   assertThat(repository.findById(founder.getId()).orElseThrow().getStatus()).isEqualTo(MembershipStatus.ACTIVE);
   assertThat(longValue("SELECT COUNT(*) FROM membership_outboxEvent WHERE eventType='MEMBERSHIP_LEFT'")).isZero();
   assertThat(longValue("SELECT COUNT(*) FROM membership_event WHERE safeResultCode='MEMBERSHIP_ADMINISTRATIVE_CONTINUITY_REQUIRED'")).isOne();
  });
 }
 @Test void membershipLifecycle_shouldFailSafeWhenContinuityIsUnavailable(){
  runner(MembershipContinuityDecision.unavailable()).run(context->{var setup=operationalAccount(context,"Indisponível");
   var founder=context.getBean(AccountMembershipRepository.class).findById(setup.membershipId()).orElseThrow();
   var result=context.getBean(MembershipLifecycleService.class).mutate(command(setup.membershipId(),founder,
     MembershipMutationOperation.SUSPEND,null,founder.getVersion(),"unavailable"));
   assertThat(result.status()).isEqualTo(MembershipMutationResultStatus.UNAVAILABLE);
   assertThat(context.getBean(AccountMembershipRepository.class).findById(founder.getId()).orElseThrow().getStatus())
     .isEqualTo(MembershipStatus.ACTIVE);
  });
 }
 @Test void membershipLifecycle_shouldRequireRecentStrongAuthentication(){
  runner().run(context->{var setup=operationalAccount(context,"Garantia");var repository=context.getBean(AccountMembershipRepository.class);
   var founder=repository.findById(setup.membershipId()).orElseThrow();
   var command=new MembershipMutationCommand(setup.membershipId(),founder.getPublicId(),MembershipMutationOperation.SUSPEND,
     null,founder.getVersion(),true,false,"weak",Instant.now());
   var result=context.getBean(MembershipLifecycleService.class).mutate(command);
   assertThat(result.status()).isEqualTo(MembershipMutationResultStatus.REJECTED);
   assertThat(result.safeReasonCode()).isEqualTo("MEMBERSHIP_STRONG_AUTHENTICATION_REQUIRED");
   assertThat(repository.findById(founder.getId()).orElseThrow().getStatus()).isEqualTo(MembershipStatus.ACTIVE);
   assertThat(longValue("SELECT COUNT(*) FROM membership_event WHERE safeResultCode='MEMBERSHIP_STRONG_AUTHENTICATION_REQUIRED'")).isOne();
  });
 }
 @Test void membershipLifecycle_shouldSerializeConcurrentMutations(){
  runner().run(context->{var setup=operationalAccount(context,"Concorrência lifecycle");var repository=context.getBean(AccountMembershipRepository.class);
   var target=repository.saveAndFlush(new AccountMembershipEntity(UUID.randomUUID(),setup.accountId(),2L,
     MembershipRoleType.COLLABORATOR,MembershipOriginType.INVITATION,Instant.now()));
   var lifecycle=context.getBean(MembershipLifecycleService.class);var executor=Executors.newFixedThreadPool(2);
   try{Callable<MembershipMutationResultStatus> call=()->lifecycle.mutate(command(setup.membershipId(),target,
     MembershipMutationOperation.SUSPEND,null,0,UUID.randomUUID().toString())).status();
    var results=executor.invokeAll(List.of(call,call),15,TimeUnit.SECONDS).stream().map(f->{try{return f.get();}catch(Exception e){throw new IllegalStateException(e);}}).toList();
    assertThat(results).containsExactlyInAnyOrder(MembershipMutationResultStatus.CHANGED,MembershipMutationResultStatus.CONFLICT);
    assertThat(repository.findById(target.getId()).orElseThrow().getStatus()).isEqualTo(MembershipStatus.SUSPENDED);
   }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}finally{executor.shutdownNow();}
  });
 }
 private MembershipMutationCommand command(long actorId,AccountMembershipEntity target,MembershipMutationOperation operation,
     MembershipRoleType role,long version,String correlation){return new MembershipMutationCommand(actorId,target.getPublicId(),
       operation,role,version,true,true,correlation,Instant.now());}
 private OperationalSetup operationalAccount(org.springframework.context.ApplicationContext context,String name){
  var creation=context.getBean(AccountCreationAcceptanceService.class);var accepted=creation.accept(1L,
    new AccountCreationRequest(UUID.randomUUID(),name,"BRL","UTC",null,true),UUID.randomUUID().toString(),Instant.now());
  var account=context.getBean(AccountRepository.class).findByPublicId(accepted.accountPublicId()).orElseThrow();
  var tenant=context.getBean(TenantRepository.class).findById(account.getTenantId()).orElseThrow();
  var bootstrap=context.getBean(FoundingMembershipBootstrapAdapter.class);bootstrap.bootstrapMembership(new AccountBootstrapRequest(
    accepted.protocolId(),account.getPublicId(),tenant.getPublicId(),1L,UUID.randomUUID().toString()));
  try{execute("UPDATE account_account SET status='ACTIVE' WHERE idAccount="+account.getId());execute("UPDATE account_tenant SET status='OPERATIONAL' WHERE idTenant="+tenant.getId());}
  catch(SQLException e){throw new IllegalStateException(e);}
  long membershipId=context.getBean(AccountMembershipRepository.class).findByAccountIdAndUserIdAndCurrentMarker(account.getId(),1L,1).orElseThrow().getId();
  return new OperationalSetup(account.getPublicId(),account.getId(),tenant.getId(),membershipId);
 }
 @Test void founderBootstrap_shouldBeIdempotentAndFeedTheStructuralGate(){
  runner().run(context->{var creation=context.getBean(AccountCreationAcceptanceService.class);
   var accepted=creation.accept(1L,new AccountCreationRequest(UUID.randomUUID(),"Conta","BRL","UTC",null,true),"create",Instant.now());
   var accounts=context.getBean(AccountRepository.class);var tenants=context.getBean(TenantRepository.class);
   var account=accounts.findByPublicId(accepted.accountPublicId()).orElseThrow();var tenant=tenants.findById(account.getTenantId()).orElseThrow();
   var request=new AccountBootstrapRequest(accepted.protocolId(),account.getPublicId(),tenant.getPublicId(),1L,"founder");
   var bootstrap=context.getBean(FoundingMembershipBootstrapAdapter.class);
   var first=bootstrap.bootstrapMembership(request);var replay=bootstrap.bootstrapMembership(request);
   assertThat(first.status()).isEqualTo(AccountBootstrapResultStatus.ACCEPTED);
   assertThat(replay.status()).isEqualTo(AccountBootstrapResultStatus.ALREADY_COMPLETED);
   assertThat(replay.externalReference()).isEqualTo(first.externalReference());
   var membership=context.getBean(AccountMembershipRepository.class)
     .findByAccountIdAndUserIdAndCurrentMarker(account.getId(),1L,1).orElseThrow();
   var snapshot=context.getBean(AccountMembershipAccessAdapter.class).inspect(membership.getId());
   assertThat(snapshot.exists()).isTrue();assertThat(snapshot.identityId()).isEqualTo(1L);
   assertThat(snapshot.tenantId()).isEqualTo(tenant.getId());assertThat(snapshot.membershipActive()).isTrue();
   assertThat(snapshot.tenantOperational()).isFalse();
   assertThat(count("membership_accountMembership")).isOne();assertThat(count("membership_event")).isOne();
   assertThat(count("membership_outboxEvent")).isOne();
  });
 }
 @Test void founderBootstrap_shouldConvergeAcrossConcurrentExecutions(){
  runner().run(context->{var creation=context.getBean(AccountCreationAcceptanceService.class);
   var accepted=creation.accept(1L,new AccountCreationRequest(UUID.randomUUID(),"Conta 2","BRL","UTC",null,true),"create2",Instant.now());
   var account=context.getBean(AccountRepository.class).findByPublicId(accepted.accountPublicId()).orElseThrow();
   var tenant=context.getBean(TenantRepository.class).findById(account.getTenantId()).orElseThrow();
   var request=new AccountBootstrapRequest(accepted.protocolId(),account.getPublicId(),tenant.getPublicId(),1L,"race");
   var bootstrap=context.getBean(FoundingMembershipBootstrapAdapter.class);var executor=Executors.newFixedThreadPool(2);
   try{Callable<AccountBootstrapResultStatus> call=()->bootstrap.bootstrapMembership(request).status();
    var results=executor.invokeAll(List.of(call,call),15,TimeUnit.SECONDS).stream().map(f->{try{return f.get();}catch(Exception e){throw new IllegalStateException(e);}}).toList();
    assertThat(results).containsExactlyInAnyOrder(AccountBootstrapResultStatus.ACCEPTED,AccountBootstrapResultStatus.ALREADY_COMPLETED);
    assertThat(count("membership_accountMembership")).isOne();assertThat(count("membership_event")).isOne();
   }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}finally{executor.shutdownNow();}
  });
 }
 private ApplicationContextRunner runner(){return runner(MembershipContinuityDecision.permit());}
 private ApplicationContextRunner runner(MembershipContinuityDecision continuity){var revision=new AtomicLong();return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(
   DataSourceAutoConfiguration.class,HibernateJpaAutoConfiguration.class,DataJpaRepositoriesAutoConfiguration.class))
   .withUserConfiguration(Config.class).withPropertyValues("spring.jpa.hibernate.ddl-auto=none",
    "spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl",
    "spring.jpa.properties.hibernate.jdbc.time_zone=UTC").withBean(DataSource.class,()->dataSource)
   .withBean(AuthenticationKeyringService.class,()->new AuthenticationKeyringService(new AuthenticationKeyringPropertiesConfig(
     true,"v1",java.util.Map.of("v1",java.util.Base64.getEncoder().encodeToString(new byte[32])))))
   .withBean(ApplicationPropertiesConfig.class,()->new ApplicationPropertiesConfig(java.net.URI.create("https://app.rinos.test")))
   .withBean(EmailDispatchService.class,()->mock(EmailDispatchService.class,invocation->{
     if(invocation.getMethod().getName().equals("createMessage"))return mock(EmailMessage.class);return null;}))
   .withBean(MembershipInvitationPropertiesConfig.class,()->new MembershipInvitationPropertiesConfig(
     java.time.Duration.ofDays(15),java.time.Duration.ofMinutes(15),100,20,5,50,100,25,
     java.time.Duration.ofMinutes(2),java.time.Duration.ofMinutes(1),java.time.Duration.ofHours(1)))
   .withBean(MembershipPlanCapacityPort.class,()->(accountId,userId)->MembershipPlanCapacityDecision.permit())
   .withBean(MembershipAdministrativeContinuityPort.class,()->request->continuity)
   .withBean(MembershipContextInvalidationPort.class,()->new MembershipContextInvalidationPort(){
     public void lock(long tenantId){} public long revise(long tenantId){return revision.incrementAndGet();}});}
 private void execute(String sql)throws SQLException{try(Connection c=dataSource.getConnection();Statement s=c.createStatement()){s.execute(sql);}}
 private long count(String table){try(Connection c=dataSource.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM "+table)){r.next();return r.getLong(1);}catch(SQLException e){throw new IllegalStateException(e);}}
 private long longValue(String sql){try(Connection c=dataSource.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){r.next();return r.getLong(1);}catch(SQLException e){throw new IllegalStateException(e);}}
 private String stringValue(String sql){try(Connection c=dataSource.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){r.next();return r.getString(1);}catch(SQLException e){throw new IllegalStateException(e);}}
 @Configuration(proxyBeanMethods=false) @EnableTransactionManagement
 @EntityScan(basePackageClasses={AccountEntity.class,AccountMembershipEntity.class,UserEntity.class})
 @EnableJpaRepositories(basePackageClasses={AccountRepository.class,AccountMembershipRepository.class,UserRepository.class})
 @Import({AccountCreationAcceptanceService.class,FoundingMembershipBootstrapAdapter.class,AccountMembershipAccessAdapter.class,
   MembershipInvitationService.class,MembershipInvitationExpiryService.class,MembershipInvitationRateLimitService.class,MembershipLifecycleService.class,
   MembershipInvitationDeliveryService.class,AuthenticationKeyringMacService.class,PublicApplicationUriService.class,
   VerificationTokenService.class,EmailNormalizationService.class})
 static class Config{}
 private record OperationalSetup(UUID accountPublicId,long accountId,long tenantId,long membershipId){}
}
