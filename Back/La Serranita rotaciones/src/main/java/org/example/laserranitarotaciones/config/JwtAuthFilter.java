package org.example.laserranitarotaciones.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.laserranitarotaciones.repository.UsuarioRepository;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** Si el pedido trae "Authorization: Bearer <token>" válido y el usuario sigue existiendo, queda autenticado. */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwt;
    private final UsuarioRepository usuarios;

    public JwtAuthFilter(JwtService jwt, UsuarioRepository usuarios) {
        this.jwt = jwt;
        this.usuarios = usuarios;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest pedido, @NonNull HttpServletResponse respuesta,
                                    @NonNull FilterChain cadena) throws ServletException, IOException {
        String cabecera = pedido.getHeader("Authorization");
        if (cabecera != null && cabecera.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) {
            jwt.leer(cabecera.substring(7)).ifPresent(this::autenticar);
        }
        cadena.doFilter(pedido, respuesta);
    }

    private void autenticar(Claims claims) {
        Long id = claims.get("id", Long.class);
        // Un usuario borrado no sigue entrando con un token viejo.
        if (id == null || !usuarios.existsById(id)) return;
        var principal = new UsuarioAutenticado(id, claims.getSubject());
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_USUARIO")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
