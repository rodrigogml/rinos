package br.com.rinos.app.backend.module.access.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.rinos.app.api.module.access.enums.AccessRuleEffect;
import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.api.module.membership.enums.MembershipMutationOperation;
import br.com.rinos.app.api.module.membership.enums.MembershipOriginType;
import br.com.rinos.app.api.module.membership.enums.MembershipRoleType;
import br.com.rinos.app.api.module.membership.enums.MembershipStatus;
import br.com.rinos.app.backend.module.access.entity.AccessKeyEntity;
import br.com.rinos.app.backend.module.access.entity.AccessRuleEntity;
import br.com.rinos.app.backend.module.access.entity.ProtectedGroupBaselineEntity;
import br.com.rinos.app.backend.module.access.entity.ProtectedGroupBaselineKeyEntity;
import br.com.rinos.app.backend.module.access.enums.AccessRuleOriginType;
import br.com.rinos.app.backend.module.access.enums.ProtectedBaselineStatus;
import br.com.rinos.app.backend.module.access.repository.*;
import br.com.rinos.app.backend.module.account.entity.AccountEntity;
import br.com.rinos.app.backend.module.account.repository.AccountRepository;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.identity.service.AuthenticationMethodInventoryService;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationMethodInventoryVO;
import br.com.rinos.app.backend.module.membership.entity.AccountMembershipEntity;
import br.com.rinos.app.backend.module.membership.repository.AccountMembershipRepository;
import br.com.rinos.app.backend.module.membership.service.MembershipContinuityRequest;

