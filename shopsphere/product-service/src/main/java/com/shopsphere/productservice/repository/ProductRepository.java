package com.shopsphere.productservice.repository;

import com.shopsphere.productservice.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    /** US44 — products referencing a category (blocks deleting it). */
    long countByCategory_Id(Long categoryId);

    /** US44 — product counts for every category in one query. */
    @Query("""
            select p.category.id as categoryId, count(p) as productCount
            from Product p
            where p.category is not null
            group by p.category.id
            """)
    List<CategoryProductCount> countProductsByCategory();

    interface CategoryProductCount {
        Long getCategoryId();
        Long getProductCount();
    }
}
