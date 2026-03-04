package kr.java.documind.global.security.jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.java.documind.domain.member.model.enums.GlobalRole;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

@Getter
public class CustomUserDetails implements UserDetails, OAuth2User {

    private final UUID memberId;
    private final GlobalRole globalRole;
    private final Collection<? extends GrantedAuthority> authorities;
    private final Map<String, Object> attributes;

    public CustomUserDetails(UUID memberId, GlobalRole globalRole) {
        this(memberId, globalRole, Map.of());
    }

    public CustomUserDetails(UUID memberId, GlobalRole globalRole, Map<String, Object> attributes) {
        this.memberId = memberId;
        this.globalRole = globalRole;
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + globalRole.name()));
        this.attributes = attributes != null ? Map.copyOf(attributes) : Map.of();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public String getName() {
        return memberId.toString();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return memberId.toString();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
