package com.danielhw99.category.service;

import java.util.List;

import com.danielhw99.category.api.CategoryDirectionValue;
import com.danielhw99.category.api.CategoryResponse;
import com.danielhw99.category.api.CategoryScopeValue;
import com.danielhw99.category.entity.Category;
import com.danielhw99.category.repository.CategoryRepository;
import com.danielhw99.user.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {
    private final CategoryRepository categories;
    private final UserRepository users;

    public CategoryService(CategoryRepository categories, UserRepository users) {
        this.categories = categories;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(Long userId, CategoryScopeValue scope, CategoryDirectionValue direction) {
        List<Category> result;
        if (scope == null && direction == null) {
            result = categories.findByUser_IdOrderByIdAsc(userId);
        } else if (scope == null) {
            result = categories.findByUser_IdAndDirectionOrderByIdAsc(userId, direction.entityValue());
        } else if (direction == null) {
            result = categories.findByUser_IdAndScopeOrderByIdAsc(userId, scope.entityValue());
        } else {
            result = categories.findByUser_IdAndScopeAndDirectionOrderByIdAsc(
                    userId, scope.entityValue(), direction.entityValue());
        }
        return result.stream().map(CategoryResponse::from).toList();
    }
}