class AdministrativeContinuityEvaluationServiceTest {
  private static final Instant NOW = Instant.parse("2026-08-16T12:00:00Z");
  private final AccountRepository accounts = mock(AccountRepository.class);
  private final AccountMembershipRepository memberships = mock(AccountMembershipRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final AuthenticationMethodInventoryService methods = mock(AuthenticationMethodInventoryService.class);
  private final ProtectedGroupBaselineRepository baselines = mock(ProtectedGroupBaselineRepository.class);
  private final ProtectedGroupBaselineKeyRepository baselineKeys = mock(ProtectedGroupBaselineKeyRepository.class);
  private final AccessKeyRepository keys = mock(AccessKeyRepository.class);
  private final AccessRuleRepository rules = mock(AccessRuleRepository.class);
  private final AccessGroupSubjectRepository subjects = mock(AccessGroupSubjectRepository.class);
  private final AccessGroupRepository groups = mock(AccessGroupRepository.class);
  private AdministrativeContinuityEvaluationService service;
  private AccountMembershipEntity administrator;

  @BeforeEach void setUp(){service=new AdministrativeContinuityEvaluationService(accounts,memberships,users,methods,
    baselines,baselineKeys,keys,rules,subjects,groups);
   var account=new AccountEntity(UUID.randomUUID(),42L,11L,"Conta","BRL","UTC");ReflectionTestUtils.setField(account,"id",7L);
   when(accounts.findById(7L)).thenReturn(Optional.of(account));when(accounts.findByTenantId(42L)).thenReturn(Optional.of(account));
   administrator=new AccountMembershipEntity(UUID.randomUUID(),7L,11L,MembershipRoleType.ACCOUNT_ADMINISTRATOR,
     MembershipOriginType.FOUNDER,NOW);ReflectionTestUtils.setField(administrator,"id",77L);
   when(memberships.findByAccountIdAndCurrentMarkerOrderById(7L,1)).thenReturn(List.of(administrator));
   when(users.findById(11L)).thenReturn(Optional.of(new UserEntity("admin@example.com","admin@example.com",UserStatusEnum.ACTIVE)));
   when(methods.inspect(11L)).thenReturn(new AuthenticationMethodInventoryVO(true,0,0,1,true,0));
   var baseline=new ProtectedGroupBaselineEntity(AccessScope.TENANT,1);ReflectionTestUtils.setField(baseline,"id",3L);
   when(baselines.findFirstByScopeAndStatusOrderByBaselineVersionDesc(AccessScope.TENANT,ProtectedBaselineStatus.ACTIVE))
     .thenReturn(Optional.of(baseline));when(baselineKeys.findByIdBaselineId(3L)).thenReturn(List.of(new ProtectedGroupBaselineKeyEntity(3L,5L)));
   when(keys.findAllById(java.util.Set.of(5L))).thenReturn(List.of(mock(AccessKeyEntity.class)));
   when(subjects.findByAccountMembershipId(77L)).thenReturn(List.of());when(groups.findByScopeAndTenantIdOrderByNormalizedName(AccessScope.TENANT,42L)).thenReturn(List.of());}

  @Test void shouldPermitOnlyWhenEveryMinimumKeyIsEffectiveAndStrongFactorExists(){
   when(rules.findByScopeAndTenantId(AccessScope.TENANT,42L)).thenReturn(List.of(rule(AccessRuleEffect.PERMITIR,null)));
   assertThat(service.evaluate(request(MembershipStatus.ACTIVE)).allowed()).isTrue();
   when(methods.inspect(11L)).thenReturn(new AuthenticationMethodInventoryVO(true,0,0,0,true,0));
   assertThat(service.evaluate(request(MembershipStatus.ACTIVE)).allowed()).isFalse();}

  @Test void shouldLetBlockWinAndRejectRemovalOfTheLastCapableAdministrator(){
   when(rules.findByScopeAndTenantId(AccessScope.TENANT,42L)).thenReturn(List.of(
     rule(AccessRuleEffect.PERMITIR,null),rule(AccessRuleEffect.BLOQUEAR,null)));
   assertThat(service.evaluate(request(MembershipStatus.ACTIVE)).allowed()).isFalse();
   when(rules.findByScopeAndTenantId(AccessScope.TENANT,42L)).thenReturn(List.of(rule(AccessRuleEffect.PERMITIR,null)));
   assertThat(service.evaluate(request(MembershipStatus.REMOVED)).allowed()).isFalse();}

  @Test void shouldRejectKnownFutureBoundaryThatExpiresTheOnlyPermit(){
   when(rules.findByScopeAndTenantId(AccessScope.TENANT,42L)).thenReturn(List.of(
     rule(AccessRuleEffect.PERMITIR,NOW.plusSeconds(3600))));
   assertThat(service.evaluate(request(MembershipStatus.ACTIVE)).allowed()).isFalse();}

  @Test void shouldEvaluateGlobalAdministratorsWithTheSamePermitAndBlockPrecedence(){
   var globalUser=new UserEntity("global@example.com","global@example.com",UserStatusEnum.ACTIVE);
   ReflectionTestUtils.setField(globalUser,"id",11L);when(users.findAll()).thenReturn(List.of(globalUser));
   var baseline=new ProtectedGroupBaselineEntity(AccessScope.GLOBAL,1);ReflectionTestUtils.setField(baseline,"id",4L);
   when(baselines.findFirstByScopeAndStatusOrderByBaselineVersionDesc(AccessScope.GLOBAL,ProtectedBaselineStatus.ACTIVE))
     .thenReturn(Optional.of(baseline));when(baselineKeys.findByIdBaselineId(4L))
       .thenReturn(List.of(new ProtectedGroupBaselineKeyEntity(4L,6L)));
   when(keys.findAllById(java.util.Set.of(6L))).thenReturn(List.of(mock(AccessKeyEntity.class)));
   when(groups.findByScopeAndTenantIdOrderByNormalizedName(AccessScope.GLOBAL,null)).thenReturn(List.of());
   when(subjects.findAll()).thenReturn(List.of());
   when(rules.findByScopeAndTenantId(AccessScope.GLOBAL,null)).thenReturn(List.of(globalRule(AccessRuleEffect.PERMITIR)));
   assertThat(service.evaluateContext(AccessScope.GLOBAL,null,NOW).allowed()).isTrue();
   when(rules.findByScopeAndTenantId(AccessScope.GLOBAL,null)).thenReturn(List.of(
     globalRule(AccessRuleEffect.PERMITIR),globalRule(AccessRuleEffect.BLOQUEAR)));
   assertThat(service.evaluateContext(AccessScope.GLOBAL,null,NOW).allowed()).isFalse();}

  private MembershipContinuityRequest request(MembershipStatus status){return new MembershipContinuityRequest(7L,42L,77L,
    MembershipMutationOperation.SUSPEND,status,MembershipRoleType.ACCOUNT_ADMINISTRATOR,NOW);}
  private static AccessRuleEntity rule(AccessRuleEffect effect,Instant until){var value=new AccessRuleEntity(AccessScope.TENANT,42L,
    AccessRuleOriginType.DIRECT_MEMBERSHIP,null,77L,null,5L,effect,null,until,11L);ReflectionTestUtils.setField(value,"id",UUID.randomUUID().getLeastSignificantBits()&Long.MAX_VALUE);return value;}
  private static AccessRuleEntity globalRule(AccessRuleEffect effect){var value=new AccessRuleEntity(AccessScope.GLOBAL,null,
    AccessRuleOriginType.DIRECT_USER,11L,null,null,6L,effect,null,null,11L);ReflectionTestUtils.setField(value,"id",UUID.randomUUID().getLeastSignificantBits()&Long.MAX_VALUE);return value;}
}
