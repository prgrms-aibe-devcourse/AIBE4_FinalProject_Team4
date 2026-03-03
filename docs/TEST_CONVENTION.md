# Test Convention

## 1. 테스트 프레임워크 및 도구

### 1.1 기본 스택

- **JUnit 5**: 테스트 실행 프레임워크
- **AssertJ**: 유창한(fluent) API를 제공하는 Assertion 라이브러리
- **Mockito**: Mock 객체 생성 및 행위 검증
- **Spring Boot Test**: 통합 테스트를 위한 Spring Context 지원

### 1.2 의존성 (build.gradle)

```gradle
dependencies {
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```

> `spring-boot-starter-test`에는 JUnit 5, AssertJ, Mockito가 모두 포함되어 있음

---

## 2. 테스트 작성 패턴 (GWT)

모든 테스트는 **Given-When-Then** 패턴을 따른다.

### 2.1 구조

```java
@Test
@DisplayName("테스트 설명")
void 테스트_메서드명_언더스코어_3개_구분() {
    // Given: 테스트 준비 (입력값, Mock 설정)

    // When: 실행 (테스트 대상 메서드 호출)

    // Then: 검증 (결과 확인)
}
```

### 2.2 실제 예시

```java
@Test
@DisplayName("Circuit Breaker: 3회 실패 후 OPEN 상태로 전환")
void circuitBreakerOpensAfterThreeFailures() {
    // Given: Redis 연결 실패 시뮬레이션
    setupRedisFailure();

    Consumer consumer = Consumer.from("test-group", "test-consumer");
    StreamReadOptions options = StreamReadOptions.empty();
    StreamOffset<String> offset = StreamOffset.latest("test-stream");

    CircuitBreaker circuitBreaker =
            circuitBreakerRegistry.circuitBreaker("redisStreamConsumer");

    // When: 3회 연속 호출
    for (int i = 0; i < 3; i++) {
        service.readMessages(consumer, options, offset);
    }

    // Then: Circuit OPEN 상태
    assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

    // And: 4번째 호출은 빈 리스트 반환 (Circuit이 OPEN이므로 Redis 호출 없음)
    List<MapRecord<String, String, String>> result =
            service.readMessages(consumer, options, offset);
    assertThat(result).isEmpty();
}
```

---

## 3. Assertion 작성 규칙

### 3.1 AssertJ 사용

JUnit 5 기본 Assertions(`assertEquals`, `assertTrue` 등) 대신 **AssertJ의 `assertThat()`** 을 사용한다.

#### ❌ 잘못된 예시 (JUnit 5 기본 Assertions)

```java
assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
assertTrue(result.isEmpty());
assertNotNull(result);
```

#### ✅ 올바른 예시 (AssertJ)

```java
assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
assertThat(result).isEmpty();
assertThat(result).isNotNull();
```

### 3.2 AssertJ 주요 메서드

| 검증 대상 | AssertJ 메서드 | 예시 |
| --- | --- | --- |
| 동등성 | `isEqualTo()` | `assertThat(actual).isEqualTo(expected)` |
| null 검증 | `isNull()`, `isNotNull()` | `assertThat(object).isNotNull()` |
| boolean | `isTrue()`, `isFalse()` | `assertThat(flag).isTrue()` |
| 컬렉션 비어있음 | `isEmpty()`, `isNotEmpty()` | `assertThat(list).isEmpty()` |
| 컬렉션 크기 | `hasSize(n)` | `assertThat(list).hasSize(3)` |
| 컬렉션 포함 | `contains()`, `containsExactly()` | `assertThat(list).contains(item)` |
| 예외 검증 | `assertThatThrownBy()` | `assertThatThrownBy(() -> {...}).isInstanceOf(Exception.class)` |

### 3.3 AssertJ 사용 이유

- **가독성**: 메서드 체이닝으로 영어 문장처럼 읽힘
- **풍부한 API**: 컬렉션, Optional, Exception 등 다양한 타입 지원
- **명확한 실패 메시지**: 테스트 실패 시 더 구체적인 정보 제공

---

## 4. Mock 객체 작성

### 4.1 Mock 생성

```java
@ExtendWith(MockitoExtension.class)
class ServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private StreamOperations<String, Object, Object> streamOperations;

    @InjectMocks
    private RedisStreamCircuitBreakerService service;
}
```

### 4.2 Stubbing (행위 정의)

#### 기본 패턴

