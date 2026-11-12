package org.example.laserranitarotaciones.model.dto;

import java.util.List;

/** principales tiene que estar dentro de puestos. */
public record EmpleadoDto(Long id, String nombre, List<String> puestos, List<String> principales) {
}
