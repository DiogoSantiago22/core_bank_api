package com.diogo.core_bancario.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        // Check if the "Authorization" header exists
        String authHeader = request.getHeader("Authorization");

        // The standard for JWT is to send it as "Bearer eyJhbGci..."
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.replace("Bearer ", "");

            // Validate the signature. If someone tampered with it, validateToken returns null
            String email = jwtService.validateToken(token);

            // If token is valid and user is not yet authenticated in this request
            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // Create the official Spring Security "VIP Pass"
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(email, null, new ArrayList<>());

                // Save the pass in the current security context
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        // Let the request continue to the next filter or to the Controller
        filterChain.doFilter(request, response);
    }
}