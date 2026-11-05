package br.com.fluxocaixa.lancamentos.aplicacao;

import br.com.fluxocaixa.lancamentos.dominio.ConflitoException;
import br.com.fluxocaixa.lancamentos.dominio.EventoLancamento;
import br.com.fluxocaixa.lancamentos.dominio.Lancamento;
import br.com.fluxocaixa.lancamentos.dominio.NaoEncontradoException;
import br.com.fluxocaixa.lancamentos.dominio.TipoLancamento;
import br.com.fluxocaixa.lancamentos.infra.IdempotenciaRepository;
import br.com.fluxocaixa.lancamentos.infra.LancamentoRepository;
import br.com.fluxocaixa.lancamentos.infra.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LancamentoService {

    /** criado = false indica repetição idempotente: nada novo foi gravado. */
    public record Resultado(Lancamento lancamento, boolean criado) {
    }

    private final LancamentoRepository lancamentos;
    private final IdempotenciaRepository idempotencia;
    private final OutboxRepository outbox;
    private final ObjectMapper json;
    private final Clock relogio;

    public LancamentoService(LancamentoRepository lancamentos, IdempotenciaRepository idempotencia,
                             OutboxRepository outbox, ObjectMapper json, Clock relogio) {
        this.lancamentos = lancamentos;
        this.idempotencia = idempotencia;
        this.outbox = outbox;
        this.json = json;
        this.relogio = relogio;
    }

    /** Lançamento, chave de idempotência e evento são gravados na mesma transação (ADR-0002). */
    @Transactional
    public Resultado registrar(String comercianteId, ZoneId fuso, String chave, TipoLancamento tipo,
                               long valorCentavos, LocalDate dataCompetencia) {
        LocalDate hoje = LocalDate.now(relogio.withZone(fuso));
        Lancamento novo = Lancamento.novo(comercianteId, tipo, valorCentavos, dataCompetencia, hoje);
        // Com data omitida, o hash usa null: repetir a requisição em outro dia continua idempotente.
        String hash = HashConteudo.de("registro", tipo, valorCentavos, dataCompetencia);
        if (!idempotencia.reservar(comercianteId, chave, hash, novo.id())) {
            return repetir(comercianteId, chave, hash);
        }
        lancamentos.inserir(novo);
        gravarEvento(novo);
        return new Resultado(novo, true);
    }

    @Transactional
    public Resultado estornar(String comercianteId, ZoneId fuso, String chave, UUID lancamentoId) {
        Lancamento original = buscar(comercianteId, lancamentoId);
        Lancamento estorno = original.estornar(LocalDate.now(relogio.withZone(fuso)));
        String hash = HashConteudo.de("estorno", lancamentoId);
        if (!idempotencia.reservar(comercianteId, chave, hash, estorno.id())) {
            return repetir(comercianteId, chave, hash);
        }
        if (!lancamentos.inserir(estorno)) {
            throw new ConflitoException("Este lançamento já foi estornado.");
        }
        gravarEvento(estorno);
        return new Resultado(estorno, true);
    }

    @Transactional(readOnly = true)
    public Lancamento buscar(String comercianteId, UUID id) {
        return lancamentos.buscar(comercianteId, id)
                .orElseThrow(() -> new NaoEncontradoException("Lançamento não encontrado."));
    }

    @Transactional(readOnly = true)
    public List<Lancamento> listar(String comercianteId, LocalDate de, LocalDate ate, int pagina, int tamanho) {
        return lancamentos.listar(comercianteId, de, ate, pagina, tamanho);
    }

    private Resultado repetir(String comercianteId, String chave, String hash) {
        IdempotenciaRepository.Registro registro = idempotencia.buscar(comercianteId, chave);
        if (!registro.hashConteudo().equals(hash)) {
            throw new ConflitoException("A Idempotency-Key já foi usada com outro conteúdo.");
        }
        return new Resultado(buscar(comercianteId, registro.lancamentoId()), false);
    }

    private void gravarEvento(Lancamento lancamento) {
        EventoLancamento evento = EventoLancamento.de(lancamento, relogio.instant());
        try {
            outbox.inserir(evento.eventId(), evento.eventType(), json.writeValueAsString(evento));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar o evento.", e);
        }
    }
}
