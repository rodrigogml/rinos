package br.com.rinos.app.backend.module.storage.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Identificador físico de tenant")
class TenantPhysicalIdentifierTest {

  @Test
  void schemaName_shouldDeriveExpectedName_whenIdentifierIsAllowed() {
    TenantPhysicalIdentifier identifier =
        new TenantPhysicalIdentifier("0123456789abcdef0123456789abcdef");

    assertThat(identifier.schemaName()).isEqualTo("rinos_0123456789abcdef0123456789abcdef");
  }

  @Test
  void constructor_shouldRejectValuesOutsideInternalFormat() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new TenantPhysicalIdentifier("RINOS; DROP DATABASE rinos_global"))
        .withMessage("physical tenant identifier is invalid");
  }

  @Test
  void constructor_shouldRejectNullValue() {
    assertThatNullPointerException()
        .isThrownBy(() -> new TenantPhysicalIdentifier(null))
        .withMessage("value must not be null");
  }
}
