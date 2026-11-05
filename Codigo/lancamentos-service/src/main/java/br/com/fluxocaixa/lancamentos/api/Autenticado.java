package br.com.fluxocaixa.lancamentos.api;

import java.time.DateTimeException;
import java.time.ZoneId;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

/** RN09: o comerciante vem sempre do token, nunca do corpo da requisição. */
final class Autenticado {

    private static final ZoneId FUSO_PADRAO = ZoneId.of("America/Sao_Paulo");

    private Autenticado() {
    }

    static String comerciante(Jwt jwt) {
        String id = jwt.getClaimAsString("comercianteId");
        if (id == null || id.isBlank()) {
            throw new AccessDeniedException("Token sem comercianteId.");
        }
        return id;
    }

    static ZoneId fuso(Jwt jwt) {
        String fuso = jwt.getClaimAsString("fuso");
        if (fuso == null) {
            return FUSO_PADRAO;
        }
        try {
            return ZoneId.of(fuso);
        } catch (DateTimeException e) {
            return FUSO_PADRAO;
        }
    }
}
