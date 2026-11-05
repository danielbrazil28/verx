package br.com.fluxocaixa.lancamentos.infra;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declara também a fila do consumidor. Assim, um evento publicado antes de o Consolidado subir
 * fica retido na fila, em vez de ser descartado como mensagem sem rota.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "fluxo-caixa.eventos";
    public static final String FILA = "consolidado.eventos";
    public static final String DLX = "fluxo-caixa.eventos.dlx";
    public static final String DLQ = "consolidado.eventos.dlq";

    @Bean
    Declarables topologia() {
        TopicExchange exchange = new TopicExchange(EXCHANGE);
        TopicExchange dlx = new TopicExchange(DLX);
        Queue fila = QueueBuilder.durable(FILA).quorum().deadLetterExchange(DLX).build();
        Queue dlq = QueueBuilder.durable(DLQ).quorum().build();
        Binding ligacao = BindingBuilder.bind(fila).to(exchange).with("lancamento.#");
        Binding ligacaoDlq = BindingBuilder.bind(dlq).to(dlx).with("#");
        return new Declarables(exchange, dlx, fila, dlq, ligacao, ligacaoDlq);
    }
}
