package br.com.fluxocaixa.lancamentos.infra;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxRepository {

    public record Pendente(UUID eventId, String tipo, String payload) {
    }

    private final JdbcClient jdbc;

    public OutboxRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void inserir(UUID eventId, String tipo, String payload) {
        jdbc.sql("INSERT INTO outbox (event_id, tipo, payload) VALUES (?, ?, ?)")
                .params(eventId, tipo, payload)
                .update();
    }

    /** SKIP LOCKED permite mais de uma réplica publicando sem bloqueio mútuo. */
    public List<Pendente> travarPendentes(int limite) {
        return jdbc.sql("""
                SELECT event_id, tipo, payload FROM outbox
                WHERE publicado_em IS NULL
                ORDER BY criado_em
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """)
                .param(limite)
                .query((rs, n) -> new Pendente(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)))
                .list();
    }

    public void marcarPublicado(UUID eventId) {
        jdbc.sql("UPDATE outbox SET publicado_em = now() WHERE event_id = ?").param(eventId).update();
    }

    public long contarPendentes() {
        return jdbc.sql("SELECT count(*) FROM outbox WHERE publicado_em IS NULL").query(Long.class).single();
    }

    public int limparPublicados(int dias) {
        return jdbc.sql("DELETE FROM outbox WHERE publicado_em < now() - make_interval(days => ?)")
                .param(dias)
                .update();
    }
}
