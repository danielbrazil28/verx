package br.com.fluxocaixa.lancamentos.dominio;

public enum TipoLancamento {
    CREDITO, DEBITO;

    public TipoLancamento inverso() {
        return this == CREDITO ? DEBITO : CREDITO;
    }
}
