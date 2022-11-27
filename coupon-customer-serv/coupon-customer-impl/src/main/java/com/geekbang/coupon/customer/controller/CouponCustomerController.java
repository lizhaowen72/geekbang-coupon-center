package com.geekbang.coupon.customer.controller;

import com.geekbang.coupon.calculation.api.beans.ShoppingCart;
import com.geekbang.coupon.calculation.api.beans.SimulationOrder;
import com.geekbang.coupon.calculation.api.beans.SimulationResponse;
import com.geekbang.coupon.customer.api.beans.RequestCoupon;
import com.geekbang.coupon.customer.api.beans.SearchCoupon;
import com.geekbang.coupon.customer.dao.entity.Coupon;
import com.geekbang.coupon.customer.service.intf.CouponCustomerService;
import com.geekbang.coupon.template.api.beans.CouponInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("coupon-customer")
public class CouponCustomerController {

    @Autowired
    private CouponCustomerService couponCustomerService;

    @PostMapping("requestCoupon")
    public Coupon requestCoupon(@Valid @RequestBody RequestCoupon request){
        return couponCustomerService.requestCoupon(request);
    }

    //用户删除优惠券
    @DeleteMapping("deleteCoupon")
    public void deleteCoupon(@RequestParam("userId") Long userId,@RequestParam("couponId") Long couponId){
        couponCustomerService.deleteCoupon(userId, couponId);
    }

    // 用户模拟计算每个优惠券的优惠价格
    @PostMapping("simulateOrder")
    public SimulationResponse simulate(@Valid @RequestBody SimulationOrder order){
        return couponCustomerService.simulateOrderPrice(order);
    }

    @PostMapping("placeOrder")
    public ShoppingCart checkOut(@Valid @RequestBody ShoppingCart info){
        return couponCustomerService.placeOrder(info);
    }

    @PostMapping("findCoupon")
    public List<CouponInfo> findCoupon(@Valid @RequestBody SearchCoupon request){
        return couponCustomerService.findCoupon(request);
    }
}

