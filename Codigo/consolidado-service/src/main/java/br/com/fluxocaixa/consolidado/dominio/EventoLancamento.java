package br.com.fluxocaixa.consolidado.dominio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Campos novos do produtor são ignorados, o que mantém o contrato compatível. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventoLancamento(
        UUID eventId,
        Instant occurredAt,
        String comercianteId,
        String tipo,
        long valorCentavos,
        LocalDate dataCompetencia) {

    public void validar() {
        if (eventId == null || occurredAt == null || comercianteId == null || comercianteId.isBlank()
                || dataCompetencia == null || valorCentavos <= 0
                || !("CREDITO".equals(tipo) || "DEBITO".equals(tipo))) {
            throw new EventoInvalidoException("Evento inválido: " + eventId);
        }
    }

    public boolean ehCredito() {
        return "CREDITO".equals(tipo);
    }
}
