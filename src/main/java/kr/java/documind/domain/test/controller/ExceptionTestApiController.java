package kr.java.documind.domain.test.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import kr.java.documind.global.exception.BadRequestException;
import kr.java.documind.global.exception.ConflictException;
import kr.java.documind.global.exception.ForbiddenException;
import kr.java.documind.global.exception.NotFoundException;
import kr.java.documind.global.exception.UnauthorizedException;
import lombok.Getter;
import lombok.Setter;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/test/exception")
public class ExceptionTestApiController {

    // ── MethodArgumentNotValidException (@Valid @RequestBody) ──

    @PostMapping("/method-argument-not-valid")
    public void apiMethodArgumentNotValid(@Valid @RequestBody TestDto dto) {
        // 유효성 검증 실패 시 MethodArgumentNotValidException 발생
    }

    // ── ConstraintViolationException (@Validated @RequestParam) ──

    @GetMapping("/constraint-violation")
    public void apiConstraintViolation(
            @RequestParam @Min(value = 1, message = "값은 1 이상이어야 합니다.") int value) {
        // 제약 조건 위반 시 ConstraintViolationException 발생
    }

    // ── HttpMessageNotReadableException (잘못된 JSON) ──

    @PostMapping("/http-message-not-readable")
    public void apiHttpMessageNotReadable(@RequestBody TestDto dto) {
        // 파싱 불가능한 JSON이 들어오면 HttpMessageNotReadableException 발생
    }

    // ── MissingServletRequestParameterException (필수 파라미터 누락) ──

    @GetMapping("/missing-param")
    public void apiMissingParam(@RequestParam String requiredParam) {
        // requiredParam 없이 호출하면 MissingServletRequestParameterException 발생
    }

    // ── MethodArgumentTypeMismatchException (타입 불일치) ──

    @GetMapping("/type-mismatch")
    public void apiTypeMismatch(@RequestParam Integer number) {
        // number=abc 등으로 호출하면 MethodArgumentTypeMismatchException 발생
    }

    // ── BusinessException 하위 예외들 ──

    @GetMapping("/bad-request")
    public void apiBadRequest() {
        throw new BadRequestException("테스트: 잘못된 요청입니다.");
    }

    @GetMapping("/unauthorized")
    public void apiUnauthorized() {
        throw new UnauthorizedException("테스트: 인증이 필요합니다.");
    }

    @GetMapping("/forbidden")
    public void apiForbidden() {
        throw new ForbiddenException("테스트: 접근 권한이 없습니다.");
    }

    @GetMapping("/not-found")
    public void apiNotFound() {
        throw new NotFoundException("테스트: 리소스를 찾을 수 없습니다.");
    }

    @GetMapping("/conflict")
    public void apiConflict() {
        throw new ConflictException("테스트: 리소스 충돌이 발생했습니다.");
    }

    // ── 일반 Exception (500) ──

    @GetMapping("/internal-error")
    public void apiInternalError() {
        throw new RuntimeException("테스트: 서버 내부 오류가 발생했습니다.");
    }

    // ── Validation DTO ──

    @Getter
    @Setter
    static class TestDto {

        @NotBlank(message = "이름은 필수입니다.")
        private String name;

        @Min(value = 1, message = "나이는 1 이상이어야 합니다.")
        private int age;
    }
}
