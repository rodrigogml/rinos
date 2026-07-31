package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import br.com.rinos.app.backend.module.identity.vo.PasswordHashCalibrationReportVO;
import br.com.rinos.app.config.PasswordHashPropertiesConfig;

@DisplayName("Calibração reproduzível do Argon2id")
class PasswordHashCalibrationServiceTest {

  @Test
  void calibrate_shouldWarmUpAndMeasureAtLeastFifty_withoutReportingSecrets() {
    AtomicInteger calls = new AtomicInteger();
    PasswordEncoder encoder = new CountingPasswordEncoder(calls);
    PasswordHashCalibrationService service = new PasswordHashCalibrationService();

    PasswordHashCalibrationReportVO report = service.calibrate(
        encoder,
        new PasswordHashPropertiesConfig(19_456, 2, 1, 16, 32),
        10,
        50);

    assertThat(calls).hasValue(60);
    assertThat(report.measurements()).isEqualTo(50);
    assertThat(report.toString())
        .doesNotContain("Calibration-Only-7!Rinos", "{argon2id}", "$argon2id$");
  }

  @Test
  void calibrate_shouldRejectSamplesBelowContractualMinimum() {
    PasswordHashCalibrationService service = new PasswordHashCalibrationService();

    assertThatThrownBy(() -> service.calibrate(
        new CountingPasswordEncoder(new AtomicInteger()),
        new PasswordHashPropertiesConfig(19_456, 2, 1, 16, 32),
        10,
        49))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("measurements must be at least 50");
  }

  private record CountingPasswordEncoder(AtomicInteger calls) implements PasswordEncoder {

    @Override
    public String encode(CharSequence rawPassword) {
      calls.incrementAndGet();
      return "discarded";
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
      return false;
    }
  }
}
