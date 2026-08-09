package br.com.rinos.app.api.facade;

import java.util.List;

import br.com.rinos.app.api.dto.TotpEnrollmentCancellationDTO;
import br.com.rinos.app.api.dto.TotpEnrollmentConfirmationDTO;
import br.com.rinos.app.api.dto.TotpEnrollmentRequestDTO;
import br.com.rinos.app.api.vo.TotpEnrollmentResultVO;
import br.com.rinos.app.api.vo.TotpFactorVO;

/** Fronteira pública para gestão TOTP do usuário autenticado. */
public interface TotpManagementFacade {

  TotpEnrollmentResultVO begin(TotpEnrollmentRequestDTO request);

  TotpEnrollmentResultVO confirm(TotpEnrollmentConfirmationDTO request);

  TotpEnrollmentResultVO cancel(TotpEnrollmentCancellationDTO request);

  List<TotpFactorVO> listActive(long userId);
}
