package org.example.laserranitarotaciones.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.example.laserranitarotaciones.model.entity.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

/** Arma y lee los tokens de sesión (JWT firmados con JWT_SECRET). */
@Component
public class JwtService {

    private final SecretKey clave;
    private final long duracionMs;
    private final long duracionMantenerMs;

    public JwtService(@Value("${jwt.secret}") String secreto,
                      @Value("${jwt.expiration-ms:43200000}") long duracionMs,
                      @Value("${jwt.remember-expiration-ms:1209600000}") long duracionMantenerMs) {
        if (secreto == null || secreto.length() < 32) {
            throw new IllegalStateException("JWT_SECRET tiene que tener al menos 32 caracteres.");
        }
        this.clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        this.duracionMs = duracionMs;
        this.duracionMantenerMs = duracionMantenerMs;
    }

    public String generar(Usuario usuario, boolean mantener) {
        Date ahora = new Date();
        return Jwts.builder()
                .subject(usuario.getUsuario())
                .claim("id", usuario.getId())
                .issuedAt(ahora)
                .expiration(new Date(ahora.getTime() + (mantener ? duracionMantenerMs : duracionMs)))
                .signWith(clave)
                .compact();
    }

    public Optional<Claims> leer(String token) {
        try {
            return Optional.of(Jwts.parser().verifyWith(clave).build().parseSignedClaims(token).getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
