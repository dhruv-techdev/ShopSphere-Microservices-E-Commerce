package com.shopsphere.productservice.repository;

import com.shopsphere.productservice.entity.Product;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

public final class ProductSpecifications {

    private ProductSpecifications() {}

    public static Specification<Product> nameContains(String name) {
        if (name == null || name.isBlank()) return null;
        String pattern = "%" + name.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern);
    }

    public static Specification<Product> hasCategoryId(Long categoryId) {
        if (categoryId == null) return null;
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Product> priceGreaterOrEqual(BigDecimal min) {
        if (min == null) return null;
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), min);
    }

    public static Specification<Product> priceLessOrEqual(BigDecimal max) {
        if (max == null) return null;
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), max);
    }

    public static Specification<Product> isActive(Boolean active) {
        if (active == null) return null;
        return (root, query, cb) -> cb.equal(root.get("active"), active);
    }

    public static Specification<Product> inStock(Boolean inStock) {
        if (inStock == null) return null;
        return (root, query, cb) -> inStock
                ? cb.greaterThan(root.get("stockQuantity"), 0)
                : cb.equal(root.get("stockQuantity"), 0);
    }
}
