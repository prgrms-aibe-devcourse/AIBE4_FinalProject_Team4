package kr.java.documind.domain.patchnote.exception;

public class IssueInsufficientInfoException extends RuntimeException {

    public IssueInsufficientInfoException(Long issueId) {
        super("이슈 description·resolutionNote가 모두 부족하여 패치노트 피드에 추가할 수 없습니다. issueId: " + issueId);
    }
}
