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
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Validated
@Controller
@RequestMapping("/test/exception")
public class ExceptionTestViewController {

    @GetMapping
    public String testPage() {
        return "test/exception-test";
    }

    // ── MethodArgumentNotValidException (@Valid @ModelAttribute) ──

    @PostMapping("/view/method-argument-not-valid")
    public String viewMethodArgumentNotValid(@Valid ViewTestForm form) {
        return "test/exception-test";
    }

    // ── ConstraintViolationException (@Validated @RequestParam) ──

    @GetMapping("/view/constraint-violation")
    public String viewConstraintViolation(
            @RequestParam @Min(value = 1, message = "값은 1 이상이어야 합니다.") int value) {
        return "test/exception-test";
    }

    // ── MissingServletRequestParameterException (필수 파라미터 누락) ──

    @GetMapping("/view/missing-param")
    public String viewMissingParam(@RequestParam String requiredParam) {
        return "test/exception-test";
    }

    // ── MethodArgumentTypeMismatchException (타입 불일치) ──

    @GetMapping("/view/type-mismatch")
    public String viewTypeMismatch(@RequestParam Integer number) {
        return "test/exception-test";
    }

    // ── BusinessException 하위 예외들 ──

    @GetMapping("/view/bad-request")
    public String viewBadRequest() {
        throw new BadRequestException("테스트: 잘못된 요청입니다.");
    }

    @GetMapping("/view/unauthorized")
    public String viewUnauthorized() {
        throw new UnauthorizedException("테스트: 인증이 필요합니다.");
    }

    @GetMapping("/view/forbidden")
    public String viewForbidden() {
        throw new ForbiddenException("테스트: 접근 권한이 없습니다.");
    }

    @GetMapping("/view/not-found")
    public String viewNotFound() {
        throw new NotFoundException("테스트: 리소스를 찾을 수 없습니다.");
    }

    @GetMapping("/view/conflict")
    public String viewConflict() {
        throw new ConflictException("테스트: 리소스 충돌이 발생했습니다.");
    }

    // ── 일반 Exception (500) ──

    @GetMapping("/view/internal-error")
    public String viewInternalError() {
        throw new RuntimeException("테스트: 서버 내부 오류가 발생했습니다.");
    }

    // ── Validation Form DTO ──

    @Getter
    @Setter
    static class ViewTestForm {

        @NotBlank(message = "이름은 필수입니다.")
        private String name;

        @Min(value = 1, message = "나이는 1 이상이어야 합니다.")
        private int age;
    }
}
