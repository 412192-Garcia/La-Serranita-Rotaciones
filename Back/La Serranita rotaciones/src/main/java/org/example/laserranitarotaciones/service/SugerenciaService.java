package org.example.laserranitarotaciones.service;

import lombok.RequiredArgsConstructor;
import org.example.laserranitarotaciones.model.dto.CronogramaDto;
import org.example.laserranitarotaciones.model.dto.PedidoSugerenciaDto;
import org.example.laserranitarotaciones.model.dto.PuestoDto;
import org.example.laserranitarotaciones.model.dto.SugerenciaDto;
import org.example.laserranitarotaciones.model.entity.Afluencia;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Ordena los cronogramas según las respuestas del día. Cada diferencia suma puntos de castigo:
 * pedir más empleados de los que hay pesa más que sobrar, porque deja puestos sin cubrir.
 */
@Service
@RequiredArgsConstructor
public class SugerenciaService {

    static final int MAXIMO = 5;
    private static final int POR_EMPLEADO_FALTANTE = 12;
    private static final int POR_EMPLEADO_SOBRANTE = 6;
    private static final int POR_NIVEL_AFLUENCIA = 8;
    private static final int POR_PUESTO_SIN_FIJO = 8;
    private static final int POR_PUESTO_INEXISTENTE = 10;
    private static final int POR_FIJO_NO_PEDIDO = 1;

    private final CronogramaService cronogramas;

    public List<SugerenciaDto> sugerir(PedidoSugerenciaDto pedido) {
        record Evaluado(CronogramaDto cronograma, int puntos, List<String> diferencias) {
        }
        List<Evaluado> evaluados = new ArrayList<>();
        for (CronogramaDto c : cronogramas.todos()) {
            List<String> diferencias = new ArrayList<>();
            int puntos = 0;

            Integer hay = pedido.empleados();
            if (hay != null && c.empleados() != hay) {
                int dif = c.empleados() - hay;
                puntos += dif > 0 ? dif * POR_EMPLEADO_FALTANTE : -dif * POR_EMPLEADO_SOBRANTE;
                diferencias.add("Es para " + c.empleados() + " empleados y hay " + hay + ".");
            }

            Afluencia afluencia = pedido.afluencia();
            if (afluencia != null && c.afluencia() != afluencia) {
                puntos += Math.abs(c.afluencia().ordinal() - afluencia.ordinal()) * POR_NIVEL_AFLUENCIA;
                diferencias.add("Es para afluencia " + c.afluencia().name().toLowerCase(Locale.ROOT) + ".");
            }

            for (String fijo : pedido.fijos() == null ? List.<String>of() : pedido.fijos()) {
                if (contiene(c.fijos(), fijo)) continue;
                if (contiene(c.puestos().stream().map(PuestoDto::nombre).toList(), fijo)) {
                    puntos += POR_PUESTO_SIN_FIJO;
                    diferencias.add("En " + fijo + " el empleado rota.");
                } else {
                    puntos += POR_PUESTO_INEXISTENTE;
                    diferencias.add("No tiene " + fijo + ".");
                }
            }
            // Desempate: entre los que cumplen, mejor el que deja rotar lo que no se pidió fijo.
            List<String> pedidos = pedido.fijos() == null ? List.of() : pedido.fijos();
            puntos += (int) c.fijos().stream().filter(f -> !contiene(pedidos, f)).count() * POR_FIJO_NO_PEDIDO;
            evaluados.add(new Evaluado(c, puntos, diferencias));
        }

        return evaluados.stream()
                .sorted(Comparator.comparingInt(Evaluado::puntos)
                        .thenComparing(e -> e.cronograma().nombre(), String.CASE_INSENSITIVE_ORDER))
                .limit(MAXIMO)
                .map(e -> new SugerenciaDto(cronogramas.resumen(e.cronograma()), e.diferencias()))
                .toList();
    }

    private static boolean contiene(List<String> nombres, String buscado) {
        return nombres.stream().anyMatch(n -> n.trim().equalsIgnoreCase(buscado.trim()));
    }
}
