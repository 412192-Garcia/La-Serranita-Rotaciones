package org.example.laserranitarotaciones.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Si el pedido trae "Authorization: Bearer <token>" válido, queda autenticado. El token es del usuario de ahora:
 * si en el .env se cambia el usuario, los tokens viejos dejan de servir.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwt;
    private final Acceso acceso;

    public JwtAuthFilter(JwtService jwt, Acceso acceso) {
        this.jwt = jwt;
        this.acceso = acceso;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest pedido, @NonNull HttpServletResponse respuesta,
                                    @NonNull FilterChain cadena) throws ServletException, IOException {
        String cabecera = pedido.getHeader("Authorization");
        if (cabecera != null && cabecera.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) {
            jwt.leer(cabecera.substring(7))
                    .filter(c -> acceso.usuario().equals(c.getSubject()))
                    .ifPresent(c -> SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                            c.getSubject(), null, List.of(new SimpleGrantedAuthority("ROLE_USUARIO")))));
        }
        cadena.doFilter(pedido, respuesta);
    }
}
