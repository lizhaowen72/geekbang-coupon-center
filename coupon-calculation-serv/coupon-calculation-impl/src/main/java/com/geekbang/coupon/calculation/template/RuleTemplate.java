package com.geekbang.coupon.calculation.template;

import com.geekbang.coupon.calculation.api.beans.ShoppingCart;

public interface RuleTemplate {

    ShoppingCart calculate(ShoppingCart settlement);
}
