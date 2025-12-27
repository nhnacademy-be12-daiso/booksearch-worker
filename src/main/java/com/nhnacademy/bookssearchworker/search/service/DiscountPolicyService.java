// java
package com.nhnacademy.bookssearchworker.search.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.bookssearchworker.search.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscountPolicyService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String PREFIX = "discount:policy";

    private static String globalKey() {
        return PREFIX + ":GLOBAL";
    }
    private static String categoryKey(long categoryId) {
        return PREFIX + ":CATEGORY:" + categoryId;
    }
    private static String publisherKey(long publisherId) {
        return PREFIX + ":PUBLISHER:" + publisherId;
    }

    /**
     * BookResponseDto 목록에 할인정책을 적용해서
     * discountedPrice / appliedDiscounts를 채워준다.
     */
    public void applyDiscounts(List<BookResponseDto> books) {
        if (books == null || books.isEmpty()) return;

        // 1) 필요한 key들 수집 (GLOBAL + categoryId + publisherId)
        Set<String> keys = new LinkedHashSet<>();
        keys.add(globalKey());

        // categoryId들 (BookResponseDto::getCategoryId 사용)
        Set<Long> categoryIds = books.stream()
                .map(BookResponseDto::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (Long cid : categoryIds) keys.add(categoryKey(cid));

        // publisherId들
        Set<Long> publisherIds = books.stream()
                .map(BookResponseDto::getPublisherId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (Long pid : publisherIds) keys.add(publisherKey(pid));

        List<String> keyList = new ArrayList<>(keys);

        // 2) Redis multiGet
        List<String> jsonList = redisTemplate.opsForValue().multiGet(keyList);
        if (jsonList == null) jsonList = Collections.emptyList();

        // 3) key -> policy 매핑
        Map<String, DiscountPolicyDto> policyByKey = new HashMap<>();
        for (int i = 0; i < keyList.size(); i++) {
            String key = keyList.get(i);
            String json = (i < jsonList.size()) ? jsonList.get(i) : null;
            if (json == null || json.isBlank()) continue;

            try {
                DiscountPolicyDto policy = objectMapper.readValue(json, DiscountPolicyDto.class);
                policyByKey.put(key, policy);
            } catch (Exception e) {
                log.warn("Discount policy deserialize fail. key={}, json={}", key, json, e);
            }
        }

        // 4) 각 책에 정책 모아서 적용
        for (BookResponseDto book : books) {
            applyDiscountToSingleBook(book, policyByKey);
        }
    }

    /**
     * 단일 도서에 할인정책 적용 (categoryId / publisherId 기준)
     * - 퍼센트: 순차 적용, 각 단계는 floor
     * - 고정금액: 합산 후 한 번에 차감
     */
    private void applyDiscountToSingleBook(BookResponseDto book, Map<String, DiscountPolicyDto> policyByKey) {
        int basePrice = book.getPrice();

        // 정책 적용 순서: GLOBAL -> CATEGORY -> PUBLISHER
        List<DiscountPolicyDto> policies = new ArrayList<>();
        DiscountPolicyDto global = policyByKey.get(globalKey());
        if (global != null) policies.add(global);

        Long cid = book.getCategoryId();
        if (cid != null) {
            DiscountPolicyDto p = policyByKey.get(categoryKey(cid));
            if (p != null) policies.add(p);
        }

        Long pid = book.getPublisherId();
        if (pid != null) {
            DiscountPolicyDto p = policyByKey.get(publisherKey(pid));
            if (p != null) policies.add(p);
        }

        // 분리: 퍼센트 정책 vs 고정금액 정책
        List<DiscountPolicyDto> percentagePolicies = new ArrayList<>();
        List<DiscountPolicyDto> fixedPolicies = new ArrayList<>();
        for (DiscountPolicyDto p : policies) {
            if (p.getDiscountType() == DiscountType.PERCENTAGE) {
                percentagePolicies.add(p);
            } else if (p.getDiscountType() == DiscountType.FIXED_AMOUNT) {
                fixedPolicies.add(p);
            }
        }

        BigDecimal current = BigDecimal.valueOf(basePrice);

        // 변경: 퍼센트들을 합산해서 한 번에 적용 (할인액은 floor)
        double totalPercent = 0.0;
        for (DiscountPolicyDto p : percentagePolicies) {
            double pct = (p.getDiscountValue() == null) ? 0.0 : p.getDiscountValue();
            totalPercent += pct;
        }
        // 안전성: 총 퍼센트가 음수면 0, 100% 초과시 100으로 클램프(원하면 제거)
        if (totalPercent < 0.0) totalPercent = 0.0;
        if (totalPercent > 100.0) totalPercent = 100.0;

        if (totalPercent > 0.0) {
            BigDecimal discount = current.multiply(BigDecimal.valueOf(totalPercent)).divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
            long discountAmt = discount.setScale(0, RoundingMode.FLOOR).longValue();
            if (discountAmt > 0) {
                current = current.subtract(BigDecimal.valueOf(discountAmt));
                if (current.compareTo(BigDecimal.ZERO) <= 0) {
                    current = BigDecimal.ZERO;
                }
            }
        }

        // 고정 금액 할인 합산 후 적용 (각 값은 반올림)
        long totalFixed = 0L;
        for (DiscountPolicyDto p : fixedPolicies) {
            double val = (p.getDiscountValue() == null) ? 0.0 : p.getDiscountValue();
            int amt = (int) Math.round(val);
            if (amt > 0) totalFixed += amt;
        }
        if (totalFixed > 0) {
            current = current.subtract(BigDecimal.valueOf(totalFixed));
            if (current.compareTo(BigDecimal.ZERO) <= 0) current = BigDecimal.ZERO;
        }

        // 최종 금액을 Integer로 설정 (0 미만이면 0)
        int finalPrice;
        if (current.compareTo(BigDecimal.ZERO) <= 0) {
            finalPrice = 0;
        } else {
            long lv = current.longValue();
            finalPrice = (lv > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) lv;
        }

        book.setDiscountedPrice(finalPrice);
    }

}
