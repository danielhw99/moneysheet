package com.danielhw99.user.service;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.danielhw99.auth.dto.Profile;
import com.danielhw99.auth.security.ActiveAccount;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import com.danielhw99.user.entity.User;
import com.danielhw99.user.repository.UserRepository;
import com.danielhw99.user.repository.AvatarImageRepository;

@Service
public class AvatarService {
    private final UserRepository users;
    private final AvatarImageRepository images;
    private final AvatarNormalizer normalizer;
    public AvatarService(UserRepository users, AvatarImageRepository images, AvatarNormalizer normalizer) {
        this.users = users; this.images = images; this.normalizer = normalizer;
    }
    private User lock(Long id) {
        return ActiveAccount.requireCurrent(users.findWithLockingById(id).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED)));
    }
    @Transactional(readOnly = true)
    public byte[] read(Long id) { return images.find(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND)); }
    @Transactional
    public Profile save(Long id, MultipartFile file) {
        byte[] bytes = normalizer.normalize(file);
        var user = lock(id);
        images.save(id, bytes);
        user.setAvatarVersion(UUID.randomUUID().toString().replace("-", ""));
        return Profile.from(user);
    }
    @Transactional
    public Profile delete(Long id) {
        var user = lock(id);
        images.delete(id); user.setAvatarVersion(null);
        return Profile.from(user);
    }
}
