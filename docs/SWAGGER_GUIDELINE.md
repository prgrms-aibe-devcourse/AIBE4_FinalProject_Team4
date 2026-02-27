# 📘 API 문서화 (Swagger/OpenAPI) 가이드라인

본 프로젝트는 유지보수성과 코드 가독성을 위해 **Swagger 설정을 비즈니스 로직과 분리**하는 전략을 사용함. DTO는 직관적인 설정을 위해 어노테이션을 직접 사용하되, Controller는 별도의 인터페이스(`SwaggerDocs`)를 통해 문서를 관리함.

---

## 1. DTO (Data Transfer Object)

DTO는 데이터의 구조를 보여주는 객체이므로, 클래스 내부 필드에 직접 `@Schema` 어노테이션을 부착하여 명세를 정의함.

### 📌 주요 어노테이션

| Annotation | 속성 | 설명 | 필수 여부 |
| --- | --- | --- | --- |
| `@Schema` | `description` | 해당 필드에 대한 설명 | ✅ 필수 |
|  | `example` | 예시 데이터 (문자열 형태) | ✅ 필수 |

### 💻 작성 예시
```java
package com.project.domain.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "회원 가입 응답 DTO")
public class MemberSignupResponse {

    @Schema(description = "회원 ID", example = "1")
    private Long memberId;

    @Schema(description = "이메일", example = "user@example.com")
    private String email;

    @Schema(description = "가입 일시", example = "2024-02-06 14:30:00")
    private String joinedAt;
}
```

---

## 2. Controller (API Layer)

Controller 클래스가 Swagger 어노테이션으로 뒤덮이는 것을 방지하기 위해, **별도의 Docs 인터페이스를 정의**하여 응답 명세를 관리함.

### 2.1 📄 Swagger Docs 인터페이스 정의

Controller와 1:1로 매핑되는 인터페이스를 생성하고, 커스텀 어노테이션을 정의하여 구체적인 응답(성공/실패 예시)을 작성함.

#### 📌 주요 어노테이션

| Annotation | 설명 | 위치 |
| --- | --- | --- |
| `@Target` / `@Retention` | 커스텀 어노테이션 정의를 위한 메타 어노테이션 | 인터페이스 내부 |
| `@ApiResponses` | 여러 개의 `@ApiResponse`를 묶는 컨테이너 | 커스텀 어노테이션 위 |
| `@ApiResponse` | 특정 HTTP 상태 코드에 대한 응답 명세 | `@ApiResponses` 내부 |
| `@Content` | 응답 본문(Media Type) 설정 | `@ApiResponse` 내부 |
| `@ExampleObject` | 구체적인 JSON 응답 예시 (name, summary, value) | `@Content` 내부 |

#### 💻 작성 예시 (`MemberSwaggerDocs.java`)
```java
package com.project.domain.member.controller;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import com.project.global.response.CommonResponse;
import java.lang.annotation.*;

public interface MemberSwaggerDocs {

    // 1. 커스텀 어노테이션 정의 (메서드에 붙일 이름)
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponses({
        // 성공 케이스
        @ApiResponse(responseCode = "200", description = "회원 가입 성공",
            content = @Content(
                schema = @Schema(implementation = CommonResponse.class),
                examples = @ExampleObject(value = """
                    {
                        "code": 200,
                        "message": "회원 가입에 성공했습니다.",
                        "data": {
                            "memberId": 1,
                            "email": "user@example.com"
                        }
                    }
                    """)
            )),
        // 실패 케이스 (여러 예시를 보여줄 경우 examples 사용)
        @ApiResponse(responseCode = "400", description = "잘못된 요청",
            content = @Content(
                schema = @Schema(implementation = CommonResponse.class),
                examples = {
                    @ExampleObject(name = "DuplicateEmail", summary = "이메일 중복",
                        value = "{\"code\": 400, \"message\": \"이미 존재하는 이메일입니다.\", \"data\": null}"),
                    @ExampleObject(name = "InvalidFormat", summary = "형식 오류",
                        value = "{\"code\": 400, \"message\": \"비밀번호 형식이 올바르지 않습니다.\", \"data\": null}")
                }
            ))
    })
    @interface SignupError { // Controller에서 사용할 어노테이션 이름
    }
}
```

---

### 2.2 🎮 Controller 적용

Controller에서는 비즈니스 로직에 집중하기 위해 `@Operation`으로 요약 정보만 제공하고, 상세 응답은 위에서 만든 **커스텀 어노테이션**(`@MemberSwaggerDocs.xxx`)을 붙여 해결함.

#### 📌 주요 어노테이션

| Annotation | 속성 | 설명 |
| --- | --- | --- |
| `@Tag` | `name`, `description` | Controller 클래스 그룹 명 및 설명 |
| `@Operation` | `summary`, `description` | API 메서드의 기능 요약 |
| `@Parameter` | `description`, `example` | PathVariable, RequestParam 등의 파라미터 설명 |
| **`@Docs.Custom`** | - | `SwaggerDocs` 인터페이스에서 정의한 커스텀 어노테이션 |

#### 💻 작성 예시 (`MemberApiController.java`)
```java
package com.project.domain.member.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Member", description = "회원 관련 API") // 1. 그룹 태그
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberApiController {

    private final MemberService memberService;

    @Operation(summary = "회원 가입", description = "신규 회원을 등록합니다.") // 2. 요약 정보
    @MemberSwaggerDocs.SignupError // 3. 커스텀 Docs 어노테이션 적용
    @PostMapping
    public CommonResponse<MemberSignupResponse> signup(
        @RequestBody MemberSignupRequest request) {
        
        return CommonResponse.success(memberService.signup(request));
    }

    @Operation(summary = "회원 조회", description = "ID로 회원을 조회합니다.")
    @GetMapping("/{memberId}")
    public CommonResponse<MemberResponse> getMember(
        @Parameter(description = "회원 ID", example = "1", required = true) // 4. 파라미터 설명
        @PathVariable Long memberId) {
        
        return CommonResponse.success(memberService.findById(memberId));
    }
}
```