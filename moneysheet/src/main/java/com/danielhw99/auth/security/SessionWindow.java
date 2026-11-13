package com.danielhw99.auth.security;
import java.time.Duration;
import java.time.Instant;
public final class SessionWindow {
    private SessionWindow() { }
    public static boolean expired(Instant login, Instant lastSeen, Instant now) {
        return login==null || lastSeen==null || login.isAfter(now) || lastSeen.isAfter(now)
                || !now.isBefore(login.plus(Duration.ofHours(12)))
                || !now.isBefore(lastSeen.plus(Duration.ofMinutes(30)));
    }
}
