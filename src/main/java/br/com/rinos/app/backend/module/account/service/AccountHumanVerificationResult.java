package br.com.rinos.app.backend.module.account.service;
public record AccountHumanVerificationResult(boolean providerAvailable,boolean valid){
 public static AccountHumanVerificationResult unavailable(){return new AccountHumanVerificationResult(false,false);}
}
