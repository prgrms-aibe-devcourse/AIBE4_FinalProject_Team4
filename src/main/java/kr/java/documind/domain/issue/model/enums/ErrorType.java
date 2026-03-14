package kr.java.documind.domain.issue.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.List;

/**
 * 에러 타입
 *
 * <p>예외/에러의 구체적인 종류
 */
public enum ErrorType {
    NULL_POINTER("NULL_POINTER", "NullPointerException"),
    INDEX_OUT_OF_BOUNDS("INDEX_OUT_OF_BOUNDS", "IndexOutOfBoundsException"),
    ILLEGAL_ARGUMENT("ILLEGAL_ARGUMENT", "IllegalArgumentException"),
    ILLEGAL_STATE("ILLEGAL_STATE", "IllegalStateException"),
    TIMEOUT("TIMEOUT", "TimeoutException"),
    IO("IO", "IOException"),
    NETWORK("NETWORK", "네트워크 오류"),
    DATABASE("DATABASE", "데이터베이스 오류"),
    DEADLOCK("DEADLOCK", "교착 상태"),
    AUTHENTICATION("AUTHENTICATION", "인증 실패"),
    AUTHORIZATION("AUTHORIZATION", "권한 부족"),
    SERIALIZATION("SERIALIZATION", "직렬화 오류"),
    OUT_OF_MEMORY("OUT_OF_MEMORY", "메모리 부족"),
    STACK_OVERFLOW("STACK_OVERFLOW", "스택 오버플로"),
    ARITHMETIC("ARITHMETIC", "산술 연산 오류"),
    UNSUPPORTED_OPERATION("UNSUPPORTED_OPERATION", "지원하지 않는 작업"),
    CONCURRENCY("CONCURRENCY", "동시성 문제"),
    DEPENDENCY_FAILURE("DEPENDENCY_FAILURE", "외부 의존성 실패"),
    UNKNOWN("UNKNOWN", "알 수 없는 오류");

    private final String value;
    private final String description;

    ErrorType(String value, String description) {
        this.value = value;
        this.description = description;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 예외 클래스명에서 ErrorType 추론
     *
     * @param exceptionClassName 예외 클래스명 (예: "NullPointerException")
     * @return 매칭되는 ErrorType (없으면 UNKNOWN)
     */
    public static ErrorType fromExceptionClassName(String exceptionClassName) {
        if (exceptionClassName == null || exceptionClassName.isBlank()) {
            return UNKNOWN;
        }

        String className = exceptionClassName.toLowerCase();

        if (className.contains("nullpointer")) return NULL_POINTER;
        if (className.contains("indexoutofbounds")) return INDEX_OUT_OF_BOUNDS;
        if (className.contains("illegalargument")) return ILLEGAL_ARGUMENT;
        if (className.contains("illegalstate")) return ILLEGAL_STATE;
        if (className.contains("timeout")) return TIMEOUT;
        if (className.contains("ioexception")) return IO;
        if (className.contains("network")) return NETWORK;
        if (className.contains("database") || className.contains("sql")) return DATABASE;
        if (className.contains("deadlock")) return DEADLOCK;
        if (className.contains("authentication")) return AUTHENTICATION;
        if (className.contains("authorization") || className.contains("accessdenied"))
            return AUTHORIZATION;
        if (className.contains("serialization")) return SERIALIZATION;
        if (className.contains("outofmemory")) return OUT_OF_MEMORY;
        if (className.contains("stackoverflow")) return STACK_OVERFLOW;
        if (className.contains("arithmetic")) return ARITHMETIC;
        if (className.contains("unsupportedoperation")) return UNSUPPORTED_OPERATION;
        if (className.contains("concurrency") || className.contains("concurrent"))
            return CONCURRENCY;

        return UNKNOWN;
    }

    @JsonCreator
    public static ErrorType fromString(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }

        for (ErrorType type : values()) {
            if (type.value.equalsIgnoreCase(value.trim())) {
                return type;
            }
        }

        // 인식하지 못한 값은 UNKNOWN으로 fallback (예외 발생하지 않음)
        // 미래 버전/레거시 데이터 호환성 유지
        return UNKNOWN;
    }

    @Override
    public String toString() {
        return value;
    }

