package br.com.fluxocaixa.lancamentos.infra;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class IdempotenciaRepository {

    public record Registro(String hashConteudo, UUID lancamentoId) {
    }

    private final JdbcClient jdbc;

    public IdempotenciaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Reserva a chave. Em requisições concorrentes, o Postgres faz a segunda aguardar o commit da
     * primeira; por isso, um retorno false garante que o registro original já está visível.
     */
    public boolean reservar(String comercianteId, String chave, String hash, UUID lancamentoId) {
        return jdbc.sql("""
                INSERT INTO idempotencia (comerciante_id, chave, hash_conteudo, lancamento_id)
                VALUES (?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """)
                .params(comercianteId, chave, hash, lancamentoId)
                .update() == 1;
    }

    public Registro buscar(String comercianteId, String chave) {
        return jdbc.sql("""
                SELECT hash_conteudo, lancamento_id FROM idempotencia
                WHERE comerciante_id = ? AND chave = ?
                """)
                .params(comercianteId, chave)
                .query((rs, n) -> new Registro(rs.getString(1), rs.getObject(2, UUID.class)))
                .single();
    }
}
