package kr.java.documind.global.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.stream.Collectors;
import kr.java.documind.global.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@ControllerAdvice(annotations = Controller.class)
public class GlobalViewExceptionHandler {

    /**
     * @Valid on @ModelAttribute - BindingResult 없이 던져진 경우 (안전망) 직전 페이지로 리다이렉트하며 flash 속성으로 에러 메시지
     * 전달 Referer 헤더에서 path만 추출해 오픈 리다이렉트 방지
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public String handleMethodArgumentNotValid(
        MethodArgumentNotValidException e, RedirectAttributes redirectAttributes,
        HttpServletRequest request
    ) {
        String errorMessage = e.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining(", "));

        log.warn("ViewValidationException [{}]: {}", request.getRequestURI(), errorMessage);

        redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
        return "redirect:" + extractSafePath(request.getHeader("Referer"));
    }

    /**
     * @Validated on @RequestParam, @PathVariable
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ModelAndView handleConstraintViolation(ConstraintViolationException e,
        HttpServletRequest request) {
        log.warn("ViewConstraintViolationException [{}]: {}", request.getRequestURI(),
            e.getMessage());

        ModelAndView mav = new ModelAndView("error/400");
        mav.addObject("status", HttpStatus.BAD_REQUEST.value());
        mav.addObject("message", "요청 값이 올바르지 않습니다.");
        mav.setStatus(HttpStatus.BAD_REQUEST);
        return mav;
    }

    /**
     * 필수 @RequestParam 누락
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ModelAndView handleMissingServletRequestParameter(
        MissingServletRequestParameterException e, HttpServletRequest request
    ) {
        log.warn("ViewMissingParamException [{}]: {}", request.getRequestURI(), e.getMessage());

        ModelAndView mav = new ModelAndView("error/400");
        mav.addObject("status", HttpStatus.BAD_REQUEST.value());
        mav.addObject("message", "필수 요청 파라미터가 없습니다: " + e.getParameterName());
        mav.setStatus(HttpStatus.BAD_REQUEST);
        return mav;
    }

    /**
     * @PathVariable, @RequestParam 타입 불일치
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ModelAndView handleMethodArgumentTypeMismatch(
        MethodArgumentTypeMismatchException e, HttpServletRequest request
    ) {
        log.warn("ViewTypeMismatchException [{}]: {}", request.getRequestURI(), e.getMessage());

        ModelAndView mav = new ModelAndView("error/400");
        mav.addObject("status", HttpStatus.BAD_REQUEST.value());
        mav.addObject("message", "요청 파라미터 타입이 올바르지 않습니다: " + e.getName());
        mav.setStatus(HttpStatus.BAD_REQUEST);
        return mav;
    }

    /**
     * 비즈니스 예외 (BusinessException 및 하위 예외) HTTP 상태 코드에 따라 전용 에러 페이지로 포워드
     */
    @ExceptionHandler(BusinessException.class)
    public ModelAndView handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("ViewBusinessException [{}]: {}", request.getRequestURI(), e.getMessage());

        int statusCode = e.getStatus().value();
        String viewName = switch (statusCode) {
            case 400 -> "error/400";
            case 401 -> "error/401";
            case 403 -> "error/403";
            case 404 -> "error/404";
            case 409 -> "error/409";
            default -> "error/error";
        };

        ModelAndView mav = new ModelAndView(viewName);
        mav.addObject("status", statusCode);
        mav.addObject("message", e.getMessage());
        mav.setStatus(e.getStatus());
        return mav;
    }

    /**
     * 그 외 모든 예외 (500)
     */
    @ExceptionHandler(Exception.class)
    public ModelAndView handleException(Exception e, HttpServletRequest request) {
        log.error("Unhandled view exception [{}]", request.getRequestURI(), e);

        ModelAndView mav = new ModelAndView("error/500");
        mav.addObject("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        mav.addObject("message", "서버 오류가 발생했습니다.");
        mav.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        return mav;
    }

    /**
     * Referer 헤더에서 path만 추출 (오픈 리다이렉트 방지) http://malicious.com/evil → /evil (자체 서버로만 리다이렉트)
     */
    private String extractSafePath(String referer) {
        if (referer == null) {
            return "/";
        }
        try {
            String path = URI.create(referer).getPath();
            return (path != null && !path.isEmpty()) ? path : "/";
        } catch (IllegalArgumentException e) {
            return "/";
        }
    }
}
