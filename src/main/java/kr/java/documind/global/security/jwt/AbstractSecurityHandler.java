package kr.java.documind.global.security.jwt;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;

public abstract class AbstractSecurityHandler {

    protected boolean isApiRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String accept = request.getHeader("Accept");
        String xRequestedWith = request.getHeader("X-Requested-With");

        return uri.startsWith("/api/")
                || "XMLHttpRequest".equals(xRequestedWith)
                || (accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE));
    }
}
