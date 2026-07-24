package com.voltwise.core.auth;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AuthHeaderFilter implements Filter {
    private final JwtTokenProvider tokenProvider;

    public AuthHeaderFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpRequest) {
            String header = httpRequest.getHeader("Authorization");
            String email = null;
            if (header != null && header.startsWith("Bearer ")) {
                String token = header.substring(7).trim();
                email = tokenProvider.extractEmail(token);
                if (email != null) {
                    UserContext.setCurrentUserEmail(email);
                }
            }
            if (requiresAuthentication(httpRequest) && email == null
                    && response instanceof HttpServletResponse httpResponse) {
                writeUnauthorized(httpRequest, httpResponse);
                return;
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    private boolean requiresAuthentication(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/")
                && !path.startsWith("/api/v1/auth/")
                && !"OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        String path = request.getRequestURI().replace("\\", "\\\\").replace("\"", "\\\"");
        response.getWriter().write(
                "{\"status\":401,\"error\":\"Unauthorized\","
                        + "\"message\":\"Geçerli bir oturum gereklidir.\","
                        + "\"path\":\"" + path + "\",\"fieldErrors\":{}}"
        );
    }
}
