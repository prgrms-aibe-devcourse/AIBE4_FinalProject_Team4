package kr.java.documind.domain.member.event;

import kr.java.documind.domain.member.service.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class InvitationEmailListener {

    private final MailService mailService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInvitationCreated(InvitationCreatedEvent event) {
        try {
            mailService.sendInvitationEmail(event);
        } catch (Exception e) {
            log.error(
                    "[InvitationEmailListener] 초대 메일 발송 실패 — invitationId={} targetEmail={}. "
                            + "운영자가 재초대를 통해 복구할 수 있습니다.",
                    event.invitationId(),
                    event.targetEmail(),
                    e);
        }
    }
}