    /**
     * 가능한 원인 목록 반환
     *
     * @return 원인 목록
     */
    public List<String> getPossibleCauses() {
        return switch (this) {
            case NULL_POINTER -> List.of(
                    "객체가 초기화되지 않은 상태에서 메서드 호출",
                    "데이터베이스 조회 결과가 null인데 null 체크 누락",
                    "Optional을 사용하지 않고 직접 객체 접근");
            case INDEX_OUT_OF_BOUNDS -> List.of(
                    "배열이나 리스트의 범위를 벗어난 인덱스 접근", "반복문에서 잘못된 인덱스 계산", "빈 컬렉션에 대한 접근");
            case ILLEGAL_ARGUMENT -> List.of(
                    "메서드에 잘못된 파라미터 전달", "유효성 검증 로직 부재", "클라이언트에서 전달된 데이터 검증 누락");
            case ILLEGAL_STATE -> List.of(
                    "객체가 작업을 수행할 수 없는 상태", "라이프사이클 순서 위반 (예: 초기화 전 사용)", "동시성 문제로 인한 상태 불일치");
            case TIMEOUT -> List.of(
                    "외부 API 응답 지연",
                    "데이터베이스 쿼리 최적화 필요 (인덱스 부족)",
                    "네트워크 연결 불안정",
                    "과도한 트래픽으로 인한 리소스 부족");
            case IO -> List.of("파일 또는 리소스에 접근할 수 없음", "권한 부족으로 읽기/쓰기 실패", "디스크 공간 부족");
            case NETWORK -> List.of(
                    "서버 또는 외부 서비스 연결 실패", "방화벽 또는 보안 정책으로 차단", "DNS 해석 실패", "일시적인 네트워크 장애");
            case DATABASE -> List.of(
                    "데이터베이스 연결 실패 (커넥션 풀 고갈)",
                    "쿼리 문법 오류 또는 스키마 불일치",
                    "제약 조건 위반 (중복 키, 외래 키 등)",
                    "트랜잭션 타임아웃");
            case DEADLOCK -> List.of("여러 트랜잭션이 서로의 리소스를 기다리는 상황", "락 획득 순서 불일치", "과도한 락 범위");
            case AUTHENTICATION -> List.of("잘못된 인증 정보 (아이디/비밀번호 불일치)", "토큰 만료 또는 무효화", "세션 타임아웃");
            case AUTHORIZATION -> List.of(
                    "요청한 작업에 대한 권한 부족", "역할 기반 접근 제어(RBAC) 설정 오류", "리소스 소유권 불일치");
            case SERIALIZATION -> List.of(
                    "직렬화 가능하지 않은 객체 직렬화 시도", "버전 불일치 (serialVersionUID)", "순환 참조 구조");
            case OUT_OF_MEMORY -> List.of(
                    "메모리 누수 (객체가 GC되지 않음)",
                    "과도한 데이터 로드 (페이징 부재)",
                    "힙 크기 부족 (-Xmx 설정 필요)",
                    "무한 루프로 인한 객체 생성");
            case STACK_OVERFLOW -> List.of("무한 재귀 호출", "재귀 종료 조건 부재", "과도한 재귀 깊이");
            case ARITHMETIC -> List.of("0으로 나누기 시도", "오버플로우 또는 언더플로우", "숫자 형식 불일치");
            case UNSUPPORTED_OPERATION -> List.of(
                    "읽기 전용 컬렉션에 쓰기 시도", "구현되지 않은 메서드 호출", "플랫폼별 제한 사항 위반");
            case CONCURRENCY -> List.of(
                    "여러 스레드가 동시에 같은 리소스 수정",
                    "동기화 누락 (synchronized, Lock 등)",
                    "ConcurrentModificationException");
            case DEPENDENCY_FAILURE -> List.of(
                    "외부 서비스(API, 메시징 등) 장애", "서드파티 라이브러리 버전 불일치", "의존성 서비스의 일시적 중단");
            case UNKNOWN -> List.of("스택트레이스 및 로그를 확인하여 원인을 파악해주세요");
        };
    }

