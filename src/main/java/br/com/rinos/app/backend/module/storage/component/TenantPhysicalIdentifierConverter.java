package br.com.rinos.app.backend.module.storage.component;

import br.com.rinos.app.backend.module.storage.vo.TenantPhysicalIdentifier;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Converte o identificador físico validado para a coluna imutável do registro global. */
@Converter
public class TenantPhysicalIdentifierConverter
    implements AttributeConverter<TenantPhysicalIdentifier, String> {

  @Override
  public String convertToDatabaseColumn(TenantPhysicalIdentifier attribute) {
    return attribute == null ? null : attribute.value();
  }

  @Override
  public TenantPhysicalIdentifier convertToEntityAttribute(String databaseData) {
    return databaseData == null ? null : new TenantPhysicalIdentifier(databaseData);
  }
}
