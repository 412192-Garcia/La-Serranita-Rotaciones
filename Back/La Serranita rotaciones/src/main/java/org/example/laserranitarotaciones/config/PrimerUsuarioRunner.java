package org.example.laserranitarotaciones.config;

import lombok.extern.slf4j.Slf4j;
import org.example.laserranitarotaciones.service.UsuarioService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Si no hay ningún usuario, crea el primero con PRIMER_USUARIO y PRIMER_USUARIO_CLAVE (en producción, del .env).
 * En dev, sin esas variables, crea admin / admin123 para poder entrar.
 */
@Slf4j
@Component
public class PrimerUsuarioRunner implements ApplicationRunner {

    private final UsuarioService usuarios;
    private final Environment entorno;

    @Value("${PRIMER_USUARIO:}")
    private String usuario;

    @Value("${PRIMER_USUARIO_CLAVE:}")
    private String clave;

    public PrimerUsuarioRunner(UsuarioService usuarios, Environment entorno) {
        this.usuarios = usuarios;
        this.entorno = entorno;
    }

    @Override
    public void run(ApplicationArguments args) {
        String u = usuario;
        String c = clave;
        if (u.isBlank() || c.isBlank()) {
            if (!Arrays.asList(entorno.getActiveProfiles()).contains("dev")) return;
            u = "admin";
            c = "admin123";
        }
        if (usuarios.crearPrimeroSiNoHay(u, c, "Administrador")) {
            log.warn("Se creó el primer usuario '{}'. Entrá, cambiá la contraseña en Configuración > Usuarios y sacá "
                    + "PRIMER_USUARIO y PRIMER_USUARIO_CLAVE del .env.", u);
        }
    }
}
