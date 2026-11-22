package com.danielhw99.auth.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import com.danielhw99.common.api.ApiId;
import com.danielhw99.user.entity.User;
public record Profile(@Schema(requiredMode=Schema.RequiredMode.REQUIRED) ApiId id, @Schema(requiredMode=Schema.RequiredMode.REQUIRED) String email, @Schema(requiredMode=Schema.RequiredMode.REQUIRED) String nickname, @Schema(requiredMode=Schema.RequiredMode.REQUIRED, nullable=true, description="본인 전용 프로필 사진 상대 경로. 사진이 없으면 null") String avatarUrl) {
    public Profile(ApiId id, String email, String nickname) { this(id, email, nickname, null); }
    public static String avatarUrl(String version) { return version == null ? null : "/api/v1/me/avatar?v=" + version; }
    public static Profile from(User user) {
        return new Profile(new ApiId(user.getId()), user.getEmail(), user.getNickname(), avatarUrl(user.getAvatarVersion()));
    }
}
