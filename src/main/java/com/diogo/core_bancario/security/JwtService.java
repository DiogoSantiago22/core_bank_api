package com.diogo.core_bancario.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class JwtService {

    // Secret key used to sign the token (in a real app, this should be in application.properties)
    private static final String SECRET = "chave_secreta_super_segura_para_o_core_bancario_2026";
    private final Algorithm algorithm = Algorithm.HMAC256(SECRET);

    // Generates a token valid for 24 hours associated with the user's email
    public String generateToken(String email) {
        return JWT.create()
                .withSubject(email)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + 86400000)) // 24h in milliseconds
                .sign(algorithm);
    }

    // Validates the tokens mathematical signature and returns the email inside it
    public String validateToken(String token) {
        try {
            return JWT.require(algorithm)
                    .build()
                    .verify(token)
                    .getSubject();
        } catch (JWTVerificationException exception) {
            // If the token was tampered with or has expired, return null (reject access)
            return null;
        }
    }
}