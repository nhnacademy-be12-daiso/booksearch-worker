package com.nhnacademy.bookssearchworker.search.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscountPolicyDto {
    private DiscountTargetType targetType; // GLOBAL / CATEGORY / PUBLISHER
    private Long targetId;                 // GLOBAL이면 null
    private DiscountType discountType;     // PERCENTAGE / FIXED_AMOUNT
    private Double discountValue;          // 3.0, 10.0, 1000.0
}
