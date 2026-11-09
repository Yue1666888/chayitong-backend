package com.chayitong.config;

import com.chayitong.auth.AccessTokenAuthenticator;
import com.chayitong.auth.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class BearerTokenFilter extends OncePerRequestFilter {

    private final AccessTokenAuthenticator authenticator;

    public BearerTokenFilter(AccessTokenAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null) {
            if (!header.startsWith("Bearer ") || header.length() == 7) {
                SecurityConfig.writeError(response, com.chayitong.common.api.ErrorCode.UNAUTHORIZED);
                return;
            }
            AuthenticatedUser user = authenticator.authenticate(header.substring(7));
            if (user == null) {
                SecurityConfig.writeError(response, com.chayitong.common.api.ErrorCode.UNAUTHORIZED);
                return;
            }
            var authentication = new UsernamePasswordAuthenticationToken(
                    user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role())));
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        chain.doFilter(request, response);
    }
}
