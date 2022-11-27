package com.geekbang.coupon.calculation.template;

import com.geekbang.coupon.calculation.api.beans.ShoppingCart;
import com.geekbang.coupon.calculation.template.impl.*;
import com.geekbang.coupon.template.api.beans.CouponTemplateInfo;
import com.geekbang.coupon.template.api.enums.CouponType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collections;

@Slf4j
@Component
public class CouponTemplateFactory {

    @Autowired
    private MoneyOffTemplate moneyOffTemplate;

    @Autowired
    private DiscountTemplate discountTemplate;

    @Autowired
    private RandomReductionTemplate randomReductionTemplate;

    @Autowired
    private LonelyNightTemplate lonelyNightTemplate;

    @Autowired
    private DummyTemplate dummyTemplate;

    public RuleTemplate getTemplate(ShoppingCart order){
        // 不使用优惠券
        if (CollectionUtils.isEmpty(order.getCouponInfos())){
            // dummy模板直接返回原价，不进行优惠计算
            return dummyTemplate;
        }

        // 获取优惠券的类别
        // 目前每个订单只支持单张优惠券
        CouponTemplateInfo template = order.getCouponInfos().get(0).getTemplate();
        CouponType category = CouponType.convert(template.getType());
        switch (category){
            // 订单满减券
            case MONEY_OFF:
                return moneyOffTemplate;
            case RANDOM_DISCOUNT:
                return randomReductionTemplate;
            case LONELY_NIGHT_MONEY_OFF:
                return lonelyNightTemplate;
            case DISCOUNT:
                return discountTemplate;
            default:
                return dummyTemplate;
        }
    }
}