```java
@BeforeEach
void setUp() {
    // 특정 조건에서만 사용되는 stubbing
    when(redisTemplate.opsForStream()).thenReturn(streamOperations);
}

private void setupRedisSuccess() {
    when(streamOperations.read(
                    any(Consumer.class),
                    any(StreamReadOptions.class),
                    any(StreamOffset.class)))
            .thenReturn(List.of());
}

private void setupRedisFailure() {
    when(streamOperations.read(
                    any(Consumer.class),
                    any(StreamReadOptions.class),
                    any(StreamOffset.class)))
            .thenThrow(new RedisConnectionFailureException("Connection lost"));
}
```

#### lenient() 사용 (UnnecessaryStubbingException 방지)

`@BeforeEach`에서 모든 테스트에 공통으로 필요한 stubbing을 설정할 때, 일부 테스트에서 사용하지 않으면 `UnnecessaryStubbingException`이 발생할 수 있다. 이 경우 `lenient()`를 사용한다.

```java
@BeforeEach
void setUp() {
    // lenient 모드로 모든 테스트에서 사용 가능
    lenient().when(redisTemplate.opsForStream()).thenReturn(streamOperations);

    service.init();
}
```

> **주의**: `lenient()`는 필요한 경우에만 사용한다. 무분별한 사용은 불필요한 stubbing을 숨길 수 있다.

### 4.3 verify() 사용 — 기술적 제약

#### 원칙

Mock 객체의 메서드 호출 여부를 검증하려면 `verify()`를 사용한다.

```java
// When
service.readMessages(consumer, options, offset);

// Then
verify(streamOperations).read(
        any(Consumer.class),
        any(StreamReadOptions.class),
        any(StreamOffset.class));
```

#### 기술적 제약으로 인한 제거

**오버로딩된 메서드의 경우 타입 모호성(ambiguous reference)** 으로 인해 `verify()` 호출이 컴파일 에러를 발생시킬 수 있다.

```java
// 컴파일 에러 예시
verify(streamOperations).read(
        any(Consumer.class),
        any(StreamReadOptions.class),
        any(StreamOffset.class));
// Error: reference to read is ambiguous
```

이 경우 다음 중 하나를 선택한다:

1. **타입 명시**: `ArgumentMatchers`의 타입을 명시하여 모호성 제거

```java
verify(streamOperations).read(
        ArgumentMatchers.<Consumer>any(),
        ArgumentMatchers.<StreamReadOptions>any(),
        ArgumentMatchers.<StreamOffset<String>>any());
```

2. **verify() 제거**: 타입 명시가 불가능하거나 코드 가독성이 떨어지는 경우, **상태 검증(state verification)** 으로 대체

```java
// verify() 대신 반환값 검증
List<MapRecord<String, String, String>> result =
        service.readMessages(consumer, options, offset);
assertThat(result).isNotNull();
assertThat(result).isEmpty();
```

> **권장**: 가능하면 타입 명시를 시도하되, 코드 복잡도가 높아지면 상태 검증으로 대체한다.

---

## 5. 테스트 클래스 구조

### 5.1 기본 구조

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("클래스명 + 단위 테스트")
class RedisStreamCircuitBreakerServiceTest {

    // 1. Mock 객체 선언
    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private StreamOperations<String, Object, Object> streamOperations;

    // 2. 실제 객체 선언 (테스트 대상 아닌 의존성)
    private CircuitBreakerRegistry circuitBreakerRegistry;
    private RetryRegistry retryRegistry;
    private MeterRegistry meterRegistry;

    // 3. 테스트 대상 (SUT: System Under Test)
    private RedisStreamCircuitBreakerService service;

    // 4. 공통 초기화
    @BeforeEach
    void setUp() {
        // Registry 설정
        circuitBreakerRegistry = CircuitBreakerRegistry.of(...);
        retryRegistry = RetryRegistry.of(...);
        meterRegistry = new SimpleMeterRegistry();

        // Service 생성
        service = new RedisStreamCircuitBreakerService(
                redisTemplate, circuitBreakerRegistry, retryRegistry, meterRegistry);

        // Mock 설정
        lenient().when(redisTemplate.opsForStream()).thenReturn(streamOperations);

        service.init();
    }

    // 5. 헬퍼 메서드 (재사용 가능한 stubbing)
    private void setupRedisSuccess() {
        when(streamOperations.read(...)).thenReturn(List.of());
    }

    private void setupRedisFailure() {
        when(streamOperations.read(...))
                .thenThrow(new RedisConnectionFailureException("Connection lost"));
    }

