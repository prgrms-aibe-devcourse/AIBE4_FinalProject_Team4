📌 목차

<aside>

1. Custom Repository란
2. QueryDSL이란
3. 언제 사용하는가
4. 구현 방법 단계별
5. 실전 예시
6. 네이밍 규칙
7. 주의사항
8. FAQ
</aside>

---

## 1. Custom Repository란?

Spring Data JPA가 제공하는 기본 메서드(findById, save 등) 외에
복잡한 쿼리나 비즈니스 로직을 위한 커스텀 메서드를 정의하는 패턴입니다.

### 기본 Repository의 한계

```java
// ✅ Spring Data JPA가 자동으로 제공
public interface IssueRepository extends JpaRepository<Issue, UUID> {
Optional<Issue> findById(UUID id);  // 자동 생성
List<Issue> findAll();              // 자동 생성
}
```

// ❌ 하지만 이런 복잡한 쿼리는?
// - 동적 필터링 (여러 조건 조합)
// - 복잡한 JOIN
// - 네이티브 쿼리
// - Bulk Update
// - JPQL이 표현하기 어려운 로직

---

## 2. QueryDSL이란?

**타입 안전한(Type-Safe)** 쿼리 작성을 위한 프레임워크

Java 코드로 SQL/JPQL을 작성하며, **컴파일 타임에 오류 검증**

Q클래스(Query Type)를 통해 엔티티 필드에 안전하게 접근

### 왜 QueryDSL인가?

|  비교 항목  |  **JPQL (@Query)**  |  **Native Query**  |  **QueryDSL** |
| --- | --- | --- | --- |
|  타입 안전성  |  ❌ 문자열  |  ❌ 문자열  |  ✅ Java 코드  |
|  컴파일 검증  |  ❌ 런타임  |  ❌ 런타임  |  ✅ 컴파일 타임  |
|  동적 쿼리  |  ❌ 어려움  |  ❌ 어려움  |  ✅ 쉬움  |
|  IDE 자동완성  |  ❌ 없음  |  ❌ 없음  |  ✅ 완벽 지원  |
|  리팩토링 안전성  |  ❌ 수동 수정  |  ❌ 수동 수정  |  ✅ 자동 반영  |
|  복잡한 조인/서브쿼리  |  △ 가능하나 복잡  |  ✅ 가능  |  ✅ 직관적  |

## 3. 언제 사용하는가?

✅ Custom Repository가 필요한 경우

```markdown
┌──────────────────────┬──────────────────────────────────────────────────────┐
│         상황         │                         예시                         │
├──────────────────────┼──────────────────────────────────────────────────────┤
│ 동적 쿼리             │ 검색 필터가 여러 개 (status, severity, dateRange 등)  │
├──────────────────────┼──────────────────────────────────────────────────────┤
│ 복잡한 JOIN          │ 3개 이상의 테이블 조인 + 집계                          │
├──────────────────────┼──────────────────────────────────────────────────────┤
│ Bulk 연산            │ 1000개 이슈를 한 번에 RESOLVED 처리                   │
├──────────────────────┼──────────────────────────────────────────────────────┤
│ 네이티브 쿼리         │ PostgreSQL 전용 기능 (JSONB, Full-text search)       │
├──────────────────────┼──────────────────────────────────────────────────────┤
│ 성능 최적화           │ Fetch Join, N+1 문제 해결                            │
├──────────────────────┼──────────────────────────────────────────────────────┤
│ 복잡한 비즈니스 로직  │ Repository 레이어에서 처리해야 하는 로직               │
└──────────────────────┴──────────────────────────────────────────────────────┘
```

❌ Custom Repository가 불필요한 경우

```markdown
┌───────────────┬────────────────────────────────────────┐
│     상황      │                  대안                  │
├───────────────┼────────────────────────────────────────┤
│ 단순 조회     │ findByFingerprint() 같은 메서드명 쿼리  │
├───────────────┼────────────────────────────────────────┤
│ 단일 조건     │ findByStatus(IssueStatus.OPEN)         │
├───────────────┼────────────────────────────────────────┤
│ 비즈니스 로직  │ Service 레이어로 이동                  │
└───────────────┴────────────────────────────────────────┘
```

---

## 4. 구현 방법

### 사전 준비

