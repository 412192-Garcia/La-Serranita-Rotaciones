package org.example.laserranitarotaciones.model.dto;

import java.util.List;

/** Un rol que nadie de los que vinieron puede hacer: en qué horarios (posición y hora) hay que elegir quién lo cubre. */
public record FaltanteRolDto(String puesto, String rol, List<Integer> celdas, List<String> horas) {
}
