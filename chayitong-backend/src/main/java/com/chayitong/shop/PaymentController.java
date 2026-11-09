package com.chayitong.shop;

import com.chayitong.common.api.CommonResponse;
import com.chayitong.common.api.ErrorCode;
import com.chayitong.common.exceptions.BusinessException;
import com.chayitong.shop.ShopDtos.PaymentRequest;
import com.chayitong.shop.ShopDtos.PaymentResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {

    private final OrderService orders;

    PaymentController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping("/api/orders/{orderNo}/payments")
    public CommonResponse<PaymentResponse> pay(@PathVariable String orderNo, @RequestBody PaymentRequest request) {
        return CommonResponse.success(orders.pay(orderNo, request.mode()));
    }

    @GetMapping("/api/orders/{orderNo}/payment-result")
    public CommonResponse<PaymentResponse> result(@PathVariable String orderNo) {
        return CommonResponse.success(orders.paymentResult(orderNo));
    }

    @PostMapping("/api/payments/wechat/notify")
    public CommonResponse<Void> wechatNotification() {
        throw new BusinessException(ErrorCode.PAYMENT_UNAVAILABLE, "真实微信支付通知适配器尚未配置");
    }
}
