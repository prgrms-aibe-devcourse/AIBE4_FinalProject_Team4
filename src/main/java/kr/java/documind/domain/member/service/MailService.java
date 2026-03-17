package kr.java.documind.domain.member.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import kr.java.documind.domain.member.event.InvitationCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private static final DateTimeFormatter EXPIRES_AT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH:mm");

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    @Value("${app.oauth2.redirect-base-url}")
    private String baseUrl;

    @Value("${app.mail.from-address}")
    private String fromAddress;

    public void sendInvitationEmail(InvitationCreatedEvent event) throws MessagingException {
        String inviteUrl = baseUrl + "/invite?token=" + event.rawToken();
        String expiresAtFormatted =
                event.expiresAt().atZoneSameInstant(ZoneId.of("Asia/Seoul")).format(EXPIRES_AT_FORMATTER);

        Context ctx = new Context();
        ctx.setVariable("inviterName", event.inviterName());
        ctx.setVariable("projectName", event.projectName());
        ctx.setVariable("targetEmail", event.targetEmail());
        ctx.setVariable("inviteUrl", inviteUrl);
        ctx.setVariable("expiresAt", expiresAtFormatted);

        String html = templateEngine.process("email/invitation", ctx);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(fromAddress);
        helper.setTo(event.targetEmail());
        helper.setSubject("[DocuMind] " + event.projectName() + " 프로젝트 초대");
        helper.setText(html, true); // true = HTML 형식

        mailSender.send(message);

        log.info(
                "[MailService] 초대 메일 발송 완료 — invitationId={} targetEmail={}",
                event.invitationId(),
                event.targetEmail());
    }
}
