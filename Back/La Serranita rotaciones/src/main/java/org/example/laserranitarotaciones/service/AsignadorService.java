package org.example.laserranitarotaciones.service;

import org.example.laserranitarotaciones.model.PlanRotacion;
import org.example.laserranitarotaciones.model.Turno;
import org.example.laserranitarotaciones.model.dto.AsignacionDto;
import org.example.laserranitarotaciones.model.entity.Prioridad;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Decide qué empleado hace cada número del cronograma. Nadie va a un puesto que no sabe o que hoy no
 * puede si hay otra forma; entre los repartos posibles, cada uno queda en su puesto principal y nadie
 * le ocupa el principal a otro que vino. Si algo tiene que quedar mal cubierto, que sea en los
 * puestos de menor prioridad.
 */
@Service
public class AsignadorService {

    private static final int FUERA_DE_SU_PRINCIPAL = 60;
    private static final int EN_PRINCIPAL_DE_OTRO = 120;
    /**
     * Poner a alguien en un puesto que no sabe (o que hoy no puede) pesa mucho: cinco veces la prioridad del
     * puesto. Que nadie lo haga pesa lo mismo. Así, en la búsqueda, antes conviene un cronograma más chico
     * con gente libre (un empleado de más pesa 80) que uno con alguien en lo que no sabe.
     */
    static final int POR_NO_SABER = 5;

    /** Puestos en claves de Nombres.clave(). */
    public record Presente(Long id, String nombre, Set<String> sabe, Set<String> hoy, Set<String> principales) {
    }

    /** costo: lo que suman los castigos; avisos: lo que no se pudo cumplir. */
    public record Resultado(int costo, List<AsignacionDto> asignacion, List<String> avisos) {
    }

    private record Numero(int numero, Map<String, String> puestos, String principal) {
    }

    public Resultado asignar(PlanRotacion plan, List<Presente> presentes) {
        return asignar(plan, presentes, Map.of());
    }

    /**
     * pesos: clave del puesto → peso de su prioridad (lo que no está es Media). Cada puesto que alguien
     * no sabe o hoy no puede, y cada puesto de un número que queda sin nadie, cuesta su peso.
     */
    public Resultado asignar(PlanRotacion plan, List<Presente> presentes, Map<String, Integer> pesos) {
        List<Numero> numeros = numeros(plan);
        Set<String> principalesDeAlguien = new HashSet<>();
        presentes.forEach(p -> principalesDeAlguien.addAll(p.principales()));

        int n = Math.max(numeros.size(), presentes.size());
        int[][] costo = new int[n][n];
        for (int i = 0; i < numeros.size(); i++) {
            for (int j = 0; j < n; j++) {
                costo[i][j] = j < presentes.size()
                        ? costo(numeros.get(i), presentes.get(j), principalesDeAlguien, pesos)
                        : numeros.get(i).puestos().keySet().stream().mapToInt(c -> peso(pesos, c) * POR_NO_SABER).sum();
            }
        }
        int[] reparto = n == 0 ? new int[0] : Hungaro.resolver(costo);

        int total = 0;
        List<AsignacionDto> asignacion = new ArrayList<>();
        List<String> avisos = new ArrayList<>();
        for (int i = 0; i < numeros.size(); i++) {
            Numero numero = numeros.get(i);
            int j = reparto[i];
            if (j >= presentes.size()) {
                total += costo[i][j];
                asignacion.add(new AsignacionDto(numero.numero(), null, null));
                avisos.add("Nadie hace el número " + numero.numero() + " ("
                        + String.join(", ", numero.puestos().values()) + ").");
                continue;
            }
            Presente presente = presentes.get(j);
            total += costo[i][j];
            asignacion.add(new AsignacionDto(numero.numero(), presente.id(), presente.nombre()));
            numero.puestos().forEach((clave, nombre) -> {
                if (!presente.sabe().contains(clave)) {
                    avisos.add(presente.nombre() + " tendría que hacer " + nombre + " y no sabe.");
                } else if (!presente.hoy().contains(clave)) {
                    avisos.add(presente.nombre() + " tendría que hacer " + nombre + " y hoy no puede.");
                }
            });
        }
        Set<Integer> asignados = new HashSet<>();
        for (int i = 0; i < numeros.size(); i++) asignados.add(reparto[i]);
        for (int j = 0; j < presentes.size(); j++) {
            if (!asignados.contains(j)) avisos.add(presentes.get(j).nombre() + " queda sin puesto.");
        }
        return new Resultado(total, asignacion, avisos);
    }

