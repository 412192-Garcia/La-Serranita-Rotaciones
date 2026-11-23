package org.example.laserranitarotaciones.config;

import org.example.laserranitarotaciones.model.dto.LoginDto;
import org.example.laserranitarotaciones.model.dto.SesionDto;
import org.example.laserranitarotaciones.service.ValidacionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * El único usuario de la app (se usa en una sola PC, no importa quién): APP_USUARIO y APP_CLAVE del .env. Sin
 * ellos la app no arranca (en desarrollo: admin / admin123).
 */
@Component
public class Acceso {

    private final String usuario;
    private final byte[] clave;

    public Acceso(@Value("${app.usuario:}") String usuario, @Value("${app.clave:}") String clave) {
        if (usuario == null || usuario.isBlank() || clave == null || clave.isBlank()) {
            throw new IllegalStateException("Faltan APP_USUARIO y APP_CLAVE (el usuario y la contraseña para entrar).");
        }
        this.usuario = normalizar(usuario);
        this.clave = clave.getBytes(StandardCharsets.UTF_8);
    }

    public String usuario() {
        return usuario;
    }

    /** Entrar: el mismo mensaje si falla el usuario o la contraseña. */
    public SesionDto entrar(LoginDto dto, JwtService jwt) {
        boolean usuarioBien = usuario.equals(normalizar(dto.usuario()));
        boolean claveBien = dto.clave() != null && MessageDigest.isEqual(clave, dto.clave().getBytes(StandardCharsets.UTF_8));
        if (!usuarioBien || !claveBien) throw new ValidacionException("Usuario o contraseña incorrectos.");
        return new SesionDto(jwt.generar(usuario, dto.mantener()), usuario);
    }

    private static String normalizar(String u) {
        return u == null ? "" : u.trim().toLowerCase(Locale.ROOT);
    }
}
