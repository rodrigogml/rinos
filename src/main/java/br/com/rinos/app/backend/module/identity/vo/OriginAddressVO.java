package br.com.rinos.app.backend.module.identity.vo;

import java.util.Arrays;
import java.util.Objects;

/**
 * Mantém o formato binário canônico de um literal IPv4 ou IPv6.
 *
 * <p>A representação textual é redigida para impedir cópia acidental em logs.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public final class OriginAddressVO {

  private final byte[] address;

  /**
   * Cria o valor a partir de quatro ou 16 bytes.
   *
   * @param address endereço binário canônico
   */
  public OriginAddressVO(byte[] address) {
    Objects.requireNonNull(address, "address must not be null");
    if (address.length != 4 && address.length != 16) {
      throw new IllegalArgumentException("address must contain 4 or 16 bytes");
    }
    this.address = Arrays.copyOf(address, address.length);
  }

  /**
   * Retorna cópia segura do endereço.
   *
   * @return quatro bytes para IPv4 ou 16 para IPv6
   */
  public byte[] getAddress() {
    return Arrays.copyOf(address, address.length);
  }

  /**
   * Indica a família do endereço sem revelar seu valor.
   *
   * @return {@code true} para IPv6
   */
  public boolean isIpv6() {
    return address.length == 16;
  }

  /**
   * Produz representação deliberadamente redigida.
   *
   * @return família e marcador de redação
   */
  @Override
  public String toString() {
    return "OriginAddressVO[family=" + (isIpv6() ? "IPv6" : "IPv4") + ", address=REDACTED]";
  }
}
