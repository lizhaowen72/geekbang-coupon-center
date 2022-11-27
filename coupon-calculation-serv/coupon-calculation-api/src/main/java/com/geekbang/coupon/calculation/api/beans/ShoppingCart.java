package com.geekbang.coupon.calculation.api.beans;

import com.geekbang.coupon.template.api.beans.CouponInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShoppingCart {

    @NotEmpty
    private List<Product> products;

    private Long couponId;

    private long cost;

    private List<CouponInfo> couponInfos;

    @NotNull
    private Long userId;
}
