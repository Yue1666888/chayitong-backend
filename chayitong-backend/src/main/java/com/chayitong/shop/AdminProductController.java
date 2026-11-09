package com.chayitong.shop;

import com.chayitong.common.api.CommonResponse;
import com.chayitong.shop.ShopDtos.ProductDetail;
import com.chayitong.shop.ShopDtos.ProductRequest;
import com.chayitong.shop.ShopDtos.StatusRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController {

    private final ProductService products;

    AdminProductController(ProductService products) {
        this.products = products;
    }

    @PostMapping
    public CommonResponse<ProductDetail> create(@RequestBody ProductRequest request) {
        return CommonResponse.success(products.create(request));
    }

    @PutMapping("/{id}")
    public CommonResponse<ProductDetail> update(@PathVariable String id, @RequestBody ProductRequest request) {
        return CommonResponse.success(products.update(id, request));
    }

    @PutMapping("/{id}/status")
    public CommonResponse<ProductDetail> status(@PathVariable String id, @RequestBody StatusRequest request) {
        return CommonResponse.success(products.changeStatus(id, request.status()));
    }
}
