package br.com.rinos.app.backend.module.membership.service;
import java.nio.ByteBuffer; import java.nio.charset.StandardCharsets; import java.time.Duration; import java.util.List;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import br.com.rinos.app.backend.module.membership.enums.MembershipInvitationRateDimension;
import br.com.rinos.app.backend.module.membership.repository.MembershipInvitationRateWindowRepository; import br.com.rinos.app.config.MembershipInvitationPropertiesConfig;
@Service
@org.springframework.context.annotation.Lazy
public class MembershipInvitationRateLimitService{
 private final MembershipInvitationRateWindowRepository windows; private final MembershipInvitationPropertiesConfig properties;
 public MembershipInvitationRateLimitService(MembershipInvitationRateWindowRepository windows,MembershipInvitationPropertiesConfig properties){this.windows=windows;this.properties=properties;}
 @Transactional
 public MembershipInvitationRateDecision reserve(long accountId,long inviterId,String recipient,String canonicalOrigin){
  if(accountId<=0||inviterId<=0||recipient==null||recipient.isBlank()||canonicalOrigin==null||canonicalOrigin.isBlank())
   throw new IllegalArgumentException("invitation rate dimensions are invalid");
  var dimensions=List.of(new Dimension(MembershipInvitationRateDimension.ACCOUNT,longBytes(accountId),properties.accountLimit()),
   new Dimension(MembershipInvitationRateDimension.INVITER,longBytes(inviterId),properties.inviterLimit()),
   new Dimension(MembershipInvitationRateDimension.RECIPIENT,recipient.getBytes(StandardCharsets.UTF_8),properties.recipientLimit()),
   new Dimension(MembershipInvitationRateDimension.ORIGIN,canonicalOrigin.getBytes(StandardCharsets.UTF_8),properties.originLimit()));
  for(var dimension:dimensions){var decision=reserve(dimension);if(!decision.allowed())return decision;}return MembershipInvitationRateDecision.permit();
 }
 private MembershipInvitationRateDecision reserve(Dimension dimension){String type=dimension.type().name();long micros=micros(properties.rateWindow());
  windows.createActiveIfAbsent(type,dimension.key(),micros);windows.closeExpired(type,dimension.key());windows.createActiveIfAbsent(type,dimension.key(),micros);
  if(windows.incrementBelowLimit(type,dimension.key(),dimension.limit())==1)return MembershipInvitationRateDecision.permit();
  return new MembershipInvitationRateDecision(false,windows.findActive(type,dimension.key()).orElseThrow().getWindowEndsAt());}
 private static byte[] longBytes(long value){return ByteBuffer.allocate(Long.BYTES).putLong(value).array();}
 private static long micros(Duration value){return Math.addExact(Math.multiplyExact(value.getSeconds(),1_000_000L),value.getNano()/1_000L);}
 private record Dimension(MembershipInvitationRateDimension type,byte[] key,int limit){}
}
