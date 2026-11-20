package com.danielhw99.auth.email;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpVerificationMailSender implements VerificationMailSender {
    private final ObjectProvider<JavaMailSender> sender;
    private final String from;
    public SmtpVerificationMailSender(ObjectProvider<JavaMailSender> sender,
            @Value("${moneysheet.mail.from:moneysheet.site@gmail.com}") String from) {
        this.sender=sender;this.from=from;
    }
    public void send(String email,String code) {
        var client=sender.getIfAvailable();
        if(client==null) throw new IllegalStateException("SMTP is not configured");
        var message=new SimpleMailMessage();
        message.setFrom(from);message.setTo(email);message.setSubject("[MoneySheet] 회원가입 이메일 인증번호");
        message.setText("MoneySheet 인증번호: "+code+"\n10분 이내에 가입 화면에 입력해 주세요.\n본인이 요청하지 않았다면 이 메일을 무시해 주세요.");
        client.send(message);
    }
}
