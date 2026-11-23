package org.example.laserranitarotaciones.model.dto;

import org.example.laserranitarotaciones.model.entity.Afluencia;
import org.example.laserranitarotaciones.model.entity.TipoHorario;

import java.util.List;

/**
 * Qué puestos se abren con tantos empleados (todos los que vinieron) y tal afluencia.
 * fijos: puesto, personas y, si abre tarde, desde qué hora (Cajero x2, Tirolesa x3 desde las 13:00). lugares: la
 * rueda en orden; cada lugar son los puestos que hace la misma persona (Travesía, Búsqueda y Laberinto).
 * horarios: lo que cambia en el día (el Show del zorrito de 16 a 17 con 3, un cajero menos desde las 16): esa gente
 * sale de la rueda o vuelve a ella, no suma a la cuenta.
 */
public record ReglaAperturaDto(Long id, int empleados, Afluencia afluencia, List<PedidoGeneradorDto.Fijo> fijos,
                               List<List<String>> lugares, List<Horario> horarios) {

    public ReglaAperturaDto(Long id, int empleados, Afluencia afluencia, List<PedidoGeneradorDto.Fijo> fijos,
                            List<List<String>> lugares) {
        this(id, empleados, afluencia, fijos, lugares, List.of());
    }

    /** Un horario especial de la regla. hasta: null = hasta el cierre. */
    public record Horario(String puesto, TipoHorario tipo, int personas, String desde, String hasta) {
    }
}
