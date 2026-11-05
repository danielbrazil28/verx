package br.com.fluxocaixa.consolidado.infra;

import br.com.fluxocaixa.consolidado.dominio.SaldoDiario;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Cache-aside versionado por comerciante (ADR-0005). Nenhuma falha do Redis chega ao chamador.
 * Depois de uma falha, o Redis é ignorado por alguns segundos para não somar o timeout a cada consulta.
 */
@Component
public class SaldoCache {

    private static final Logger log = LoggerFactory.getLogger(SaldoCache.class);
    private static final long PAUSA_APOS_FALHA_MS = 5_000;

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final Duration ttl;
    private volatile long ignorarAte = 0;

    public SaldoCache(StringRedisTemplate redis, ObjectMapper json,
                      @Value("${app.cache.ttl-segundos}") long ttlSegundos) {
        this.redis = redis;
        this.json = json;
        this.ttl = Duration.ofSeconds(ttlSegundos);
    }

    /**
     * A versão é lida antes de consultar o banco. Se um evento for aplicado durante a consulta, o
     * valor antigo fica gravado na versão antiga e não é mais encontrado.
     */
    public SaldoDiario obter(String comercianteId, LocalDate data, Supplier<SaldoDiario> carregar) {
        String versao = protegido(() -> Optional.ofNullable(redis.opsForValue().get(chaveVersao(comercianteId))).orElse("0"), null);
        if (versao == null) {
            return carregar.get();
        }
        String chave = "consolidado:" + comercianteId + ":" + versao + ":" + data;
        Optional<SaldoDiario> emCache = protegido(() -> Optional.ofNullable(redis.opsForValue().get(chave)).map(this::ler), Optional.empty());
        if (emCache.isPresent()) {
            return emCache.get();
        }
        SaldoDiario saldo = carregar.get();
        protegido(() -> {
            redis.opsForValue().set(chave, escrever(saldo), ttl);
            return true;
        }, false);
        return saldo;
    }

    /** Muda a versão: todas as datas do comerciante deixam de ser encontradas e expiram pelo TTL. */
    public void invalidar(String comercianteId) {
        protegido(() -> redis.opsForValue().increment(chaveVersao(comercianteId)), 0L);
    }

    private static String chaveVersao(String comercianteId) {
        return "consolidado:versao:" + comercianteId;
    }

    private <T> T protegido(Supplier<T> operacao, T seFalhar) {
        if (System.currentTimeMillis() < ignorarAte) {
            return seFalhar;
        }
        try {
            return operacao.get();
        } catch (RuntimeException e) {
            ignorarAte = System.currentTimeMillis() + PAUSA_APOS_FALHA_MS;
            log.warn("Cache indisponível; consultando o banco: {}", e.getMessage());
            return seFalhar;
        }
    }

    private SaldoDiario ler(String valor) {
        try {
            return json.readValue(valor, SaldoDiario.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Valor inválido no cache.", e);
        }
    }

    private String escrever(SaldoDiario saldo) {
        try {
            return json.writeValueAsString(saldo);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar o saldo.", e);
        }
    }
}
