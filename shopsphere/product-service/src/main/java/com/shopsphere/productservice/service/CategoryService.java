package com.shopsphere.productservice.service;

import com.shopsphere.productservice.dto.CategoryRequest;
import com.shopsphere.productservice.dto.CategoryResponse;
import com.shopsphere.productservice.entity.Category;
import com.shopsphere.productservice.exception.CategoryInUseException;
import com.shopsphere.productservice.exception.CategoryNotFoundException;
import com.shopsphere.productservice.exception.DuplicateCategoryException;
import com.shopsphere.productservice.repository.CategoryRepository;
import com.shopsphere.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    /** Repository (not ProductService) to avoid a service-level dependency cycle. */
    private final ProductRepository productRepository;

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.getName().trim();
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicateCategoryException(name);
        }
        Category category = Category.builder()
                .name(name)
                .description(normalize(request.getDescription()))
                .build();
        return toResponse(categoryRepository.save(category), 0L);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        Map<Long, Long> counts = productRepository.countProductsByCategory().stream()
                .collect(Collectors.toMap(
                        ProductRepository.CategoryProductCount::getCategoryId,
                        ProductRepository.CategoryProductCount::getProductCount));
        return categoryRepository.findAll(Sort.by("name")).stream()
                .map(c -> toResponse(c, counts.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getById(Long id) {
        return toResponse(findOrThrow(id), productRepository.countByCategory_Id(id));
    }

    /** US44 — rename / re-describe. Names stay unique (case-insensitive). */
    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = findOrThrow(id);
        String name = request.getName().trim();
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new DuplicateCategoryException(name);
        }
        category.setName(name);
        category.setDescription(normalize(request.getDescription()));
        return toResponse(categoryRepository.save(category), productRepository.countByCategory_Id(id));
    }

    /** US44 — refuses to orphan products; the admin must move or delete them first. */
    @Transactional
    public void delete(Long id) {
        Category category = findOrThrow(id);
        long productCount = productRepository.countByCategory_Id(id);
        if (productCount > 0) {
            throw new CategoryInUseException(id, productCount);
        }
        categoryRepository.delete(category);
    }

    @Transactional(readOnly = true)
    public Category findOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(id));
    }

    /** Used when a category is nested inside a ProductResponse (no count). */
    public CategoryResponse toResponse(Category c) {
        return toResponse(c, null);
    }

    private CategoryResponse toResponse(Category c, Long productCount) {
        return CategoryResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .description(c.getDescription())
                .createdAt(c.getCreatedAt())
                .productCount(productCount)
                .build();
    }

    private static String normalize(String description) {
        if (description == null) {
            return null;
        }
        String trimmed = description.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
