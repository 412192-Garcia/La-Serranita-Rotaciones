package org.example.laserranitarotaciones.model.dto;

import org.example.laserranitarotaciones.model.entity.Afluencia;

import java.util.List;

/** Las respuestas del día. Un dato en null no se tiene en cuenta. */
public record PedidoSugerenciaDto(Integer empleados, Afluencia afluencia, List<String> fijos) {
}
