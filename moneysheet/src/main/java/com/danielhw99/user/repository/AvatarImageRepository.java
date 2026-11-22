package com.danielhw99.user.repository;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AvatarImageRepository {
    private final JdbcTemplate jdbc;
    public AvatarImageRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<byte[]> find(Long userId) {
        return jdbc.query("SELECT image_data FROM user_profile_images WHERE user_id=?",
                result -> result.next() ? Optional.of(result.getBytes(1)) : Optional.empty(), userId);
    }
    public void save(Long userId, byte[] data) {
        jdbc.update("INSERT INTO user_profile_images(user_id,image_data) VALUES (?,?) ON DUPLICATE KEY UPDATE image_data=?,updated_at=CURRENT_TIMESTAMP(6)", userId, data, data);
    }
    public void delete(Long userId) { jdbc.update("DELETE FROM user_profile_images WHERE user_id=?", userId); }
}
