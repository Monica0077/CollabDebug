package com.collabdebug.collabdebug_backend.security;

import com.collabdebug.collabdebug_backend.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder; // Added import for robust query parsing

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    @Autowired
    private JwtService jwtService;

    // IMPORTANT: You need a StompPrincipal class defined somewhere
    // to correctly map the username to a Principal object.
    // For this example, I'll assume it exists or you'll create it.
    // Example: class StompPrincipal implements Principal { private final String name; public StompPrincipal(String name) { this.name = name; } @Override public String getName() { return name; } }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) throws Exception {

        String token = null;

        // 1. Try to extract the token from the "Authorization" header (Preferred way)
        List<String> auth = request.getHeaders().get("Authorization");
        if (auth != null && !auth.isEmpty()) {
            String header = auth.get(0);
            if (header.startsWith("Bearer ")) {
                token = header.substring(7);
            }
        }

        // 2. Fallback: Try to extract token from query parameter (SockJS fallback)
        if (token == null && request instanceof ServletServerHttpRequest) {
            HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
            String query = servletRequest.getQueryString();

            if (StringUtils.hasText(query)) {
                // ✅ FIX: Use robust Spring utility to extract the 'token' query parameter
                token = UriComponentsBuilder.fromUriString("?" + query)
                        .build()
                        .getQueryParams()
                        .getFirst("token");
            }
        }

        // --- Authentication Logic ---
        if (!StringUtils.hasText(token)) {
            System.out.println("Handshake rejected: No JWT token found in header or query.");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            String username = jwtService.extractUsername(token);
            // Assuming isTokenValid also checks expiration and signature
            if (jwtService.isTokenValid(token, username)) {
                // Authentication successful: attach principal
                // NOTE: Ensure StompPrincipal is available or use another Principal implementation
                Principal user = new StompPrincipal(username);
                attributes.put("principal", user);
                System.out.println("✅ HTTP Handshake authenticated for user: " + username);
                System.out.println("   Principal stored in session attributes");
                return true;
            }
        } catch (Exception e) {
            // Token validation failed (expired, malformed, etc.)
            System.err.println("Handshake rejected: JWT validation failed for token: " + e.getMessage());
        }

        // Final rejection if validation failed
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;

    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {}
}