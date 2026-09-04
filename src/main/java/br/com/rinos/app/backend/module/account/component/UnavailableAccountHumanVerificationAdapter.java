package br.com.rinos.app.backend.module.account.component;
import java.util.UUID;
import br.com.rinos.app.backend.module.account.service.*;
public class UnavailableAccountHumanVerificationAdapter implements AccountHumanVerificationPort{
 public AccountHumanVerificationResult verify(String token,String origin,UUID key){return AccountHumanVerificationResult.unavailable();}
}
