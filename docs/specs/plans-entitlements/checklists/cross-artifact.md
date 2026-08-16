# Análise cruzada

| Decisão | Spec | Plano/modelo | Contrato | Tarefas |
|---|---|---|---|---|
| dois escopos | FR-PE-016..018 | `ContractScope`, FKs | `EntitlementSubject` | 1.1, 2.1, 6.1 |
| contrato por titular | FR-PE-ASG-001..004 | `ServiceContract` | bootstrap ports | 2.2, 3.3, 5.1..2 |
| dois `FREE` | FR-PE-PLAN-003..014 | catálogo tipado | descriptors | 3.1..2 |
| limite dez | FR-PE-ENT-013 | composição tenant | capacity facade | 3.2, 4.3, 6.2 |
| ocupação permanente | FR-PE-USG-023..024 | occupancy | occupy | 3.4, 5.4..5, 6.3 |
| reserva de convite | FR-PE-USG-021..022 | reservation | reserve/convert/release | 4.3, 5.3..4, 6.4 |
| global não é pessoal | FR-PE-017 | evaluation | requirement scope | 1.3..4, 5.6 |
| RFW antes da UI | FR-PE-ADM-* | interface spec | facades/VOs | 7.1..3 |

Resultado: não há autorização documental para schema ou código antes da conclusão da revisão dos SDDs dependentes e
da validação final deste conjunto.
