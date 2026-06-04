package com.shopsphere.productservice.service;

import com.shopsphere.productservice.dto.CategoryRequest;
import com.shopsphere.productservice.dto.CategoryResponse;
import com.shopsphere.productservice.entity.Category;
import com.shopsphere.productservice.exception.CategoryNotFoundException;
import com.shopsphere.productservice.exception.DuplicateCategoryException;
import com.shopsphere.productservice.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        if (categoryRepository.existsByNameIgnoreCase(request.getName())) {
            throw new DuplicateCategoryException(request.getName());
        }
        Category category = Category.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();
        return toResponse(categoryRepository.save(category));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        return categoryRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Category findOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(id));
    }

    public CategoryResponse toResponse(Category c) {
        return CategoryResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .description(c.getDescription())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
