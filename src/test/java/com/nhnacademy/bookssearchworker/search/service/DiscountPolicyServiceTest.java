
package com.nhnacademy.bookssearchworker.search.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.bookssearchworker.search.dto.BookResponseDto;
import com.nhnacademy.bookssearchworker.search.dto.DiscountPolicyDto;
import com.nhnacademy.bookssearchworker.search.dto.DiscountTargetType;
import com.nhnacademy.bookssearchworker.search.dto.DiscountType;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@SpringJUnitConfig(classes = DiscountPolicyServiceTest.Config.class)
class DiscountPolicyServiceTest {

    @Configuration
    @Import(DiscountPolicyService.class)
    static class Config {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
    }

    @Autowired
    DiscountPolicyService discountPolicyService;

    @MockitoBean
    StringRedisTemplate redisTemplate;

    @Autowired
    ObjectMapper objectMapper;

    private final Map<String, String> store = new HashMap<>();

    @BeforeEach
    void setUpValueOpsStub() {
        ValueOperations<String, String> valueOps = valueOpsStub(store);
        given(redisTemplate.opsForValue()).willReturn(valueOps);
    }

    @Test
    @DisplayName("GLOBAL(%) + CATEGORY(%) + PUBLISHER(정액) 정책을 모두 적용해 discountedPrice를 채운다")
    void applyDiscounts_combinesPolicies() throws Exception {
        long categoryId = 10L;
        long publisherId = 20L;

        BookResponseDto book = BookResponseDto.builder()
                .isbn("111")
                .price(20000)
                .categoryId(categoryId)
                .publisherId(publisherId)
                .build();

        putPolicy("discount:policy:GLOBAL",
                DiscountPolicyDto.builder()
                        .targetType(DiscountTargetType.GLOBAL)
                        .discountType(DiscountType.PERCENTAGE)
                        .discountValue(10.0)
                        .build());

        putPolicy("discount:policy:CATEGORY:" + categoryId,
                DiscountPolicyDto.builder()
                        .targetType(DiscountTargetType.CATEGORY)
                        .targetId(categoryId)
                        .discountType(DiscountType.PERCENTAGE)
                        .discountValue(5.0)
                        .build());

        putPolicy("discount:policy:PUBLISHER:" + publisherId,
                DiscountPolicyDto.builder()
                        .targetType(DiscountTargetType.PUBLISHER)
                        .targetId(publisherId)
                        .discountType(DiscountType.FIXED_AMOUNT)
                        .discountValue(1000.0)
                        .build());

        discountPolicyService.applyDiscounts(List.of(book));

        assertThat(book.getDiscountedPrice()).isEqualTo(16000);

        then(redisTemplate).should().opsForValue();
    }

    @Test
    @DisplayName("정액 할인 합이 가격보다 크면 discountedPrice는 0으로 처리한다")
    void applyDiscounts_fixedAmountOverPrice_resultsZero() throws Exception {
        BookResponseDto book = BookResponseDto.builder()
                .isbn("111")
                .price(500)
                .build();

        putPolicy("discount:policy:GLOBAL",
                DiscountPolicyDto.builder()
                        .targetType(DiscountTargetType.GLOBAL)
                        .discountType(DiscountType.FIXED_AMOUNT)
                        .discountValue(1000.0)
                        .build());

        discountPolicyService.applyDiscounts(List.of(book));

        assertThat(book.getDiscountedPrice()).isEqualTo(0);
    }

    @Test
    @DisplayName("Redis에 잘못된 JSON이 섞여 있어도 예외 없이 무시하고 진행한다")
    void applyDiscounts_invalidJson_isIgnored() throws Exception {
        long categoryId = 10L;

        BookResponseDto book = BookResponseDto.builder()
                .isbn("111")
                .price(10000)
                .categoryId(categoryId)
                .build();

        // global은 정상
        putPolicy("discount:policy:GLOBAL",
                DiscountPolicyDto.builder()
                        .targetType(DiscountTargetType.GLOBAL)
                        .discountType(DiscountType.PERCENTAGE)
                        .discountValue(10.0)
                        .build());

        // category는 깨진 JSON
        store.put("discount:policy:CATEGORY:" + categoryId, "{not-json");

        discountPolicyService.applyDiscounts(List.of(book));

        // global 10%만 적용: 10000 - 1000 = 9000
        assertThat(book.getDiscountedPrice()).isEqualTo(9000);
    }

    private void putPolicy(String key, DiscountPolicyDto dto) throws Exception {
        store.put(key, objectMapper.writeValueAsString(dto));
    }

    @SuppressWarnings("unchecked")
    private static ValueOperations<String, String> valueOpsStub(Map<String, String> store) {
        return (ValueOperations<String, String>) Proxy.newProxyInstance(
                DiscountPolicyServiceTest.class.getClassLoader(),
                new Class[]{ValueOperations.class},
                (proxy, method, args) -> {
                    String name = method.getName();

                    if (name.equals("multiGet") && args != null && args.length == 1 && args[0] instanceof List<?> keys) {
                        return keys.stream().map(k -> store.get(String.valueOf(k))).toList();
                    }

                    throw new UnsupportedOperationException("Unsupported ValueOperations method: " + method);
                }
        );
    }
}
