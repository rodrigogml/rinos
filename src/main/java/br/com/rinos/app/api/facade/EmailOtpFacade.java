package br.com.rinos.app.api.facade;

import java.util.concurrent.CompletionStage;

import br.com.rinos.app.api.dto.EmailOtpEmissionRequestDTO;
import br.com.rinos.app.api.dto.EmailOtpVerificationRequestDTO;
import br.com.rinos.app.api.enums.EmailOtpVerificationStatusEnum;
import br.com.rinos.app.api.vo.EmailOtpEmissionResultVO;

/**
 * Fronteira pública de emissão, reenvio e consumo do OTP por e-mail.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public interface EmailOtpFacade {
  CompletionStage<EmailOtpEmissionResultVO> begin(EmailOtpEmissionRequestDTO request);
  CompletionStage<EmailOtpEmissionResultVO> resend(EmailOtpEmissionRequestDTO request);
  EmailOtpVerificationStatusEnum verify(EmailOtpVerificationRequestDTO request);
}
