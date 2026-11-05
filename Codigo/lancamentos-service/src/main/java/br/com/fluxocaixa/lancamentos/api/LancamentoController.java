package br.com.fluxocaixa.lancamentos.api;

import br.com.fluxocaixa.lancamentos.aplicacao.LancamentoService;
import br.com.fluxocaixa.lancamentos.dominio.Lancamento;
import br.com.fluxocaixa.lancamentos.dominio.TipoLancamento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/lancamentos")
@Validated
public class LancamentoController {

    public record NovoLancamento(@NotNull TipoLancamento tipo, @NotNull @Positive Long valorCentavos,
                                 LocalDate dataCompetencia) {
    }

    public record LancamentoResposta(UUID id, TipoLancamento tipo, long valorCentavos,
                                     LocalDate dataCompetencia, UUID estornoDe) {
        static LancamentoResposta de(Lancamento l) {
            return new LancamentoResposta(l.id(), l.tipo(), l.valorCentavos(), l.dataCompetencia(), l.estornoDe());
        }
    }

    private final LancamentoService servico;

    public LancamentoController(LancamentoService servico) {
        this.servico = servico;
    }

    @PostMapping
    public ResponseEntity<LancamentoResposta> registrar(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") @Size(min = 1, max = 128) String chave,
            @Valid @RequestBody NovoLancamento corpo) {
        return responder(servico.registrar(Autenticado.comerciante(jwt), Autenticado.fuso(jwt), chave,
                corpo.tipo(), corpo.valorCentavos(), corpo.dataCompetencia()));
    }

    @PostMapping("/{id}/estorno")
    public ResponseEntity<LancamentoResposta> estornar(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") @Size(min = 1, max = 128) String chave,
            @PathVariable UUID id) {
        return responder(servico.estornar(Autenticado.comerciante(jwt), Autenticado.fuso(jwt), chave, id));
    }

    @GetMapping("/{id}")
    public LancamentoResposta buscar(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return LancamentoResposta.de(servico.buscar(Autenticado.comerciante(jwt), id));
    }

    @GetMapping
    public List<LancamentoResposta> listar(@AuthenticationPrincipal Jwt jwt,
                                           @RequestParam LocalDate de,
                                           @RequestParam LocalDate ate,
                                           @RequestParam(defaultValue = "0") @Min(0) int pagina,
                                           @RequestParam(defaultValue = "50") @Min(1) @Max(200) int tamanho) {
        return servico.listar(Autenticado.comerciante(jwt), de, ate, pagina, tamanho).stream()
                .map(LancamentoResposta::de)
                .toList();
    }

    private static ResponseEntity<LancamentoResposta> responder(LancamentoService.Resultado r) {
        LancamentoResposta corpo = LancamentoResposta.de(r.lancamento());
        return r.criado()
                ? ResponseEntity.created(URI.create("/lancamentos/" + corpo.id())).body(corpo)
                : ResponseEntity.ok(corpo);
    }
}
