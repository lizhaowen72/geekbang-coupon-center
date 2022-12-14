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
            // ???????????????????????????
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
        // ????????????????????????
        if (templateInfo == null) {
            log.error("invalid template id={}", request.getCouponTemplateId());
            throw new IllegalArgumentException("Invalid template id");
        }
        // ??????????????????
        long now = Calendar.getInstance().getTimeInMillis();
        Long expTime = templateInfo.getRule().getDeadline();
        if (expTime != null && now >= expTime || BooleanUtils.isFalse(templateInfo.getAvailable())) {
            log.error("template is not available id={}", request.getCouponTemplateId());
            throw new IllegalArgumentException("template is unavailable");
        }

        // ??????????????????????????????
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
        // ??????????????????????????????
        if (CollectionUtils.isEmpty(order.getProducts())) {
            log.error("invalid check out request,order={}", order);
            throw new IllegalArgumentException("cart is empty");
        }
        Coupon coupon = null;
        if (order.getCouponId() != null) {
            // ?????????????????????????????????????????????????????????????????????????????????
            Coupon example = Coupon.builder().userId(order.getUserId())
                    .id(order.getCouponId())
                    .status(CouponStatus.AVAILABLE)
                    .build();
            coupon = couponDao.findAll(Example.of(example)).stream()
                    .findFirst()
                    // ????????????????????????????????????????????????????????????
                    .orElseThrow(() -> new RuntimeException("Coupon not found"));
            // ??????????????????????????????????????????????????????
            // ???????????????discount??????????????????????????????????????????
            CouponInfo couponInfo = CouponConverter.convertToCoupon(coupon);
            couponInfo.setTemplate(loadTemplateInfo(coupon.getTemplateId()));
            order.setCouponInfos(Lists.newArrayList(couponInfo));
        }
        // order ??????
        ShoppingCart checkoutInfo = webClientBuilder.build().post()
                .uri("http://coupon-calculation-serv/calculator/checkout")
                .bodyValue(order)
                .retrieve()
                .bodyToMono(ShoppingCart.class)
                .block();
        if (coupon != null) {
            // ???????????????????????????????????????????????????????????????????????????????????????????????????????????????
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

    // ?????????????????????
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
     * ??????????????????????????????
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
        // ??????????????????????????????id
        String templateIds = coupons.stream()
                .map(Coupon::getTemplateId)
                .map(String::valueOf)
                .distinct()
                .collect(Collectors.joining(","));
        // ?????????????????????????????????
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
