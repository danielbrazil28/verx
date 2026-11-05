package br.com.fluxocaixa.consolidado.aplicacao;

import br.com.fluxocaixa.consolidado.dominio.SaldoDiario;
import br.com.fluxocaixa.consolidado.infra.ConsolidadoRepository;
import br.com.fluxocaixa.consolidado.infra.SaldoCache;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ConsultaSaldoService {

    private final ConsolidadoRepository repositorio;
    private final SaldoCache cache;

    public ConsultaSaldoService(ConsolidadoRepository repositorio, SaldoCache cache) {
        this.repositorio = repositorio;
        this.cache = cache;
    }

    public SaldoDiario saldoEm(String comercianteId, LocalDate data) {
        return cache.obter(comercianteId, data, () -> repositorio.saldoEm(comercianteId, data));
    }

    public List<SaldoDiario> saldosNoPeriodo(String comercianteId, LocalDate de, LocalDate ate) {
        return repositorio.saldosNoPeriodo(comercianteId, de, ate);
    }
}
