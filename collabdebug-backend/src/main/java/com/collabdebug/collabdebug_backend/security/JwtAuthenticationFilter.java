package com.collabdebug.collabdebug_backend.security;


import com.collabdebug.collabdebug_backend.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.util.AntPathMatcher; // Import AntPathMatcher

import java.io.IOException;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    // AntPathMatcher for wildcard path matching (e.g., /api/auth/**)
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    // Define the public path pattern to exclude from JWT validation
    private static final String AUTH_PATH_PATTERN = "/api/auth/**";

    public JwtAuthenticationFilter(JwtService jwtService, CustomUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestUri = request.getRequestURI();

        // 1. CRITICAL: Check if the request should be skipped (Login/Register/Preflight OPTIONS)
        // This is necessary because Spring Security's permitAll() happens AFTER the filter chain.
        if (request.getMethod().equals("OPTIONS") || pathMatcher.match(AUTH_PATH_PATTERN, requestUri)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. JWT Extraction and Validation (Only for secured endpoints)
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            String username = jwtService.extractUsername(token);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                var userDetails = userDetailsService.loadUserByUsername(username);

                // Note: Ensure your isTokenValid method correctly uses the userDetails object
                if (jwtService.isTokenValid(token, userDetails.getUsername())) {
                    var authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities()
                    );
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        }

        // 3. Continue the filter chain
        filterChain.doFilter(request, response);
    }
}