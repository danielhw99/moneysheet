package com.danielhw99.category;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalDateTime;
import java.util.Optional;
import com.danielhw99.category.entity.Category;
import com.danielhw99.category.entity.CategoryDirection;
import com.danielhw99.category.entity.CategoryScope;
import com.danielhw99.category.repository.CategoryRepository;
import com.danielhw99.category.service.CategoryService;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import com.danielhw99.user.entity.User;
import com.danielhw99.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CategoryManagementTest {
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CategoryService service = new CategoryService(categories, users);
    private Category original;

    @BeforeEach void setup() {
        var owner = new User("a@example.com", "a@example.com", "A", "hash", "v1", LocalDateTime.now());
        ReflectionTestUtils.setField(owner, "id", 7L);
        original = category(101L, owner, "Food");
        when(users.findWithLockingById(7L)).thenReturn(Optional.of(owner));
        when(categories.findById(101L)).thenReturn(Optional.of(original));
    }

    @Test void renamePreservesIdAndScopeAndNormalizesName() {
        var result = service.update(7L, 101L, "  New Food  ");
        assertThat(result.id().value()).isEqualTo(101L);
        assertThat(result.name()).isEqualTo("New Food");
        assertThat(original.getNameKey()).isEqualTo("new food");
        assertThat(original.getScope()).isEqualTo(CategoryScope.MAIN_TRANSACTION);
        assertThat(original.getDirection()).isEqualTo(CategoryDirection.EXPENSE);
        var order = inOrder(users, categories);
        order.verify(users).findWithLockingById(7L);
        order.verify(categories).findById(101L);
    }

    @Test void duplicateRenameHasNameErrorAndDoesNotModifyOriginal() {
        var other = category(102L, original.getUser(), "Rent");
        when(categories.findByUser_IdAndScopeAndDirectionAndNameKey(7L,
                CategoryScope.MAIN_TRANSACTION, CategoryDirection.EXPENSE, "rent"))
                .thenReturn(Optional.of(other));
        var error = catchThrowableOfType(ApiException.class, () -> service.update(7L, 101L, " RENT "));
        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
        assertThat(error.getFieldErrors()).singleElement().extracting("field").isEqualTo("name");
        assertThat(original.getName()).isEqualTo("Food");
        verify(categories, never()).delete(any());
    }

    @Test void sameIdIsNotADuplicateAndUnicodeBlankIsRejected() {
        when(categories.findByUser_IdAndScopeAndDirectionAndNameKey(7L,
                CategoryScope.MAIN_TRANSACTION, CategoryDirection.EXPENSE, "food"))
                .thenReturn(Optional.of(original));
        assertThat(service.update(7L, 101L, " FOOD ").name()).isEqualTo("FOOD");
        assertThatThrownBy(() -> service.update(7L, 101L, "\u00a0\u2003"))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.VALIDATION);
    }

    @Test void ownerDeletionAndRepeatDeletionFollowContract() {
        when(categories.findById(101L)).thenReturn(Optional.of(original), Optional.empty());
        service.delete(7L, 101L);
        verify(categories).delete(original);
        assertThatThrownBy(() -> service.delete(7L, 101L)).isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test void foreignAndMissingIdsAreNotFound() {
        ReflectionTestUtils.setField(original.getUser(), "id", 8L);
        assertThatThrownBy(() -> service.update(7L, 101L, "New"))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
        assertThatThrownBy(() -> service.delete(7L, 101L))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
        assertThatThrownBy(() -> service.delete(7L, 999L))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
        verify(categories, never()).delete(any());
    }

    private Category category(Long id, User owner, String name) {
        var value = new Category(owner, CategoryScope.MAIN_TRANSACTION, CategoryDirection.EXPENSE,
                name, name.toLowerCase(java.util.Locale.ROOT));
        ReflectionTestUtils.setField(value, "id", id);
        return value;
    }
}
