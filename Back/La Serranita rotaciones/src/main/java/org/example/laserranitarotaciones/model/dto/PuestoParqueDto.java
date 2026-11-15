package org.example.laserranitarotaciones.model.dto;

import org.example.laserranitarotaciones.model.entity.Prioridad;
import org.example.laserranitarotaciones.model.entity.TipoHorario;

/**
 * Un puesto del parque. mostrarEnFijos: si aparece en la columna "Puestos fijos" de los cronogramas.
 * abreTarde: si abre más tarde (Tirolesa, Casa del zorrito); null si está abierto todo el día.
 */
public record PuestoParqueDto(String nombre, Prioridad prioridad, boolean mostrarEnFijos, AbreTarde abreTarde) {

    public PuestoParqueDto(String nombre, Prioridad prioridad) {
        this(nombre, prioridad, true, null);
    }

    public PuestoParqueDto(String nombre, Prioridad prioridad, boolean mostrarEnFijos) {
        this(nombre, prioridad, mostrarEnFijos, null);
    }

    /**
     * Un horario especial del puesto. abre / hasta: "13:00"; hasta null = hasta el cierre. baja/media/alta:
     * personas según la afluencia (las que van, las de más o las de menos, según el tipo).
     */
    public record AbreTarde(String abre, String hasta, int baja, int media, int alta, TipoHorario tipo) {

        public AbreTarde(String abre, String hasta, int baja, int media, int alta) {
            this(abre, hasta, baja, media, alta, TipoHorario.ABRE_TARDE);
        }
    }
}
