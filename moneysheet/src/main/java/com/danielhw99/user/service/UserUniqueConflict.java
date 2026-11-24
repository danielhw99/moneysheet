package com.danielhw99.user.service;

import java.sql.SQLException;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;

/** Translate only known MySQL user unique constraints; preserve unexpected integrity errors. */
public final class UserUniqueConflict {
    private UserUniqueConflict() { }

    public static ApiException nickname() {
        return new ApiException(ErrorCode.CONFLICT, "nickname", "이미 사용 중인 닉네임입니다.");
    }

    public static RuntimeException translate(DataIntegrityViolationException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getErrorCode() == 1062) {
                String message = sql.getMessage();
                int keyStart = message == null ? -1 : message.lastIndexOf(" for key '");
                if (keyStart >= 0 && message.endsWith("'")) {
                    String qualifiedKey = message.substring(keyStart + " for key '".length(), message.length() - 1);
                    String key = qualifiedKey.substring(qualifiedKey.lastIndexOf('.') + 1);
                    if (key.equals("uq_users_nickname_key")) return nickname();
                    if (key.equals("uq_users_email")) return new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
                }
            }
        }
        return failure;
    }
}
