package org.example.laserranitarotaciones.model.dto;

import org.example.laserranitarotaciones.model.entity.Afluencia;
import org.example.laserranitarotaciones.model.entity.TipoHorario;

import java.util.List;

/**
 * Lo que necesita el generador de borradores.
 * rueda: los lugares por los que rotan, en orden; un lugar puede ser varios puestos que hace la misma
 * persona (Travesía, Búsqueda y Laberinto). coberturas: en los descansos de los que rotan, la persona
 * de un puesto fijo pasa a cubrir otro. abrenTarde: puestos que abren más tarde (Tirolesa a las 13:00, Casa
 * del zorrito de 16 a 17); mientras no están abiertos, su gente cubre el parque.
 */
public record PedidoGeneradorDto(
        String nombre,
        Afluencia afluencia,
        int empleados,
        String desde,
        String hasta,
        int cada,
        String descansoDesde,
        String descansoHasta,
        List<Fijo> fijos,
        List<Lugar> rueda,
        List<Cobertura> coberturas,
        List<AbreTarde> abrenTarde) {

    public PedidoGeneradorDto(String nombre, Afluencia afluencia, int empleados, String desde, String hasta, int cada,
                              String descansoDesde, String descansoHasta, List<Fijo> fijos, List<Lugar> rueda,
                              List<Cobertura> coberturas) {
        this(nombre, afluencia, empleados, desde, hasta, cada, descansoDesde, descansoHasta, fijos, rueda, coberturas, List.of());
    }

    /** abre: en una regla, desde qué hora ("13:00") para un puesto que abre tarde (Tirolesa); null = todo el día. */
    public record Fijo(String puesto, int personas, String abre) {

        public Fijo(String puesto, int personas) {
            this(puesto, personas, null);
        }
    }

    public record Lugar(List<String> puestos, boolean inicia) {
    }

    public record Cobertura(String fijo, String cubre) {
    }

    /**
     * Un horario especial (ver TipoHorario). abre / hasta: "13:00"; hasta null = hasta el cierre.
     * personas: las que van (abre tarde), las de más (refuerzo) o las que se sacan (menos).
     */
    public record AbreTarde(String puesto, int personas, String abre, String hasta, TipoHorario tipo) {

        public AbreTarde(String puesto, int personas, String abre) {
            this(puesto, personas, abre, null, TipoHorario.ABRE_TARDE);
        }

        public AbreTarde(String puesto, int personas, String abre, String hasta) {
            this(puesto, personas, abre, hasta, TipoHorario.ABRE_TARDE);
        }

        public boolean refuerzo() {
            return tipo == TipoHorario.REFUERZO;
        }

        public boolean menos() {
            return tipo == TipoHorario.MENOS;
        }
    }
}
