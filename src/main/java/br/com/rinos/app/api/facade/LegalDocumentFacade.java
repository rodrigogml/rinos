package br.com.rinos.app.api.facade;

import java.util.List;
import java.util.Optional;

import br.com.rinos.app.api.vo.LegalDocumentContentVO;
import br.com.rinos.app.api.vo.LegalDocumentReferenceVO;

/**
 * Publica documentos jurídicos vigentes e históricos sem expor entities à apresentação.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public interface LegalDocumentFacade {

  /**
   * Resolve a fotografia vigente usada para compor os aceites do cadastro.
   *
   * @return documentos vigentes, incluindo os dois documentos-base obrigatórios
   * @throws IllegalStateException quando o catálogo não pode liberar o cadastro
   */
  List<LegalDocumentReferenceVO> findCurrentDocuments();

  /**
   * Localiza uma versão que já iniciou sua vigência e verifica sua integridade.
   *
   * <p>Versões encerradas continuam consultáveis porque podem estar vinculadas a evidências
   * históricas. Versões futuras não são publicadas antecipadamente.
   *
   * @param reference referência recebida pela rota pública
   * @return conteúdo íntegro ou vazio para referência inválida, inexistente ou futura
   * @throws IllegalStateException quando o conteúdo persistido não corresponde ao hash
   */
  Optional<LegalDocumentContentVO> findPublishedDocument(String reference);
}
