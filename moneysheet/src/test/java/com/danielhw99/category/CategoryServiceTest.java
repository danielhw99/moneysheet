package com.danielhw99.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import com.danielhw99.category.api.CategoryDirectionValue;
import com.danielhw99.category.api.CategoryScopeValue;
import com.danielhw99.category.entity.Category;
import com.danielhw99.category.entity.CategoryDirection;
import com.danielhw99.category.entity.CategoryScope;
import com.danielhw99.category.repository.CategoryRepository;
import com.danielhw99.category.service.CategoryService;
import com.danielhw99.user.entity.User;
import com.danielhw99.user.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CategoryServiceTest {
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CategoryService service = new CategoryService(categories, users);

    @Test
    void listsOnlyTheAuthenticatedUsersMatchingCategories() {
        Category lunch = category(101L, CategoryScope.MAIN_TRANSACTION, CategoryDirection.EXPENSE, "식비");
        when(categories.findByUser_IdAndScopeAndDirectionOrderByIdAsc(
                7L, CategoryScope.MAIN_TRANSACTION, CategoryDirection.EXPENSE)).thenReturn(List.of(lunch));

        var result = service.list(7L, CategoryScopeValue.MAIN_TRANSACTION, CategoryDirectionValue.EXPENSE);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id().jsonValue()).isEqualTo("101");
        assertThat(result.getFirst().name()).isEqualTo("식비");
        assertThat(result.getFirst().scope().getValue()).isEqualTo("mainTransaction");
        assertThat(result.getFirst().direction().getValue()).isEqualTo("expense");
    }

    @Test
    void fixedBudgetCategoryHasNullApiDirection() {
        Category housing = category(102L, CategoryScope.FIXED_BUDGET, CategoryDirection.NONE, "주거");
        when(categories.findByUser_IdAndScopeOrderByIdAsc(7L, CategoryScope.FIXED_BUDGET))
                .thenReturn(List.of(housing));

        var result = service.list(7L, CategoryScopeValue.FIXED_BUDGET, null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().direction()).isNull();
    }

    @Test
    void unfilteredListMayBeEmpty() {
        when(categories.findByUser_IdOrderByIdAsc(7L)).thenReturn(List.of());
        assertThat(service.list(7L, null, null)).isEmpty();
    }

    private Category category(Long id, CategoryScope scope, CategoryDirection direction, String name) {
        User user = new User("a@example.com", "a@example.com", "A", "hash", "v1", LocalDateTime.now());
        ReflectionTestUtils.setField(user, "id", 7L);
        Category category = new Category(user, scope, direction, name, name.toLowerCase());
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }
}
