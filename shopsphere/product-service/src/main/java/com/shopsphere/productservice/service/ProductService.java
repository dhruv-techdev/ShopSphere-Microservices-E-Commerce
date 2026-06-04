package com.shopsphere.productservice.service;

import com.shopsphere.productservice.dto.ProductRequest;
import com.shopsphere.productservice.dto.ProductResponse;
import com.shopsphere.productservice.entity.Category;
import com.shopsphere.productservice.entity.Product;
import com.shopsphere.productservice.exception.ProductNotFoundException;
import com.shopsphere.productservice.repository.ProductRepository;
import com.shopsphere.productservice.repository.ProductSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryService categoryService;

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Category category = request.getCategoryId() == null
                ? null
                : categoryService.findOrThrow(request.getCategoryId());

        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .category(category)
                .stockQuantity(request.getStockQuantity())
                .active(request.getActive() == null ? Boolean.TRUE : request.getActive())
                .build();
        return toResponse(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> search(String name,
                                        Long categoryId,
                                        BigDecimal minPrice,
                                        BigDecimal maxPrice,
                                        Boolean active,
                                        Boolean inStock,
                                        Pageable pageable) {
        Specification<Product> spec = Specification
                .where(ProductSpecifications.nameContains(name))
                .and(ProductSpecifications.hasCategoryId(categoryId))
                .and(ProductSpecifications.priceGreaterOrEqual(minPrice))
                .and(ProductSpecifications.priceLessOrEqual(maxPrice))
                .and(ProductSpecifications.isActive(active))
                .and(ProductSpecifications.inStock(inStock));
        return productRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = findOrThrow(id);
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        if (request.getActive() != null) {
            product.setActive(request.getActive());
        }
        if (request.getCategoryId() != null) {
            product.setCategory(categoryService.findOrThrow(request.getCategoryId()));
        } else {
            product.setCategory(null);
        }
        return toResponse(productRepository.save(product));
    }

    @Transactional
    public void delete(Long id) {
        Product product = findOrThrow(id);
        productRepository.delete(product);
    }

    private Product findOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    private ProductResponse toResponse(Product p) {
        return ProductResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .price(p.getPrice())
                .category(p.getCategory() == null ? null : categoryService.toResponse(p.getCategory()))
                .stockQuantity(p.getStockQuantity())
                .active(p.getActive())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