    // 6. 테스트 케이스
    @Test
    @DisplayName("정상 동작: Redis 메시지 읽기 성공")
    void readMessages_Success() {
        // Given
        setupRedisSuccess();

        // When
        List<MapRecord<String, String, String>> result = ...;

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }
}
```

### 5.2 네이밍 규칙

| 대상 | 규칙 | 예시 |
| --- | --- | --- |
| 테스트 클래스 | `대상클래스명 + Test` | `RedisStreamCircuitBreakerServiceTest` |
| 테스트 메서드 | `메서드명_상황_예상결과` (언더스코어 3개) | `readMessages_Success()` |
| 테스트 메서드 (복잡한 경우) | `상황_설명` | `circuitBreakerOpensAfterThreeFailures()` |

### 5.3 @DisplayName 작성 규칙

- **한글로 작성**: 테스트 의도를 명확하게 전달
- **형식**: `기능: 상황 → 예상결과`

```java
@DisplayName("Circuit Breaker: 3회 실패 후 OPEN 상태로 전환")
@DisplayName("정상 동작: Redis 메시지 읽기 성공")
@DisplayName("Circuit Breaker 상태 조회")
```

---

## 6. 테스트 전략

### 6.1 단위 테스트 (Unit Test)

**목적**: 개별 클래스/메서드의 로직 검증

**특징**:
- `@ExtendWith(MockitoExtension.class)` 사용
- 외부 의존성을 Mock으로 대체
- 빠른 실행 속도

**적용 대상**:
- Service 계층 비즈니스 로직
- Utility 클래스
- Domain 모델 메서드

**예시**: `RedisStreamCircuitBreakerServiceTest`

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("RedisStreamCircuitBreakerService 단위 테스트")
class RedisStreamCircuitBreakerServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Test
    @DisplayName("정상 동작: Redis 메시지 읽기 성공")
    void readMessages_Success() {
        // Given: Mock 설정
        when(streamOperations.read(...)).thenReturn(List.of());

        // When: 실제 호출
        List<MapRecord<String, String, String>> result =
                service.readMessages(consumer, options, offset);

        // Then: 결과 검증
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }
}
```

### 6.2 통합 테스트 (Integration Test)

**목적**: 여러 컴포넌트 간 상호작용 검증

**특징**:
- `@SpringBootTest` 사용
- 실제 Spring Context 로드
- 느린 실행 속도 (최소화 권장)

**적용 대상**:
- Controller ↔ Service ↔ Repository 흐름
- 트랜잭션 전파 검증
- 이벤트 리스너 동작 검증

**예시**: `RedisCircuitBreakerIntegrationTest`

```java
@SpringBootTest
@Testcontainers
@DisplayName("Redis Circuit Breaker 통합 테스트")
class RedisCircuitBreakerIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Autowired
    private RedisStreamCircuitBreakerService service;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Test
    @DisplayName("Redis 재시작 후 Circuit Breaker 자동 복구")
    void circuitBreakerRecoverAfterRedisRestart() {
        // Given: Redis 중지
        redis.stop();

        // When: 3회 연속 호출로 Circuit OPEN
        for (int i = 0; i < 3; i++) {
            service.readMessages(consumer, options, offset);
        }

        CircuitBreaker circuitBreaker =
                circuitBreakerRegistry.circuitBreaker("redisStreamConsumer");
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // And: Redis 재시작
        redis.start();
        Thread.sleep(10000);  // wait-duration-in-open-state

        // Then: HALF_OPEN → CLOSED 복귀 확인
        service.readMessages(consumer, options, offset);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
```

### 6.3 부하 테스트 (Load Test)

**목적**: 대량 트래픽 환경에서 성능 및 안정성 검증

**특징**:
- JMeter, Gatling, K6 등 전문 도구 사용
- 실제 운영 환경과 유사한 인프라 필요
- CI/CD에서 선택적으로 실행

**적용 대상**:
- Backpressure 메커니즘 검증
- Circuit Breaker 임계값 튜닝
- DB Connection Pool 최적화

**예시**: JMeter 시나리오

```
시나리오: 초당 1000건 로그 발행
1. Redis Streams에 초당 1000건 메시지 발행
2. LogStreamListener의 처리 속도 측정
3. BackpressureManager의 지연 시간 모니터링
4. Circuit Breaker OPEN 발생 여부 확인

검증 지표:
- 평균 처리 속도 (messages/sec)
- P95 응답 시간
- Circuit Breaker 상태 전환 횟수
- DB Connection Pool 사용률
```

---

## 7. 테스트 데이터 준비

### 7.1 Fixture 메서드 패턴

테스트에서 반복적으로 사용되는 객체 생성 로직은 별도 메서드로 분리한다.

```java
private Consumer createConsumer() {
    return Consumer.from("test-group", "test-consumer");
}

private StreamReadOptions createReadOptions() {
    return StreamReadOptions.empty().count(10);
}

private StreamOffset<String> createStreamOffset() {
    return StreamOffset.latest("test-stream");
}
```

### 7.2 테스트용 Builder 패턴

복잡한 객체는 테스트용 Builder를 제공한다.

