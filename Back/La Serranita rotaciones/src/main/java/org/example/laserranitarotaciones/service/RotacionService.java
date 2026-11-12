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

    /** Un renglón por horario; si a esa hora tiene varios puestos van juntos ("Travesía y Búsqueda"). */
    private TarjetaDto armarTarjeta(int empleado, List<Turno> turnos, PlanRotacion plan) {
        Map<Horario, List<Puesto>> porHorario = new TreeMap<>(Comparator.comparingInt(Horario::orden));
        turnos.forEach(t -> porHorario.computeIfAbsent(t.horario(), h -> new ArrayList<>()).add(t.puesto()));

        String descanso = null;
        List<RenglonDto> renglones = new ArrayList<>();
        List<Puesto> anteriores = List.of();
        Horario horarioAnterior = null;
        for (Map.Entry<Horario, List<Puesto>> e : porHorario.entrySet()) {
            Horario horario = e.getKey();
            List<Puesto> actuales = e.getValue();
            if (descanso == null && actuales.stream().anyMatch(Puesto::esDescanso)) {
                descanso = rangoDescanso(horario, plan.horarios());
            }
            boolean seguido = horarioAnterior != null && horarioAnterior.orden() == horario.orden() - 1;
            String texto = texto(empleado, horario, seguido ? anteriores : List.of(), actuales, plan);
            renglones.add(new RenglonDto(formatear(horario.hora()), texto));
            anteriores = actuales;
            horarioAnterior = horario;
        }
        return new TarjetaDto(empleado, descanso, renglones);
    }

    private String texto(int empleado, Horario horario, List<Puesto> anteriores, List<Puesto> actuales, PlanRotacion plan) {
        String destino = enumerar(actuales.stream().map(Puesto::conNota).toList());
        // El descanso no necesita que nadie lo cubra: al volver sólo se dice adónde va.
        List<Puesto> dejados = anteriores.stream().filter(p -> !actuales.contains(p) && !p.esDescanso()).toList();
        if (dejados.isEmpty()) return destino;

        List<Puesto> abiertos = dejados.stream().filter(p -> !plan.cerrado(p, horario)).toList();
        if (abiertos.isEmpty()) return "Rote a " + destino;

        // Quiénes quedan en los puestos que deja.
        List<Integer> cobertores = plan.turnos().stream()
                .filter(t -> abiertos.contains(t.puesto()) && t.horario().equals(horario) && t.empleado() != empleado)
                .map(Turno::empleado)
                .distinct()
                .toList();
        if (cobertores.isEmpty()) return "Sin ser cubierto, rote a " + destino;
        String quienes = enumerar(cobertores.stream().map(String::valueOf).toList());
        return "Al ser cubierto por " + quienes + " rote a " + destino;
    }

    /** "A", "A y B", "A, B y C". */
    static String enumerar(List<String> partes) {
        if (partes.size() <= 1) return String.join("", partes);
        return String.join(", ", partes.subList(0, partes.size() - 1)) + " y " + partes.get(partes.size() - 1);
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
