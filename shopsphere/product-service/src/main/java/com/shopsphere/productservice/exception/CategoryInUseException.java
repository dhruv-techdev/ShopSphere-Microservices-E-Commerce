package com.shopsphere.productservice.exception;

/** US44 — a category can't be deleted while products still reference it. */
public class CategoryInUseException extends RuntimeException {
    public CategoryInUseException(Long id, long productCount) {
        super("Category " + id + " is still used by " + productCount
                + " product(s); move or delete them first");
    }
}
