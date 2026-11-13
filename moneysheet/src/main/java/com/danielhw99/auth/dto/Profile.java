package com.danielhw99.auth.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import com.danielhw99.common.api.ApiId;
import com.danielhw99.user.entity.User;
public record Profile(@Schema(requiredMode=Schema.RequiredMode.REQUIRED) ApiId id, @Schema(requiredMode=Schema.RequiredMode.REQUIRED) String email, @Schema(requiredMode=Schema.RequiredMode.REQUIRED) String nickname) {
    public static Profile from(User user) {
        return new Profile(new ApiId(user.getId()), user.getEmail(), user.getNickname());
    }
}
