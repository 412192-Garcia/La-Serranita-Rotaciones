package org.example.laserranitarotaciones.model.dto;

import org.example.laserranitarotaciones.model.entity.Afluencia;

import java.util.List;

/** Las respuestas del día. Sin afluencia, no se tiene en cuenta. */
public record PedidoSugerenciaDto(Afluencia afluencia, List<PresenteDto> presentes) {
}
