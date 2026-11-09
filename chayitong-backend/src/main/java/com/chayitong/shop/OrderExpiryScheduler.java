package com.chayitong.shop;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class OrderExpiryScheduler {

    private final OrderService orderService;

    OrderExpiryScheduler(OrderService orderService) {
        this.orderService = orderService;
    }

    @Scheduled(fixedDelayString = "${app.shop.expiry-scan-millis:30000}")
    void closeExpiredOrders() {
        orderService.closeExpiredOrders();
    }
}
