package com.geekbang.coupon.customer.service;

import com.geekbang.coupon.customer.service.intf.CouponCustomerService;
import com.geekbang.coupon.calculation.api.beans.ShoppingCart;
import com.geekbang.coupon.calculation.api.beans.SimulationOrder;
import com.geekbang.coupon.calculation.api.beans.SimulationResponse;
import com.geekbang.coupon.customer.api.beans.RequestCoupon;
import com.geekbang.coupon.customer.api.beans.SearchCoupon;
import com.geekbang.coupon.customer.api.enums.CouponStatus;
import com.geekbang.coupon.customer.dao.CouponDao;
import com.geekbang.coupon.customer.dao.entity.Coupon;
import com.geekbang.coupon.template.api.beans.CouponInfo;
import com.geekbang.coupon.template.api.beans.CouponTemplateInfo;
import com.geekbang.coupon.template.service.intf.CouponTemplateService;
import com.google.common.collect.Lists;
import jdk.nashorn.internal.ir.annotations.Ignore;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Example;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.geekbang.coupon.customer.constant.Constant.TRAFFIC_VERSION;

@Slf4j
@Service
public class CouponCustomerServiceImpl implements CouponCustomerService {

    @Autowired
    private CouponDao couponDao;

    @Autowired
    private WebClient.Builder webClientBuilder;

    @Override
    public SimulationResponse simulateOrderPrice(SimulationOrder order) {
        List<CouponInfo> couponInfos = Lists.newArrayList();
        for (Long couponId : order.getCouponIds()) {
            Coupon example = Coupon.builder()
                    .userId(order.getUserId())
                    .id(couponId)
                    .status(CouponStatus.AVAILABLE)
                    .build();
            Optional<Coupon> couponOptional = couponDao.findAll(Example.of(example))
                    .stream()
                    .findFirst();
            // 加载优惠券模板信息
            if (couponOptional.isPresent()) {
                Coupon coupon = couponOptional.get();
                CouponInfo couponInfo = CouponConverter.convertToCoupon(coupon);
                couponInfo.setTemplate(loadTemplateInfo(coupon.getTemplateId()));
                couponInfos.add(couponInfo);
            }
        }
        order.setCouponInfos(couponInfos);
        return webClientBuilder.build().post()
                .uri("http://coupon-calculation-serv/calculator/simulate")
                .bodyValue(order)
                .retrieve()
                .bodyToMono(SimulationResponse.class)
                .block();
    }


    @Override
    public Coupon requestCoupon(RequestCoupon request) {
        CouponTemplateInfo templateInfo = webClientBuilder.build()
                .get()
                .uri("http://coupon-template-serv/template/getTemplate?id=" + request.getCouponTemplateId())
                .header(TRAFFIC_VERSION, request.getTrafficVersion())
                .retrieve()
                .bodyToMono(CouponTemplateInfo.class)
                .block();
        // 模板不存在则报错
        if (templateInfo == null) {
            log.error("invalid template id={}", request.getCouponTemplateId());
            throw new IllegalArgumentException("Invalid template id");
        }
        // 模板不能过期
        long now = Calendar.getInstance().getTimeInMillis();
        Long expTime = templateInfo.getRule().getDeadline();
        if (expTime != null && now >= expTime || BooleanUtils.isFalse(templateInfo.getAvailable())) {
            log.error("template is not available id={}", request.getCouponTemplateId());
            throw new IllegalArgumentException("template is unavailable");
        }

        // 用户领券数量超过上限
        long count = couponDao.countByUserIdAndTemplateId(request.getUserId(), request.getCouponTemplateId());
        if (count >= templateInfo.getRule().getLimitation()){
            log.error("exceeds maximum number");
            throw new IllegalArgumentException("exceeds maximum number");
        }
        Coupon coupon = Coupon.builder()
                .templateId(request.getCouponTemplateId())
                .userId(request.getUserId())
                .shopId(templateInfo.getShopId())
                .status(CouponStatus.AVAILABLE)
                .templateInfo(templateInfo)
                .build();
        couponDao.save(coupon);
        return coupon;
    }

