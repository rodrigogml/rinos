package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.web.webauthn.api.AuthenticatorTransport;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.PasskeyCredentialRepository;
import br.com.rinos.app.backend.module.identity.repository.PasskeyUserRepository;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.testsupport.mysql.MySqlTestDatabase;

/** Valida o roundtrip dos contratos Spring WebAuthn contra o schema global MySQL 9. */
@DisplayName("Adapters Spring WebAuthn no MySQL")
class SpringWebAuthnRepositoryAdapterIT {

  private static final Instant CREATED_AT = Instant.parse("2026-08-10T12:00:00Z");
  private static final Instant USED_AT = CREATED_AT.plusSeconds(30);
  private static MySqlTestDatabase testDatabase;
  private DataSource dataSource;

  @BeforeAll
  static void startDatabase() {
    testDatabase = MySqlTestDatabase.openIfAvailable().orElse(null);
  }

  @AfterAll
  static void stopDatabase() {
    if (testDatabase != null) {
      testDatabase.close();
    }
  }

  @BeforeEach
  void resetDatabase() {
    Assumptions.assumeTrue(testDatabase != null, "MySQL de teste indisponível");
    dataSource = testDatabase.recreateSchema();
    new ResourceDatabasePopulator(new ClassPathResource("db/global/init/01-ddl.sql"))
        .execute(dataSource);
  }

  @Test
  void adapters_shouldRoundTripAndUpdateOnlyAssertionState() {
    contextRunner().run(context -> {
      UserRepository users = context.getBean(UserRepository.class);
      PasskeyUserRepository owners = context.getBean(PasskeyUserRepository.class);
      PasskeyCredentialRepository credentials = context.getBean(PasskeyCredentialRepository.class);
      SpringWebAuthnUserRepositoryAdapter userAdapter = new SpringWebAuthnUserRepositoryAdapter(
          users, owners, new EmailNormalizationService());
      SpringWebAuthnCredentialRepositoryAdapter credentialAdapter =
          new SpringWebAuthnCredentialRepositoryAdapter(
              owners, credentials, new IdentityReferenceService(),
              java.time.Clock.fixed(USED_AT, java.time.ZoneOffset.UTC));
      byte[] userHandle = bytes(32, (byte) 2);
      byte[] credentialId = bytes(16, (byte) 3);

      transaction(context).executeWithoutResult(status -> users.saveAndFlush(new UserEntity(
          "passkey@example.test", "passkey@example.test", UserStatusEnum.ACTIVE)));
      transaction(context).executeWithoutResult(status -> userAdapter.save(
          ImmutablePublicKeyCredentialUserEntity.builder()
              .name("passkey@example.test")
              .displayName("passkey@example.test")
              .id(new Bytes(userHandle))
              .build()));
      transaction(context).executeWithoutResult(status -> credentialAdapter.save(record(
          userHandle, credentialId, 7L, false, null)));

      CredentialRecord persisted = transaction(context).execute(status ->
          credentialAdapter.findByCredentialId(new Bytes(credentialId)));
      assertThat(persisted).isNotNull();
      assertThat(persisted.getUserEntityUserId().getBytes()).containsExactly(userHandle);
      assertThat(persisted.getCredentialId().getBytes()).containsExactly(credentialId);
      assertThat(persisted.getPublicKey().getBytes()).containsExactly(bytes(5, (byte) 4));
      assertThat(persisted.getTransports()).containsExactlyInAnyOrder(
          AuthenticatorTransport.HYBRID,
          AuthenticatorTransport.INTERNAL,
          AuthenticatorTransport.SMART_CARD);
      assertThat(persisted.getSignatureCount()).isEqualTo(7L);

      transaction(context).executeWithoutResult(status -> credentialAdapter.save(record(
          userHandle, credentialId, 8L, true, USED_AT)));
      CredentialRecord updated = transaction(context).execute(status ->
          credentialAdapter.findByCredentialId(new Bytes(credentialId)));
      assertThat(updated.getSignatureCount()).isEqualTo(8L);
      assertThat(updated.isBackupState()).isTrue();
      assertThat(updated.getLastUsed()).isEqualTo(USED_AT);
      assertThat(credentials.count()).isEqualTo(1L);
      assertThat(owners.count()).isEqualTo(1L);
    });
  }

  private static CredentialRecord record(
      byte[] userHandle,
      byte[] credentialId,
      long signatureCount,
      boolean backupState,
      Instant lastUsed) {
    ImmutableCredentialRecord.ImmutableCredentialRecordBuilder builder =
        ImmutableCredentialRecord.builder()
            .credentialType(PublicKeyCredentialType.PUBLIC_KEY)
            .credentialId(new Bytes(credentialId))
            .userEntityUserId(new Bytes(userHandle))
            .publicKey(new ImmutablePublicKeyCose(bytes(5, (byte) 4)))
            .signatureCount(signatureCount)
            .uvInitialized(true)
            .transports(Set.of(
                AuthenticatorTransport.INTERNAL,
                AuthenticatorTransport.HYBRID,
                AuthenticatorTransport.SMART_CARD))
            .backupEligible(true)
            .backupState(backupState)
            .attestationObject(new Bytes(bytes(6, (byte) 5)))
            .attestationClientDataJSON(new Bytes(bytes(7, (byte) 6)))
            .created(CREATED_AT)
            .label("Notebook");
    if (lastUsed != null) {
      builder.lastUsed(lastUsed);
    }
    return builder.build();
  }

  private ApplicationContextRunner contextRunner() {
    return new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataJpaRepositoriesAutoConfiguration.class))
        .withUserConfiguration(RepositoryTestConfig.class)
        .withPropertyValues(
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.jpa.hibernate.naming.physical-strategy="
                + "org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl",
            "spring.jpa.properties.hibernate.jdbc.time_zone=UTC")
        .withBean(DataSource.class, () -> dataSource);
  }

  private static TransactionTemplate transaction(ApplicationContext context) {
    return new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
  }

  private static byte[] bytes(int length, byte value) {
    byte[] result = new byte[length];
    java.util.Arrays.fill(result, value);
    return result;
  }

  @Configuration(proxyBeanMethods = false)
  @EntityScan(basePackageClasses = UserEntity.class)
  @EnableJpaRepositories(basePackageClasses = UserRepository.class)
  static class RepositoryTestConfig {
  }
}
