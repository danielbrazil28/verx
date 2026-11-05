package br.com.fluxocaixa.lancamentos.dominio;

/** Requisição bem formada que viola uma regra de negócio. */
public class RegraNegocioException extends RuntimeException {
    public RegraNegocioException(String mensagem) {
        super(mensagem);
    }
}
