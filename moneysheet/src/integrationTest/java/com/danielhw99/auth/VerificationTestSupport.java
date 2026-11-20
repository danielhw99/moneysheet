package com.danielhw99.auth;

import com.danielhw99.auth.email.EmailVerificationService;
import com.danielhw99.auth.email.VerificationMailSender;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods=false)
public class VerificationTestSupport {
    public static class Mailbox implements VerificationMailSender {
        public final ConcurrentHashMap<String,String> codes=new ConcurrentHashMap<>();
        public volatile boolean fail;
        public void send(String email,String code){if(fail)throw new IllegalStateException("test mail failure");codes.put(email,code);}
    }
    @Bean @Primary Mailbox testMailbox(){return new Mailbox();}
    public static String proof(EmailVerificationService service,Mailbox mailbox,String email) {
        var sent=service.request(email);
        return service.confirm(email,sent.challengeId(),mailbox.codes.get(email)).verificationToken();
    }
}
