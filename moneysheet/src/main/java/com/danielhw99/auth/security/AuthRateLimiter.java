package com.danielhw99.auth.security;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;

import org.springframework.stereotype.Component;

@Component
public class AuthRateLimiter {
    private static final int MAX_IP_KEYS = 10000;
    private static final int MAX_ACCOUNT_KEYS = 10000;
    private static final int ACCOUNT_LIMIT = 5;
    private static final long ACCOUNT_WINDOW_SECONDS = 900;

    private record Window(int count, long expires) { }

    private static final class AccountWindow {
        private int failures;
        private int inFlight;
        private long expires;

        private AccountWindow(long expires) { this.expires = expires; }
    }

    private enum Outcome { SUCCEEDED, FAILED, CANCELLED }

    public final class Attempt implements AutoCloseable {
        private final String email;
        private final AccountWindow window;
        private boolean completed;

        private Attempt(String email, AccountWindow window) {
            this.email = email;
            this.window = window;
        }

        public void failed() { complete(this, Outcome.FAILED); }
        public void succeeded() { complete(this, Outcome.SUCCEEDED); }
        @Override public void close() { complete(this, Outcome.CANCELLED); }
    }

    private final Map<String, Window> signupWindows = new HashMap<>();
    private final Map<String, Window> loginWindows = new HashMap<>();
    private final Map<String, AccountWindow> accountWindows = new HashMap<>();
    private final Clock clock;

    public AuthRateLimiter(Clock clock) { this.clock = clock; }

    public synchronized void request(String ip, boolean signup) {
        Map<String, Window> windows = signup ? signupWindows : loginWindows;
        long now = clock.instant().getEpochSecond();
        Window window = active(windows, ip, now);
        int limit = signup ? 10 : 30;
        if (window != null && window.count() >= limit) limited(window.expires() - now);
        if (!windows.containsKey(ip) && windows.size() >= MAX_IP_KEYS) {
            windows.values().removeIf(value -> value.expires() <= now);
            if (windows.size() >= MAX_IP_KEYS) limited(60);
        }
        windows.put(ip, new Window(window == null ? 1 : window.count() + 1,
                window == null ? now + (signup ? 3600 : 60) : window.expires()));
    }

    // The failure check and reservation share the same monitor, not the expensive hash calculation.
    public synchronized Attempt beginAttempt(String email) {
        long now = clock.instant().getEpochSecond();
        AccountWindow window = accountWindows.get(email);
        if (window != null && window.expires <= now) {
            window.failures = 0;
            if (window.inFlight == 0) {
                accountWindows.remove(email);
                window = null;
            }
        }
        if (window == null) {
            if (accountWindows.size() >= MAX_ACCOUNT_KEYS) {
                // Never evict a live reservation, including one spanning a TTL boundary.
                accountWindows.values().removeIf(value -> value.inFlight == 0 && value.expires <= now);
                if (accountWindows.size() >= MAX_ACCOUNT_KEYS) limited(60);
            }
            window = new AccountWindow(now + ACCOUNT_WINDOW_SECONDS);
            accountWindows.put(email, window);
        }
        if (window.failures >= ACCOUNT_LIMIT) limited(window.expires - now);
        if (window.failures + window.inFlight >= ACCOUNT_LIMIT) limited(1);
        window.inFlight++;
        return new Attempt(email, window);
    }

    private synchronized void complete(Attempt attempt, Outcome outcome) {
        if (attempt.completed) return;
        attempt.completed = true;
        AccountWindow window = attempt.window;
        window.inFlight--;
        long now = clock.instant().getEpochSecond();
        if (outcome == Outcome.SUCCEEDED) {
            window.failures = 0;
            window.expires = now + ACCOUNT_WINDOW_SECONDS;
        } else if (outcome == Outcome.FAILED) {
            if (window.expires <= now) window.failures = 0;
            if (window.failures == 0) window.expires = now + ACCOUNT_WINDOW_SECONDS;
            window.failures++;
            if (window.failures == ACCOUNT_LIMIT) window.expires = now + ACCOUNT_WINDOW_SECONDS;
        }
        if (window.inFlight == 0 && (window.failures == 0 || window.expires <= now)) {
            accountWindows.remove(attempt.email, window);
        }
    }

    private Window active(Map<String, Window> windows, String key, long now) {
        Window window = windows.get(key);
        if (window != null && window.expires() <= now) {
            windows.remove(key);
            return null;
        }
        return window;
    }

    private void limited(long seconds) {
        throw new ApiException(ErrorCode.RATE_LIMITED, Math.max(1, seconds));
    }
}
