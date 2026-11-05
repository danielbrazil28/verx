package br.com.fluxocaixa.lancamentos.dominio;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Contrato publicado para os demais contextos. Mudanças incompatíveis geram nova versão. */
public record EventoLancamento(
        UUID eventId,
        String eventType,
        int schemaVersion,
        Instant occurredAt,
        String comercianteId,
        UUID lancamentoId,
        TipoLancamento tipo,
        long valorCentavos,
        String moeda,
        LocalDate dataCompetencia) {

    public static EventoLancamento de(Lancamento l, Instant agora) {
        String tipoEvento = l.ehEstorno() ? "LancamentoEstornado" : "LancamentoRegistrado";
        return new EventoLancamento(UUID.randomUUID(), tipoEvento, 1, agora, l.comercianteId(), l.id(),
                l.tipo(), l.valorCentavos(), "BRL", l.dataCompetencia());
    }
}
