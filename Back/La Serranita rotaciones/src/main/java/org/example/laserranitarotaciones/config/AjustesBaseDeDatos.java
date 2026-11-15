package org.example.laserranitarotaciones.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Ajustes que ddl-auto=update no hace en una base que ya existe.
 * La prioridad de los puestos quedó limitada a los valores de cuando se creó la tabla (H2 la hizo de
 * tipo enum; Postgres le pone un check). Así, agregar un nivel (SIEMPRE) fallaba al guardar: se deja
 * como texto y valida la app.
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class AjustesBaseDeDatos implements ApplicationRunner {

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        String base;
        try (Connection c = dataSource.getConnection()) {
            base = c.getMetaData().getDatabaseProductName().toLowerCase();
        } catch (SQLException e) {
            log.warn("No se pudo ver qué base es: {}", e.getMessage());
            return;
        }
        try {
            if (base.contains("h2")) {
                jdbc.execute("ALTER TABLE puestos ALTER COLUMN prioridad VARCHAR(10)");
            } else if (base.contains("postgres")) {
                List<String> checks = jdbc.queryForList("""
                        SELECT tc.constraint_name FROM information_schema.table_constraints tc
                        JOIN information_schema.check_constraints cc ON cc.constraint_name = tc.constraint_name
                        WHERE tc.table_name = 'puestos' AND tc.constraint_type = 'CHECK'
                          AND cc.check_clause ILIKE '%prioridad%'""", String.class);
                for (String nombre : checks) jdbc.execute("ALTER TABLE puestos DROP CONSTRAINT \"" + nombre.replace("\"", "") + "\"");
            }
        } catch (RuntimeException e) {
            log.warn("No se pudo ajustar la columna de prioridad de los puestos: {}", e.getMessage());
        }
    }
}
