package org.example.laserranitarotaciones.model.dto;

import java.time.LocalDate;
import java.util.List;

public record PedidoTareasDto(LocalDate fecha, List<PresenteTareaDto> presentes) {
}
