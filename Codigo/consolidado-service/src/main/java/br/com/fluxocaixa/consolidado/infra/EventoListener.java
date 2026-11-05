package br.com.fluxocaixa.consolidado.infra;

import br.com.fluxocaixa.consolidado.aplicacao.ConsolidacaoService;
import br.com.fluxocaixa.consolidado.dominio.EventoInvalidoException;
import br.com.fluxocaixa.consolidado.dominio.EventoLancamento;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Exceção aqui gera retry com backoff; esgotadas as tentativas, a mensagem vai para a DLQ. */
@Component
public class EventoListener {

    private final ConsolidacaoService consolidacao;
    private final SaldoCache cache;
    private final ObjectMapper json;
    private final Timer atraso;

    public EventoListener(ConsolidacaoService consolidacao, SaldoCache cache, ObjectMapper json, MeterRegistry metricas) {
        this.consolidacao = consolidacao;
        this.cache = cache;
        this.json = json;
        this.atraso = Timer.builder("consolidado.atraso")
                .description("Tempo entre occurredAt e a aplicação do evento (RNF05)")
                .publishPercentiles(0.95)
                .register(metricas);
    }

    @RabbitListener(queues = RabbitConfig.FILA)
    public void receber(Message mensagem) {
        EventoLancamento evento = ler(mensagem);
        if (consolidacao.aplicar(evento)) {
            cache.invalidar(evento.comercianteId());
            atraso.record(Duration.between(evento.occurredAt(), Instant.now()));
        }
    }

    private EventoLancamento ler(Message mensagem) {
        try {
            return json.readValue(new String(mensagem.getBody(), StandardCharsets.UTF_8), EventoLancamento.class);
        } catch (JsonProcessingException e) {
            throw new EventoInvalidoException("Evento ilegível: " + e.getOriginalMessage());
        }
    }
}
