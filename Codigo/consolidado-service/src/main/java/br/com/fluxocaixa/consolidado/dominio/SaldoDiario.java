package br.com.fluxocaixa.consolidado.dominio;

import java.time.LocalDate;

public record SaldoDiario(LocalDate data, long totalCreditos, long totalDebitos, long saldoDia, long saldoAcumulado) {

    public static SaldoDiario de(LocalDate data, long creditos, long debitos, long acumulado) {
        return new SaldoDiario(data, creditos, debitos, creditos - debitos, acumulado);
    }
}
