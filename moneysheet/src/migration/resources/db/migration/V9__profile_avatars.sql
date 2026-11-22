ALTER TABLE users ADD COLUMN avatar_version VARCHAR(32) NULL;

CREATE TABLE user_profile_images (
    user_id BIGINT NOT NULL,
    image_data MEDIUMBLOB NOT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id),
    CONSTRAINT fk_profile_images_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_profile_images_size CHECK (OCTET_LENGTH(image_data) BETWEEN 1 AND 524288)
) ENGINE=InnoDB;