```java
1️⃣ 의존성 추가 (`build.gradle`)
ggradle
// ========== QueryDSL 설정 ==========
def querydslDir = "$buildDir/generated/querydsl"

sourceSets {
    main.java.srcDirs += [querydslDir]
}

tasks.withType(JavaCompile) {
    options.generatedSourceOutputDirectory = file(querydslDir)
}

clean {
    delete file(querydslDir)
}

dependencies {
    // QueryDSL
    implementation 'com.querydsl:querydsl-jpa:5.0.0:jakarta'
    annotationProcessor "com.querydsl:querydsl-apt:5.0.0:jakarta"
    annotationProcessor "jakarta.annotation:jakarta.annotation-api"
    annotationProcessor "jakarta.persistence:jakarta.persistence-api"
}
```

**⚠️ 중요**: 위 설정을 추가하면 Q클래스가 자동으로 생성되고 IDE가 자동 인식합니다!

```java
2️⃣ JPAQueryFactory Bean 등록
java
// 📁 global/config/QueryDslConfig.java
package kr.java.documind.global.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QueryDslConfig {

    @Bean
    public JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
        return new JPAQueryFactory(entityManager);
    }
}
```

### Step 1: Custom 인터페이스 정의

```java
// 📁 IssueRepositoryCustom.java
package kr.java.documind.domain.issue.model.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.enums.IssueStatus;
import kr.java.documind.domain.logprocessor.model.enums.LogSeverity;

/**
 * Issue Repository 커스텀 인터페이스
 */
public interface IssueRepositoryCustom {

    /**
     * 동적 필터링으로 이슈 검색
     *
     * @param projectId 프로젝트 ID (필수)
     * @param status 상태 (선택)
     * @param severity 심각도 (선택)
     * @param startDate 시작일 (선택)
     * @param endDate 종료일 (선택)
     * @return 필터링된 이슈 목록
     */
    List<Issue> findByDynamicFilter(
            UUID projectId,
            IssueStatus status,
            LogSeverity severity,
            OffsetDateTime startDate,
            OffsetDateTime endDate);

    /**
     * 특정 프로젝트의 이슈 통계
     *
     * @param projectId 프로젝트 ID
     * @return 통계 DTO
     */
    IssueStatisticsDto getStatistics(UUID projectId);
}
```

네이밍 규칙:

- 인터페이스명: {Entity}RepositoryCustom
- 예: IssueRepositoryCustom, GameLogRepositoryCustom

---

### Step 2: Custom 구현체 작성

```java
// 📁 IssueRepositoryImpl.java
package kr.java.documind.domain.issue.model.repository;

import static kr.java.documind.domain.issue.model.entity.QIssue.issue;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import kr.java.documind.domain.issue.model.dto.IssueStatisticsDto;
import kr.java.documind.domain.issue.model.entity.Issue;
import kr.java.documind.domain.issue.model.enums.IssueStatus;
import kr.java.documind.domain.logprocessor.model.enums.LogSeverity;

/**
 * Issue Repository 커스텀 구현체
 * <p>⚠️ 네이밍 규칙: {Entity}RepositoryImpl (Impl 필수!)
 */
public class IssueRepositoryImpl implements IssueRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    // EntityManager 주입 → JPAQueryFactory 생성
    public IssueRepositoryImpl(EntityManager em) {
        this.queryFactory = new JPAQueryFactory(em);
    }

    @Override
    public List<Issue> findByDynamicFilter(
            UUID projectId,
            IssueStatus status,
            LogSeverity severity,
            OffsetDateTime startDate,
            OffsetDateTime endDate) {

        return queryFactory
                .selectFrom(issue)
                .where(
                        projectIdEq(projectId),
                        statusEq(status),
                        severityEq(severity),
                        createdAtBetween(startDate, endDate))
                .orderBy(issue.lastOccurredAt.desc())
                .fetch();
    }

    @Override
    public IssueStatisticsDto getStatistics(UUID projectId) {
        return queryFactory
                .select(
                        Projections.constructor(
                                IssueStatisticsDto.class,
                                issue.count(),
                                issue.occurrenceCount.sum(),
                                issue.status
                                        .when(IssueStatus.OPEN)
                                        .then(1L)
                                        .otherwise(0L)
                                        .sum()))
                .from(issue)
                .where(issue.projectId.eq(projectId))
                .fetchOne();
    }

    // ========== 동적 쿼리 조건 메서드 (BooleanExpression) ==========

    private BooleanExpression projectIdEq(UUID projectId) {
        return projectId != null ? issue.projectId.eq(projectId) : null;
    }

    private BooleanExpression statusEq(IssueStatus status) {
        return status != null ? issue.status.eq(status) : null;
    }

    private BooleanExpression severityEq(LogSeverity severity) {
        return severity != null ? issue.severity.eq(severity) : null;
    }

    private BooleanExpression createdAtBetween(OffsetDateTime start, OffsetDateTime end) {
        if (start != null && end != null) {
            return issue.createdAt.between(start, end);
        } else if (start != null) {
            return issue.createdAt.goe(start);
        } else if (end != null) {
            return issue.createdAt.loe(end);
        }
        return null;
    }
}
```