    @Override
    public ShoppingCart placeOrder(ShoppingCart order) {
        // 购物车为空，丢出异常
        if (CollectionUtils.isEmpty(order.getProducts())) {
            log.error("invalid check out request,order={}", order);
            throw new IllegalArgumentException("cart is empty");
        }
        Coupon coupon = null;
        if (order.getCouponId() != null) {
            // 如果有优惠券就把它查出来，看是不是属于当前用户并且可用
            Coupon example = Coupon.builder().userId(order.getUserId())
                    .id(order.getCouponId())
                    .status(CouponStatus.AVAILABLE)
                    .build();
            coupon = couponDao.findAll(Example.of(example)).stream()
                    .findFirst()
                    // 如果当前用户查不到可用优惠券，就抛出错误
                    .orElseThrow(() -> new RuntimeException("Coupon not found"));
            // 优惠券有了，再把它的券模板信息查出来
            // 券模板里的discount规则会在稍后用于订单价格计算
            CouponInfo couponInfo = CouponConverter.convertToCoupon(coupon);
            couponInfo.setTemplate(loadTemplateInfo(coupon.getTemplateId()));
            order.setCouponInfos(Lists.newArrayList(couponInfo));
        }
        // order 清算
        ShoppingCart checkoutInfo = webClientBuilder.build().post()
                .uri("http://coupon-calculation-serv/calculator/checkout")
                .bodyValue(order)
                .retrieve()
                .bodyToMono(ShoppingCart.class)
                .block();
        if (coupon != null) {
            // 如果优惠券没有被结算掉，而用户传递了优惠券，报错提示该订单满足不了优惠条件
            if (CollectionUtils.isEmpty(checkoutInfo.getCouponInfos())) {
                log.error("cannot apply coupon to order,couponId={}", coupon.getId());
                throw new IllegalArgumentException("coupon is not applicable to this order");
            }
            log.info("update coupon status to used,couponId={}", coupon.getId());
            coupon.setStatus(CouponStatus.USED);
            couponDao.save(coupon);
        }
        return checkoutInfo;
    }

    // 逻辑删除优惠券
    @Override
    public void deleteCoupon(Long userId, Long couponId) {
        Coupon example = Coupon.builder()
                .userId(userId)
                .id(couponId)
                .status(CouponStatus.AVAILABLE)
                .build();
        Coupon coupon = couponDao.findAll(Example.of(example))
                .stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Could not find available coupon"));
        coupon.setStatus(CouponStatus.INACTIVE);
        couponDao.save(coupon);
    }

    /**
     * 用户查询优惠券的接口
     * @param request
     * @return
     */
    @Override
    public List<CouponInfo> findCoupon(SearchCoupon request) {
        Coupon example = Coupon.builder()
                .userId(request.getUserId())
                .status(CouponStatus.convert(request.getCouponStatus()))
                .shopId(request.getShopId())
                .build();
        List<Coupon> coupons = couponDao.findAll(Example.of(example));
        if (CollectionUtils.isEmpty(coupons)){
            return Lists.newArrayList();
        }
        // 获取这些优惠券的模板id
        String templateIds = coupons.stream()
                .map(Coupon::getTemplateId)
                .map(String::valueOf)
                .distinct()
                .collect(Collectors.joining(","));
        // 发起请求批量查询券模板
        Map<Long, CouponTemplateInfo> templateInfoMap = webClientBuilder.build()
                .get()
                .uri("http://coupon-template-serv/template/getBatch?ids=" + templateIds)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<Long, CouponTemplateInfo>>() {
                })
                .block();
        coupons.stream().forEach(e->e.setTemplateInfo(templateInfoMap.get(e.getTemplateId())));
        return coupons.stream().map(CouponConverter::convertToCoupon).collect(Collectors.toList());
    }

    private CouponTemplateInfo loadTemplateInfo(Long templateId) {
        return webClientBuilder.build().get()
                .uri("http://coupon-template-serv/template/getTemplate?id=" + templateId)
                .retrieve()
                .bodyToMono(CouponTemplateInfo.class)
                .block();
    }
}
