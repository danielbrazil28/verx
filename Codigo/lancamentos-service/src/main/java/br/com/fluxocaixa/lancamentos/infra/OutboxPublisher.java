package br.com.fluxocaixa.lancamentos.infra;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publica eventos pendentes e só os marca como publicados após a confirmação do broker. Uma falha
 * desfaz a transação e o lote é tentado de novo; duplicidades são tratadas pelo consumidor.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final long TIMEOUT_CONFIRMACAO_MS = 5_000;
    private static final int RETENCAO_DIAS = 7;

    private final OutboxRepository outbox;
    private final RabbitTemplate rabbit;
    private final TransactionTemplate transacao;
    private final int lote;

    public OutboxPublisher(OutboxRepository outbox, RabbitTemplate rabbit, TransactionTemplate transacao,
                           MeterRegistry metricas, @Value("${app.outbox.lote}") int lote) {
        this.outbox = outbox;
        this.rabbit = rabbit;
        this.transacao = transacao;
        this.lote = lote;
        Gauge.builder("outbox.pendentes", outbox, OutboxRepository::contarPendentes).register(metricas);
    }

    @Scheduled(fixedDelayString = "${app.outbox.intervalo-ms}")
    public void publicarPendentes() {
        try {
            transacao.executeWithoutResult(status -> publicarLote());
        } catch (RuntimeException e) {
            log.warn("Falha ao publicar eventos; nova tentativa no próximo ciclo: {}", e.getMessage());
        }
    }

    private void publicarLote() {
        List<OutboxRepository.Pendente> pendentes = outbox.travarPendentes(lote);
        if (pendentes.isEmpty()) {
            return;
        }
        rabbit.invoke(operacoes -> {
            pendentes.forEach(p -> operacoes.send(RabbitConfig.EXCHANGE, rota(p), mensagem(p)));
            operacoes.waitForConfirmsOrDie(TIMEOUT_CONFIRMACAO_MS);
            return null;
        });
        pendentes.forEach(p -> outbox.marcarPublicado(p.eventId()));
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void limpar() {
        outbox.limparPublicados(RETENCAO_DIAS);
    }

    private static String rota(OutboxRepository.Pendente p) {
        return "lancamento." + p.tipo();
    }

    private static Message mensagem(OutboxRepository.Pendente p) {
        return MessageBuilder.withBody(p.payload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(p.eventId().toString())
                .setType(p.tipo())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
    }
}
