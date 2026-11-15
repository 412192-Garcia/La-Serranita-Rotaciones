package org.example.laserranitarotaciones.service;

import lombok.RequiredArgsConstructor;
import org.example.laserranitarotaciones.model.PlanRotacion;
import org.example.laserranitarotaciones.model.dto.BorradorDto;
import org.example.laserranitarotaciones.model.dto.CronogramaDto;
import org.example.laserranitarotaciones.model.dto.PedidoGeneradorDiaDto;
import org.example.laserranitarotaciones.model.dto.PedidoGeneradorDto;
import org.example.laserranitarotaciones.model.dto.EmpleadoDto;
import org.example.laserranitarotaciones.model.dto.PedidoSugerenciaDto;
import org.example.laserranitarotaciones.model.dto.PresenteDto;
import org.example.laserranitarotaciones.model.dto.PuestoDto;
import org.example.laserranitarotaciones.model.dto.SugerenciaDto;
import org.example.laserranitarotaciones.model.entity.Afluencia;
import org.example.laserranitarotaciones.model.entity.Prioridad;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ordena los cronogramas según quién vino y cuánta gente se espera, y para cada uno decide quién
 * hace cada número. Cada diferencia suma puntos de castigo; gana el que menos tiene.
 */
@Service
@RequiredArgsConstructor
public class SugerenciaService {

    static final int MAXIMO = 5;
    // En la misma escala que los pesos de prioridad (Media = 100). Alguien sin puesto pesa un poco menos
    // que un puesto Media mal cubierto. El que falta ya lo castiga el reparto (sus puestos quedan sin nadie).
    private static final int POR_EMPLEADO_FALTANTE = Prioridad.MEDIA.peso() / 10;
    private static final int POR_EMPLEADO_SOBRANTE = Prioridad.MEDIA.peso() * 4 / 5;
    private static final int POR_NIVEL_AFLUENCIA = Prioridad.BAJA.peso();

    private final CronogramaService cronogramas;
    private final EmpleadoService empleados;
    private final AsignadorService asignador;
    private final PuestoParqueService puestos;
    private final GeneradorService generador;
    private final RotacionService rotacion;

    public List<SugerenciaDto> sugerir(PedidoSugerenciaDto pedido) {
        List<AsignadorService.Presente> presentes = presentes(pedido.presentes());
        Map<String, Integer> pesos = puestos.pesos();

        record Evaluado(CronogramaDto cronograma, int puntos, List<String> diferencias, AsignadorService.Resultado reparto) {
        }
        List<Evaluado> evaluados = new ArrayList<>();
        for (CronogramaDto c : cronogramas.todos()) {
            List<String> diferencias = new ArrayList<>();
            int puntos = 0;

            int hay = presentes.size();
            if (c.empleados() != hay) {
                int dif = c.empleados() - hay;
                // El que hoy no puede hacer nada no suma como sobrante: igual no trabajaría.
                int sobran = Math.max(0, (int) presentes.stream().filter(p -> !p.hoy().isEmpty()).count() - c.empleados());
                puntos += dif > 0 ? dif * POR_EMPLEADO_FALTANTE : sobran * POR_EMPLEADO_SOBRANTE;
                diferencias.add("Es para " + c.empleados() + " empleados y vinieron " + hay + ".");
            }

            Afluencia afluencia = pedido.afluencia();
            if (afluencia != null && c.afluencia() != afluencia) {
                puntos += Math.abs(c.afluencia().ordinal() - afluencia.ordinal()) * POR_NIVEL_AFLUENCIA;
                diferencias.add("Es para afluencia " + c.afluencia().name().toLowerCase(Locale.ROOT) + ".");
            }

            AsignadorService.Resultado reparto = asignador.asignar(PlanRotacion.de(c.horarios(), c.puestos()), presentes, pesos);
            puntos += reparto.costo();
            diferencias.addAll(reparto.avisos());
            evaluados.add(new Evaluado(c, puntos, diferencias, reparto));
        }

        return evaluados.stream()
                .sorted(Comparator.comparingInt(Evaluado::puntos)
                        .thenComparing(e -> e.cronograma().nombre(), String.CASE_INSENSITIVE_ORDER))
                .limit(MAXIMO)
                .map(e -> new SugerenciaDto(cronogramas.resumen(e.cronograma()), e.diferencias(), e.reparto().asignacion()))
                .toList();
    }

