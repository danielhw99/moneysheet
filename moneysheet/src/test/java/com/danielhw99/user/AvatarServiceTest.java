package com.danielhw99.user;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import com.danielhw99.user.entity.User;
import com.danielhw99.user.repository.UserRepository;
import com.danielhw99.user.repository.AvatarImageRepository;
import com.danielhw99.user.service.AvatarNormalizer;
import com.danielhw99.user.service.AvatarService;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;

class AvatarServiceTest {
    final UserRepository users = mock(UserRepository.class);
    final AvatarImageRepository images = mock(AvatarImageRepository.class);
    final AvatarNormalizer normalizer = mock(AvatarNormalizer.class);
    final AvatarService service = new AvatarService(users, images, normalizer);
    final MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1});
    User user() {
        var user = new User("a@example.com", "a@example.com", "Nick", "hash", "v1", LocalDateTime.now());
        ReflectionTestUtils.setField(user, "id", 7L);
        when(users.findWithLockingById(7L)).thenReturn(Optional.of(user));
        return user;
    }
    @Test void savesNormalizedImageAndReturnsVersionedProfileWithoutRevokingSessions() {
        var user = user(); byte[] bytes = {1,2,3}; when(normalizer.normalize(file)).thenReturn(bytes);
        var result = service.save(7L, file);
        verify(images).save(7L, bytes);
        assertThat(result.avatarUrl()).matches("/api/v1/me/avatar\\?v=[a-f0-9]{32}");
        assertThat(result.nickname()).isEqualTo("Nick"); assertThat(user.getSessionVersion()).isZero();
    }
    @Test void invalidPhotoLeavesPreviousReferenceAndImageUntouched() {
        var user = user();user.setAvatarVersion("old");
        when(normalizer.normalize(file)).thenThrow(new ApiException(ErrorCode.VALIDATION));
        assertThatThrownBy(() -> service.save(7L, file)).isInstanceOf(ApiException.class);
        verifyNoInteractions(images); assertThat(user.getAvatarVersion()).isEqualTo("old");
    }
    @Test void deleteRestoresInitialsAndIsIdempotentForMissingPhoto() {
        var user = user();user.setAvatarVersion("old");
        assertThat(service.delete(7L).avatarUrl()).isNull();
        assertThat(service.delete(7L).avatarUrl()).isNull();
        verify(images, times(2)).delete(7L); assertThat(user.getSessionVersion()).isZero();
    }
    @Test void missingImageIsNotFoundAndMissingAccountCannotWrite() {
        when(images.find(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.read(7L)).isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
        when(normalizer.normalize(file)).thenReturn(new byte[]{1});
        assertThatThrownBy(() -> service.save(7L,file)).isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.UNAUTHENTICATED);
        verify(images, never()).save(anyLong(), any());
    }
}
