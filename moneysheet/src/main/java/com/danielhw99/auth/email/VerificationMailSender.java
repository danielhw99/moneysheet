package com.danielhw99.auth.email;

public interface VerificationMailSender {
    void send(String email,String code);
}
