package com.geekbang.coupon.customer.api.beans;

import com.geekbang.coupon.template.api.beans.CouponTemplateInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RequestCoupon {

    @NotNull
    private Long userId;

    @NotNull
    private Long couponTemplateId;

    private CouponTemplateInfo templateSDK;

    private String trafficVersion;
}