```java
// src/test/java/.../fixture/GameLogFixture.java
public class GameLogFixture {

    public static GameLog.GameLogBuilder defaultLog() {
        return GameLog.builder()
                .timestamp(LocalDateTime.now())
                .severity(LogSeverity.INFO)
                .category(EventCategory.GAME_START)
                .message("Test log message");
    }
}

// 테스트에서 사용
@Test
void processLog_Success() {
    // Given
    GameLog log = GameLogFixture.defaultLog()
            .severity(LogSeverity.ERROR)
            .build();

    // When & Then
    ...
}
```

---

## 8. 테스트 실행 및 관리

### 8.1 Gradle 명령어

```bash
# 전체 테스트 실행
./gradlew test

# 특정 테스트 클래스만 실행
./gradlew test --tests RedisStreamCircuitBreakerServiceTest

# 특정 테스트 메서드만 실행
./gradlew test --tests RedisStreamCircuitBreakerServiceTest.readMessages_Success

# 테스트 결과 HTML 리포트 생성
./gradlew test --info
# 결과: build/reports/tests/test/index.html
```

### 8.2 IntelliJ 단축키

| 동작 | Windows/Linux | macOS |
| --- | --- | --- |
| 테스트 실행 | `Ctrl + Shift + F10` | `Cmd + Shift + R` |
| 이전 테스트 재실행 | `Ctrl + F5` | `Cmd + R` |
| 커서 위치 테스트 실행 | `Ctrl + Shift + F10` | `Cmd + Shift + R` |

### 8.3 테스트 격리

각 테스트는 **독립적으로 실행 가능**해야 한다.

#### ❌ 잘못된 예시 (테스트 간 의존성)

```java
private static CircuitBreaker sharedCircuitBreaker;

@Test
void test1() {
    sharedCircuitBreaker = circuitBreakerRegistry.circuitBreaker("test");
    // Circuit을 OPEN 상태로 변경
}

@Test
void test2() {
    // test1의 영향을 받아 Circuit이 이미 OPEN 상태
    assertThat(sharedCircuitBreaker.getState()).isEqualTo(State.CLOSED);  // ❌ 실패
}
```

#### ✅ 올바른 예시 (각 테스트마다 초기화)

```java
@BeforeEach
void setUp() {
    circuitBreakerRegistry = CircuitBreakerRegistry.of(config);
    service = new RedisStreamCircuitBreakerService(...);
}

@Test
void test1() {
    CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("test");
    // 이 테스트만의 Circuit Breaker
}

@Test
void test2() {
    CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("test");
    // 새로운 Circuit Breaker (test1과 격리됨)
}
```

---

## 9. 주의사항

### 9.1 금지 패턴

| 패턴 | 이유 |
| --- | --- |
| `System.out.println()` 디버깅 | 테스트 로그 오염. 로거 사용 또는 디버거 활용 |
| `Thread.sleep()` 남용 | 테스트 실행 시간 증가. 필요시 `Awaitility` 사용 |
| 하드코딩된 포트/URL | 환경별 설정 차이로 실패. `@LocalServerPort` 또는 Testcontainers 사용 |
| `@Disabled` 남발 | 깨진 테스트를 방치. 즉시 수정하거나 이슈 등록 후 제거 |

### 9.2 테스트 작성 시 체크리스트

- [ ] GWT 패턴이 명확하게 구분되어 있는가?
- [ ] `@DisplayName`이 한글로 작성되어 있는가?
- [ ] AssertJ의 `assertThat()`을 사용하는가?
- [ ] Mock 객체가 필요한 부분만 사용되었는가?
- [ ] 테스트 메서드명이 언더스코어 3개로 구분되어 있는가?
- [ ] 테스트 간 의존성이 없는가? (실행 순서 무관)
- [ ] 불필요한 `verify()` 호출이 제거되었는가?
- [ ] 예외 상황도 테스트되었는가?

---

## 10. 참고 자료

### 10.1 공식 문서

- **JUnit 5**: https://junit.org/junit5/docs/current/user-guide/
- **AssertJ**: https://assertj.github.io/doc/
- **Mockito**: https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html
- **Spring Boot Test**: https://docs.spring.io/spring-boot/reference/testing/index.html

### 10.2 프로젝트 내 테스트 예시

- **단위 테스트**: `src/test/java/.../service/resilience/RedisStreamCircuitBreakerServiceTest.java`
- **통합 테스트**: 추후 추가 예정
- **Fixture 패턴**: 추후 추가 예정

---

## 변경 이력

| 날짜 | 변경 내용 |
| --- | --- |
| 2026-03-03 | 초기 작성 (JUnit5 + AssertJ + Mockito 조합, verify() 기술적 제약 반영) |
