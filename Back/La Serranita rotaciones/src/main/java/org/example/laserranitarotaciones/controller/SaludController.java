package org.example.laserranitarotaciones.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Para el healthcheck de Docker y el deploy: 200 si el backend y la base responden; 503 si la base no. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SaludController {

    private final JdbcTemplate jdbc;

    @GetMapping("/salud")
    public ResponseEntity<Map<String, String>> salud() {
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(Map.of("estado", "OK"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(503).body(Map.of("estado", "SIN_BASE"));
        }
    }
}