네이밍 규칙:

- 구현체명: {Entity}RepositoryImpl (Impl 필수!)
- Spring Data JPA가 자동으로 Impl 접미사를 찾아 연결

---

### Step 3: 기본 Repository에 상속

```java
// 📁 IssueRepository.java
package kr.java.documind.domain.issue.model.repository;

import java.util.Optional;
import java.util.UUID;
import kr.java.documind.domain.issue.model.entity.Issue;
import org.springframework.data.jpa.repository.JpaRepository;

/**
- Issue Repository
- <p>IssueRepositoryCustom을 상속하여 커스텀 메서드 사용 가능
*/
public interface IssueRepository
extends JpaRepository<Issue, UUID>, IssueRepositoryCustom { // ✅ Custom 상속

    // Spring Data JPA 기본 메서드
    Optional<Issue> findByFingerprintAndProjectId(String fingerprint, UUID projectId);

    // + IssueRepositoryCustom의 메서드도 사용 가능
    // - findByDynamicFilter()
    // - getStatistics()
    }
```

---

### Step 4: Service에서 사용

```java
// 📁 IssueService.java
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class IssueService {
  private final IssueRepository issueRepository;

  /**
   * 동적 필터링으로 이슈 검색
   */
  public List<IssueListResponse> searchIssues(IssueSearchRequest request) {
      // ✅ Custom Repository 메서드 사용
      List<Issue> issues = issueRepository.findByDynamicFilter(
              request.projectId(),
              request.status(),
              request.severity(),
              request.startDate(),
              request.endDate());

      return issues.stream()
              .map(IssueListResponse::from)
              .collect(Collectors.toList());
  }

  /**
   * 프로젝트 이슈 통계 조회
   */
  public IssueStatisticsResponse getProjectStatistics(UUID projectId) {
      // ✅ Custom Repository 메서드 사용
      IssueStatisticsDto stats = issueRepository.getStatistics(projectId);
      return IssueStatisticsResponse.from(stats);
  }
}
```

---

## 5. 실전 예시

예시 1: 동적 검색 (QueryDSL)

요구사항: 이슈 검색 API에서 status, severity, dateRange를 선택적으로 필터링

```java
// IssueRepositoryCustom.java
public interface IssueRepositoryCustom {
    List<Issue> findByDynamicFilter(
            UUID projectId,
            IssueStatus status,
            LogSeverity severity,
            OffsetDateTime startDate,
            OffsetDateTime endDate);
}

// IssueRepositoryImpl.java
public class IssueRepositoryImpl implements IssueRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public IssueRepositoryImpl(EntityManager em) {
        this.queryFactory = new JPAQueryFactory(em);
    }

    @Override
    public List<Issue> findByDynamicFilter(
            UUID projectId,
            IssueStatus status,
            LogSeverity severity,
            OffsetDateTime startDate,
            OffsetDateTime endDate) {

        return queryFactory
                .selectFrom(issue)
                .where(
                        projectIdEq(projectId),
                        statusEq(status),
                        severityEq(severity),
                        createdAtBetween(startDate, endDate))
                .fetch();
    }

    // 동적 조건 메서드
    private BooleanExpression projectIdEq(UUID projectId) {
        return projectId != null ? issue.projectId.eq(projectId) : null;
    }

    private BooleanExpression statusEq(IssueStatus status) {
        return status != null ? issue.status.eq(status) : null;
    }

    private BooleanExpression severityEq(LogSeverity severity) {
        return severity != null ? issue.severity.eq(severity) : null;
    }

    private BooleanExpression createdAtBetween(OffsetDateTime start, OffsetDateTime end) {
        if (start != null && end != null) {
            return issue.createdAt.between(start, end);
        } else if (start != null) {
            return issue.createdAt.goe(start);
        } else if (end != null) {
            return issue.createdAt.loe(end);
        }
        return null;
    }
}
```