    /**
     * Arma un borrador para los que vinieron (la cantidad de empleados es la de ellos) y reparte los
     * números: cada uno cae donde sabe y puede hoy, y en su principal si se puede.
     */
    public BorradorDto generarConPresentes(PedidoGeneradorDiaDto pedido) {
        if (pedido.pedido() == null) throw new ValidacionException("Faltan los datos del cronograma.");
        List<AsignadorService.Presente> presentes = presentes(pedido.presentes());
        PedidoGeneradorDto p = pedido.pedido();
        BorradorDto borrador = generador.generar(new PedidoGeneradorDto(p.nombre(), p.afluencia(), presentes.size(), p.desde(),
                p.hasta(), p.cada(), p.descansoDesde(), p.descansoHasta(), p.fijos(), p.rueda(), p.coberturas(), p.abrenTarde()));
        CronogramaDto c = borrador.cronograma();
        Map<String, Integer> pesos = puestos.pesos();
        AsignadorService.Resultado reparto = asignador.asignar(PlanRotacion.de(c.horarios(), c.puestos()), presentes, pesos);
        // Con la gente ya puesta: si dos quedan mejor cambiándose de lugar en algún horario, se cambian.
        Map<Long, AsignadorService.Presente> porId = presentes.stream()
                .collect(Collectors.toMap(AsignadorService.Presente::id, Function.identity()));
        for (int vuelta = 0; vuelta < 3; vuelta++) {
            Map<Integer, AsignadorService.Presente> porNumero = new HashMap<>();
            reparto.asignacion().forEach(a -> {
                if (a.empleadoId() != null) porNumero.put(a.numero(), porId.get(a.empleadoId()));
            });
            List<PuestoDto> mejor = MejoraReparto.mejorar(c.puestos(), porNumero, pesos);
            if (mejor.equals(c.puestos())) break;
            PlanRotacion plan = PlanRotacion.de(c.horarios(), mejor);
            c = new CronogramaDto(null, c.nombre(), c.afluencia(), c.horarios(), mejor,
                    rotacion.cantidadEmpleados(plan), rotacion.fijos(plan), rotacion.armarTarjetas(plan));
            reparto = asignador.asignar(plan, presentes, pesos);
        }
        // Un puesto de prioridad baja no se le da a quien no lo sabe: queda cerrado ese rato y la persona, libre.
        // Quién hace cada número no cambia: sólo se tocan celdas y se vuelven a sacar los avisos.
        Map<Integer, AsignadorService.Presente> porNumero = new HashMap<>();
        reparto.asignacion().forEach(a -> {
            if (a.empleadoId() != null) porNumero.put(a.numero(), porId.get(a.empleadoId()));
        });
        // Nunca va otro a un puesto que podría hacer alguien con estrella en él.
        List<PuestoDto> conEstrellas = MejoraReparto.respetarEstrellas(c.puestos(), porNumero);
        // Lo que quedó sin nadie lo cubre alguien que sobra (la segunda cajera a la Casa del zorrito, y así); los
        // de Libre también abren lo cerrado que saben. Nunca un puesto que todavía no abrió (Tirolesa, Fotos).
        Set<String> noAbrir = (p.abrenTarde() == null ? List.<PedidoGeneradorDto.AbreTarde>of() : p.abrenTarde()).stream()
                .filter(t -> t.tipo() == null || t.tipo() == org.example.laserranitarotaciones.model.entity.TipoHorario.ABRE_TARDE)
                .map(t -> Nombres.clave(t.puesto())).collect(Collectors.toSet());
        conEstrellas = MejoraReparto.cubrirHuecos(conEstrellas, porNumero, pesos, noAbrir);
        MejoraReparto.Cierre cierre = MejoraReparto.cerrarBajos(conEstrellas, porNumero, pesos);
        List<PuestoDto> finales = MejoraReparto.cubrirHuecos(cierre.puestos(), porNumero, pesos, noAbrir);
        // Sólo se avisa lo que sigue cerrado (si alguien de Libre lo volvió a abrir, no).
        Map<String, Set<Integer>> cerradosPorNoSaber = new java.util.TreeMap<>();
        cierre.cerrados().forEach((puesto, ks) -> finales.stream().filter(x -> x.nombre().equals(puesto)).findFirst().ifPresent(x -> {
            Set<Integer> siguen = new HashSet<>();
            for (int k : ks) if (PlanRotacion.CERRADO.equalsIgnoreCase(x.celdas().get(k).trim())) siguen.add(k);
            if (!siguen.isEmpty()) cerradosPorNoSaber.put(puesto, siguen);
        }));
        if (!finales.equals(c.puestos())) {
            PlanRotacion plan = PlanRotacion.de(c.horarios(), finales);
            c = new CronogramaDto(null, c.nombre(), c.afluencia(), c.horarios(), finales,
                    rotacion.cantidadEmpleados(plan), rotacion.fijos(plan), rotacion.armarTarjetas(plan));
            reparto = asignador.conAsignacion(plan, presentes, reparto.asignacion());
        }
        List<String> avisos = new ArrayList<>(borrador.avisos());
        List<String> horarios = c.horarios();
        cerradosPorNoSaber.forEach((puesto, ks) -> avisos.add(puesto + " queda cerrado a las "
                + RotacionService.enumerar(ks.stream().sorted().map(horarios::get).toList()) + ": nadie de los que tocaba ahí lo sabe."));
        avisos.addAll(reparto.avisos());
        return new BorradorDto(c, avisos, reparto.asignacion());
    }

    private List<AsignadorService.Presente> presentes(List<PresenteDto> pedidos) {
        if (pedidos == null || pedidos.isEmpty()) throw new ValidacionException("Marcá quién vino hoy.");
        Map<Long, EmpleadoDto> porId = empleados.buscar(pedidos.stream().map(PresenteDto::empleadoId).toList())
                .stream().collect(Collectors.toMap(EmpleadoDto::id, Function.identity()));

        List<AsignadorService.Presente> presentes = new ArrayList<>();
        for (PresenteDto p : pedidos) {
            EmpleadoDto e = porId.get(p.empleadoId());
            if (e == null) throw new NoEncontradoException("Uno de los empleados ya no existe. Recargá la página.");
            if (e.enEntrenamiento()) continue; // acompaña a otro: no ocupa un número
            Set<String> sabe = claves(e.puedeHacer()); // con los grupos ya cambiados por sus puestos
            Set<String> hoy = p.puestosHoy() == null ? new HashSet<>(sabe) : claves(p.puestosHoy());
            hoy.retainAll(sabe);
            presentes.add(new AsignadorService.Presente(e.id(), e.nombre(), sabe, hoy, claves(e.principales())));
        }
        if (presentes.isEmpty()) throw new ValidacionException("Vinieron sólo empleados en entrenamiento.");
        return presentes;
    }

    private static Set<String> claves(List<String> nombres) {
        return nombres.stream().map(Nombres::clave).collect(Collectors.toCollection(HashSet::new));
    }
}
