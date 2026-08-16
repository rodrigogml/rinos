package br.com.rinos.app.api.module.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import br.com.rinos.app.api.module.access.keys.AccessControlAccessKeys;
import br.com.rinos.app.api.module.access.keys.InitialModuleAccessKeys;

class AccessCatalogI18nTest {

  @Test
  void everyKey_shouldHaveHumanTextsThatDoNotExposeItsTechnicalCode() {
    ResourceBundle messages = ResourceBundle.getBundle("messages", Locale.ROOT);

    Stream.concat(AccessControlAccessKeys.ALL.stream(), InitialModuleAccessKeys.ALL.stream())
        .distinct()
        .forEach(key -> {
          assertThat(messages.containsKey(key.nameI18nKey())).isTrue();
          assertThat(messages.containsKey(key.descriptionI18nKey())).isTrue();
          assertThat(messages.getString(key.nameI18nKey())).doesNotContain(key.code());
          assertThat(messages.getString(key.descriptionI18nKey())).doesNotContain(key.code());
        });
  }
}
