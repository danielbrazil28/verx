package br.com.fluxocaixa.consolidado.api;

import br.com.fluxocaixa.consolidado.aplicacao.ConsultaSaldoService;
import br.com.fluxocaixa.consolidado.dominio.SaldoDiario;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/consolidado")
public class ConsolidadoController {

    private static final long PERIODO_MAXIMO_DIAS = 366;

    private final ConsultaSaldoService consulta;

    public ConsolidadoController(ConsultaSaldoService consulta) {
        this.consulta = consulta;
    }

    @GetMapping("/{data}")
    public SaldoDiario saldoEm(@AuthenticationPrincipal Jwt jwt, @PathVariable LocalDate data) {
        return consulta.saldoEm(comerciante(jwt), data);
    }

    @GetMapping
    public List<SaldoDiario> periodo(@AuthenticationPrincipal Jwt jwt,
                                     @RequestParam LocalDate de, @RequestParam LocalDate ate) {
        if (ate.isBefore(de) || ChronoUnit.DAYS.between(de, ate) > PERIODO_MAXIMO_DIAS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Período inválido; máximo de 366 dias.");
        }
        return consulta.saldosNoPeriodo(comerciante(jwt), de, ate);
    }

    /** RN09: o comerciante vem sempre do token. */
    private static String comerciante(Jwt jwt) {
        String id = jwt.getClaimAsString("comercianteId");
        if (id == null || id.isBlank()) {
            throw new AccessDeniedException("Token sem comercianteId.");
        }
        return id;
    }
}
