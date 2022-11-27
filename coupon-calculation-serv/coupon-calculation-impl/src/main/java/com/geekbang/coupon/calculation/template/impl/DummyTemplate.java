package com.geekbang.coupon.calculation.template.impl;

import com.geekbang.coupon.calculation.api.beans.ShoppingCart;
import com.geekbang.coupon.calculation.template.AbstractRuleTemplate;
import com.geekbang.coupon.calculation.template.RuleTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 空实现
 */

@Slf4j
@Component
public class DummyTemplate extends AbstractRuleTemplate implements RuleTemplate {

    @Override
    public ShoppingCart calculate(ShoppingCart order) {
        // 获取订单总价
        Long orderTotalPrice = getTotalPrice(order.getProducts());
        order.setCost(orderTotalPrice);
        return order;
    }

    @Override
    protected Long calculateNewPrice(Long orderTotalAmount, Long shopTotalAmount, Long quota) {
        return orderTotalAmount;
    }
}
