package com.synapse.clinicafemina.security;

import com.synapse.clinicafemina.domain.Usuario;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED_PATHS = Set.of(
            "/api/auth/me",
            "/api/auth/change-password"
    );

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (mustChangePassword(authentication) && !isAllowed(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"status\":403,\"code\":\"PASSWORD_CHANGE_REQUIRED\","
                            + "\"message\":\"Troca de senha obrigatória.\"}"
            );
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean mustChangePassword(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Usuario usuario)) {
            return false;
        }
        return Boolean.TRUE.equals(usuario.getMustChangePassword());
    }

    private boolean isAllowed(HttpServletRequest request) {
        return HttpMethod.OPTIONS.matches(request.getMethod())
                || ALLOWED_PATHS.contains(request.getRequestURI());
    }
}
