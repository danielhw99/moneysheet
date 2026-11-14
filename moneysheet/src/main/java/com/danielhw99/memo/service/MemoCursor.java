package com.danielhw99.memo.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import com.danielhw99.common.api.ApiFormats;
import com.danielhw99.common.api.ApiId;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import com.danielhw99.memo.api.MemoStatus;
import org.springframework.stereotype.Component;

@Component
public class MemoCursor {
    public record Anchor(LocalDate date, LocalTime time, Long id) {}
    private final SecretKeySpec key;
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    public MemoCursor() {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        key = new SecretKeySpec(bytes, "HmacSHA256");
    }
    private String scope(Long owner, YearMonth month, LocalDate date, MemoStatus status) {
        return "memo|" + owner + "|" + ApiFormats.formatMonth(month) + "|" + (date == null ? "-" : ApiFormats.formatDate(date))
                + "|" + (status == null ? "-" : status.getValue());
    }
    public String encode(Long owner, YearMonth month, LocalDate date, MemoStatus status, Anchor anchor) {
        byte[] payload = (scope(owner,month,date,status) + "|" + ApiFormats.formatDate(anchor.date())
                + "|" + ApiFormats.formatTime(anchor.time()) + "|" + anchor.id()).getBytes(StandardCharsets.UTF_8);
        return encoder.encodeToString(payload) + "." + encoder.encodeToString(sign(payload));
    }
    public Anchor decode(String token, Long owner, YearMonth month, LocalDate date, MemoStatus status) {
        if (token == null) return null;
        if (token.length() > 2048) throw new ApiException(ErrorCode.VALIDATION);
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2) throw new IllegalArgumentException();
            byte[] payload = Base64.getUrlDecoder().decode(parts[0]);
            byte[] signature = Base64.getUrlDecoder().decode(parts[1]);
            if (!encoder.encodeToString(payload).equals(parts[0]) || !encoder.encodeToString(signature).equals(parts[1])
                    || !MessageDigest.isEqual(sign(payload), signature)) throw new IllegalArgumentException();
            String value = new String(payload, StandardCharsets.UTF_8);
            String prefix = scope(owner,month,date,status) + "|";
            if (!value.startsWith(prefix)) throw new IllegalArgumentException();
            String[] position = value.substring(prefix.length()).split("\\|", -1);
            if (position.length != 3) throw new IllegalArgumentException();
            Anchor anchor = new Anchor(ApiFormats.parseDate(position[0]),ApiFormats.parseTime(position[1]),ApiId.parse(position[2]).value());
            if (!YearMonth.from(anchor.date()).equals(month) || anchor.date().isAfter(date) || (status == MemoStatus.ALL && !date.equals(anchor.date()))) throw new IllegalArgumentException();
            return anchor;
        } catch (IllegalArgumentException | java.time.DateTimeException invalid) {
            throw new ApiException(ErrorCode.VALIDATION);
        }
    }
    private byte[] sign(byte[] value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(key); return mac.doFinal(value);
        } catch (GeneralSecurityException failure) { throw new IllegalStateException("Cursor signing unavailable",failure); }
    }
}
