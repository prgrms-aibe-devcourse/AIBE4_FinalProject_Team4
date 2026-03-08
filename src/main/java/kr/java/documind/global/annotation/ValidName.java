package kr.java.documind.global.annotation;

import jakarta.validation.Constraint;
import jakarta.validation.OverridesAttribute;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이름성 텍스트 필드 공통 검증 합성 애노테이션.
 *
 * <ul>
 *   <li>필수 입력 — 빈 문자열·공백 불가 ({@link NotBlank})
 *   <li>최대 글자수 제한 — 기본값 100자 ({@link Size})
 *   <li>허용 문자 — 한글(가-힣) · 영문(a-zA-Z) · 숫자(0-9) · 공백만 허용 ({@link Pattern})
 * </ul>
 *
 * <p>사용 예:
 *
 * <pre>
 *   {@literal @}ValidName(
 *       max             = 20,
 *       notBlankMessage = "닉네임을 입력해주세요.",
 *       maxMessage      = "닉네임은 20자 이하로 입력해주세요.")
 *   String nickname;
 * </pre>
 */
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = {})
@NotBlank
@Size
@Pattern(regexp = "^[가-힣a-zA-Z0-9 ]*$")
public @interface ValidName {

    String message() default "올바르지 않은 이름 형식입니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    @OverridesAttribute(constraint = NotBlank.class, name = "message")
    String notBlankMessage() default "필수 항목입니다.";

    @OverridesAttribute(constraint = Size.class, name = "max")
    int max() default 100;

    @OverridesAttribute(constraint = Size.class, name = "message")
    String maxMessage() default "100자 이하로 입력해주세요.";

    @OverridesAttribute(constraint = Pattern.class, name = "message")
    String patternMessage() default "한글, 영문, 숫자, 공백만 사용할 수 있습니다.";
}
