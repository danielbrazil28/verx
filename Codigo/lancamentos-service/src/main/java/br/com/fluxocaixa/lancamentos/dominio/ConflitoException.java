package br.com.fluxocaixa.lancamentos.dominio;

/** A operação conflita com o estado atual (RN06 e RN08). */
public class ConflitoException extends RuntimeException {
    public ConflitoException(String mensagem) {
        super(mensagem);
    }
}
