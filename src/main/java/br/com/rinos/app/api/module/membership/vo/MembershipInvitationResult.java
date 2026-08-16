package br.com.rinos.app.api.module.membership.vo;
import java.time.Instant; import java.util.UUID;
import br.com.rinos.app.api.module.membership.enums.MembershipInvitationResultStatus;
public record MembershipInvitationResult(MembershipInvitationResultStatus status,UUID invitationPublicId,
    String transientProof,Instant expiresAt,String safeReasonCode){
 @Override public String toString(){return "MembershipInvitationResult[status="+status+", invitationPublicId=REDACTED, transientProof=REDACTED, expiresAt="+expiresAt+", safeReasonCode="+safeReasonCode+"]";}
}
