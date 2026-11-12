package org.example.laserranitarotaciones.service;

import org.example.laserranitarotaciones.model.HojaLeida;
import org.example.laserranitarotaciones.model.Horario;
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
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Arma la tarjeta de cada empleado: a qué puesto va en cada horario y quién lo cubre al rotar. */
@Service
public class RotacionService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    public static String formatear(LocalTime hora) {
        return hora.format(HH_MM);
    }

    public List<TarjetaDto> armarTarjetas(HojaLeida hoja) {
        Map<Integer, List<Turno>> porEmpleado = hoja.turnos().stream()
                .sorted(Comparator.comparingInt((Turno t) -> t.horario().orden()))
                .collect(Collectors.groupingBy(Turno::empleado, TreeMap::new, Collectors.toList()));

        List<TarjetaDto> tarjetas = new ArrayList<>();
        porEmpleado.forEach((empleado, turnos) -> tarjetas.add(armarTarjeta(empleado, turnos, hoja)));
        return tarjetas;
    }

    private TarjetaDto armarTarjeta(int empleado, List<Turno> turnos, HojaLeida hoja) {
        String descanso = null;
        List<RenglonDto> renglones = new ArrayList<>();
        Horario horarioAnterior = null;

        for (Turno turno : turnos) {
            Horario horario = turno.horario();
            String puesto = turno.puesto().nombre();
            if (turno.puesto().esDescanso() && descanso == null) {
                descanso = rangoDescanso(horario, hoja.horarios());
            }

            // Dos puestos a la misma hora van en el mismo renglón.
            if (horario.equals(horarioAnterior)) {
                RenglonDto ultimo = renglones.remove(renglones.size() - 1);
                renglones.add(new RenglonDto(ultimo.hora(), ultimo.texto() + " y " + puesto));
                continue;
            }
            renglones.add(new RenglonDto(formatear(horario.hora()), texto(turno, turnos, hoja.turnos())));
            horarioAnterior = horario;
        }
        return new TarjetaDto(empleado, descanso, renglones);
    }

    private String texto(Turno turno, List<Turno> delEmpleado, List<Turno> todos) {
        String puesto = turno.puesto().nombre();
        Turno previo = delEmpleado.stream()
                .filter(t -> t.horario().orden() == turno.horario().orden() - 1)
                .findFirst().orElse(null);
        if (previo == null) return puesto;

        // Quién queda en el puesto que deja.
        List<Integer> cobertores = todos.stream()
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
