package org.example.laserranitarotaciones.model.dto;

import org.example.laserranitarotaciones.model.entity.Afluencia;

import java.util.List;

/**
 * Qué puestos se abren con tantos empleados (sin la Tirolesa) y tal afluencia.
 * fijos: puesto y personas (Cajero x2, Baños). lugares: la rueda en orden; cada lugar son los puestos que
 * hace la misma persona (Travesía, Búsqueda y Laberinto).
 */
public record ReglaAperturaDto(Long id, int empleados, Afluencia afluencia, List<PedidoGeneradorDto.Fijo> fijos,
                               List<List<String>> lugares) {
}
