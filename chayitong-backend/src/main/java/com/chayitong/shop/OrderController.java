package com.chayitong.shop;

import com.chayitong.common.api.CommonResponse;
import com.chayitong.common.api.PageResponse;
import com.chayitong.shop.ShopDtos.OrderCreateRequest;
import com.chayitong.shop.ShopDtos.OrderResponse;
import com.chayitong.shop.ShopDtos.QuoteRequest;
import com.chayitong.shop.ShopDtos.QuoteResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

    private final OrderService orders;

    OrderController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping("/api/checkout/quote")
    public CommonResponse<QuoteResponse> quote(@RequestBody QuoteRequest request) {
        return CommonResponse.success(orders.quote(request.cartItemIds(), request.pickup()));
    }

    @PostMapping("/api/orders")
    public CommonResponse<OrderResponse> create(@RequestBody OrderCreateRequest request) {
        return CommonResponse.success(orders.create(request));
    }

    @GetMapping("/api/orders")
    public CommonResponse<PageResponse<OrderResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return CommonResponse.success(orders.list(status, pageNum, pageSize));
    }

    @GetMapping("/api/orders/{orderNo}")
    public CommonResponse<OrderResponse> get(@PathVariable String orderNo) {
        return CommonResponse.success(orders.get(orderNo));
    }

    @PostMapping("/api/orders/{orderNo}/cancel")
    public CommonResponse<OrderResponse> cancel(@PathVariable String orderNo) {
        return CommonResponse.success(orders.cancel(orderNo));
    }
}
