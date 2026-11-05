package br.com.fluxocaixa.consolidado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fluxocaixa.consolidado.dominio.SaldoDiario;
import br.com.fluxocaixa.consolidado.infra.ConsolidadoRepository;
import br.com.fluxocaixa.consolidado.infra.RabbitConfig;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.retry.max-attempts=2",
        "spring.rabbitmq.listener.simple.retry.initial-interval=100ms"})
@AutoConfigureMockMvc
@Testcontainers
class ConsolidadoIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static final LocalDate D1 = LocalDate.of(2026, 11, 1);
    private static final LocalDate D2 = LocalDate.of(2026, 11, 2);
    private static final Duration ESPERA = Duration.ofSeconds(15);

    @Autowired MockMvc mvc;
    @Autowired RabbitTemplate rabbit;
    @Autowired ConsolidadoRepository repositorio;

    private void publicar(UUID eventId, String comerciante, String tipo, long valor, LocalDate data) {
        publicarBruto("""
                {"eventId":"%s","eventType":"LancamentoRegistrado","schemaVersion":1,"occurredAt":"%s",
                 "comercianteId":"%s","tipo":"%s","valorCentavos":%d,"moeda":"BRL","dataCompetencia":"%s","novoCampo":1}
                """.formatted(eventId, Instant.now(), comerciante, tipo, valor, data));
    }

    private void publicarBruto(String corpo) {
        Message mensagem = MessageBuilder.withBody(corpo.getBytes(StandardCharsets.UTF_8)).build();
        rabbit.send(RabbitConfig.EXCHANGE, "lancamento.teste", mensagem);
    }

    private static String novoComerciante() {
        return "c-" + UUID.randomUUID();
    }

    private static RequestPostProcessor token(String comerciante) {
        return jwt().jwt(j -> j.claim("comercianteId", comerciante))
                .authorities(new SimpleGrantedAuthority("SCOPE_consolidado:read"));
    }

    private void aguardarSaldo(String comerciante, LocalDate data, long acumulado) {
        await().atMost(ESPERA).untilAsserted(() ->
                assertThat(repositorio.saldoEm(comerciante, data).saldoAcumulado()).isEqualTo(acumulado));
    }

    @Test
    void consolidaCreditosEDebitos() throws Exception {
        String c = novoComerciante();
        publicar(UUID.randomUUID(), c, "CREDITO", 1000, D1);
        publicar(UUID.randomUUID(), c, "DEBITO", 300, D1);
        aguardarSaldo(c, D1, 700);

        mvc.perform(get("/consolidado/{data}", D1).with(token(c)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCreditos").value(1000))
                .andExpect(jsonPath("$.totalDebitos").value(300))
                .andExpect(jsonPath("$.saldoDia").value(700))
                .andExpect(jsonPath("$.saldoAcumulado").value(700));
    }

    @Test
    void eventoDuplicadoNaoAlteraOSaldo() {
        String c = novoComerciante();
        UUID evento = UUID.randomUUID();
        publicar(evento, c, "CREDITO", 500, D1);
        publicar(evento, c, "CREDITO", 500, D1);
        publicar(UUID.randomUUID(), c, "CREDITO", 1, D1);
        aguardarSaldo(c, D1, 501);
    }

    @Test
    void lancamentoRetroativoAtualizaAcumuladoEInvalidaCache() throws Exception {
        String c = novoComerciante();
        publicar(UUID.randomUUID(), c, "CREDITO", 100, D2);
        aguardarSaldo(c, D2, 100);
        mvc.perform(get("/consolidado/{data}", D2).with(token(c)))
                .andExpect(jsonPath("$.saldoAcumulado").value(100));

        publicar(UUID.randomUUID(), c, "CREDITO", 50, D1);
        aguardarSaldo(c, D2, 150);

        await().atMost(ESPERA).untilAsserted(() ->
                mvc.perform(get("/consolidado/{data}", D2).with(token(c)))
                        .andExpect(jsonPath("$.saldoDia").value(100))
                        .andExpect(jsonPath("$.saldoAcumulado").value(150)));
    }

    @Test
    void diaSemMovimentoMantemAcumulado() {
        String c = novoComerciante();
        publicar(UUID.randomUUID(), c, "CREDITO", 100, D1);
        aguardarSaldo(c, D1, 100);
        SaldoDiario saldo = repositorio.saldoEm(c, D1.plusDays(5));
        assertThat(saldo.saldoDia()).isZero();
        assertThat(saldo.saldoAcumulado()).isEqualTo(100);
    }

    @Test
    void periodoTrazAcumuladoCorreto() throws Exception {
        String c = novoComerciante();
        publicar(UUID.randomUUID(), c, "CREDITO", 100, D1);
        publicar(UUID.randomUUID(), c, "DEBITO", 30, D2);
        aguardarSaldo(c, D2, 70);
        mvc.perform(get("/consolidado").param("de", D2.toString()).param("ate", D2.toString()).with(token(c)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].saldoAcumulado").value(70));
    }

    @Test
    void comercianteNaoVeDadosDeOutro() throws Exception {
        String c = novoComerciante();
        publicar(UUID.randomUUID(), c, "CREDITO", 100, D1);
        aguardarSaldo(c, D1, 100);
        mvc.perform(get("/consolidado/{data}", D1).with(token(novoComerciante())))
                .andExpect(jsonPath("$.saldoAcumulado").value(0));
    }

    @Test
    void exigeToken() throws Exception {
        mvc.perform(get("/consolidado/{data}", D1)).andExpect(status().isUnauthorized());
    }

    @Test
    void eventoInvalidoVaiParaADlq() {
        String marcador = UUID.randomUUID().toString();
        publicarBruto("{\"invalido\":\"" + marcador + "\"}");
        await().atMost(ESPERA).untilAsserted(() -> {
            Message mensagem = rabbit.receive(RabbitConfig.DLQ, 500);
            assertThat(mensagem).isNotNull();
            assertThat(new String(mensagem.getBody(), StandardCharsets.UTF_8)).contains(marcador);
        });
    }
}
