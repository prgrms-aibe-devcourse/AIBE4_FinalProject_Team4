package kr.java.documind.global.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@Getter
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    private final String secret;
    private final long accessExpirationSeconds;
    private final long refreshExpirationSeconds;
    private final String accessCookieName;
    private final String refreshCookieName;
    private final boolean cookieSecure;

    public JwtProperties(
            String secret,
            @DefaultValue("1800") long accessExpirationSeconds,
            @DefaultValue("604800") long refreshExpirationSeconds,
            @DefaultValue("access_token") String accessCookieName,
            @DefaultValue("refresh_token") String refreshCookieName,
            @DefaultValue("false") boolean cookieSecure) {
        this.secret = secret;
        this.accessExpirationSeconds = accessExpirationSeconds;
        this.refreshExpirationSeconds = refreshExpirationSeconds;
        this.accessCookieName = accessCookieName;
        this.refreshCookieName = refreshCookieName;
        this.cookieSecure = cookieSecure;
    }
}
