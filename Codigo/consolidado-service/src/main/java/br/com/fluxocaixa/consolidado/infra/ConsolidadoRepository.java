package br.com.fluxocaixa.consolidado.infra;

import br.com.fluxocaixa.consolidado.dominio.EventoLancamento;
import br.com.fluxocaixa.consolidado.dominio.SaldoDiario;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ConsolidadoRepository {

    private final JdbcClient jdbc;

    public ConsolidadoRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public boolean registrarEvento(UUID eventId) {
        return jdbc.sql("INSERT INTO eventos_processados (event_id) VALUES (?) ON CONFLICT DO NOTHING")
                .param(eventId)
                .update() == 1;
    }

    public void somar(EventoLancamento e) {
        long credito = e.ehCredito() ? e.valorCentavos() : 0;
        long debito = e.ehCredito() ? 0 : e.valorCentavos();
        jdbc.sql("""
                INSERT INTO consolidado_diario (comerciante_id, data, total_credito_centavos, total_debito_centavos)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (comerciante_id, data) DO UPDATE SET
                    total_credito_centavos = consolidado_diario.total_credito_centavos + EXCLUDED.total_credito_centavos,
                    total_debito_centavos  = consolidado_diario.total_debito_centavos + EXCLUDED.total_debito_centavos,
                    atualizado_em = now()
                """)
                .params(e.comercianteId(), Date.valueOf(e.dataCompetencia()), credito, debito)
                .update();
    }

    /** Dia sem movimento retorna totais zerados, mas mantém o acumulado até a data. */
    public SaldoDiario saldoEm(String comercianteId, LocalDate data) {
        Date dia = Date.valueOf(data);
        return jdbc.sql("""
                SELECT COALESCE(SUM(total_credito_centavos) FILTER (WHERE data = ?), 0)::bigint,
                       COALESCE(SUM(total_debito_centavos)  FILTER (WHERE data = ?), 0)::bigint,
                       COALESCE(SUM(total_credito_centavos - total_debito_centavos), 0)::bigint
                FROM consolidado_diario
                WHERE comerciante_id = ? AND data <= ?
                """)
                .params(dia, dia, comercianteId, dia)
                .query((rs, n) -> SaldoDiario.de(data, rs.getLong(1), rs.getLong(2), rs.getLong(3)))
                .single();
    }

    /** Retorna apenas os dias com movimento no período. */
    public List<SaldoDiario> saldosNoPeriodo(String comercianteId, LocalDate de, LocalDate ate) {
        return jdbc.sql("""
                SELECT data, c, d, acumulado FROM (
                    SELECT data,
                           total_credito_centavos AS c,
                           total_debito_centavos AS d,
                           SUM(total_credito_centavos - total_debito_centavos) OVER (ORDER BY data)::bigint AS acumulado
                    FROM consolidado_diario
                    WHERE comerciante_id = ? AND data <= ?
                ) t
                WHERE data >= ?
                ORDER BY data
                """)
                .params(comercianteId, Date.valueOf(ate), Date.valueOf(de))
                .query((rs, n) -> SaldoDiario.de(rs.getObject(1, LocalDate.class), rs.getLong(2), rs.getLong(3), rs.getLong(4)))
                .list();
    }
}
