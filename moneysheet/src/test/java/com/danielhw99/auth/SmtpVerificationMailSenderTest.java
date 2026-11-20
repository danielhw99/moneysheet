package com.danielhw99.auth;

import com.danielhw99.auth.email.SmtpVerificationMailSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmtpVerificationMailSenderTest {
    @Test void usesConfiguredSenderAndNeverReturnsTheCodeToTheCaller() {
        ObjectProvider<JavaMailSender> provider=mock(ObjectProvider.class);
        var client=mock(JavaMailSender.class);when(provider.getIfAvailable()).thenReturn(client);
        new SmtpVerificationMailSender(provider,"moneysheet.site@gmail.com").send("recipient@example.com","001234");
        var message=org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);verify(client).send(message.capture());
        assertThat(message.getValue().getFrom()).isEqualTo("moneysheet.site@gmail.com");
        assertThat(message.getValue().getTo()).containsExactly("recipient@example.com");
        assertThat(message.getValue().getText()).contains("001234","10분");
    }
}
