package br.com.fluxocaixa.lancamentos.dominio;

import java.time.LocalDate;
import java.util.UUID;

/** Registro imutável de um crédito ou débito (RN03). Sem dependência de framework. */
public record Lancamento(
        UUID id,
        String comercianteId,
        TipoLancamento tipo,
        long valorCentavos,
        LocalDate dataCompetencia,
        UUID estornoDe) {

    public static Lancamento novo(String comercianteId, TipoLancamento tipo, long valorCentavos,
                                  LocalDate dataCompetencia, LocalDate hoje) {
        if (tipo == null) {
            throw new RegraNegocioException("O tipo é obrigatório.");
        }
        if (valorCentavos <= 0) {
            throw new RegraNegocioException("O valor deve ser positivo.");
        }
        LocalDate data = dataCompetencia != null ? dataCompetencia : hoje;
        if (data.isAfter(hoje)) {
            throw new RegraNegocioException("A data de competência não pode estar no futuro.");
        }
        return new Lancamento(UUID.randomUUID(), comercianteId, tipo, valorCentavos, data, null);
    }

    /** RN05: tipo inverso, vínculo ao original e competência na data do estorno. */
    public Lancamento estornar(LocalDate hoje) {
        if (ehEstorno()) {
            throw new RegraNegocioException("Um estorno não pode ser estornado.");
        }
        return new Lancamento(UUID.randomUUID(), comercianteId, tipo.inverso(), valorCentavos, hoje, id);
    }

    public boolean ehEstorno() {
        return estornoDe != null;
    }
}
