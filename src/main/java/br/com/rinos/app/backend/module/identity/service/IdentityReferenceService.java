package br.com.rinos.app.backend.module.identity.service;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Gera e converte referências UUID opacas usadas somente para gestão.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
@Service
public class IdentityReferenceService {
  public UUID generate() { return UUID.randomUUID(); }
  public byte[] encode(UUID reference) {
    Objects.requireNonNull(reference, "reference must not be null");
    return ByteBuffer.allocate(16).putLong(reference.getMostSignificantBits())
        .putLong(reference.getLeastSignificantBits()).array();
  }
}
