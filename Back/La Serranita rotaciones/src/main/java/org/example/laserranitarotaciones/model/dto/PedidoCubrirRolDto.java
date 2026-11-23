package org.example.laserranitarotaciones.model.dto;

import java.util.List;

/**
 * Elegir a mano quién cubre un rol que nadie podía hacer. Viene el borrador como está (cronograma, avisos y
 * asignación), los que vinieron, el rol (puesto, rol y en qué celdas), el empleado elegido y los refuerzos que se
 * pedían (puede ser null).
 */
public record PedidoCubrirRolDto(CronogramaDto cronograma, List<String> avisos, List<AsignacionDto> asignacion,
                                 List<PresenteDto> presentes, String puesto, String rol, List<Integer> celdas,
                                 Long empleadoId, List<RefuerzoDto> refuerzos) {

    /** Sin los refuerzos de antes: se piden sólo donde quedó sin nadie. */
    public PedidoCubrirRolDto(CronogramaDto cronograma, List<String> avisos, List<AsignacionDto> asignacion,
                              List<PresenteDto> presentes, String puesto, String rol, List<Integer> celdas, Long empleadoId) {
        this(cronograma, avisos, asignacion, presentes, puesto, rol, celdas, empleadoId, null);
    }
}
