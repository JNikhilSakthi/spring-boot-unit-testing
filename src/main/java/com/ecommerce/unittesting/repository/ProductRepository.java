package com.ecommerce.unittesting.repository;

import com.ecommerce.unittesting.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

// @Repository → Marks this as a Spring Data repository bean
// extends JpaRepository<Product, Long> →
//   Product = the entity this repo manages
//   Long    = the type of the primary key (id)
//
// JpaRepository gives you these methods FOR FREE (no implementation needed):
//   save(entity)         → INSERT or UPDATE
//   findById(id)         → SELECT by primary key
//   findAll()            → SELECT all
//   delete(entity)       → DELETE
//   deleteById(id)       → DELETE by primary key
//   count()              → COUNT(*)
//   existsById(id)       → EXISTS check
//
// Spring Data Query Method Naming Convention:
//   findBy<FieldName><Condition> → Spring auto-generates the SQL query
//   No need to write @Query or native SQL for simple queries!
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    // findByCategory → SELECT * FROM products WHERE category = ?
    List<Product> findByCategory(String category);

    // findByNameContainingIgnoreCase → SELECT * FROM products WHERE LOWER(name) LIKE LOWER('%?%')
    // Containing = LIKE %value%
    // IgnoreCase = case-insensitive comparison
    List<Product> findByNameContainingIgnoreCase(String name);

    // findByPriceBetween → SELECT * FROM products WHERE price BETWEEN ? AND ?
    List<Product> findByPriceBetween(BigDecimal minPrice, BigDecimal maxPrice);

    // findByQuantityGreaterThan → SELECT * FROM products WHERE quantity > ?
    List<Product> findByQuantityGreaterThan(Integer quantity);

    // SELECT EXISTS(SELECT 1 FROM products WHERE created_by = ? OR updated_by = ?)
    // Used to check if a user has any associated products before deleting the user
    // Prevents FK constraint violation (ugly 500 error)
    boolean existsByCreatedByIdOrUpdatedById(Long createdById, Long updatedById);
}
