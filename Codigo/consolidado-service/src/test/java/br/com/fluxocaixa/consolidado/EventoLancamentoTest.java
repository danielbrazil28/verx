package br.com.fluxocaixa.consolidado;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.fluxocaixa.consolidado.dominio.EventoInvalidoException;
import br.com.fluxocaixa.consolidado.dominio.EventoLancamento;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventoLancamentoTest {

    private static EventoLancamento evento(String tipo, long valor) {
        return new EventoLancamento(UUID.randomUUID(), Instant.now(), "c", tipo, valor, LocalDate.of(2026, 11, 5));
    }

    @Test
    void aceitaEventoValido() {
        assertThatCode(() -> evento("CREDITO", 1).validar()).doesNotThrowAnyException();
    }

    @Test
    void rejeitaTipoDesconhecido() {
        assertThatThrownBy(() -> evento("OUTRO", 1).validar()).isInstanceOf(EventoInvalidoException.class);
    }

    @Test
    void rejeitaValorNaoPositivo() {
        assertThatThrownBy(() -> evento("DEBITO", 0).validar()).isInstanceOf(EventoInvalidoException.class);
    }
}
