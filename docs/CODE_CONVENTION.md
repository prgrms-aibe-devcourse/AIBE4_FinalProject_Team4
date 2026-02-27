# Code Convention

## 1. 명명 규칙

### 1.1 일반 원칙

| 대상 | 규칙 | 예시 |
| --- | --- | --- |
| Package | 소문자, 하이픈/언더스코어 금지 | `domain.member` |
| Class | PascalCase | `MemberService` |
| Method / Variable | camelCase | `findMember()`, `totalCount` |
| Constant | UPPER_SNAKE_CASE | `MAX_LOGIN_RETRY` |
| HTML 파일 | kebab-case | `member-list.html` |

### 1.2 클래스 Suffix 규칙

| 종류 | 규칙 | 예시 |
| --- | --- | --- |
| API Controller | `도메인 + ApiController` | `MemberApiController` |
| View Controller | `도메인 + ViewController` | `MemberViewController` |
| 이벤트 객체 | `도메인 + 동사 + Event` | `IssueCreatedEvent` |
| 이벤트 리스너 | `도메인 + 대상 + Listener` | `IssueNotificationListener` |

### 1.3 DTO 명명 규칙

| 용도 | 규칙 | 예시 |
| --- | --- | --- |
| API 요청 | `도메인 + 동사 + Request` | `MemberJoinRequest` |
| API 응답 (단건) | `도메인 + Detail/Summary` | `MemberDetailResponse` |
| API 응답 (액션) | `도메인 + 동사 + Response` | `MemberJoinResponse` |
| SSR 폼 바인딩 | `도메인 + 동사 + Form` | `MemberJoinForm` |

**금지 패턴**

```
MemberDto         ❌  용도 불명확
MemberRequestDto  ❌  Dto 접미사 중복
CreateMemberDto   ❌  동사가 앞에 옴
```

> SSR `Form` 객체는 `th:object` 바인딩 전용이다. API 요청 객체(`Request`)와 혼용하지 않는다.

---

## 2. API URL 설계

- **View URL** : `/api` 접두사 없음 → `GET /members/join`
- **API URL** : `/api` 접두사 필수 → `GET /api/members/{id}`
- URL에 행위(Verb) 포함 금지 → `/api/getMembers` ❌, `/api/members` ✅

| 기능 | Method | View URL | API URL |
| --- | --- | --- | --- |
| 목록 조회 | GET | `/members` | `/api/members` |
| 단건 조회 | GET | `/members/{id}` | `/api/members/{id}` |
| 생성 | POST | - | `/api/members` |
| 수정 | PUT/PATCH | - | `/api/members/{id}` |
| 삭제 | DELETE | - | `/api/members/{id}` |

---

## 3. 레이어별 작성 규칙

### 3.1 Controller

**`@RestController` (CSR)**

- 반환 타입은 항상 `ApiResponse<T>`
- Entity 직접 반환 금지
- `try-catch` 작성 금지 — 모든 예외는 `GlobalApiExceptionHandler`에 위임

**`@Controller` (SSR)**

- 반환 타입은 `String` (HTML 경로)
- 폼 처리 중 `BusinessException`은 Controller에서 직접 `try-catch` 후 폼 재렌더링
- 단순 조회 실패 등 페이지 이동이 자연스러운 경우는 `GlobalViewExceptionHandler`에 위임

### 3.2 Entity

- `@Data` 금지 → `@Getter`만 사용
- `@NoArgsConstructor(access = AccessLevel.PROTECTED)` 필수
- Setter 금지 → 값 변경은 비즈니스 메서드로 표현 (`changePassword()` 등)

### 3.3 Service

- 클래스 레벨: `@Transactional(readOnly = true)` 기본 적용
- 쓰기 메서드에만 `@Transactional` 추가
- Entity ↔ DTO 변환은 Service에서 수행 (Controller에서 변환 금지)
- SSR/CSR 전략을 알지 못한다 — 항상 Exception만 `throw`

---

## 4. 아키텍처 (Hybrid Monolithic)

기능 성격에 따라 SSR과 CSR 패턴을 구분하여 적용한다.

| 구분 | SSR | CSR |
| --- | --- | --- |
| 담당 컨트롤러 | `ViewController` | `ApiController` |
| 반환값 | HTML 경로 (`String`) | `ApiResponse<T>` |
| 데이터 전달 | `Model`, `FlashAttribute` | JSON Body |
| 적합한 기능 | 단순 CRUD, 정적 페이지 | 검색/필터, 실시간 갱신, 채팅 |

