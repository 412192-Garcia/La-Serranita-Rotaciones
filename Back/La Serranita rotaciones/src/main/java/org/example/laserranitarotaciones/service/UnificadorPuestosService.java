package org.example.laserranitarotaciones.service;

import lombok.RequiredArgsConstructor;
import org.example.laserranitarotaciones.model.PlanRotacion;
import org.example.laserranitarotaciones.model.dto.RenombreDto;
import org.example.laserranitarotaciones.model.entity.Cronograma;
import org.example.laserranitarotaciones.model.entity.Empleado;
import org.example.laserranitarotaciones.model.entity.PuestoCronograma;
import org.example.laserranitarotaciones.model.entity.PuestoParque;
import org.example.laserranitarotaciones.repository.CronogramaRepository;
import org.example.laserranitarotaciones.repository.EmpleadoRepository;
import org.example.laserranitarotaciones.repository.PuestoParqueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cambia el nombre de un puesto en todos lados: cronogramas, lo que sabe cada empleado y la prioridad.
 * Si un cronograma ya tiene el puesto nuevo, las dos filas se juntan en una ("Cajero 2" + "Cajero"
 * queda una fila Cajero con las dos personas).
 */
@Service
@RequiredArgsConstructor
public class UnificadorPuestosService {

    private final CronogramaRepository cronogramas;
    private final EmpleadoRepository empleados;
    private final PuestoParqueRepository prioridades;
    private final GrupoPuestosService grupos;

    public record Resultado(int cronogramas, int empleados) {
    }

    @Transactional
    public Resultado renombrar(RenombreDto pedido) {
        String de = pedido.de() == null ? "" : pedido.de().trim();
        String a = pedido.a() == null ? "" : pedido.a().trim();
        if (de.isEmpty() || a.isEmpty()) throw new ValidacionException("Falta el nombre.");
        if (a.length() > 60) throw new ValidacionException("El nombre es muy largo.");
        if (Nombres.esDescanso(de) || Nombres.esDescanso(a)) throw new ValidacionException("Descanso no se puede renombrar.");
        String claveDe = Nombres.clave(de);
        String claveA = Nombres.clave(a);

        int cambiados = 0;
        for (Cronograma c : cronogramas.findAll()) {
            if (renombrarEn(c, claveDe, claveA, a)) cambiados++;
        }

        int conEmpleados = 0;
        for (Empleado e : empleados.findAll()) {
            boolean sabia = reemplazar(e.getPuestos(), claveDe, a);
            boolean principal = reemplazar(e.getPrincipales(), claveDe, a);
            if (sabia || principal) conEmpleados++;
        }

        grupos.renombrarPuesto(claveDe, a);

        Optional<PuestoParque> viejo = prioridades.findByClave(claveDe);
        if (viejo.isPresent() && !claveDe.equals(claveA)) {
            if (prioridades.findByClave(claveA).isPresent()) {
                prioridades.delete(viejo.get());
            } else {
                viejo.get().setNombre(a);
                viejo.get().setClave(claveA);
            }
        }
        return new Resultado(cambiados, conEmpleados);
    }

    private static boolean renombrarEn(Cronograma c, String claveDe, String claveA, String a) {
        List<PuestoCronograma> puestos = c.getPuestos();
        List<PuestoCronograma> viejos = puestos.stream().filter(p -> Nombres.clave(p.getNombre()).equals(claveDe)).toList();
        if (viejos.isEmpty()) return false;
        PuestoCronograma destino = puestos.stream()
                .filter(p -> Nombres.clave(p.getNombre()).equals(claveA) && !viejos.contains(p))
                .findFirst().orElse(null);
        for (PuestoCronograma viejo : new ArrayList<>(viejos)) {
            if (destino == null) {
                viejo.setNombre(a);
                destino = viejo;
                continue;
            }
            juntar(destino, viejo);
            puestos.remove(viejo);
        }
        return true;
    }

    /** Celda por celda: las personas de las dos filas; Cerrado sólo si las dos estaban cerradas. */
    private static void juntar(PuestoCronograma destino, PuestoCronograma otro) {
        List<String> celdas = destino.getCeldas();
        for (int k = 0; k < celdas.size(); k++) {
            String a = celdas.get(k);
            String b = k < otro.getCeldas().size() ? otro.getCeldas().get(k) : "";
            Set<Integer> numeros = new LinkedHashSet<>(PlanRotacion.empleados(a));
            numeros.addAll(PlanRotacion.empleados(b));
            if (!numeros.isEmpty()) {
                celdas.set(k, numeros.stream().map(String::valueOf).collect(Collectors.joining("-")));
            } else {
                celdas.set(k, PlanRotacion.esCerrado(a) && PlanRotacion.esCerrado(b) ? PlanRotacion.CERRADO : "");
            }
        }
        if ((destino.getNota() == null || destino.getNota().isBlank()) && otro.getNota() != null) destino.setNota(otro.getNota());
        destino.setIniciaRotacion(destino.isIniciaRotacion() || otro.isIniciaRotacion());
    }

    /** Cambia el puesto viejo por el nuevo en la lista, sin que quede repetido. */
    private static boolean reemplazar(Set<String> lista, String claveDe, String a) {
        boolean estaba = lista.removeIf(p -> Nombres.clave(p).equals(claveDe));
        if (estaba && lista.stream().noneMatch(p -> Nombres.clave(p).equals(Nombres.clave(a)))) lista.add(a);
        return estaba;
    }
}
