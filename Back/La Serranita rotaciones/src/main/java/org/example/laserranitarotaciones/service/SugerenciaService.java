package org.example.laserranitarotaciones.service;

import lombok.RequiredArgsConstructor;
import org.example.laserranitarotaciones.model.PlanRotacion;
import org.example.laserranitarotaciones.model.dto.CronogramaDto;
import org.example.laserranitarotaciones.model.dto.EmpleadoDto;
import org.example.laserranitarotaciones.model.dto.PedidoSugerenciaDto;
import org.example.laserranitarotaciones.model.dto.PresenteDto;
import org.example.laserranitarotaciones.model.dto.SugerenciaDto;
import org.example.laserranitarotaciones.model.entity.Afluencia;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
    private static final int POR_EMPLEADO_FALTANTE = 12;
    private static final int POR_EMPLEADO_SOBRANTE = 6;
    private static final int POR_NIVEL_AFLUENCIA = 8;

    private final CronogramaService cronogramas;
    private final EmpleadoService empleados;
    private final AsignadorService asignador;
    private final PuestoParqueService puestos;

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
                puntos += dif > 0 ? dif * POR_EMPLEADO_FALTANTE : -dif * POR_EMPLEADO_SOBRANTE;
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

    private List<AsignadorService.Presente> presentes(List<PresenteDto> pedidos) {
        if (pedidos == null || pedidos.isEmpty()) throw new ValidacionException("Marcá quién vino hoy.");
        Map<Long, EmpleadoDto> porId = empleados.buscar(pedidos.stream().map(PresenteDto::empleadoId).toList())
                .stream().collect(Collectors.toMap(EmpleadoDto::id, Function.identity()));

        List<AsignadorService.Presente> presentes = new ArrayList<>();
        for (PresenteDto p : pedidos) {
            EmpleadoDto e = porId.get(p.empleadoId());
            if (e == null) throw new NoEncontradoException("Uno de los empleados ya no existe. Recargá la página.");
            Set<String> sabe = claves(e.puestos());
            Set<String> hoy = p.puestosHoy() == null ? new HashSet<>(sabe) : claves(p.puestosHoy());
            hoy.retainAll(sabe);
            presentes.add(new AsignadorService.Presente(e.id(), e.nombre(), sabe, hoy, claves(e.principales())));
        }
        return presentes;
    }

    private static Set<String> claves(List<String> nombres) {
        return nombres.stream().map(Nombres::clave).collect(Collectors.toCollection(HashSet::new));
    }
}
