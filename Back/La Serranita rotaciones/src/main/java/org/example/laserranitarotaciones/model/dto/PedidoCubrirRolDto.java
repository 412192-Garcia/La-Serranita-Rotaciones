package org.example.laserranitarotaciones.model.dto;

import java.util.List;

/**
 * Elegir a mano quién cubre un rol que nadie podía hacer. Viene el borrador como está (cronograma, avisos y
 * asignación), los que vinieron, el rol (puesto, rol y en qué celdas) y el empleado elegido.
 */
public record PedidoCubrirRolDto(CronogramaDto cronograma, List<String> avisos, List<AsignacionDto> asignacion,
                                 List<PresenteDto> presentes, String puesto, String rol, List<Integer> celdas,
                                 Long empleadoId) {
}
