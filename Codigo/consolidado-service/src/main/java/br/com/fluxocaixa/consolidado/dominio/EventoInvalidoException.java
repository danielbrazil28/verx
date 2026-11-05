package br.com.fluxocaixa.consolidado.dominio;

public class EventoInvalidoException extends RuntimeException {
    public EventoInvalidoException(String mensagem) {
        super(mensagem);
    }
}
