package br.com.fluxocaixa.consolidado.aplicacao;

import br.com.fluxocaixa.consolidado.dominio.EventoLancamento;
import br.com.fluxocaixa.consolidado.infra.ConsolidadoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsolidacaoService {

    private final ConsolidadoRepository repositorio;

    public ConsolidacaoService(ConsolidadoRepository repositorio) {
        this.repositorio = repositorio;
    }

    /** Idempotente (ADR-0004). Retorna false quando o evento já tinha sido aplicado. */
    @Transactional
    public boolean aplicar(EventoLancamento evento) {
        evento.validar();
        if (!repositorio.registrarEvento(evento.eventId())) {
            return false;
        }
        repositorio.somar(evento);
        return true;
    }
}
