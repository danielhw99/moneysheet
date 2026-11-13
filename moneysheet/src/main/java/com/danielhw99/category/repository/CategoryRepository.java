package com.danielhw99.category.repository;

import com.danielhw99.category.entity.Category;
import com.danielhw99.category.entity.CategoryDirection;
import com.danielhw99.category.entity.CategoryScope;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByUser_IdOrderByIdAsc(Long userId);

    List<Category> findByUser_IdAndScopeOrderByIdAsc(Long userId, CategoryScope scope);

    List<Category> findByUser_IdAndDirectionOrderByIdAsc(Long userId, CategoryDirection direction);

    List<Category> findByUser_IdAndScopeAndDirectionOrderByIdAsc(
            Long userId, CategoryScope scope, CategoryDirection direction);
}
