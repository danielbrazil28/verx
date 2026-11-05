package br.com.fluxocaixa.lancamentos.infra;

import br.com.fluxocaixa.lancamentos.dominio.Lancamento;
import br.com.fluxocaixa.lancamentos.dominio.TipoLancamento;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class LancamentoRepository {

    private final JdbcClient jdbc;

    public LancamentoRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Retorna false quando o original já foi estornado (índice único de RN06). */
    public boolean inserir(Lancamento l) {
        return jdbc.sql("""
                INSERT INTO lancamento (id, comerciante_id, tipo, valor_centavos, data_competencia, estorno_de)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """)
                .params(l.id(), l.comercianteId(), l.tipo().name(), l.valorCentavos(),
                        Date.valueOf(l.dataCompetencia()), l.estornoDe())
                .update() == 1;
    }

    public Optional<Lancamento> buscar(String comercianteId, UUID id) {
        return jdbc.sql("""
                SELECT id, comerciante_id, tipo, valor_centavos, data_competencia, estorno_de
                FROM lancamento WHERE comerciante_id = ? AND id = ?
                """)
                .params(comercianteId, id)
                .query(LancamentoRepository::mapear)
                .optional();
    }

    public List<Lancamento> listar(String comercianteId, LocalDate de, LocalDate ate, int pagina, int tamanho) {
        return jdbc.sql("""
                SELECT id, comerciante_id, tipo, valor_centavos, data_competencia, estorno_de
                FROM lancamento
                WHERE comerciante_id = ? AND data_competencia BETWEEN ? AND ?
                ORDER BY data_competencia, id
                LIMIT ? OFFSET ?
                """)
                .params(comercianteId, Date.valueOf(de), Date.valueOf(ate), tamanho, pagina * tamanho)
                .query(LancamentoRepository::mapear)
                .list();
    }

    private static Lancamento mapear(ResultSet rs, int linha) throws SQLException {
        return new Lancamento(
                rs.getObject("id", UUID.class),
                rs.getString("comerciante_id"),
                TipoLancamento.valueOf(rs.getString("tipo")),
                rs.getLong("valor_centavos"),
                rs.getObject("data_competencia", LocalDate.class),
                rs.getObject("estorno_de", UUID.class));
    }
}
