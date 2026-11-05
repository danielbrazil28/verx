package br.com.fluxocaixa.lancamentos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** RNF01: sem broker (e, portanto, sem Consolidado), o registro continua funcionando. */
@SpringBootTest(properties = {"spring.rabbitmq.host=localhost", "spring.rabbitmq.port=1"})
@AutoConfigureMockMvc
@Testcontainers
class IsolamentoDoBrokerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;

    @Test
    void aceitaRegistroEMantemEventoPendente() throws Exception {
        mvc.perform(post("/lancamentos")
                        .with(jwt().jwt(j -> j.claim("comercianteId", "c1"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_lancamentos:write")))
                        .header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"CREDITO\",\"valorCentavos\":100}"))
                .andExpect(status().isCreated());
        long pendentes = jdbc.sql("SELECT count(*) FROM outbox WHERE publicado_em IS NULL").query(Long.class).single();
        assertThat(pendentes).isEqualTo(1);
    }
}
