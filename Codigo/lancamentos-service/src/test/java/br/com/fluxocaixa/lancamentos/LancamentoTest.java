package br.com.fluxocaixa.lancamentos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.fluxocaixa.lancamentos.dominio.Lancamento;
import br.com.fluxocaixa.lancamentos.dominio.RegraNegocioException;
import br.com.fluxocaixa.lancamentos.dominio.TipoLancamento;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LancamentoTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 5);

    @Test
    void usaHojeQuandoDataOmitida() {
        assertThat(Lancamento.novo("c", TipoLancamento.CREDITO, 100, null, HOJE).dataCompetencia()).isEqualTo(HOJE);
    }

    @Test
    void aceitaRetroativo() {
        LocalDate ontem = HOJE.minusDays(1);
        assertThat(Lancamento.novo("c", TipoLancamento.DEBITO, 100, ontem, HOJE).dataCompetencia()).isEqualTo(ontem);
    }

    @Test
    void rejeitaDataFutura() {
        assertThatThrownBy(() -> Lancamento.novo("c", TipoLancamento.CREDITO, 100, HOJE.plusDays(1), HOJE))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void rejeitaValorNaoPositivo() {
        assertThatThrownBy(() -> Lancamento.novo("c", TipoLancamento.CREDITO, 0, null, HOJE))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void estornoInverteTipoEUsaDataDoEstorno() {
        Lancamento original = Lancamento.novo("c", TipoLancamento.CREDITO, 100, HOJE.minusDays(3), HOJE);
        Lancamento estorno = original.estornar(HOJE);
        assertThat(estorno.tipo()).isEqualTo(TipoLancamento.DEBITO);
        assertThat(estorno.valorCentavos()).isEqualTo(100);
        assertThat(estorno.dataCompetencia()).isEqualTo(HOJE);
        assertThat(estorno.estornoDe()).isEqualTo(original.id());
    }

    @Test
    void estornoNaoPodeSerEstornado() {
        Lancamento estorno = Lancamento.novo("c", TipoLancamento.CREDITO, 100, null, HOJE).estornar(HOJE);
        assertThatThrownBy(() -> estorno.estornar(HOJE)).isInstanceOf(RegraNegocioException.class);
    }
}
