package org.example.laserranitarotaciones.service;

import lombok.RequiredArgsConstructor;
import org.example.laserranitarotaciones.model.dto.EmpleadoDto;
import org.example.laserranitarotaciones.model.dto.PedidoTareasDto;
import org.example.laserranitarotaciones.model.dto.PresenteTareaDto;
import org.example.laserranitarotaciones.model.dto.TareaAsignadaDto;
import org.example.laserranitarotaciones.model.dto.TareaDto;
import org.example.laserranitarotaciones.model.dto.TareaHechaDto;
import org.example.laserranitarotaciones.model.entity.Tarea;
import org.example.laserranitarotaciones.model.entity.TareaHecha;
import org.example.laserranitarotaciones.repository.TareaHechaRepository;
import org.example.laserranitarotaciones.repository.TareaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tareas de antes de abrir. Se reparten todas, todos los días, entre los que vinieron: quien está en
 * entrenamiento va con su acompañante y cuenta como uno más. Para no repetir, se mira quién hizo
 * cada tarea en los últimos días (lo que se guarda al imprimir las tarjetas).
 */
@Service
@RequiredArgsConstructor
public class TareaService {

    static final int DIAS_DE_MEMORIA = 14;
    /** Haber hecho la misma tarea ayer cuesta esto; hace DIAS_DE_MEMORIA días, casi nada. */
    private static final int POR_REPETIR = 100;
    /** Para repartir parejo: cada tarea hecha en esos días suma un poco. */
    private static final int POR_TAREA_RECIENTE = 5;

    private final TareaRepository repositorio;
    private final TareaHechaRepository hechas;
    private final EmpleadoService empleados;

