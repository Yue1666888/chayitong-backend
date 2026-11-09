package com.chayitong.shop;

import com.chayitong.common.api.CommonResponse;
import com.chayitong.common.api.PageResponse;
import com.chayitong.shop.ShopDtos.ProductDetail;
import com.chayitong.shop.ShopDtos.ProductSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService products;

    ProductController(ProductService products) {
        this.products = products;
    }

    @GetMapping
    public CommonResponse<PageResponse<ProductSummary>> list(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String teaId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean featured,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return CommonResponse.success(products.list(categoryId, teaId, keyword, featured, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public CommonResponse<ProductDetail> detail(@PathVariable String id) {
        return CommonResponse.success(products.publicDetail(id));
    }
}
