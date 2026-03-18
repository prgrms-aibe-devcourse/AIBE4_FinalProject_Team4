package kr.java.documind.domain.issue.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class IssueResolvedEventListener {

    // TODO: PatchNoteService 주입 (패치노트 자동 생성 기능 구현 후 연결)
    // private final PatchNoteService patchNoteService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueResolved(IssueResolvedEvent event) {
        try {
            log.info(
                    "[IssueResolvedEventListener] 이슈 해결됨 — issueId={}, projectId={}, title='{}'",
                    event.issueId(),
                    event.projectId(),
                    event.title());

            // TODO: AI 패치노트 생성 트리거
            // patchNoteService.generatePatchNote(event);

            log.debug(
                    "[IssueResolvedEventListener] AI 패치노트 생성 트리거 완료 (미구현) — issueId={}",
                    event.issueId());

        } catch (Exception e) {
            log.error(
                    "[IssueResolvedEventListener] AI 패치노트 생성 실패 — issueId={}, projectId={}",
                    event.issueId(),
                    event.projectId(),
                    e);
        }
    }
}
