package br.com.rinos.app.backend.module.access.enums;

/** Tipo imutável de alteração registrada no histórico de uma regra. */
public enum AccessRuleChangeType {
  CREATE,
  EFFECT_CHANGE,
  VALIDITY_CHANGE,
  DEACTIVATE
}
