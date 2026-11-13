package com.danielhw99.auth.security;
import java.io.Serializable;
import java.security.Principal;
import com.danielhw99.auth.dto.Profile;
import com.danielhw99.common.api.ApiId;
import com.danielhw99.user.entity.User;
public record AuthPrincipal(Long userId, String email, String nickname) implements Principal, Serializable {
    public static AuthPrincipal from(User user) { return new AuthPrincipal(user.getId(),user.getEmail(),user.getNickname()); }
    public Profile profile() { return new Profile(new ApiId(userId),email,nickname); }
    @Override public String getName() { return userId.toString(); }
    @Override public String toString() { return "AuthPrincipal[userId="+userId+"]"; }
}
