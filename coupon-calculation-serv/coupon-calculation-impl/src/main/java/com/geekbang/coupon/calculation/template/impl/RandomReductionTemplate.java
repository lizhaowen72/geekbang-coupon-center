package com.geekbang.coupon.calculation.template.impl;

import com.geekbang.coupon.calculation.template.AbstractRuleTemplate;
import com.geekbang.coupon.calculation.template.RuleTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Random;

@Slf4j
@Component
public class RandomReductionTemplate extends AbstractRuleTemplate implements RuleTemplate {

    @Override
    protected Long calculateNewPrice(Long orderTotalAmount, Long shopTotalAmount, Long quota) {
        // 计算使用优惠券之后的价格
        Long maxBenefit = Math.min(shopTotalAmount, quota);
        int reductionAmount = new Random().nextInt(maxBenefit.intValue());
        Long newCost = orderTotalAmount - reductionAmount;
        log.debug("original price={},new price={}", orderTotalAmount, newCost);
        return newCost;
    }
}