    /**
     * Los avisos de un reparto que ya está hecho (después de tocar el cronograma sin cambiar quién hace cada
     * número, por ejemplo al cerrar un puesto): lo que alguien no sabe o hoy no puede, y quién quedó sin número.
     */
    public Resultado conAsignacion(PlanRotacion plan, List<Presente> presentes, List<AsignacionDto> asignacion) {
        Map<Integer, Numero> porNumero = new HashMap<>();
        numeros(plan).forEach(x -> porNumero.put(x.numero(), x));
        Map<Long, Presente> porId = new HashMap<>();
        presentes.forEach(p -> porId.put(p.id(), p));
        List<String> avisos = new ArrayList<>();
        Set<Long> conNumero = new HashSet<>();
        for (AsignacionDto a : asignacion) {
            Numero numero = porNumero.get(a.numero());
            if (a.empleadoId() == null) {
                if (numero != null) avisos.add("Nadie hace el número " + a.numero() + " (" + String.join(", ", numero.puestos().values()) + ").");
                continue;
            }
            conNumero.add(a.empleadoId());
            Presente presente = porId.get(a.empleadoId());
            if (numero == null || presente == null) continue;
            numero.puestos().forEach((clave, nombre) -> {
                if (!presente.sabe().contains(clave)) avisos.add(presente.nombre() + " tendría que hacer " + nombre + " y no sabe.");
                else if (!presente.hoy().contains(clave)) avisos.add(presente.nombre() + " tendría que hacer " + nombre + " y hoy no puede.");
            });
        }
        presentes.stream().filter(p -> !conNumero.contains(p.id())).forEach(p -> avisos.add(p.nombre() + " queda sin puesto."));
        return new Resultado(0, asignacion, avisos);
    }

    private static int peso(Map<String, Integer> pesos, String clave) {
        return pesos.getOrDefault(clave, Prioridad.MEDIA.peso());
    }

    private static int costo(Numero numero, Presente presente, Set<String> principalesDeAlguien, Map<String, Integer> pesos) {
        int c = 0;
        for (String clave : numero.puestos().keySet()) {
            if (!presente.sabe().contains(clave) || !presente.hoy().contains(clave)) c += peso(pesos, clave) * POR_NO_SABER;
        }
        String principal = numero.principal();
        if (principal != null && !presente.principales().contains(principal)) {
            if (!presente.principales().isEmpty()) c += FUERA_DE_SU_PRINCIPAL;
            if (principalesDeAlguien.contains(principal)) c += EN_PRINCIPAL_DE_OTRO;
        }
        return c;
    }

    /** Cada número con sus puestos (sin Descanso) y el puesto donde pasa más horarios. */
    private static List<Numero> numeros(PlanRotacion plan) {
        Map<Integer, List<Turno>> porNumero = plan.turnos().stream()
                .collect(Collectors.groupingBy(Turno::empleado, TreeMap::new, Collectors.toList()));
        List<Numero> numeros = new ArrayList<>();
        porNumero.forEach((numero, turnos) -> {
            Map<String, String> puestos = new LinkedHashMap<>();
            Map<String, Long> horarios = new LinkedHashMap<>();
            for (Turno t : turnos) {
                if (t.puesto().esDescanso() || t.puesto().esLibre()) continue;
                String clave = Nombres.clave(t.puesto().nombre());
                puestos.putIfAbsent(clave, t.puesto().nombre());
                horarios.merge(clave, 1L, Long::sum);
            }
            String principal = horarios.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey).orElse(null);
            numeros.add(new Numero(numero, puestos, principal));
        });
        return numeros;
    }
}
