package br.com.rinos.app.backend.module.storage.vo;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Representa o identificador físico interno e imutável usado para derivar o schema de um tenant.
 *
 * <p>O valor não aceita entrada livre: somente hexadecimal minúsculo de 32 caracteres pode compor o nome físico.
 * A derivação não expõe nem persiste uma segunda cópia do nome do schema.</p>
 *
 * @param value sequência hexadecimal interna de 32 caracteres
 * @author Rodrigo Leitão
 * @since 2026-08-29
 */
public record TenantPhysicalIdentifier(String value) {

  private static final Pattern FORMAT = Pattern.compile("[a-f0-9]{32}");
  private static final String SCHEMA_PREFIX = "rinos_";

  /**
   * Valida o formato que impede o identificador de alterar a estrutura da URL ou o nome SQL derivado.
   *
   * @throws IllegalArgumentException quando o valor é ausente ou não possui o formato interno permitido
   */
  public TenantPhysicalIdentifier {
    Objects.requireNonNull(value, "value must not be null");
    if (!FORMAT.matcher(value).matches()) {
      throw new IllegalArgumentException("physical tenant identifier is invalid");
    }
  }

  /**
   * Deriva o único nome físico permitido para o tenant, sem aceitar catálogo vindo de chamadas externas.
   *
   * @return nome do schema do tenant
   */
  public String schemaName() {
    return SCHEMA_PREFIX + value;
  }
}
