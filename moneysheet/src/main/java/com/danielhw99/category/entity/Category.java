package com.danielhw99.category.entity;

import com.danielhw99.common.persistence.BaseEntity;
import com.danielhw99.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "categories")
public class Category extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "scope", nullable = false, length = 30)
    private CategoryScope scope;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "direction", nullable = false, length = 10)
    private CategoryDirection direction;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "name_key", nullable = false, length = 100)
    private String nameKey;

    protected Category() {
    }

    public Category(User user, CategoryScope scope, CategoryDirection direction, String name, String nameKey) {
        this.user = user;
        this.scope = scope;
        this.direction = direction;
        this.name = name;
        this.nameKey = nameKey;
    }

    public User getUser() {
        return user;
    }

    public CategoryScope getScope() {
        return scope;
    }

    public CategoryDirection getDirection() {
        return direction;
    }

    public String getName() {
        return name;
    }

    public String getNameKey() {
        return nameKey;
    }
}