```
[SSR]  브라우저 → GET/POST → ViewController → Service → redirect / 템플릿 렌더링
[CSR]  브라우저 → JS fetch  → ApiController  → Service → JSON 응답
```

---

## 5. 예외 처리

### 5.1 예외 계층 구조

```
RuntimeException
├── BusinessException
│   ├── BadRequestException     400
│   ├── UnauthorizedException   401
│   ├── ForbiddenException      403
│   ├── NotFoundException       404
│   └── ConflictException       409
│       └── (도메인 특화 예외)  예: MenteeCapacityExceededException
└── 자바 표준 예외
    ├── NoSuchElementException    → GlobalHandler 404 처리
    ├── IllegalArgumentException  → GlobalHandler 400 처리
    └── IllegalStateException     → GlobalHandler 409 처리
```

### 5.2 예외 선택 기준

- **표준 예외** — 한 곳에서만 쓰이는 단순한 경우
- **`BusinessException` 하위** — HTTP 상태 코드를 명확히 보장해야 하는 경우
- **도메인 특화 예외** — 같은 메시지가 두 곳 이상 반복되거나, GlobalHandler에서 해당 예외만 따로 처리해야 하는 경우

```text
// 단순 조회 실패 — 표준 예외
repository.findById(id)
    .orElseThrow(() -> new NoSuchElementException("멘토링 정보가 없습니다."));

// 상태 코드 보장 필요 — BusinessException 하위
throw new NotFoundException("멘토링 정보가 없습니다.");

// 여러 곳에서 반복 — 도메인 특화 예외 (domain/xxx/exception/ 하위에 생성)
throw new MenteeCapacityExceededException();
```

**도메인 특화 예외 작성 패턴**

```java
public class MenteeCapacityExceededException extends ConflictException {
    public MenteeCapacityExceededException() {
        super("멘티의 진행 중인 상담 수가 최대입니다.");
    }
}
```

### 5.3 API 공통 응답 (`ApiResponse<T>`)

모든 API 응답은 `ApiResponse<T>`를 사용한다. 성공/실패 여부는 `success` 필드로 구분한다.

```
성공  → { "success": true,  "data": { ... } }
실패  → { "success": false, "error": { "message": "..." } }
검증  → { "success": false, "error": { "message": "...", "details": [ { "field": "...", "reason": "..." } ] } }
```

`details`는 `@JsonInclude(NON_NULL)` 적용으로 `null`이면 응답에서 제외된다.

### 5.4 SSR 예외 처리 흐름

```java
@PostMapping("/join")
public String join(
        @Valid @ModelAttribute("form") MemberJoinForm form,
        BindingResult bindingResult,
        RedirectAttributes redirectAttributes) {

    if (bindingResult.hasErrors()) {        // ① @Valid 실패 → 폼 재렌더링
        return "member/join-form";
    }

    try {
        memberService.join(form);
        redirectAttributes.addFlashAttribute("successMessage", "회원가입 완료");
        return "redirect:/auth/login";      // ② 성공 → PRG 패턴

    } catch (BusinessException e) {
        bindingResult.reject("joinFailed", e.getMessage());
        return "member/join-form";          // ③ 비즈니스 예외 → 폼 재렌더링
    }
    // ④ 그 외 예외 → GlobalViewExceptionHandler → error/500
}
```

**SSR 데이터 전달 규칙**

| 상황 | 이동 방식 | 데이터 운반체 |
| --- | --- | --- |
| 조회 성공/실패 | Forward (URL 유지) | `Model` |
| CUD 성공 | Redirect | `FlashAttribute` |
| CUD 실패 (폼 재렌더링) | Forward (URL 유지) | `BindingResult` |

### 5.5 CSR 예외 처리 흐름

Service는 예외를 `throw`만 한다. `ApiController`에 `try-catch` 작성 금지.

```java
// Service
@Transactional
public void apply(Long mentoringId, Long menteeId) {
    Mentoring mentoring = mentoringRepository.findById(mentoringId)
            .orElseThrow(() -> new NoSuchElementException("멘토링 정보가 없습니다."));

    if (mentee.getActiveConsultationCount() >= 3) {
        throw new MenteeCapacityExceededException();  // 도메인 특화 예외
    }

    if (mentoring.isClosed()) {
        throw new ConflictException("이미 마감된 멘토링입니다.");  // 일회성 — 직접 사용
    }
}

// ApiController — try-catch 없음
@PostMapping("/{mentoringId}/apply")
public ApiResponse<Void> apply(@PathVariable Long mentoringId) {
    mentoringService.apply(mentoringId, memberId);
    return ApiResponse.success("신청이 완료되었습니다.");
}
```

