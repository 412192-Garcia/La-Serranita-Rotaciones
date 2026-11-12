package org.example.laserranitarotaciones.service;

import org.example.laserranitarotaciones.model.Horario;
import org.example.laserranitarotaciones.model.PlanRotacion;
import org.example.laserranitarotaciones.model.Puesto;
import org.example.laserranitarotaciones.model.Turno;
import org.example.laserranitarotaciones.model.dto.RenglonDto;
import org.example.laserranitarotaciones.model.dto.TarjetaDto;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Arma las tarjetas de cada empleado y calcula los datos que se usan para elegir cronograma. */
@Service
public class RotacionService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final int PORCENTAJE_FIJO = 75;

    public static String formatear(LocalTime hora) {
        return hora.format(HH_MM);
    }

    public int cantidadEmpleados(PlanRotacion plan) {
        return (int) plan.turnos().stream().map(Turno::empleado).distinct().count();
    }

    /**
     * Puestos donde un mismo empleado está en al menos el 75% de los horarios en que hay alguien:
     * así el que lo cubre en el almuerzo no le quita lo de fijo.
     */
    public List<String> fijos(PlanRotacion plan) {
        List<String> fijos = new ArrayList<>();
        for (Puesto puesto : plan.puestos()) {
            if (puesto.esDescanso()) continue;
            List<Turno> delPuesto = plan.turnos().stream().filter(t -> t.puesto().equals(puesto)).toList();
            long horariosConAlguien = delPuesto.stream().map(Turno::horario).distinct().count();
            if (horariosConAlguien == 0) continue;
            long maximo = delPuesto.stream()
                    .collect(Collectors.groupingBy(Turno::empleado, Collectors.mapping(Turno::horario, Collectors.toSet())))
                    .values().stream().mapToLong(Set::size).max().orElse(0);
            if (maximo * 100 >= horariosConAlguien * PORCENTAJE_FIJO) fijos.add(puesto.nombre());
        }
        return fijos;
    }

    public List<TarjetaDto> armarTarjetas(PlanRotacion plan) {
        Map<Integer, List<Turno>> porEmpleado = plan.turnos().stream()
                .sorted(Comparator.comparingInt((Turno t) -> t.horario().orden()))
                .collect(Collectors.groupingBy(Turno::empleado, TreeMap::new, Collectors.toList()));

        List<TarjetaDto> tarjetas = new ArrayList<>();
        porEmpleado.forEach((empleado, turnos) -> tarjetas.add(armarTarjeta(empleado, turnos, plan)));
        return tarjetas;
    }

    private TarjetaDto armarTarjeta(int empleado, List<Turno> turnos, PlanRotacion plan) {
        String descanso = null;
        List<RenglonDto> renglones = new ArrayList<>();
        Horario horarioAnterior = null;

        for (Turno turno : turnos) {
            Horario horario = turno.horario();
            String puesto = turno.puesto().nombre();
            if (turno.puesto().esDescanso() && descanso == null) {
                descanso = rangoDescanso(horario, plan.horarios());
            }

            // Dos puestos a la misma hora van en el mismo renglón.
            if (horario.equals(horarioAnterior)) {
                RenglonDto ultimo = renglones.remove(renglones.size() - 1);
                renglones.add(new RenglonDto(ultimo.hora(), ultimo.texto() + " y " + puesto));
                continue;
            }
            renglones.add(new RenglonDto(formatear(horario.hora()), texto(turno, turnos, plan)));
            horarioAnterior = horario;
        }
        return new TarjetaDto(empleado, descanso, renglones);
    }

    private String texto(Turno turno, List<Turno> delEmpleado, PlanRotacion plan) {
        String puesto = turno.puesto().nombre();
        Turno previo = delEmpleado.stream()
                .filter(t -> t.horario().orden() == turno.horario().orden() - 1)
                .findFirst().orElse(null);
        if (previo == null) return puesto;
        if (plan.cerrado(previo.puesto(), turno.horario())) return "Rote a " + puesto;

        // Quién queda en el puesto que deja.
        List<Integer> cobertores = plan.turnos().stream()
                .filter(t -> t.puesto().equals(previo.puesto()) && t.horario().equals(turno.horario()))
                .map(Turno::empleado)
                .distinct()
                .toList();
        if (cobertores.isEmpty()) return "Sin ser cubierto, rote a " + puesto;
        if (cobertores.contains(turno.empleado())) return puesto;
        String quienes = cobertores.stream().map(String::valueOf).collect(Collectors.joining(" y "));
        return "Al ser cubierto por " + quienes + " rote a " + puesto;
    }

    private static String rangoDescanso(Horario inicio, List<Horario> horarios) {
        String desde = formatear(inicio.hora());
        return horarios.stream()
                .filter(h -> h.orden() == inicio.orden() + 1)
                .findFirst()
                .map(h -> desde + " a " + formatear(h.hora()))
                .orElse(desde);
    }
}
