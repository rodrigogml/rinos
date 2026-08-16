package br.com.rinos.app.backend.module.membership.service;
import java.time.Instant;
public record MembershipInvitationRateDecision(boolean allowed,Instant retryAt){public static MembershipInvitationRateDecision permit(){return new MembershipInvitationRateDecision(true,null);}}
