package com.laboratory.auth.infrastructure.security;

import com.laboratory.auth.domain.model.Usuario;
import com.laboratory.auth.domain.usecase.UsuarioUsecase;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UsuarioUsecase usuarioUsecase;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);
        final String email = jwtService.extractEmail(jwt);

        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            Usuario usuario = usuarioUsecase.buscarUsuarioEmail(email);
            if (usuario != null && jwtService.isTokenValid(jwt, email)) {
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(email, null,
                                List.of(new SimpleGrantedAuthority(autoridad(usuario.getRol()))));
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
        filterChain.doFilter(request, response);
    }

    private String autoridad(String rol) {
        if (rol == null || rol.isBlank()) {
            return "ROLE_USUARIO";
        }
        String normalizado = rol.toUpperCase();
        if (normalizado.contains("ADMIN")) {
            return "ROLE_ADMINISTRADOR";
        }
        if (normalizado.contains("GESTOR")) {
            return "ROLE_GESTOR";
        }
        return "ROLE_" + normalizado.replaceAll("[^A-Z_]", "_");
    }
}