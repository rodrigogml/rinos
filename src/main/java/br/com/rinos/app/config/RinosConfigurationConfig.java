package br.com.rinos.app.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registra os grupos de propriedades cujo domínio e ciclo de vida pertencem ao Rinos.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-27
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
    ApplicationPropertiesConfig.class,
    AccountCreationPropertiesConfig.class,
    AccessBootstrapPropertiesConfig.class,
    AccessCachePropertiesConfig.class,
    AuthenticationAbusePropertiesConfig.class,
    AuthenticationKeyringPropertiesConfig.class,
    AuthenticationMfaPropertiesConfig.class,
    AuthenticationNotificationPropertiesConfig.class,
    AuthenticationRetentionPropertiesConfig.class,
    AuthenticationSessionPropertiesConfig.class,
    CleanupPropertiesConfig.class,
    MaintenancePropertiesConfig.class,
    MembershipInvitationPropertiesConfig.class,
    OriginPropertiesConfig.class,
    PasswordHashPropertiesConfig.class,
    PasswordRecoveryPropertiesConfig.class,
    ProxyPropertiesConfig.class,
    PwnedPasswordsPropertiesConfig.class,
    RegistrationPropertiesConfig.class,
    VerificationPropertiesConfig.class
})
public class RinosConfigurationConfig {
}
