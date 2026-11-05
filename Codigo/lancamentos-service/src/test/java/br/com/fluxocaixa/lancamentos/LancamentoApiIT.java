package br.com.fluxocaixa.lancamentos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fluxocaixa.lancamentos.infra.RabbitConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class LancamentoApiIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    private static final String CREDITO = "{\"tipo\":\"CREDITO\",\"valorCentavos\":15000}";

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired RabbitTemplate rabbit;
    @Autowired ObjectMapper json;

    private static RequestPostProcessor token(String comerciante, String... escopos) {
        SimpleGrantedAuthority[] autoridades = java.util.Arrays.stream(escopos)
                .map(e -> new SimpleGrantedAuthority("SCOPE_" + e))
                .toArray(SimpleGrantedAuthority[]::new);
        return jwt().jwt(j -> j.claim("comercianteId", comerciante)).authorities(autoridades);
    }

    private ResultActions registrar(String comerciante, String chave, String corpo) throws Exception {
        return mvc.perform(post("/lancamentos")
                .with(token(comerciante, "lancamentos:write"))
                .header("Idempotency-Key", chave)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));
    }

    private String idDe(ResultActions resposta) throws Exception {
        return json.readTree(resposta.andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private static String novoComerciante() {
        return "c-" + UUID.randomUUID();
    }

    @Test
    void registraEGravaEventoNoOutbox() throws Exception {
        String id = idDe(registrar(novoComerciante(), "k1", CREDITO).andExpect(status().isCreated()));
        long eventos = jdbc.sql("SELECT count(*) FROM outbox WHERE payload LIKE ?").param("%" + id + "%")
                .query(Long.class).single();
        assertThat(eventos).isEqualTo(1);
    }

    @Test
    void repeticaoComMesmaChaveNaoDuplica() throws Exception {
        String comerciante = novoComerciante();
        String primeiro = idDe(registrar(comerciante, "k1", CREDITO).andExpect(status().isCreated()));
        String segundo = idDe(registrar(comerciante, "k1", CREDITO).andExpect(status().isOk()));
        assertThat(segundo).isEqualTo(primeiro);
        long total = jdbc.sql("SELECT count(*) FROM lancamento WHERE comerciante_id = ?").param(comerciante)
                .query(Long.class).single();
        assertThat(total).isEqualTo(1);
    }

    @Test
    void mesmaChaveComOutroConteudoRetorna409() throws Exception {
        String comerciante = novoComerciante();
        registrar(comerciante, "k1", CREDITO).andExpect(status().isCreated());
        registrar(comerciante, "k1", "{\"tipo\":\"DEBITO\",\"valorCentavos\":1}").andExpect(status().isConflict());
    }

    @Test
    void rejeitaDadosInvalidos() throws Exception {
        registrar(novoComerciante(), "k1", "{\"tipo\":\"CREDITO\",\"valorCentavos\":-1}").andExpect(status().isBadRequest());
        registrar(novoComerciante(), "k1", "{\"tipo\":\"CREDITO\",\"valorCentavos\":1,\"dataCompetencia\":\"2999-01-01\"}")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void estornaUmaUnicaVez() throws Exception {
        String comerciante = novoComerciante();
        String id = idDe(registrar(comerciante, "k1", CREDITO));
        mvc.perform(post("/lancamentos/{id}/estorno", id).with(token(comerciante, "lancamentos:write"))
                        .header("Idempotency-Key", "e1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("DEBITO"))
                .andExpect(jsonPath("$.estornoDe").value(id));
        mvc.perform(post("/lancamentos/{id}/estorno", id).with(token(comerciante, "lancamentos:write"))
                        .header("Idempotency-Key", "e1"))
                .andExpect(status().isOk());
        mvc.perform(post("/lancamentos/{id}/estorno", id).with(token(comerciante, "lancamentos:write"))
                        .header("Idempotency-Key", "e2"))
                .andExpect(status().isConflict());
    }

    @Test
    void comercianteNaoAcessaDadosDeOutro() throws Exception {
        String id = idDe(registrar(novoComerciante(), "k1", CREDITO));
        mvc.perform(get("/lancamentos/{id}", id).with(token(novoComerciante(), "lancamentos:read")))
                .andExpect(status().isNotFound());
    }

    @Test
    void exigeTokenEEscopo() throws Exception {
        mvc.perform(get("/lancamentos/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(get("/lancamentos/{id}", UUID.randomUUID()).with(token("c", "consolidado:read")))
                .andExpect(status().isForbidden());
    }

    @Test
    void publicaEventoNoBroker() throws Exception {
        String comerciante = novoComerciante();
        String id = idDe(registrar(comerciante, "k1", CREDITO));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Message mensagem = rabbit.receive(RabbitConfig.FILA, 500);
            assertThat(mensagem).isNotNull();
            JsonNode evento = json.readTree(mensagem.getBody());
            assertThat(evento.get("lancamentoId").asText()).isEqualTo(id);
            assertThat(evento.get("eventType").asText()).isEqualTo("LancamentoRegistrado");
            assertThat(evento.get("comercianteId").asText()).isEqualTo(comerciante);
        });
    }
}
