package org.example.laserranitarotaciones.model.dto;

import java.util.List;

/** Generar con los que vinieron: lo que se respondió del cronograma y quiénes son (con lo que pueden hoy). */
public record PedidoGeneradorDiaDto(PedidoGeneradorDto pedido, List<PresenteDto> presentes) {
}
