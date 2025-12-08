package com.mycompany.projecttracker.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.enterprise.context.ApplicationScoped;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;

@ApplicationScoped
public class TokenService {

    // En producción, esta clave debe estar en una variable de entorno o vault
    private static final String SECRET_KEY = "MiSuperSecretoParaFirmarTokensJWT_DebeSerLargo";
    private final SecretKey key = Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));

    /**
     * Genera un JWT firmado con roles y expiración (1 hora).
     */
    public String generateToken(String username, Set<String> roles) {
        return Jwts.builder()
            .subject(username)
            .claim("groups", roles)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 3600000)) // 1 hora
            .signWith(key)
            .compact();
    }

    /**
     * Valida el token y extrae el usuario (Subject).
     * Lanza excepción si el token es inválido o expiró.
     */
    public String validateTokenAndGetUser(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
        return claims.getSubject();
    }

    /**
     * Extrae los roles del token.
     */
    public Set<String> getRoles(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
        // Asumimos que viene como una lista de strings
        return Set.copyOf(claims.get("groups", java.util.List.class));
    }
}