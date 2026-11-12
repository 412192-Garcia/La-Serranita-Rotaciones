package org.example.laserranitarotaciones.model;

import org.example.laserranitarotaciones.model.dto.PuestoDto;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Un cronograma listo para calcular: horarios, puestos, quién está en cada uno y qué está cerrado. */
public record PlanRotacion(List<Horario> horarios, List<Puesto> puestos, List<Turno> turnos, Set<Cierre> cierres) {

    /** Valor de una celda de un puesto que no abre en ese horario. */
    public static final String CERRADO = "Cerrado";

    private static final Pattern NUMERO = Pattern.compile("\\d+");

    public record Cierre(Puesto puesto, Horario horario) {
    }

    public boolean cerrado(Puesto puesto, Horario horario) {
        return cierres.contains(new Cierre(puesto, horario));
    }

    /** Las horas tienen que venir ya validadas ("HH:mm"). */
    public static PlanRotacion de(List<String> horas, List<PuestoDto> filas) {
        List<Horario> horarios = new ArrayList<>();
        for (int i = 0; i < horas.size(); i++) {
            horarios.add(new Horario(i + 1, LocalTime.parse(horas.get(i))));
        }
        List<Puesto> puestos = new ArrayList<>();
        List<Turno> turnos = new ArrayList<>();
        Set<Cierre> cierres = new HashSet<>();
        for (PuestoDto fila : filas) {
            Puesto puesto = new Puesto(puestos.size() + 1, fila.nombre());
            puestos.add(puesto);
            for (int i = 0; i < horarios.size() && i < fila.celdas().size(); i++) {
                String celda = fila.celdas().get(i);
                if (esCerrado(celda)) {
                    cierres.add(new Cierre(puesto, horarios.get(i)));
                    continue;
                }
                for (int empleado : empleados(celda)) {
                    turnos.add(new Turno(puesto, horarios.get(i), empleado));
                }
            }
        }
        return new PlanRotacion(horarios, puestos, turnos, cierres);
    }

    /** "Cerrado" o "C", sin importar mayúsculas. */
    public static boolean esCerrado(String celda) {
        if (celda == null) return false;
        String t = celda.trim().toLowerCase(Locale.ROOT);
        return t.equals("cerrado") || t.equals("c");
    }

    /** "1-2" da [1, 2]; lo que no es número se ignora. */
    public static List<Integer> empleados(String celda) {
        List<Integer> lista = new ArrayList<>();
        if (celda == null) return lista;
        for (String parte : celda.split("-")) {
            String p = parte.trim();
            if (NUMERO.matcher(p).matches()) lista.add(Integer.parseInt(p));
        }
        return lista;
    }
}