    /**
     * 권장 해결책 목록 반환
     *
     * @return 해결책 목록
     */
    public List<String> getSolutions() {
        return switch (this) {
            case NULL_POINTER -> List.of(
                    "null 체크 추가: if (obj != null) { ... }",
                    "Optional 사용으로 변경",
                    "Objects.requireNonNull() 또는 Objects.requireNonNullElse() 활용",
                    "@NonNull 어노테이션 사용");
            case INDEX_OUT_OF_BOUNDS -> List.of(
                    "인덱스 범위 검증: if (index >= 0 && index < list.size())",
                    "반복문 조건 확인",
                    "컬렉션이 비어있는지 먼저 체크");
            case ILLEGAL_ARGUMENT -> List.of(
                    "메서드 시작 부분에 파라미터 검증 추가",
                    "@Valid, @NotNull 등 Bean Validation 어노테이션 사용",
                    "Preconditions 유틸리티 사용");
            case ILLEGAL_STATE -> List.of(
                    "상태 전환 로직 검토 및 수정",
                    "동기화 메커니즘 추가 (synchronized, AtomicReference 등)",
                    "상태 패턴 적용 검토");
            case TIMEOUT -> List.of(
                    "타임아웃 시간 조정",
                    "비동기 처리로 변경 (@Async, CompletableFuture)",
                    "캐싱 도입 (Redis, Local Cache)",
                    "데이터베이스 쿼리 최적화 (인덱스 추가)",
                    "커넥션 풀 크기 증가");
            case IO -> List.of(
                    "파일 경로 및 권한 확인", "try-with-resources로 리소스 자동 해제", "디스크 공간 확보", "예외 처리 로직 개선");
            case NETWORK -> List.of(
                    "연결 재시도 로직 추가 (Exponential Backoff)",
                    "Circuit Breaker 패턴 적용 (Resilience4j)",
                    "타임아웃 설정 조정",
                    "네트워크 설정 및 방화벽 규칙 확인");
            case DATABASE -> List.of(
                    "커넥션 풀 설정 조정 (HikariCP)",
                    "트랜잭션 범위 최소화",
                    "쿼리 최적화 및 인덱스 추가",
                    "배치 처리 적용 (JDBC Batch)",
                    "제약 조건 확인 및 수정");
            case DEADLOCK -> List.of(
                    "락 획득 순서 통일", "트랜잭션 시간 최소화", "락 범위 축소", "낙관적 락(Optimistic Lock) 고려");
            case AUTHENTICATION -> List.of(
                    "인증 정보 확인 및 갱신", "토큰 갱신 로직 추가", "세션 타임아웃 설정 조정", "비밀번호 재설정 플로우 검토");
            case AUTHORIZATION -> List.of(
                    "권한 설정 검토 및 수정",
                    "역할 기반 접근 제어(RBAC) 재설계",
                    "리소스 소유권 확인 로직 추가",
                    "Spring Security 설정 점검");
            case SERIALIZATION -> List.of(
                    "Serializable 인터페이스 구현",
                    "transient 키워드로 직렬화 제외",
                    "serialVersionUID 명시적 선언",
                    "순환 참조 제거 또는 @JsonIgnore 사용");
            case OUT_OF_MEMORY -> List.of(
                    "메모리 누수 분석 도구 사용 (VisualVM, JProfiler)",
                    "페이징 처리 적용 (Pageable)",
                    "힙 크기 증가 (-Xmx 옵션)",
                    "불필요한 객체 참조 제거",
                    "캐시 크기 제한 설정");
            case STACK_OVERFLOW -> List.of(
                    "재귀 호출 종료 조건 추가", "반복문으로 변환", "스택 크기 증가 (-Xss 옵션)", "꼬리 재귀 최적화 적용");
            case ARITHMETIC -> List.of(
                    "0으로 나누기 방지 로직 추가",
                    "BigDecimal 사용 (정밀 계산)",
                    "Math.addExact() 등 오버플로우 검증 메서드 사용",
                    "숫자 타입 변환 시 범위 확인");
            case UNSUPPORTED_OPERATION -> List.of(
                    "읽기 전용 컬렉션 대신 변경 가능한 컬렉션 사용",
                    "메서드 구현 추가",
                    "플랫폼별 대안 구현 검토",
                    "Collections.unmodifiableList() 사용 여부 확인");
            case CONCURRENCY -> List.of(
                    "synchronized 블록 또는 메서드 사용",
                    "ConcurrentHashMap, CopyOnWriteArrayList 등 동시성 컬렉션 사용",
                    "ReentrantLock, ReadWriteLock 활용",
                    "AtomicInteger, AtomicReference 등 Atomic 클래스 사용",
                    "Collections.synchronizedList() 등 동기화 래퍼 사용");
            case DEPENDENCY_FAILURE -> List.of(
                    "Circuit Breaker 패턴 적용",
                    "재시도 로직 추가 (Retry with Backoff)",
                    "폴백(Fallback) 메커니즘 구현",
                    "의존성 버전 확인 및 업데이트",
                    "헬스 체크 엔드포인트 모니터링");
            case UNKNOWN -> List.of("스택트레이스를 분석하여 구체적인 해결 방법을 찾아주세요");
        };
    }
}
