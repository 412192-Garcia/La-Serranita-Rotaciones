package org.example.laserranitarotaciones.model.dto;

import org.example.laserranitarotaciones.model.entity.Afluencia;

import java.util.List;

/**
 * Qué puestos se abren con tantos empleados (todos los que vinieron) y tal afluencia.
 * fijos: puesto, personas y, si abre tarde, desde qué hora (Cajero x2, Tirolesa x3 desde las 13:00). lugares: la
 * rueda en orden; cada lugar son los puestos que hace la misma persona (Travesía, Búsqueda y Laberinto).
 */
public record ReglaAperturaDto(Long id, int empleados, Afluencia afluencia, List<PedidoGeneradorDto.Fijo> fijos,
                               List<List<String>> lugares) {
}
