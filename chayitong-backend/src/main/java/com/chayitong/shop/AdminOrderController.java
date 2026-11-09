package com.chayitong.shop;

import com.chayitong.common.api.CommonResponse;
import com.chayitong.shop.ShopDtos.OrderResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderService orders;

    AdminOrderController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping("/{orderNo}/pickup-completion")
    public CommonResponse<OrderResponse> complete(@PathVariable String orderNo) {
        return CommonResponse.success(orders.completePickup(orderNo));
    }
}
