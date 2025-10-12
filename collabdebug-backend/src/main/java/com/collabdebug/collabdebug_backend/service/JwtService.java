package com.collabdebug.collabdebug_backend.service;

import com.collabdebug.collabdebug_backend.model.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key; // Import Key
import java.util.Date;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long expiration;

    // Helper method to consistently get the key
    private Key getSigningKey() {
        // This is how you convert the plain string to a security-compliant Key object
        return Keys.hmacShaKeyFor(secretKey.getBytes());
    }

    public String generateToken(User user)
    {
        return Jwts.builder()
                .setSubject(user.getUsername())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                // Use the consistent Key object for signing
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // Extract username from token
    public String extractUsername(String token) {
        // Use the modern parser builder and the consistent Key object for verification
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    // Check if token is valid
    public boolean isTokenValid(String token, String username) {
        try {
            String tokenUsername = extractUsername(token);
            return tokenUsername.equals(username) && !isTokenExpired(token);
        } catch (Exception e) {
            // Log the exception here if you want to see if the token is invalid for another reason
            // e.printStackTrace();
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        Date expirationDate = Jwts.parserBuilder() // Use modern parser builder
                .setSigningKey(getSigningKey())     // Use the consistent Key object
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getExpiration();
        return expirationDate.before(new Date());
    }
}