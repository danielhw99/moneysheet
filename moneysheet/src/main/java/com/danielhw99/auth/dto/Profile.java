package com.danielhw99.auth.dto;
import com.danielhw99.common.api.ApiId;
import com.danielhw99.user.entity.User;
public record Profile(ApiId id, String email, String nickname) {
    public static Profile from(User user) {
        return new Profile(new ApiId(user.getId()), user.getEmail(), user.getNickname());
    }
}
