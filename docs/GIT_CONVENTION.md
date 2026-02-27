# Git Convention

---

## 1. Issue
- Jira로 모든 이슈 관리 (GitHub Issue 사용 X)

## 2. Branch
**생성**: Jira 이슈에서 직접 생성 (base: `dev`)

**이름**: `타입/이슈키-작업요약` (ex. `feat/DOC-1-login-api-implementation`)

**타입**:
- `feat`: 새로운 기능
- `fix`: 버그 수정
- `refactor`: 리팩토링
- `chore`: 빌드, 패키지 설정
- `docs`: 문서 수정


## 3. Commit
**형식**:
```
타입: [이슈키] 제목

본문 (선택)

이슈키 #time 작업시간
```

**예시**:
```
feat: [DOC-1] JWT 발급 로직 구현

보안 정책에 따라 만료 시간 30분 설정

DOC-1 #time 4.5h
```

**규칙**:
- 작업 최소 단위로 커밋
- 마지막 커밋이나 PR 수정 커밋에 `#time`으로 작업 시간 기록 (0.5h 단위)
- Jira 이메일과 Git 이메일 일치 필수

**Rebase**:
- 불필요한 Merge Commit 방지를 위해 반드시 Rebase 사용
```bash
# 원격 변경사항 가져오기
git pull --rebase origin dev
# 충돌 해결 시
git add .
git rebase --continue
# GitHub에 반영 (강제 푸시)
git push --force-with-lease
```

## 4. Pull Request
**제목**: `[이슈키] 이슈 제목` (ex. `[DOC-1] 로그인 API 구현`)

**본문**:
- PR 템플릿을 준수, 해당 사항 없는 항목은 삭제
- Jira 이슈 등록: 리뷰어의 편의를 위해 이슈 링크를 삽입

**프로세스**:
1. Jira에서 PR 생성 (`dev ← feat/브랜치`)
2. 리뷰어가 리뷰 및 코멘트
3. 작업자는 수정 후 추가 커밋. 필요 시 PR 본문도 수정
4. 리뷰어가 **Squash and merge** 실행
    - Merge Message는 커밋 규칙에 따라 수정하고, 본문은 "작업 내용" 외 삭제하여 정리
5. 작업 브랜치 자동 삭제

**주의**:
- 변경 라인 400줄 이하 권장
- PR 본문에 Jira 이슈 링크 삽입

## 5. 협업 주의사항
- 작업 전후로 `git pull --rebase origin dev`로 최신 상태 유지
- PR 쌓기 금지: 선행 PR 머지 대기 후 작업
- 설정 파일 변경 시 팀원에게 사전 공유