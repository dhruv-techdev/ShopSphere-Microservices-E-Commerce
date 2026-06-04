package com.shopsphere.productservice.controller;

import com.shopsphere.productservice.dto.ProductRequest;
import com.shopsphere.productservice.dto.ProductResponse;
import com.shopsphere.productservice.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product CRUD APIs")
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @Operation(summary = "Create a new product")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse created = productService.create(request);
        return ResponseEntity
                .created(URI.create("/api/v1/products/" + created.getId()))
                .body(created);
    }

    @GetMapping
    @Operation(summary = "List all products (paginated)")
    public Page<ProductResponse> list(Pageable pageable) {
        return productService.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a product by id")
    public ProductResponse getById(@PathVariable Long id) {
        return productService.getById(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a product")
    public ProductResponse update(@PathVariable Long id,
                                  @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a product")
    public void delete(@PathVariable Long id) {
        productService.delete(id);
    }
}
