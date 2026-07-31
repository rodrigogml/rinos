package br.com.rinos.app.backend.module.identity.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import br.com.rinos.app.backend.module.identity.vo.OriginAddressVO;
import br.com.rinos.app.config.ProxyPropertiesConfig;

/**
 * Resolve a origem somente por cadeia encaminhada por proxies explicitamente autorizados.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
public class TrustedProxyService {

  private final OriginAddressService addressService;
  private final List<Network> trustedNetworks;

  /**
   * Compila a allowlist declarada no {@code application.properties}.
   *
   * @param properties redes confiáveis
   * @param addressService normalizador sem DNS
   */
  public TrustedProxyService(
      ProxyPropertiesConfig properties,
      OriginAddressService addressService) {
    this.addressService = addressService;
    trustedNetworks = properties.trustedProxies().stream()
        .map(this::parseNetwork)
        .toList();
  }

  /**
   * Resolve a origem e rejeita cabeçalhos injetados ou contraditórios.
   *
   * @param immediatePeer endereço da conexão TCP
   * @param forwarded cabeçalho RFC 7239 completo ou {@code null}
   * @param xForwardedFor cabeçalho legado completo ou {@code null}
   * @return origem canônica
   * @throws IllegalArgumentException quando a cadeia não é confiável ou consistente
   */
  public OriginAddressVO resolve(
      String immediatePeer,
      String forwarded,
      String xForwardedFor) {
    OriginAddressVO peer = addressService.normalize(immediatePeer);
    boolean hasForwarded = forwarded != null && !forwarded.isBlank();
    boolean hasXForwardedFor = xForwardedFor != null && !xForwardedFor.isBlank();
    boolean trustedPeer = isTrusted(peer);
    if (!trustedPeer) {
      if (hasForwarded || hasXForwardedFor) {
        throw invalidChain();
      }
      return peer;
    }
    if (!hasForwarded && !hasXForwardedFor) {
      throw invalidChain();
    }

    OriginAddressVO standardOrigin = hasForwarded
        ? resolveChain(parseForwarded(forwarded))
        : null;
    OriginAddressVO legacyOrigin = hasXForwardedFor
        ? resolveChain(parseXForwardedFor(xForwardedFor))
        : null;
    if (standardOrigin != null
        && legacyOrigin != null
        && !Arrays.equals(standardOrigin.getAddress(), legacyOrigin.getAddress())) {
      throw invalidChain();
    }
    return standardOrigin != null ? standardOrigin : legacyOrigin;
  }

  private OriginAddressVO resolveChain(List<OriginAddressVO> forwardedChain) {
    for (int index = forwardedChain.size() - 1; index >= 0; index--) {
      OriginAddressVO candidate = forwardedChain.get(index);
      if (!isTrusted(candidate)) {
        return candidate;
      }
    }
    throw invalidChain();
  }

  private List<OriginAddressVO> parseXForwardedFor(String header) {
    String[] elements = header.split(",", -1);
    List<OriginAddressVO> addresses = new ArrayList<>(elements.length);
    for (String element : elements) {
      if (element.isBlank()) {
        throw invalidChain();
      }
      addresses.add(addressService.normalize(stripOptionalPort(element.strip())));
    }
    return List.copyOf(addresses);
  }

  private List<OriginAddressVO> parseForwarded(String header) {
    String[] elements = header.split(",", -1);
    List<OriginAddressVO> addresses = new ArrayList<>(elements.length);
    for (String element : elements) {
      String found = null;
      for (String parameter : element.split(";", -1)) {
        String[] parts = parameter.strip().split("=", 2);
        if (parts.length == 2 && parts[0].equalsIgnoreCase("for")) {
          if (found != null) {
            throw invalidChain();
          }
          found = unquote(parts[1].strip());
        }
      }
      if (found == null || found.equalsIgnoreCase("unknown") || found.startsWith("_")) {
        throw invalidChain();
      }
      addresses.add(addressService.normalize(stripOptionalPort(found)));
    }
    return List.copyOf(addresses);
  }

  private boolean isTrusted(OriginAddressVO address) {
    return trustedNetworks.stream().anyMatch(network -> network.contains(address.getAddress()));
  }

  private Network parseNetwork(String definition) {
    Objects.requireNonNull(definition, "trusted proxy definition must not be null");
    String[] parts = definition.strip().split("/", -1);
    if (parts.length > 2 || parts[0].isBlank()) {
      throw new IllegalArgumentException("trusted proxy must be an IP literal or CIDR");
    }
    byte[] address = addressService.normalize(parts[0]).getAddress();
    int maximumPrefix = address.length * Byte.SIZE;
    int prefix = maximumPrefix;
    if (parts.length == 2) {
      try {
        prefix = Integer.parseInt(parts[1]);
      } catch (NumberFormatException exception) {
        throw new IllegalArgumentException("trusted proxy CIDR prefix is invalid", exception);
      }
    }
    if (prefix < 0 || prefix > maximumPrefix) {
      throw new IllegalArgumentException("trusted proxy CIDR prefix is out of range");
    }
    return new Network(address, prefix);
  }

  private static String unquote(String value) {
    if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
      return value.substring(1, value.length() - 1);
    }
    if (value.contains("\"")) {
      throw invalidChain();
    }
    return value;
  }

  private static String stripOptionalPort(String value) {
    if (value.startsWith("[")) {
      int closingBracket = value.indexOf(']');
      if (closingBracket < 0) {
        throw invalidChain();
      }
      if (closingBracket + 1 < value.length()) {
        String port = value.substring(closingBracket + 1);
        if (!port.startsWith(":")
            || port.length() == 1
            || !port.substring(1).chars().allMatch(Character::isDigit)) {
          throw invalidChain();
        }
      }
      return value.substring(1, closingBracket);
    }
    long colons = value.chars().filter(character -> character == ':').count();
    if (colons == 1) {
      int separator = value.lastIndexOf(':');
      String port = value.substring(separator + 1);
      if (port.isEmpty() || !port.chars().allMatch(Character::isDigit)) {
        throw invalidChain();
      }
      return value.substring(0, separator);
    }
    return value;
  }

  private static IllegalArgumentException invalidChain() {
    return new IllegalArgumentException("forwarded origin chain is not trusted and consistent");
  }

  private record Network(byte[] address, int prefixLength) {

    private Network {
      address = Arrays.copyOf(address, address.length);
    }

    private boolean contains(byte[] candidate) {
      if (candidate.length != address.length) {
        return false;
      }
      int completeBytes = prefixLength / Byte.SIZE;
      int remainingBits = prefixLength % Byte.SIZE;
      for (int index = 0; index < completeBytes; index++) {
        if (candidate[index] != address[index]) {
          return false;
        }
      }
      if (remainingBits == 0) {
        return true;
      }
      int mask = 0xFF << (Byte.SIZE - remainingBits);
      return (candidate[completeBytes] & mask) == (address[completeBytes] & mask);
    }
  }
}
