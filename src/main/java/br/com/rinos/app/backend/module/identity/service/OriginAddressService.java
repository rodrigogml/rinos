package br.com.rinos.app.backend.module.identity.service;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import br.com.rinos.app.backend.module.identity.vo.OriginAddressVO;

/**
 * Converte exclusivamente literais IPv4 e IPv6 para seu formato binário canônico.
 *
 * <p>Hostnames e zonas IPv6 são rejeitados antes de qualquer chamada da JDK, impedindo resolução
 * DNS ou dependência de interface da máquina.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
public class OriginAddressService {

  private static final Pattern IPV6_LITERAL = Pattern.compile("[0-9A-Fa-f:.]+");

  /**
   * Normaliza um literal de endereço.
   *
   * @param literal IPv4 decimal ou IPv6 hexadecimal
   * @return endereço binário com quatro ou 16 bytes
   * @throws NullPointerException quando o valor é nulo
   * @throws IllegalArgumentException quando o valor não é um literal canônico aceito
   */
  public OriginAddressVO normalize(String literal) {
    Objects.requireNonNull(literal, "literal must not be null");
    String address = literal.strip();
    if (address.contains(":")) {
      return normalizeIpv6(address);
    }
    return normalizeIpv4(address);
  }

  /**
   * Converte o formato binário validado em literal canônico sem executar resolução de nome.
   *
   * @param origin origem binária não nula
   * @return literal IPv4 ou IPv6 próprio para protocolos externos
   */
  public String toCanonicalLiteral(OriginAddressVO origin) {
    Objects.requireNonNull(origin, "origin must not be null");
    try {
      return InetAddress.getByAddress(origin.getAddress()).getHostAddress();
    } catch (UnknownHostException exception) {
      throw new IllegalStateException("validated origin has an unsupported binary length", exception);
    }
  }

  /**
   * Analisa IPv4 sem delegar a resolução de nomes à rede.
   *
   * @param literal endereço decimal pontuado
   * @return quatro bytes
   */
  private static OriginAddressVO normalizeIpv4(String literal) {
    String[] parts = literal.split("\\.", -1);
    if (parts.length != 4) {
      throw invalidLiteral();
    }
    byte[] bytes = new byte[4];
    for (int index = 0; index < parts.length; index++) {
      String part = parts[index];
      if (part.isEmpty()
          || !part.chars().allMatch(Character::isDigit)
          || (part.length() > 1 && part.startsWith("0"))) {
        throw invalidLiteral();
      }
      try {
        int value = Integer.parseInt(part);
        if (value > 255) {
          throw invalidLiteral();
        }
        bytes[index] = (byte) value;
      } catch (NumberFormatException exception) {
        throw invalidLiteral();
      }
    }
    return new OriginAddressVO(bytes);
  }

  /**
   * Analisa somente sintaxe que contém dois-pontos e caracteres de literal IP.
   *
   * @param literal endereço IPv6 sem zona
   * @return 16 bytes
   */
  private static OriginAddressVO normalizeIpv6(String literal) {
    if (!IPV6_LITERAL.matcher(literal).matches()) {
      throw invalidLiteral();
    }
    try {
      InetAddress address = InetAddress.getByName(literal);
      if (!(address instanceof Inet6Address)) {
        throw invalidLiteral();
      }
      return new OriginAddressVO(address.getAddress());
    } catch (UnknownHostException exception) {
      throw invalidLiteral();
    }
  }

  private static IllegalArgumentException invalidLiteral() {
    return new IllegalArgumentException("origin address must be an IPv4 or IPv6 literal");
  }
}