---

## 6. 네이밍 규칙

필수 규칙 (Spring Data JPA 자동 인식)

```markdown
┌───────────────────┬──────────────────────────┬───────────────────────────────────┐
│       대상        │           규칙            │               예시                │
├───────────────────┼──────────────────────────┼───────────────────────────────────┤
│ Custom 인터페이스 │ {Entity}RepositoryCustom │ IssueRepositoryCustom             │
├───────────────────┼──────────────────────────┼───────────────────────────────────┤
│ Custom 구현체     │ {Entity}RepositoryImpl   │ IssueRepositoryImpl ⚠️ Impl 필수! │
├───────────────────┼──────────────────────────┼───────────────────────────────────┤
│ 기본 Repository   │ {Entity}Repository       │ IssueRepository                   │
└───────────────────┴──────────────────────────┴───────────────────────────────────┘
```

메서드 네이밍

```markdown
┌─────────────┬──────────────┬──────────────────────────┐
│    용도     │    접두사    │           예시            │
├─────────────┼──────────────┼──────────────────────────┤
│ 조회 (단건) │ find, get    │ findByComplexCondition() │
├─────────────┼──────────────┼──────────────────────────┤
│ 조회 (목록) │ find, search │ searchByDynamicFilter()  │
├─────────────┼──────────────┼──────────────────────────┤
│ 집계/통계   │ get, count   │ getStatistics()          │
├─────────────┼──────────────┼──────────────────────────┤
│ 수정        │ update, bulk │ bulkUpdateStatus()       │
├─────────────┼──────────────┼──────────────────────────┤
│ 삭제        │ delete, bulk │ bulkDeleteOldLogs()      │
└─────────────┴──────────────┴──────────────────────────┘
```

---

## 7. 주의사항

⚠️ 1. 구현체 이름은 반드시 Impl

```java
// ❌ 잘못된 이름
public class IssueRepositoryCustomImpl implements IssueRepositoryCustom { }
public class IssueRepositoryCustomization implements IssueRepositoryCustom { }
```

```java
// ✅ 올바른 이름
public class IssueRepositoryImpl implements IssueRepositoryCustom { }
```

이유: Spring Data JPA가 {Entity}RepositoryImpl 패턴을 자동으로 찾음

---

⚠️ 2. Custom 인터페이스는 기본 Repository에 상속

```java
// ❌ 잘못된 구조
public interface IssueRepository extends JpaRepository<Issue, UUID> { }
public interface IssueRepositoryCustom { }  // 별도 인터페이스
```

```java
// ✅ 올바른 구조
public interface IssueRepository
extends JpaRepository<Issue, UUID>, IssueRepositoryCustom {  // 상속!
}
```

---

⚠️ 3. QueryDSL 의존성 추가 필요

```java
// build.gradle
dependencies {
implementation 'com.querydsl:querydsl-jpa:5.0.0:jakarta'
annotationProcessor "com.querydsl:querydsl-apt:5.0.0:jakarta"
annotationProcessor "jakarta.annotation:jakarta.annotation-api"
annotationProcessor "jakarta.persistence:jakarta.persistence-api"
}
```

---

⚠️ 4. JPAQueryFactory Bean 등록

```java
// 📁 QueryDslConfig.java
@Configuration
public class QueryDslConfig {

  @Bean
  public JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
      return new JPAQueryFactory(entityManager);
  }
}
```

---

⚠️ 5. N+1 문제 주의

```java
// ❌ N+1 문제 발생
public List<Issue> findAllWithComments() {
return queryFactory
.selectFrom(issue)
.fetch();  // 이슈만 조회, 댓글은 Lazy Loading → N+1
}
```

```java
// ✅ Fetch Join 사용
public List<Issue> findAllWithComments() {
return queryFactory
.selectFrom(issue)
.leftJoin(issue.comments).fetchJoin()  // 한 번에 조회
.fetch();
}
```

---

## 8. FAQ

Q1. Custom Repository vs @Query?

A:

```markdown
┌─────────────┬─────────────────────┬─────────────────────┐
│    항목     │  Custom Repository  │       @Query        │
├─────────────┼─────────────────────┼─────────────────────┤
│ 용도        │ 복잡한 동적 쿼리     │ 단순 정적 쿼리       │
├─────────────┼─────────────────────┼─────────────────────┤
│ 코드 재사용 │ ✅ 가능             │ ❌ 어려움           │
├─────────────┼─────────────────────┼─────────────────────┤
│ 타입 안정성 │ ✅ QueryDSL 사용 시 │ ⚠️  문자열          │
├─────────────┼─────────────────────┼─────────────────────┤
│ 테스트      │ ✅ 쉬움             │ ⚠️  통합 테스트 필요 │
└─────────────┴─────────────────────┴─────────────────────┘
```

```java
예시:
// 간단한 쿼리 → @Query
@Query("SELECT i FROM Issue i WHERE i.status = :status")
List<Issue> findByStatus(@Param("status") IssueStatus status);

// 복잡한 동적 쿼리 → Custom Repository
List<Issue> findByDynamicFilter(status, severity, dateRange);
```

---

Q2. QueryDSL vs Native Query vs JPQL?

A:

```markdown
┌──────────────┬─────────────────────┬──────────────────┬─────────────────────────┐
│     기술     │        장점         │       단점       │        사용 시기         │
├──────────────┼─────────────────────┼──────────────────┼─────────────────────────┤
│ QueryDSL     │ 타입 안전, IDE 지원  │ 러닝 커브        │ 동적 쿼리, 복잡한 조건   │
├──────────────┼─────────────────────┼──────────────────┼─────────────────────────┤
│ JPQL         │ 표준, 간결          │ 동적 쿼리 어려움 │ 정적 쿼리                 │
├──────────────┼─────────────────────┼──────────────────┼─────────────────────────┤
│ Native Query │ DB 최적화           │ DB 종속          │ DB 전용 기능 (JSONB 등)  │
└──────────────┴─────────────────────┴──────────────────┴─────────────────────────┘
```

---

Q3. Service 로직 vs Repository 로직?

A:

```markdown
┌──────────────────┬────────────┬─────────────────────────────────┐
│       로직       │    위치    │              예시                │
├──────────────────┼────────────┼─────────────────────────────────┤
│ 데이터 조회/변환  │ Repository │ findByDynamicFilter()           │
├──────────────────┼────────────┼─────────────────────────────────┤
│ 비즈니스 규칙     │ Service    │ if (issue.isResolved()) { ... } │
├──────────────────┼────────────┼─────────────────────────────────┤
│ 트랜잭션 처리     │ Service    │ @Transactional                  │
├──────────────────┼────────────┼─────────────────────────────────┤
│ 복잡한 집계      │ Repository │ getStatistics()                  │
└──────────────────┴────────────┴─────────────────────────────────┘
```

---

Q4. 기존 JPA Repository를 Custom으로 변경해야 하나?

A: 필요할 때만 변경하세요.

```markdown
// ✅ 이런 건 그대로 유지
Optional<Issue> findByFingerprintAndProjectId(String fingerprint, UUID projectId);

// ✅ 이런 건 Custom으로
List<Issue> searchByMultipleFilters(/* 여러 선택적 파라미터 */);
```

Q5. Q클래스가 생성되지 않을 때?

A: build.gradle 설정 확인이 가장 중요합니다.

```java
gradle
// ✅ 이 설정이 있어야 자동 생성됨
def querydslDir = "$buildDir/generated/querydsl"

sourceSets {
    main.java.srcDirs += [querydslDir]
}

tasks.withType(JavaCompile) {
    options.generatedSourceOutputDirectory = file(querydslDir)
}
```

해결 순서:
1. build.gradle에 위 설정 추가
2. `./gradlew clean build` 실행
3. `build/generated/querydsl` 폴더에 Q클래스 확인
4. IDE가 자동으로 인식 (수동 마킹 불필요)

여전히 안 될 때:
- Entity에 `@Entity` 어노테이션 있는지 확인
- Gradle JVM 버전 확인 (Java 17 이상)
- IntelliJ 캐시 삭제: `File` → `Invalidate Caches / Restart`
```

---

## 📚 참고 자료

- [Spring Data JPA Custom Implementations](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/#repositories.custom-implementations)
- [QueryDSL Reference](http://querydsl.com/static/querydsl/latest/reference/html/)