    @Transactional(readOnly = true)
    public List<TareaDto> listar() {
        return repositorio.findAll().stream()
                .map(TareaService::aDto)
                .sorted(Comparator.comparing(TareaDto::nombre, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public TareaDto crear(TareaDto dto) {
        Tarea t = new Tarea();
        aplicar(t, dto);
        return aDto(repositorio.save(t));
    }

    @Transactional
    public TareaDto actualizar(Long id, TareaDto dto) {
        Tarea t = buscar(id);
        aplicar(t, dto);
        return aDto(repositorio.save(t));
    }

    @Transactional
    public void eliminar(Long id) {
        repositorio.delete(buscar(id));
        hechas.deleteByIdTarea(id);
        empleados.olvidarTarea(id);
    }

    /** Guarda quién hizo qué ese día; reemplaza lo que hubiera de esa fecha. */
    @Transactional
    public void guardarHechas(LocalDate fecha, List<TareaHechaDto> lista) {
        if (fecha == null) throw new ValidacionException("Falta la fecha.");
        hechas.deleteByFecha(fecha);
        for (TareaHechaDto t : lista == null ? List.<TareaHechaDto>of() : lista) {
            for (Long empleado : t.empleadoIds() == null ? List.<Long>of() : t.empleadoIds()) {
                hechas.save(new TareaHecha(fecha, t.tareaId(), empleado));
            }
        }
    }

    private record Unidad(EmpleadoDto lider, List<EmpleadoDto> miembros) {
        int tamano() {
            return miembros.size();
        }
    }

    @Transactional(readOnly = true)
    public List<TareaAsignadaDto> repartir(PedidoTareasDto pedido) {
        if (pedido.fecha() == null) throw new ValidacionException("Falta la fecha.");
        List<PresenteTareaDto> presentes = pedido.presentes() == null ? List.of() : pedido.presentes();
        Map<Long, EmpleadoDto> porId = empleados.buscar(presentes.stream().map(PresenteTareaDto::empleadoId).toList())
                .stream().collect(Collectors.toMap(EmpleadoDto::id, Function.identity()));

        // Cada uno es una unidad; el que está en entrenamiento se suma a la de su acompañante.
        Map<Long, Unidad> unidades = new LinkedHashMap<>();
        for (PresenteTareaDto p : presentes) {
            EmpleadoDto e = porId.get(p.empleadoId());
            if (e != null && !e.enEntrenamiento()) unidades.put(e.id(), new Unidad(e, new ArrayList<>(List.of(e))));
        }
        for (PresenteTareaDto p : presentes) {
            EmpleadoDto e = porId.get(p.empleadoId());
            if (e == null || !e.enEntrenamiento()) continue;
            Unidad del = p.acompananteId() == null ? null : unidades.get(p.acompananteId());
            if (del != null) del.miembros().add(e);
            else unidades.put(e.id(), new Unidad(e, new ArrayList<>(List.of(e))));
        }

        Historial historial = historial(pedido.fecha());
        List<Tarea> tareas = repositorio.findAll().stream()
                .sorted(Comparator.comparing(Tarea::getNombre, String.CASE_INSENSITIVE_ORDER)).toList();
        // Primero las que piden saber y tienen menos gente que pueda; después las más grandes.
        List<Tarea> orden = tareas.stream()
                .sorted(Comparator.comparing((Tarea t) -> !t.isRequiereSaber())
                        .thenComparingLong(t -> unidades.values().stream().filter(u -> puede(u, t)).count())
                        .thenComparing(Comparator.comparingInt(Tarea::getCantidad).reversed()))
                .toList();

        Set<Unidad> usadas = new HashSet<>();
        Map<Long, TareaAsignadaDto> resultado = new HashMap<>();
        for (Tarea t : orden) {
            List<Unidad> elegidas = new ArrayList<>();
            int faltan = t.getCantidad();
            // Primero los que no tienen tarea; si no alcanza, alguien hace dos.
            for (boolean repetir : new boolean[]{false, true}) {
                while (faltan > 0) {
                    int resto = faltan;
                    Unidad mejor = unidades.values().stream()
                            .filter(u -> puede(u, t) && !elegidas.contains(u) && (repetir || !usadas.contains(u)))
                            .min(Comparator.comparing((Unidad u) -> u.tamano() > resto)
                                    .thenComparingInt(u -> historial.costo(u, t.getId()))
                                    .thenComparing(u -> u.lider().nombre(), String.CASE_INSENSITIVE_ORDER))
                            .orElse(null);
                    if (mejor == null) break;
                    elegidas.add(mejor);
                    usadas.add(mejor);
                    faltan -= mejor.tamano();
                }
            }
            String aviso = null;
            if (elegidas.isEmpty() && t.isRequiereSaber()) aviso = "Nadie de los que vinieron sabe hacerla.";
            else if (faltan > 0) aviso = faltan == 1 ? "Falta 1 persona." : "Faltan " + faltan + " personas.";
            List<Long> ids = elegidas.stream().flatMap(u -> u.miembros().stream()).map(EmpleadoDto::id).toList();
            resultado.put(t.getId(), new TareaAsignadaDto(t.getId(), t.getNombre(), t.getCantidad(), ids, aviso));
        }
        return tareas.stream().map(t -> resultado.get(t.getId())).toList();
    }

    private static boolean puede(Unidad u, Tarea t) {
        return !t.isRequiereSaber() || u.lider().tareas().contains(t.getId());
    }

    /** Lo hecho en los últimos días, para no repetir y repartir parejo. */
    private record Historial(LocalDate hoy, Map<Long, Map<Long, LocalDate>> ultimaVez, Map<Long, Long> cuantas) {

        int costo(Unidad u, Long tarea) {
            int c = 0;
            for (EmpleadoDto e : u.miembros()) {
                LocalDate ultima = ultimaVez.getOrDefault(e.id(), Map.of()).get(tarea);
                if (ultima != null) {
                    long dias = ChronoUnit.DAYS.between(ultima, hoy);
                    c += (int) (POR_REPETIR * (DIAS_DE_MEMORIA + 1 - dias) / DIAS_DE_MEMORIA);
                }
                c += (int) (cuantas.getOrDefault(e.id(), 0L) * POR_TAREA_RECIENTE);
            }
            return c;
        }
    }

    private Historial historial(LocalDate fecha) {
        Map<Long, Map<Long, LocalDate>> ultimaVez = new HashMap<>();
        Map<Long, Long> cuantas = new HashMap<>();
        for (TareaHecha h : hechas.findByFechaBetween(fecha.minusDays(DIAS_DE_MEMORIA), fecha.minusDays(1))) {
            ultimaVez.computeIfAbsent(h.getIdEmpleado(), k -> new HashMap<>())
                    .merge(h.getIdTarea(), h.getFecha(), (a, b) -> a.isAfter(b) ? a : b);
            cuantas.merge(h.getIdEmpleado(), 1L, Long::sum);
        }
        return new Historial(fecha, ultimaVez, cuantas);
    }

    private Tarea buscar(Long id) {
        return repositorio.findById(id).orElseThrow(() -> new NoEncontradoException("Esa tarea no existe."));
    }

    private static TareaDto aDto(Tarea t) {
        return new TareaDto(t.getId(), t.getNombre(), t.getCantidad(), t.isRequiereSaber());
    }

    private void aplicar(Tarea t, TareaDto dto) {
        String nombre = dto.nombre() == null ? "" : dto.nombre().trim();
        if (nombre.isEmpty()) throw new ValidacionException("Falta el nombre.");
        if (nombre.length() > 60) throw new ValidacionException("El nombre es muy largo.");
        if (dto.cantidad() < 1 || dto.cantidad() > 20) throw new ValidacionException("La cantidad tiene que ser de 1 a 20.");
        boolean repetida = repositorio.findAll().stream()
                .anyMatch(o -> !o.getId().equals(t.getId()) && Nombres.clave(o.getNombre()).equals(Nombres.clave(nombre)));
        if (repetida) throw new ValidacionException("Ya hay una tarea " + nombre + ".");
        t.setNombre(nombre);
        t.setCantidad(dto.cantidad());
        t.setRequiereSaber(dto.requiereSaber());
    }
}
