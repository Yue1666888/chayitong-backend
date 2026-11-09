package com.chayitong.shop;

import com.chayitong.common.api.CommonResponse;
import com.chayitong.shop.ShopDtos.CartItemPatchRequest;
import com.chayitong.shop.ShopDtos.CartItemRequest;
import com.chayitong.shop.ShopDtos.CartResponse;
import com.chayitong.shop.ShopDtos.SelectionRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService carts;

    CartController(CartService carts) {
        this.carts = carts;
    }

    @GetMapping
    public CommonResponse<CartResponse> get() {
        return CommonResponse.success(carts.get());
    }

    @PostMapping("/items")
    public CommonResponse<CartResponse> add(@RequestBody CartItemRequest request) {
        return CommonResponse.success(carts.add(request.productId(), request.skuId(), request.quantity()));
    }

    @PatchMapping("/items/{id}")
    public CommonResponse<CartResponse> patch(@PathVariable long id, @RequestBody CartItemPatchRequest request) {
        return CommonResponse.success(carts.patch(id, request.quantity(), request.checked()));
    }

    @DeleteMapping("/items/{id}")
    public CommonResponse<CartResponse> remove(@PathVariable long id) {
        return CommonResponse.success(carts.remove(id));
    }

    @PutMapping("/selection")
    public CommonResponse<CartResponse> selection(@RequestBody SelectionRequest request) {
        return CommonResponse.success(carts.selectAll(request.checked()));
    }

    @DeleteMapping
    public CommonResponse<CartResponse> clear() {
        return CommonResponse.success(carts.clear());
    }
}
