package com.danielhw99.category.service;

import com.danielhw99.auth.security.ActiveAccount;

import java.util.List;
import java.util.Locale;

import com.danielhw99.category.api.CategoryDirectionValue;
import com.danielhw99.category.api.CategoryInput;
import com.danielhw99.category.api.CategoryResponse;
import com.danielhw99.category.api.CategoryScopeValue;
import com.danielhw99.category.entity.Category;
import com.danielhw99.category.entity.CategoryDirection;
import com.danielhw99.category.repository.CategoryRepository;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
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

    @Transactional
    public CategoryResponse createOrReuse(Long userId, CategoryInput input) {
        if (input == null || input.scope() == null || input.name() == null) {
            throw new ApiException(ErrorCode.VALIDATION);
        }
        CategoryDirection direction;
        if (input.scope() == CategoryScopeValue.FIXED_BUDGET) {
            if (input.direction() != null) throw new ApiException(ErrorCode.VALIDATION);
            direction = CategoryDirection.NONE;
        } else {
            if (input.direction() == null) throw new ApiException(ErrorCode.VALIDATION);
            direction = input.direction().entityValue();
        }
        String name = stripUnicodeSpace(input.name());
        String nameKey = name.toLowerCase(Locale.ROOT);
        if (name.isBlank() || name.codePointCount(0, name.length()) > 100
                || nameKey.codePointCount(0, nameKey.length()) > 100) {
            throw new ApiException(ErrorCode.VALIDATION);
        }
        var owner = ActiveAccount.requireCurrent(users.findWithLockingById(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED)));
        var existing = categories.findByUser_IdAndScopeAndDirectionAndNameKey(
                userId, input.scope().entityValue(), direction, nameKey);
        if (existing.isPresent()) return CategoryResponse.from(existing.get());
        return CategoryResponse.from(categories.saveAndFlush(new Category(
                owner, input.scope().entityValue(), direction, name, nameKey)));
    }

    private String stripUnicodeSpace(String value) {
        int start = 0;
        int end = value.length();
        while (start < end) {
            int codePoint = value.codePointAt(start);
            if (!Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint)) break;
            start += Character.charCount(codePoint);
        }
        while (end > start) {
            int codePoint = value.codePointBefore(end);
            if (!Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint)) break;
            end -= Character.charCount(codePoint);
        }
        return value.substring(start, end);
    }
}
