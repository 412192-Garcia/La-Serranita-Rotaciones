package org.example.laserranitarotaciones.service;

import lombok.RequiredArgsConstructor;
import org.example.laserranitarotaciones.model.PlanRotacion;
import org.example.laserranitarotaciones.model.dto.BorradorDto;
import org.example.laserranitarotaciones.model.dto.CronogramaDto;
import org.example.laserranitarotaciones.model.dto.PedidoGeneradorDiaDto;
import org.example.laserranitarotaciones.model.dto.PedidoGeneradorDto;
import org.example.laserranitarotaciones.model.dto.EmpleadoDto;
import org.example.laserranitarotaciones.model.dto.AsignacionDto;
import org.example.laserranitarotaciones.model.dto.FaltanteRolDto;
import org.example.laserranitarotaciones.model.dto.PedidoCubrirRolDto;
import org.example.laserranitarotaciones.model.dto.RefuerzoDto;
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
        List<PuestoDto> conEstrellas = MejoraReparto.respetarEstrellas(c.puestos(), porNumero, pesos);
        // Si en el almuerzo alguien que no sabe cubre un puesto mientras come el que sí, el que sabe come más tarde
        // (dentro de los descansos) y lo cubre otro que lo sepa y esté libre.
        conEstrellas = MejoraReparto.correrAlmuerzos(conEstrellas, porNumero, horariosDeAlmuerzo(c.horarios(), p));
        // Lo que quedó sin nadie lo cubre alguien que sobra (la segunda cajera a la Casa del zorrito, y así); los
        // de Libre también abren lo cerrado que saben. Nunca un puesto que todavía no abrió (Tirolesa, Fotos).
        Set<String> noAbrir = (p.abrenTarde() == null ? List.<PedidoGeneradorDto.AbreTarde>of() : p.abrenTarde()).stream()
                .filter(t -> t.tipo() == null || t.tipo() == org.example.laserranitarotaciones.model.entity.TipoHorario.ABRE_TARDE)
                .map(t -> Nombres.clave(t.puesto())).collect(Collectors.toSet());
        // De la Tirolesa o el Show (puestos con roles) no se saca a nadie para cubrir otra cosa.
        Map<String, RolesPuesto.Config> configs = puestos.roles();
        conEstrellas = MejoraReparto.cubrirHuecos(conEstrellas, porNumero, pesos, noAbrir, configs.keySet());
        MejoraReparto.Cierre cierre = MejoraReparto.cerrarBajos(conEstrellas, porNumero, pesos);
        // El Cajero (y lo que tiene que estar siempre) nunca lo hace alguien que no sabe: antes, refuerzo. Tampoco
        // un puesto con roles (el Show del zorrito): no se improvisa.
        List<PuestoDto> antesDeSaber = MejoraReparto.cubrirHuecos(cierre.puestos(), porNumero, pesos, noAbrir, configs.keySet());
        // Cuántos tenía que haber en esos puestos: si al sacar al que no sabe quedan menos, se pide un refuerzo
        // aunque quede alguien (el Show con dos de los tres).
        Map<String, List<Integer>> esperados = cuantos(antesDeSaber, pesos, configs.keySet());
        List<PuestoDto> finales = cerrarVaciosBajos(MejoraReparto.siempreConQuienSabe(
                antesDeSaber, porNumero, pesos, configs.keySet()), pesos);
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
        // Los roles (Zorrito, Presentador): con la gente ya puesta, quién hace cada uno. Puede traer a alguien de
        // Libre o cambiarlo por otro; lo que nadie puede hacer queda para elegir a mano.
        List<FaltanteRolDto> faltantes = List.of();
        if (!configs.isEmpty()) {
            Map<Integer, AsignadorService.Presente> conNumero = porNumero(reparto.asignacion(), porId);
            List<PuestoDto> conRoles = RolesPuesto.asignar(c.puestos(), conNumero, configs, sabeRoles(pedido.presentes()), pesos);
            boolean movio = !celdas(conRoles).equals(celdas(c.puestos()));
            PlanRotacion plan = PlanRotacion.de(c.horarios(), conRoles);
            c = new CronogramaDto(null, c.nombre(), c.afluencia(), c.horarios(), conRoles, rotacion.cantidadEmpleados(plan),
                    rotacion.fijos(plan), RolesPuesto.conRoles(rotacion.armarTarjetas(plan), conRoles, c.horarios(), configs));
            if (movio) reparto = asignador.conAsignacion(plan, presentes, reparto.asignacion());
            faltantes = RolesPuesto.faltantes(conRoles, configs, c.horarios());
        }
        // Los que quedaron en Libre ayudan en lo que saben (Nancy en Baños), o toman el puesto de uno que así vuelve
        // a su estrella (la cajera que sale va a Fotos y Nancy vuelve a Baños).
        Map<Integer, AsignadorService.Presente> quienEs = porNumero(reparto.asignacion(), porId);
        Set<String> deLaRueda = (p.rueda() == null ? List.<PedidoGeneradorDto.Lugar>of() : p.rueda()).stream()
                .flatMap(l -> l.puestos().stream()).map(Nombres::clave).collect(Collectors.toSet());
        // Y en la rueda nadie repite el mismo puesto dos medias horas seguidas (los cambios de arriba lo pueden dejar).
        // Un puesto que abre tarde con una persona hasta el cierre (Fotos) lo hace la misma todo ese rato.
        List<PuestoDto> armado = c.puestos();
        Set<String> hastaElCierre = new HashSet<>();
        for (PedidoGeneradorDto.AbreTarde t : p.abrenTarde() == null ? List.<PedidoGeneradorDto.AbreTarde>of() : p.abrenTarde()) {
            boolean abre = t.tipo() == null || t.tipo() == org.example.laserranitarotaciones.model.entity.TipoHorario.ABRE_TARDE;
            if (!abre || t.hasta() != null || t.personas() != 1 || configs.containsKey(Nombres.clave(t.puesto()))) continue;
            armado = MejoraReparto.unoHastaElCierre(armado, quienEs, pesos, t.puesto(), configs.keySet());
            hastaElCierre.add(Nombres.clave(t.puesto()));
        }
        // Lo que tiene que estar completo (Tirolesa, Cajero) y quedó con menos: cierra uno de prioridad baja y ese completa.
        Set<String> intocables = new HashSet<>(configs.keySet());
        intocables.addAll(hastaElCierre);
        // Y nadie hace un puesto de prioridad baja con su estrella abierta (la cajera vuelve al Cajero, Pecera cierra).
        List<PuestoDto> conAyuda = MejoraReparto.sinRepetir(MejoraReparto.completarConBajos(
                MejoraReparto.estrellaAntesQueBajo(MejoraReparto.libresAyudan(armado, quienEs, pesos, hastaElCierre),
                        quienEs, pesos, intocables), quienEs, pesos, esperados, intocables),
                quienEs, deLaRueda);
        if (!conAyuda.equals(c.puestos())) {
            PlanRotacion plan = PlanRotacion.de(c.horarios(), conAyuda);
            c = new CronogramaDto(null, c.nombre(), c.afluencia(), c.horarios(), conAyuda, rotacion.cantidadEmpleados(plan),
                    rotacion.fijos(plan), RolesPuesto.conRoles(rotacion.armarTarjetas(plan), conAyuda, c.horarios(), configs));
            reparto = asignador.conAsignacion(plan, presentes, reparto.asignacion());
        }
        // Los avisos del generador son de cómo está cargado (reglas, horarios): van aparte de los del día.
        List<String> avisos = new ArrayList<>();
        List<String> horarios = c.horarios();
        cerradosPorNoSaber.forEach((puesto, ks) -> avisos.add(puesto + " queda cerrado a las "
                + RotacionService.enumerar(ks.stream().sorted().map(horarios::get).toList()) + ": nadie de los que tocaba ahí lo sabe."));
        faltantes.forEach(f -> avisos.add(RolesPuesto.aviso(f)));
        // Lo que quedó sin nadie (o con menos gente de la que va) después de probar todo: se pide un refuerzo.
        List<RefuerzoDto> refuerzos = refuerzos(c.puestos(), c.horarios(), esperados);
        refuerzos.forEach(r -> avisos.add(aviso(r)));
        avisos.addAll(reparto.avisos());
        return new BorradorDto(c, avisos, reparto.asignacion(), faltantes, refuerzos, borrador.avisos());
    }

    /**
     * Un rol que nadie podía hacer lo cubre el empleado elegido (cualquiera de los que tienen número). Si estaba
     * en otro puesto, se cambia con uno del puesto sin rol. Se vuelven a sacar los avisos del reparto y de roles.
     */
    public BorradorDto cubrirRol(PedidoCubrirRolDto pedido) {
        CronogramaDto viejo = pedido.cronograma();
        if (viejo == null || viejo.puestos() == null || viejo.horarios() == null) throw new ValidacionException("Falta el cronograma.");
        if (pedido.empleadoId() == null) throw new ValidacionException("Elegí quién lo cubre.");
        List<AsignadorService.Presente> presentes = presentes(pedido.presentes());
        Map<Long, AsignadorService.Presente> porId = presentes.stream()
                .collect(Collectors.toMap(AsignadorService.Presente::id, Function.identity()));
        List<AsignacionDto> asignacion = pedido.asignacion() == null ? List.of() : pedido.asignacion();
        AsignacionDto elegido = asignacion.stream().filter(a -> pedido.empleadoId().equals(a.empleadoId())).findFirst()
                .orElseThrow(() -> new ValidacionException("Ese empleado no tiene número en este cronograma."));
        Map<String, RolesPuesto.Config> configs = puestos.roles();
        RolesPuesto.Config config = configs.get(Nombres.clave(pedido.puesto() == null ? "" : pedido.puesto()));
        if (config == null) throw new ValidacionException((pedido.puesto() == null ? "Ese puesto" : pedido.puesto()) + " no tiene roles.");
        String rol = pedido.rol() == null ? "" : pedido.rol();

        Map<Integer, AsignadorService.Presente> conNumero = porNumero(asignacion, porId);
        List<PuestoDto> nuevos = cerrarVaciosBajos(RolesPuesto.cubrir(viejo.puestos(), config, rol, pedido.celdas(),
                elegido.numero(), elegido.nombre(), conNumero, viejo.horarios()), puestos.pesos());

        PlanRotacion planViejo = PlanRotacion.de(viejo.horarios(), viejo.puestos());
        List<String> avisos = new ArrayList<>(pedido.avisos() == null ? List.of() : pedido.avisos());
        asignador.conAsignacion(planViejo, presentes, asignacion).avisos().forEach(avisos::remove);
        RolesPuesto.faltantes(viejo.puestos(), configs, viejo.horarios()).forEach(f -> avisos.remove(RolesPuesto.aviso(f)));
        List<RefuerzoDto> viejos = pedido.refuerzos() == null ? refuerzos(viejo.puestos(), viejo.horarios()) : pedido.refuerzos();
        viejos.forEach(r -> avisos.remove(aviso(r)));

        PlanRotacion plan = PlanRotacion.de(viejo.horarios(), nuevos);
        List<FaltanteRolDto> faltantes = RolesPuesto.faltantes(nuevos, configs, viejo.horarios());
        faltantes.forEach(f -> avisos.add(RolesPuesto.aviso(f)));
        List<RefuerzoDto> refuerzos = refuerzos(nuevos, viejo.horarios(), esperadosDe(viejos, viejo.puestos()));
        refuerzos.forEach(r -> avisos.add(aviso(r)));
        avisos.addAll(asignador.conAsignacion(plan, presentes, asignacion).avisos());
        CronogramaDto c = new CronogramaDto(viejo.id(), viejo.nombre(), viejo.afluencia(), viejo.horarios(), nuevos,
                rotacion.cantidadEmpleados(plan), rotacion.fijos(plan),
                RolesPuesto.conRoles(rotacion.armarTarjetas(plan), nuevos, viejo.horarios(), configs));
        return new BorradorDto(c, avisos, asignacion, faltantes, refuerzos);
    }

    private static Map<Integer, AsignadorService.Presente> porNumero(List<AsignacionDto> asignacion,
                                                                     Map<Long, AsignadorService.Presente> porId) {
        Map<Integer, AsignadorService.Presente> porNumero = new HashMap<>();
        asignacion.forEach(a -> {
            if (a.empleadoId() != null && porId.containsKey(a.empleadoId())) porNumero.put(a.numero(), porId.get(a.empleadoId()));
        });
        return porNumero;
    }

    /**
     * Un puesto de prioridad baja (Baños) que quedó sin nadie en un horario queda Cerrado ese rato: no se pide
     * refuerzo para él. Los refuerzos son para los puestos importantes.
     */
    static List<PuestoDto> cerrarVaciosBajos(List<PuestoDto> puestos, Map<String, Integer> pesos) {
        List<PuestoDto> resultado = new ArrayList<>();
        for (PuestoDto p : puestos) {
            boolean bajo = !Nombres.noEsPuesto(p.nombre())
                    && pesos.getOrDefault(Nombres.clave(p.nombre()), Prioridad.MEDIA.peso()) < Prioridad.MEDIA.peso();
            if (!bajo || p.celdas().stream().noneMatch(c -> !PlanRotacion.esCerrado(c) && PlanRotacion.empleados(c).isEmpty())) {
                resultado.add(p);
                continue;
            }
            List<String> celdas = p.celdas().stream()
                    .map(c -> !PlanRotacion.esCerrado(c) && PlanRotacion.empleados(c).isEmpty() ? PlanRotacion.CERRADO : c).toList();
            resultado.add(new PuestoDto(p.nombre(), p.nota(), p.iniciaRotacion(), celdas, p.roles()));
        }
        return resultado;
    }

    /** Los puestos con algún horario sin nadie (ni Cerrado): ahí hace falta un refuerzo. */
    static List<RefuerzoDto> refuerzos(List<PuestoDto> puestos, List<String> horarios) {
        return refuerzos(puestos, horarios, Map.of());
    }

    /**
     * Los puestos con algún horario sin nadie (ni Cerrado), o con menos gente de la esperada (esperados: puesto →
     * cuántos por horario): ahí hace falta un refuerzo.
     */
    static List<RefuerzoDto> refuerzos(List<PuestoDto> puestos, List<String> horarios, Map<String, List<Integer>> esperados) {
        List<RefuerzoDto> refuerzos = new ArrayList<>();
        for (PuestoDto p : puestos) {
            if (Nombres.noEsPuesto(p.nombre())) continue;
            List<Integer> esperado = esperados.getOrDefault(p.nombre(), List.of());
            List<Integer> ks = new ArrayList<>();
            for (int k = 0; k < p.celdas().size() && k < horarios.size(); k++) {
                String celda = p.celdas().get(k);
                if (PlanRotacion.esCerrado(celda)) continue;
                int hay = PlanRotacion.empleados(celda).size();
                if (hay == 0 || (k < esperado.size() && hay < esperado.get(k))) ks.add(k);
            }
            if (!ks.isEmpty()) refuerzos.add(new RefuerzoDto(p.nombre(), ks, ks.stream().map(horarios::get).toList()));
        }
        return refuerzos;
    }

    /** Por cada puesto que tiene que estar siempre o tiene roles: cuántos hay en cada horario. */
    private static Map<String, List<Integer>> cuantos(List<PuestoDto> puestos, Map<String, Integer> pesos, Set<String> conRoles) {
        Map<String, List<Integer>> m = new HashMap<>();
        for (PuestoDto p : puestos) {
            String clave = Nombres.clave(p.nombre());
            if (Nombres.noEsPuesto(p.nombre())) continue;
            if (pesos.getOrDefault(clave, Prioridad.MEDIA.peso()) < Prioridad.SIEMPRE.peso() && !conRoles.contains(clave)) continue;
            m.put(p.nombre(), p.celdas().stream().map(c -> PlanRotacion.esCerrado(c) ? 0 : PlanRotacion.empleados(c).size()).toList());
        }
        return m;
    }

    /** Al cubrir un rol a mano: lo que se esperaba donde ya se pedía refuerzo (uno más de los que había). */
    private static Map<String, List<Integer>> esperadosDe(List<RefuerzoDto> refuerzos, List<PuestoDto> puestos) {
        Map<String, List<Integer>> m = new HashMap<>();
        for (RefuerzoDto r : refuerzos) {
            puestos.stream().filter(p -> p.nombre().equals(r.puesto())).findFirst().ifPresent(p -> {
                List<Integer> e = new ArrayList<>();
                for (int k = 0; k < p.celdas().size(); k++) {
                    e.add(r.celdas().contains(k) ? PlanRotacion.empleados(p.celdas().get(k)).size() + 1 : 0);
                }
                m.put(p.nombre(), e);
            });
        }
        return m;
    }

    static String aviso(RefuerzoDto r) {
        return "Falta gente en " + r.puesto() + " a las " + RotacionService.enumerar(r.horas()) + ": pedí un refuerzo.";
    }

    /** Las posiciones de los horarios que caen en los descansos (desde incluido, hasta no). */
    private static List<Integer> horariosDeAlmuerzo(List<String> horarios, PedidoGeneradorDto p) {
        java.time.LocalTime desde = hora(p.descansoDesde()), hasta = hora(p.descansoHasta());
        List<Integer> ks = new ArrayList<>();
        if (desde == null || hasta == null) return ks;
        for (int k = 0; k < horarios.size(); k++) {
            java.time.LocalTime h = java.time.LocalTime.parse(horarios.get(k));
            if (!h.isBefore(desde) && h.isBefore(hasta)) ks.add(k);
        }
        return ks;
    }

    private static java.time.LocalTime hora(String texto) {
        return texto == null || texto.isBlank() ? null : ImportadorExcelService.parsearHora(texto);
    }

    private static List<List<String>> celdas(List<PuestoDto> puestos) {
        return puestos.stream().map(PuestoDto::celdas).toList();
    }

    /** id del empleado → los roles que sabe (RolesPuesto.clave). */
    private Map<Long, Set<String>> sabeRoles(List<PresenteDto> pedidos) {
        Map<Long, Set<String>> roles = new HashMap<>();
        empleados.buscar(pedidos.stream().map(PresenteDto::empleadoId).toList()).forEach(e -> roles.put(e.id(),
                (e.roles() == null ? List.<org.example.laserranitarotaciones.model.dto.RolSabidoDto>of() : e.roles()).stream()
                        .map(r -> RolesPuesto.clave(r.puesto(), r.rol())).collect(Collectors.toSet())));
        return roles;
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
            presentes.add(new AsignadorService.Presente(e.id(), e.nombreCorto(), sabe, hoy, claves(e.principales())));
        }
        if (presentes.isEmpty()) throw new ValidacionException("Vinieron sólo empleados en entrenamiento.");
        return presentes;
    }

    private static Set<String> claves(List<String> nombres) {
        return nombres.stream().map(Nombres::clave).collect(Collectors.toCollection(HashSet::new));
    }
}
