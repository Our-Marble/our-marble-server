package com.sparta.ourmarbleserver.config;

import com.sparta.ourmarbleserver.auth.dto.AuthPlayer;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

public class JwtAuthenticationToken extends AbstractAuthenticationToken {
    private final AuthPlayer authPlayer;

    public JwtAuthenticationToken(
            AuthPlayer authPlayer,
            Collection<? extends GrantedAuthority> authorities
    ) {
        super(authorities);
        this.authPlayer = authPlayer;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return authPlayer;
    }
}
