package com.shopsphere.productservice.exception;

public class CategoryNotFoundException extends RuntimeException {
    public CategoryNotFoundException(Long id) {
        super("Category not found with id: " + id);
    }
}