### 5.6 CSR 페이지 간 데이터 전달

서버 `redirect`가 없는 CSR 페이지 전환에서는 `FlashAttribute` 대신 `sessionStorage`를 사용한다.

```javascript
// 송신
sessionStorage.setItem('successMessage', '회원가입이 완료되었습니다.');
window.location.href = '/auth/login';

// 수신
const msg = sessionStorage.getItem('successMessage');
if (msg) {
    document.getElementById('successMessage').textContent = msg;
    sessionStorage.removeItem('successMessage');
}
```

---

## 6. JavaScript 공통 유틸 (fetchUtil)

CSR에서 모든 API 호출은 `fetchUtil.js`의 `callApi()`를 통해 수행한다. 직접 `fetch()`를 호출하지 않는다.

```javascript
// 올바른 사용 패턴
try {
    const body = await callApi('/api/members', {
        method: 'POST',
        body: JSON.stringify(formData),
    });

    if (body.success) {
        // 성공 처리
    } else {
        renderApiError(body.error);  // 필드 에러 또는 글로벌 에러 렌더링
    }
} catch (err) {
    showGlobalError(err.message);    // 네트워크 오류 등
}
```

- `callApi()` 내부에서 Content-Type을 검증하므로 서버가 HTML을 반환해도 안전하게 처리된다.
- 폼 제출 전 반드시 `clearErrors()`를 호출하여 이전 에러를 초기화한다.

---

## 7. 도메인 간 연동 및 이벤트

### 7.1 연동 방식 선택

| 방식 | 사용 조건 |
| --- | --- |
| 서비스 간 직접 호출 | 강한 데이터 정합성 필요, 동기적 흐름 보장이 필요한 경우 |
| 이벤트 리스너 | 메인 로직과 생명주기를 분리해도 되는 경우 (알림, 외부 I/O 등) |

타 도메인 데이터가 필요할 때 Repository 직접 호출 금지 → 해당 도메인의 Service를 호출한다.

### 7.2 이벤트 리스너 구현 규칙

`@EventListener` 사용 금지 → 반드시 `@TransactionalEventListener(phase = AFTER_COMMIT)` 사용

| 방식 | 어노테이션 조합 | 사용 상황 |
| --- | --- | --- |
| 동기 (방식 A) | `@TransactionalEventListener` | 커밋 직후 DB 작업 |
| 비동기 (방식 B) | `@Async` + `@TransactionalEventListener` | 외부 I/O, 오래 걸리는 작업 |

```java
// 방식 A — DB 작업 포함 시 REQUIRES_NEW 필수
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void saveNotificationLog(IssueCreatedEvent event) {
    // ...
}

// 방식 B — 외부 API 호출
@Async
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void sendSlackAlert(IssueCreatedEvent event) {
    // ...
}
```

### 7.3 이벤트 관련 주의사항

- **이벤트 객체에 JPA Entity 전달 금지** — `AFTER_COMMIT` 시점에 영속성 컨텍스트가 닫혀 `LazyInitializationException` 발생. 반드시 필요한 값만 담은 Record/DTO로 구성
- **방식 A에서 `REQUIRES_NEW` 누락 금지** — 이미 닫힌 트랜잭션에 참여 시도 시 DB 저장이 무시됨
- **트랜잭션 내 외부 I/O 금지** — DB 커넥션 풀 고갈로 서버 장애 유발. 방식 B(`@Async`) 사용

---

## 8. 비동기 처리 (`@Async`)

### 8.1 `@Async` 메서드 작성 규칙

| 규칙 | 내용 |
| --- | --- |
| 리턴 타입 | `void` 또는 `CompletableFuture`만 허용 |
| Self-Invocation 금지 | 동일 클래스 내 `@Async` 메서드 직접 호출 시 프록시 미적용으로 동기 동작 |
| `public` 메서드만 | `private`, `protected`에 선언 시 AOP 프록시가 무시함 |
| 스레드 풀 | `SimpleAsyncTaskExecutor` 사용 금지 — `AsyncConfig`에 등록된 커스텀 `ThreadPoolTaskExecutor` 사용 |

### 8.2 예외 처리

`void` 리턴 `@Async` 메서드의 예외는 메인 스레드로 전파되지 않고 소멸한다.
`CustomAsyncExceptionHandler`가 등록되어 있으므로 별도 `try-catch` 없이 로깅된다.
단, 예외 발생 시 재시도나 보상 트랜잭션이 필요한 경우 직접 `try-catch`로 처리한다.
