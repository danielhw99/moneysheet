package com.danielhw99.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.sql.SQLException;

import com.danielhw99.category.api.CategoryDirectionValue;
import com.danielhw99.category.api.CategoryInput;
import com.danielhw99.category.api.CategoryScopeValue;
import com.danielhw99.category.entity.Category;
import com.danielhw99.category.entity.CategoryDirection;
import com.danielhw99.category.entity.CategoryScope;
import com.danielhw99.category.repository.CategoryRepository;
import com.danielhw99.category.service.CategoryService;
import com.danielhw99.user.entity.User;
import com.danielhw99.user.repository.UserRepository;
import com.danielhw99.common.error.ApiException;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
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

    @Test
    void createsNormalizedCategoryForAuthenticatedUser() {
        User owner = category(1L, CategoryScope.FIXED_BUDGET, CategoryDirection.NONE, "x").getUser();
        when(users.findById(7L)).thenReturn(Optional.of(owner));
        when(categories.saveAndFlush(org.mockito.ArgumentMatchers.any(Category.class))).thenAnswer(invocation -> {
            Category created = invocation.getArgument(0);
            ReflectionTestUtils.setField(created, "id", 103L);
            return created;
        });

        var result = service.createOrReuse(7L, new CategoryInput("  Housing  ", CategoryScopeValue.FIXED_BUDGET, null));

        assertThat(result.id().jsonValue()).isEqualTo("103");
        assertThat(result.name()).isEqualTo("Housing");
        assertThat(result.direction()).isNull();
        org.mockito.ArgumentCaptor<Category> saved = org.mockito.ArgumentCaptor.forClass(Category.class);
        verify(categories).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getNameKey()).isEqualTo("housing");
        assertThat(saved.getValue().getUser()).isSameAs(owner);
    }

    @Test
    void reusesExistingCategoryForSameNormalizedKey() {
        Category existing = category(101L, CategoryScope.MAIN_TRANSACTION, CategoryDirection.EXPENSE, "Lunch");
        when(categories.findByUser_IdAndScopeAndDirectionAndNameKey(7L,
                CategoryScope.MAIN_TRANSACTION, CategoryDirection.EXPENSE, "lunch"))
                .thenReturn(Optional.of(existing));
        var result = service.createOrReuse(7L,
                new CategoryInput(" LUNCH ", CategoryScopeValue.MAIN_TRANSACTION, CategoryDirectionValue.EXPENSE));
        assertThat(result.id().jsonValue()).isEqualTo("101");
        org.mockito.Mockito.verify(categories, org.mockito.Mockito.never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsInvalidCombinationsAndWhitespaceAndExpandedKey() {
        assertThatThrownBy(() -> service.createOrReuse(7L,
                new CategoryInput("Rent", CategoryScopeValue.FIXED_BUDGET, CategoryDirectionValue.EXPENSE)))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.createOrReuse(7L,
                new CategoryInput("Lunch", CategoryScopeValue.MAIN_TRANSACTION, null)))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.createOrReuse(7L,
                new CategoryInput("\u2003", CategoryScopeValue.FIXED_BUDGET, null)))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.createOrReuse(7L,
                new CategoryInput("\u00a0", CategoryScopeValue.FIXED_BUDGET, null)))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.createOrReuse(7L,
                new CategoryInput("\u0130".repeat(51), CategoryScopeValue.FIXED_BUDGET, null)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void recoversOnlyNamedDuplicateAfterFailedInsert() {
        Category existing = category(105L, CategoryScope.MAIN_TRANSACTION, CategoryDirection.INCOME, "Salary");
        when(users.findById(7L)).thenReturn(Optional.of(existing.getUser()));
        when(categories.findByUser_IdAndScopeAndDirectionAndNameKey(7L,
                CategoryScope.MAIN_TRANSACTION, CategoryDirection.INCOME, "salary"))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(categories.saveAndFlush(org.mockito.ArgumentMatchers.any(Category.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate",
                        new SQLException("Duplicate entry for key 'uq_categories_name'", "23000", 1062)));

        var result = service.createOrReuse(7L,
                new CategoryInput(" Salary ", CategoryScopeValue.MAIN_TRANSACTION, CategoryDirectionValue.INCOME));

        assertThat(result.id().jsonValue()).isEqualTo("105");
        verify(categories, org.mockito.Mockito.times(2)).findByUser_IdAndScopeAndDirectionAndNameKey(
                7L, CategoryScope.MAIN_TRANSACTION, CategoryDirection.INCOME, "salary");
    }

    @Test
    void unrelatedIntegrityErrorIsNotHidden() {
        Category sample = category(105L, CategoryScope.MAIN_TRANSACTION, CategoryDirection.INCOME, "Salary");
        when(users.findById(7L)).thenReturn(Optional.of(sample.getUser()));
        DataIntegrityViolationException failure = new DataIntegrityViolationException("foreign key",
                new SQLException("Cannot add or update child row", "23000", 1452));
        when(categories.saveAndFlush(org.mockito.ArgumentMatchers.any(Category.class))).thenThrow(failure);
        assertThatThrownBy(() -> service.createOrReuse(7L,
                new CategoryInput("Salary", CategoryScopeValue.MAIN_TRANSACTION, CategoryDirectionValue.INCOME)))
                .isSameAs(failure);
    }

    @Test
    void unrelatedDuplicateKeyIsNotHidden() {
        Category sample = category(105L, CategoryScope.MAIN_TRANSACTION, CategoryDirection.INCOME, "Salary");
        when(users.findById(7L)).thenReturn(Optional.of(sample.getUser()));
        DataIntegrityViolationException failure = new DataIntegrityViolationException("other unique key",
                new SQLException("Duplicate entry for key 'uq_other'", "23000", 1062));
        when(categories.saveAndFlush(org.mockito.ArgumentMatchers.any(Category.class))).thenThrow(failure);
        assertThatThrownBy(() -> service.createOrReuse(7L,
                new CategoryInput("Salary", CategoryScopeValue.MAIN_TRANSACTION, CategoryDirectionValue.INCOME)))
                .isSameAs(failure);
    }

    private Category category(Long id, CategoryScope scope, CategoryDirection direction, String name) {
        User user = new User("a@example.com", "a@example.com", "A", "hash", "v1", LocalDateTime.now());
        ReflectionTestUtils.setField(user, "id", 7L);
        Category category = new Category(user, scope, direction, name, name.toLowerCase());
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }
}
