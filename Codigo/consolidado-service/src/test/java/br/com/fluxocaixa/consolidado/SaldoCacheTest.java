package br.com.fluxocaixa.consolidado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.fluxocaixa.consolidado.dominio.SaldoDiario;
import br.com.fluxocaixa.consolidado.infra.SaldoCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class SaldoCacheTest {

    private static final LocalDate DATA = LocalDate.of(2026, 11, 5);

    @Test
    @SuppressWarnings("unchecked")
    void comRedisForaDoArConsultaOBancoEPausaAsTentativas() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valores = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valores);
        when(valores.get(anyString())).thenThrow(new RedisConnectionFailureException("fora do ar"));
        SaldoCache cache = new SaldoCache(redis, new ObjectMapper().findAndRegisterModules(), 300);
        SaldoDiario doBanco = SaldoDiario.de(DATA, 100, 40, 60);

        assertThat(cache.obter("c", DATA, () -> doBanco)).isEqualTo(doBanco);
        assertThat(cache.obter("c", DATA, () -> doBanco)).isEqualTo(doBanco);

        verify(valores, times(1)).get(anyString());
    }
}
