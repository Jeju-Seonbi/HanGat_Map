package com.example.hangat.config.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 인증 메일 발송 - 비밀번호 찾기, 카카오 소셜 로그인 + 가입할 때 이용함.
 * @Async를 이용해서 비동기 처리
 *  이유 - 메일 발송은 느리기 때문에 유저가 기다려야함.
 *          + 메일 발송 처리는 다른 스레드에서 실행되는데
 *              실패할 경우 사용하는 서비스들이 트랜잭션이라 안 닿을 수 있음.
 */
@Slf4j
@Component
public class AuthMailSender {

    private final JavaMailSender mailSender;
    private final String from;
    private final String frontendUrl;

    public AuthMailSender(
            JavaMailSender mailSender,
            @Value("${app.mail.from:${spring.mail.username}}") String from,
            @Value("${app.frontend-url}") String frontendUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.frontendUrl = frontendUrl;
    }

    // 가입 인증 링크
    @Async
    public void sendVerification(String to, String token) {
        String link = frontendUrl + "/verify?token=" + token;
        send(to, "[한갓지도] 이메일 인증을 완료해주세요",
                "아래 링크를 눌러 인증을 완료해주세요. 24시간 동안 유효합니다.\n\n" + link);
    }

    /** 비밀번호 재설정 6자리 코드 */
    @Async
    public void sendResetCode(String to, String code) {
        send(to, "[한갓지도] 비밀번호 재설정 코드",
                "인증 코드: " + code + "\n\n10분 안에 입력해주세요. 5회 틀리면 코드가 폐기됩니다.\n"
                        + "본인이 요청하지 않았다면 이 메일을 무시하세요.");
    }

    private void send(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
        } catch (Exception e) {
            // 던지면 @Async 스레드에서 죽어서 호출자가 못 받음. 로그만 남기고 재발송으로 구제
            log.error("메일 발송 실패 to={}, subject={}", to, subject, e);
        }
    }
}
