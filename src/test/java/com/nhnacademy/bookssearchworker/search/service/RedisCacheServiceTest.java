
package com.nhnacademy.bookssearchworker.search.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@SpringJUnitConfig(classes = RedisCacheServiceTest.Config.class)
class RedisCacheServiceTest {

    @Configuration
    @Import(RedisCacheService.class)
    static class Config {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
    }

    @Autowired
    RedisCacheService redisCacheService;

    @MockitoBean
    StringRedisTemplate redisTemplate;

    private final Map<String, String> store = new HashMap<>();
    private final Map<String, Duration> ttlStore = new HashMap<>();

    @BeforeEach
    void setUpValueOpsStub() {
        ValueOperations<String, String> valueOps = valueOpsStub(store, ttlStore);
        given(redisTemplate.opsForValue()).willReturn(valueOps);
    }

    @Test
    @DisplayName("save: 직렬화된 JSON을 Redis에 저장(set)한다")
    void save_writesJsonToRedis() {
        Dummy value = new Dummy("a", 1);

        redisCacheService.save("k1", value, Duration.ofMinutes(5));

        assertThat(store).containsKey("k1");
        assertThat(store.get("k1")).contains("\"name\":\"a\"").contains("\"count\":1");
        assertThat(ttlStore.get("k1")).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("get: Redis에서 가져온 JSON을 역직렬화해서 반환한다")
    void get_readsAndDeserializes() {
        store.put("k1", "{\"name\":\"b\",\"count\":2}");

        Dummy result = redisCacheService.get("k1", Dummy.class);

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("b");
        assertThat(result.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("get: 역직렬화 실패 시 null 반환(예외 삼킴)한다")
    void get_deserializeFail_returnsNull() {
        store.put("k1", "not-json");

        Dummy result = redisCacheService.get("k1", Dummy.class);

        assertThat(result).isNull();
    }

    /**
     * MockitoBean을 쓰되, nested mock(ValueOperations)을 만들지 않기 위해
     * Proxy 기반의 간단 스텁을 사용한다.
     */
    @SuppressWarnings("unchecked")
    private static ValueOperations<String, String> valueOpsStub(Map<String, String> store, Map<String, Duration> ttlStore) {
        return (ValueOperations<String, String>) Proxy.newProxyInstance(
                RedisCacheServiceTest.class.getClassLoader(),
                new Class[]{ValueOperations.class},
                (proxy, method, args) -> {
                    String name = method.getName();

                    if (name.equals("get") && args != null && args.length == 1) {
                        return store.get(String.valueOf(args[0]));
                    }

                    if (name.equals("set") && args != null && args.length == 3 && args[2] instanceof Duration d) {
                        String key = String.valueOf(args[0]);
                        store.put(key, String.valueOf(args[1]));
                        ttlStore.put(key, d);
                        return null;
                    }

                    throw new UnsupportedOperationException("Unsupported ValueOperations method: " + method);
                }
        );
    }

    public record Dummy(String name, int count) {}
}
