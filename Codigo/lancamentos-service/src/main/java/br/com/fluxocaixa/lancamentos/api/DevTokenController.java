package br.com.fluxocaixa.lancamentos.api;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Emissor de tokens somente para execução local. O bean não existe fora do perfil "local". */
@RestController
@Profile("local")
public class DevTokenController {

    private static final String ESCOPOS_PADRAO = "lancamentos:write lancamentos:read consolidado:read";

    private final NimbusJwtEncoder encoder;

    public DevTokenController(@Value("${app.security.chave-local}") String chave) {
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(chave.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
    }

    @PostMapping("/dev/token")
    public Map<String, String> emitir(@RequestParam(defaultValue = "comerciante-demo") String comercianteId,
                                      @RequestParam(defaultValue = ESCOPOS_PADRAO) String escopos) {
        Instant agora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(comercianteId)
                .issuedAt(agora)
                .expiresAt(agora.plusSeconds(3600))
                .claim("comercianteId", comercianteId)
                .claim("scope", escopos)
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return Map.of("access_token", token);
    }
}
