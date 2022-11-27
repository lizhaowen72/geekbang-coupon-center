package com.geekbang.coupon.calculation.controller.service;

import com.alibaba.fastjson.JSON;
import com.geekbang.coupon.calculation.api.beans.ShoppingCart;
import com.geekbang.coupon.calculation.api.beans.SimulationOrder;
import com.geekbang.coupon.calculation.api.beans.SimulationResponse;
import com.geekbang.coupon.calculation.controller.service.intf.CouponCalculationService;
import com.geekbang.coupon.calculation.template.CouponTemplateFactory;
import com.geekbang.coupon.calculation.template.RuleTemplate;
import com.geekbang.coupon.template.api.beans.CouponInfo;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CouponCalculationServiceImpl implements CouponCalculationService {

    @Autowired
    private CouponTemplateFactory couponTemplateFactory;

    // 优惠券计算
    // 这里通过Factory类决定使用哪个底层Rule，底层规则对上层透明
    @Override
    public ShoppingCart calculateOrderPrice(ShoppingCart cart) {
        log.info("calculate order price:{}", JSON.toJSONString(cart));
        RuleTemplate ruleTemplate = couponTemplateFactory.getTemplate(cart);
        return ruleTemplate.calculate(cart);
    }

    // 对所有优惠券进行计算，看哪个最赚钱
    @Override
    public SimulationResponse simulateOrder(SimulationOrder order) {
        SimulationResponse response = new SimulationResponse();
        Long minOrderPrice = Long.MAX_VALUE;

        // 计算每一个优惠券的订单价格
        for (CouponInfo coupon : order.getCouponInfos()) {
            ShoppingCart cart = new ShoppingCart();
            cart.setProducts(order.getProducts());
            cart.setCouponInfos(Lists.newArrayList(coupon));
            cart = couponTemplateFactory.getTemplate(cart).calculate(cart);

            Long couponId = coupon.getId();
            Long orderPrice = cart.getCost();

            // 设置当前优惠券对应的订单价格
            response.getCouponToOrderPrice().put(couponId,orderPrice);

            // 比较订单价格，设置当前最优优惠券的id
            if (minOrderPrice > orderPrice){
                response.setBestCouponId(couponId);
                minOrderPrice = orderPrice;
            }
        }
        return response;
    }
}
