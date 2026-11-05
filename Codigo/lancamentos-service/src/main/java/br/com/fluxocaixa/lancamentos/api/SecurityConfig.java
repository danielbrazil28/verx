package br.com.fluxocaixa.lancamentos.api;

import jakarta.servlet.DispatcherType;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain seguranca(HttpSecurity http) throws Exception {
        return http
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        // Permite que a resposta de erro chegue ao cliente com o status original.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/prometheus", "/dev/token").permitAll()
                        .requestMatchers(HttpMethod.POST, "/lancamentos/**").hasAuthority("SCOPE_lancamentos:write")
                        .requestMatchers(HttpMethod.GET, "/lancamentos/**").hasAuthority("SCOPE_lancamentos:read")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${app.security.issuer-uri}") String issuer,
                          @Value("${app.security.chave-local}") String chaveLocal) {
        if (!issuer.isBlank()) {
            return JwtDecoders.fromIssuerLocation(issuer);
        }
        SecretKeySpec chave = new SecretKeySpec(chaveLocal.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(chave).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    Clock relogio() {
        return Clock.systemUTC();
    }
}
