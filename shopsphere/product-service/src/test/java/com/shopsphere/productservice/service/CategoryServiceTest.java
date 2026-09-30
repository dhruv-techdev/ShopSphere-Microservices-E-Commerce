package com.shopsphere.productservice.service;

import com.shopsphere.productservice.dto.CategoryRequest;
import com.shopsphere.productservice.dto.CategoryResponse;
import com.shopsphere.productservice.entity.Category;
import com.shopsphere.productservice.exception.CategoryInUseException;
import com.shopsphere.productservice.exception.CategoryNotFoundException;
import com.shopsphere.productservice.exception.DuplicateCategoryException;
import com.shopsphere.productservice.repository.CategoryRepository;
import com.shopsphere.productservice.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock CategoryRepository categoryRepository;
    @Mock ProductRepository productRepository;
    @InjectMocks CategoryService categoryService;

    private static Category category(long id, String name) {
        return Category.builder().id(id).name(name).build();
    }

    private static ProductRepository.CategoryProductCount count(long categoryId, long products) {
        return new ProductRepository.CategoryProductCount() {
            @Override public Long getCategoryId() { return categoryId; }
            @Override public Long getProductCount() { return products; }
        };
    }

    @Test
    void create_trimsValues_andStartsWithZeroProducts() {
        when(categoryRepository.existsByNameIgnoreCase("Toys")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> {
            Category c = inv.getArgument(0);
            c.setId(9L);
            return c;
        });

        CategoryResponse created = categoryService.create(new CategoryRequest("  Toys ", "   "));

        assertThat(created.getId()).isEqualTo(9L);
        assertThat(created.getName()).isEqualTo("Toys");
        assertThat(created.getDescription()).isNull();
        assertThat(created.getProductCount()).isZero();
    }

    @Test
    void create_duplicateName_throws() {
        when(categoryRepository.existsByNameIgnoreCase("Toys")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.create(new CategoryRequest("Toys", null)))
                .isInstanceOf(DuplicateCategoryException.class);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void list_isSortedByName_andCarriesProductCounts() {
        when(categoryRepository.findAll(any(Sort.class)))
                .thenReturn(List.of(category(4, "Books"), category(3, "Toys")));
        when(productRepository.countProductsByCategory()).thenReturn(List.of(count(3, 5)));

        List<CategoryResponse> list = categoryService.list();

        assertThat(list).extracting(CategoryResponse::getName).containsExactly("Books", "Toys");
        assertThat(list).extracting(CategoryResponse::getProductCount).containsExactly(0L, 5L);
    }

    @Test
    void update_renamesCategory() {
        Category existing = category(3, "Toys");
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(existing));
        when(categoryRepository.existsByNameIgnoreCaseAndIdNot("Toys & Games", 3L)).thenReturn(false);
        when(categoryRepository.save(existing)).thenReturn(existing);
        when(productRepository.countByCategory_Id(3L)).thenReturn(2L);

        CategoryResponse updated = categoryService.update(3L, new CategoryRequest("Toys & Games", "Board games"));

        assertThat(updated.getName()).isEqualTo("Toys & Games");
        assertThat(updated.getDescription()).isEqualTo("Board games");
        assertThat(updated.getProductCount()).isEqualTo(2L);
    }

    @Test
    void update_toAnotherCategorysName_throwsDuplicate() {
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(category(3, "Toys")));
        when(categoryRepository.existsByNameIgnoreCaseAndIdNot("Books", 3L)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.update(3L, new CategoryRequest("Books", null)))
                .isInstanceOf(DuplicateCategoryException.class);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void update_missingCategory_throwsNotFound() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.update(99L, new CategoryRequest("X", null)))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    void delete_unusedCategory_deletesIt() {
        Category existing = category(3, "Toys");
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(existing));
        when(productRepository.countByCategory_Id(3L)).thenReturn(0L);

        categoryService.delete(3L);

        verify(categoryRepository).delete(existing);
    }

    @Test
    void delete_categoryWithProducts_isRefused() {
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(category(3, "Toys")));
        when(productRepository.countByCategory_Id(3L)).thenReturn(4L);

        assertThatThrownBy(() -> categoryService.delete(3L))
                .isInstanceOf(CategoryInUseException.class)
                .hasMessageContaining("4 product(s)");
        verify(categoryRepository, never()).delete(any());
    }
}
