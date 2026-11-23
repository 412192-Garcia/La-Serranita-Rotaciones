package org.example.laserranitarotaciones.service;

import org.example.laserranitarotaciones.model.PlanRotacion;
import org.example.laserranitarotaciones.model.dto.BorradorDto;
import org.example.laserranitarotaciones.model.dto.CronogramaDto;
import org.example.laserranitarotaciones.model.dto.PedidoGeneradorDto;
import org.example.laserranitarotaciones.model.dto.PuestoDto;
import org.example.laserranitarotaciones.model.dto.PuestoParqueDto;
import org.example.laserranitarotaciones.model.dto.ReglaAperturaDto;
import org.example.laserranitarotaciones.model.entity.Afluencia;
import org.example.laserranitarotaciones.model.entity.Prioridad;
import org.example.laserranitarotaciones.model.entity.TipoHorario;
import org.example.laserranitarotaciones.repository.PuestoParqueRepository;
import org.example.laserranitarotaciones.repository.ReglaAperturaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class GeneradorServiceTest {

    private static final List<String> RUEDA = List.of("Entrada", "Canilla", "Minigolf", "Travesía+Búsqueda+Laberinto", "Tobogán", "Carrera");

    @Autowired
    private GeneradorService generador;
    @Autowired
    private PuestoParqueService prioridades;
    @Autowired
    private PuestoParqueRepository prioridadRepositorio;
    @Autowired
    private ReglaAperturaRepository reglaRepositorio;
    @Autowired
    private ReglaAperturaService reglas;

    @BeforeEach
    void limpiar() {
        prioridadRepositorio.deleteAll();
        reglaRepositorio.deleteAll();
    }

    @Test
    void armaElPlanDeDiezConLasReglasDelParque() {
        BorradorDto b = generador.generar(pedido(10));
        CronogramaDto c = b.cronograma();

        assertThat(b.avisos()).isEmpty();
        assertThat(c.empleados()).isEqualTo(10);
        assertThat(c.horarios()).hasSize(15).startsWith("11:00").endsWith("18:00");
        assertThat(c.fijos()).contains("Cajero", "Encargado", "Baños");
        assertThat(fila(c, "Travesía").iniciaRotacion()).isTrue();

        // Todos descansan una vez, en la franja: los que rotan antes que los fijos.
        PuestoDto descanso = fila(c, "Descanso");
        Map<Integer, Integer> cuando = cuandoDescansa(descanso);
        assertThat(cuando).hasSize(10);
        assertThat(cuando.values()).allMatch(k -> k >= 2 && k <= 4);
        assertThat(cuando.get(5)).isLessThan(cuando.get(1));

        // Fuera del almuerzo la rueda está completa y cada uno baja un lugar por horario.
        PlanRotacion plan = PlanRotacion.de(c.horarios(), c.puestos());
        for (String puesto : List.of("Entrada", "Canilla", "Minigolf", "Travesía", "Tobogán", "Carrera")) {
            PuestoDto p = fila(c, puesto);
            for (int k : List.of(0, 1, 5, 6, 14)) assertThat(p.celdas().get(k)).as(puesto + " " + k).isNotEmpty();
        }
        assertThat(lugar(c, 5, 0)).isEqualTo("Travesía");
        assertThat(lugar(c, 5, 1)).isEqualTo("Tobogán");
        assertThat(lugar(c, 5, 5)).isEqualTo("Minigolf");
        assertThat(plan.turnos()).isNotEmpty();
    }

    @Test
    void enElAlmuerzoLosFijosCubrenLoQueQuedaVacio() {
        CronogramaDto c = generador.generar(pedido(10)).cronograma();
        // A las 12:00 (k = 2) descansan algunos de los que rotan: Baños y Encargado pasan a cubrir.
        int k = 2;
        boolean bañosCubre = fila(c, "Baños").celdas().get(k).isEmpty() && fila(c, "Minigolf").celdas().get(k).equals("4");
        boolean encargadoCubre = fila(c, "Encargado").celdas().get(k).isEmpty() && fila(c, "Travesía").celdas().get(k).equals("3");
        assertThat(bañosCubre || encargadoCubre).isTrue();
    }

    @Test
    void dosDelMismoPuestoFijoNoDescansanALaVez() {
        // Franja de 12:00 a 13:00: a los fijos les toca sólo 13:00; los dos cajeros no pueden ir juntos.
        CronogramaDto c = generador.generar(pedido(11)).cronograma();
        PuestoDto cajero = fila(c, "Cajero");
        assertThat(cajero.celdas()).allMatch(cel -> !cel.isEmpty());
        Map<Integer, Integer> cuando = cuandoDescansa(fila(c, "Descanso"));
        assertThat(cuando.get(1)).isNotEqualTo(cuando.get(2));
        assertThat(cuando.get(1)).isEqualTo(4);
    }

    @Test
    void laTirolesaAbreTardeYSuGenteCubreElParqueHastaQueAbre() {
        PedidoGeneradorDto base = pedido(14);
        PedidoGeneradorDto p = new PedidoGeneradorDto(base.nombre(), base.afluencia(), 14, base.desde(), base.hasta(), base.cada(),
                "12:00", "13:30", base.fijos(), base.rueda(), base.coberturas(),
                List.of(new PedidoGeneradorDto.AbreTarde("Tirolesa", 4, "13:00")));
        BorradorDto b = generador.generar(p);
        CronogramaDto c = b.cronograma();
        assertThat(b.avisos()).isEmpty();

        // Números: 1-4 fijos (Cajero x2, Encargado, Baños), 5-8 Tirolesa, 9-14 rotan.
        PuestoDto tirolesa = fila(c, "Tirolesa");
        assertThat(tirolesa.celdas().subList(0, 4)).containsOnly("Cerrado");
        for (int k = 4; k < c.horarios().size(); k++) {
            assertThat(PlanRotacion.empleados(tirolesa.celdas().get(k))).as("13:00 en adelante, k=" + k).hasSizeGreaterThanOrEqualTo(3);
        }

        // Descansan una vez, después de abrir y de a uno.
        Map<Integer, Integer> cuando = cuandoDescansa(fila(c, "Descanso"));
        List<Integer> deTirolesa = List.of(5, 6, 7, 8).stream().map(cuando::get).toList();
        assertThat(deTirolesa).doesNotHaveDuplicates().allMatch(k -> k >= 4);

        // Antes de abrir están en el parque, y en los almuerzos de los que rotan no queda nada vacío.
        for (int n : List.of(5, 6, 7, 8)) assertThat(lugar(c, n, 0)).isNotEqualTo("Tirolesa").isNotNull();
        // A las 11:00 la rueda está completa: no refuerzan ningún puesto, quedan en Libre.
        assertThat(fila(c, "Libre").celdas().get(0)).isEqualTo("5-6-7-8");
        assertThat(fila(c, "Entrada").celdas().get(0)).doesNotContain("-");
        assertThat(c.fijos()).doesNotContain("Libre");
        for (String puesto : List.of("Entrada", "Canilla", "Minigolf", "Travesía", "Tobogán", "Carrera")) {
            for (int k : List.of(2, 3)) assertThat(fila(c, puesto).celdas().get(k)).as(puesto + " " + k).isNotEmpty();
        }
        // Como cubren ellos, los fijos se quedan en su puesto.
        assertThat(fila(c, "Baños").celdas().get(2)).isEqualTo("4");
    }

    @Test
    void elDeFotosRotaConLaRuedaHastaQueAbreYDespuesCierraElDeMenosPrioridad() {
        // 10: 4 fijos, Fotos (5) desde las 17 y 5 que rotan para 6 lugares: el de Fotos completa la rueda.
        PedidoGeneradorDto base = pedido(10);
        BorradorDto b = generador.generar(new PedidoGeneradorDto(base.nombre(), base.afluencia(), 10, base.desde(), base.hasta(),
                base.cada(), "12:00", "13:00", base.fijos(), base.rueda(), base.coberturas(),
                List.of(new PedidoGeneradorDto.AbreTarde("Fotos", 1, "17:00"))));
        CronogramaDto c = b.cronograma();
        assertThat(b.avisos()).noneMatch(a -> a.contains("queda cerrado") || a.contains("sin nadie"));
        List<String> rueda = List.of("Entrada", "Canilla", "Minigolf", "Travesía", "Tobogán", "Carrera");
        for (String puesto : rueda) assertThat(fila(c, puesto).celdas().get(0)).as(puesto).isNotEmpty().isNotEqualTo("Cerrado");
        assertThat(lugar(c, 5, 0)).isIn(rueda);
        assertThat(c.puestos()).noneMatch(p -> p.nombre().equals("Libre"));

        // Desde las 17 está en Fotos y se cierra un lugar de la rueda (uno solo), sin huecos que vayan rotando.
        assertThat(fila(c, "Fotos").celdas().get(13)).isEqualTo("5");
        long cerrados = rueda.stream().filter(p -> fila(c, p).celdas().get(13).equals("Cerrado")).count();
        assertThat(cerrados).isEqualTo(1);
        assertThat(rueda.stream().filter(p -> fila(c, p).celdas().get(13).isEmpty())).isEmpty();
    }

    @Test
    void laCajeraQueSobraVaAlShowYNoQuedaEnDosLugares() {
        // 10: Cajero x2 (1, 2), Encargado, Baños, el del Show (5) y 5 que rotan. Desde las 16 Cajero tiene uno menos
        // y a esa hora abre el Show: la cajera 2 va al Show, y no además a un lugar de la rueda.
        PedidoGeneradorDto base = pedido(10);
        CronogramaDto c = generador.generar(new PedidoGeneradorDto(base.nombre(), base.afluencia(), 10, base.desde(), base.hasta(),
                base.cada(), "12:00", "13:00", base.fijos(), base.rueda(), base.coberturas(),
                List.of(new PedidoGeneradorDto.AbreTarde("Cajero", 1, "16:00", null, TipoHorario.MENOS),
                        new PedidoGeneradorDto.AbreTarde("Show del zorrito", 1, "16:00", "17:00")))).cronograma();
        for (int k = 0; k < c.horarios().size(); k++) {
            int kk = k;
            for (int n = 1; n <= 10; n++) {
                int nn = n;
                long filas = c.puestos().stream().filter(p -> !p.nombre().equals("Travesía") && !p.nombre().equals("Búsqueda")
                        && !p.nombre().equals("Laberinto") && PlanRotacion.empleados(p.celdas().get(kk)).contains(nn)).count();
                assertThat(filas).as("el " + n + " a las " + c.horarios().get(k)).isLessThanOrEqualTo(1);
            }
        }
        assertThat(PlanRotacion.empleados(fila(c, "Show del zorrito").celdas().get(10))).contains(2);
    }

    @Test
    void unEventoConHoraDeFinDejaASuGenteEnElParqueAntesYDespues() {
        PedidoGeneradorDto base = pedido(12);
        PedidoGeneradorDto p = new PedidoGeneradorDto(base.nombre(), base.afluencia(), 12, base.desde(), base.hasta(), base.cada(),
                "12:00", "13:30", base.fijos(), base.rueda(), base.coberturas(),
                List.of(new PedidoGeneradorDto.AbreTarde("Casa del zorrito", 2, "16:00", "17:00")));
        CronogramaDto c = generador.generar(p).cronograma();

        // 11:00 es k = 0: 16:00 es k = 10 y 17:00 es k = 12.
        PuestoDto zorrito = fila(c, "Casa del zorrito");
        for (int k = 0; k < c.horarios().size(); k++) {
            boolean abierto = k == 10 || k == 11;
            assertThat(zorrito.celdas().get(k)).as("k=" + k).isEqualTo(abierto ? "5-6" : "Cerrado");
        }
        // Antes y después del evento están en el parque; descansan en la parte de la franja de los fijos.
        assertThat(lugar(c, 5, 0)).isNotEqualTo("Casa del zorrito").isNotNull();
        assertThat(lugar(c, 6, 13)).isNotEqualTo("Casa del zorrito").isNotNull();
        Map<Integer, Integer> cuando = cuandoDescansa(fila(c, "Descanso"));
        assertThat(List.of(cuando.get(5), cuando.get(6))).allMatch(k -> k == 4 || k == 5).doesNotHaveDuplicates();
    }

    @Test
    void mientrasDuraElEventoLaRuedaSigueRotandoYDespuesNoVuelveAtras() {
        // De 16 a 17 dos de la rueda van a la Casa del zorrito: los que quedan siguen rotando entre los lugares
        // abiertos (nadie se queda clavado) y a las 17 cada uno sigue desde donde estaba.
        PedidoGeneradorDto base = pedido(12);
        PedidoGeneradorDto p = new PedidoGeneradorDto(base.nombre(), base.afluencia(), 12, base.desde(), base.hasta(), base.cada(),
                "12:00", "13:30", base.fijos(), base.rueda(), base.coberturas(),
                List.of(new PedidoGeneradorDto.AbreTarde("Casa del zorrito", 2, "16:00", "17:00")));
        CronogramaDto c = generador.generar(p).cronograma();
        List<String> rueda = List.of("Entrada", "Canilla", "Minigolf", "Travesía", "Tobogán", "Carrera");
        // Desde las 14:00 (k = 6) hasta el cierre: nadie repite el mismo lugar dos medias horas seguidas.
        for (String puesto : rueda) {
            List<String> celdas = fila(c, puesto).celdas();
            for (int k = 7; k < celdas.size(); k++) {
                String antes = celdas.get(k - 1), ahora = celdas.get(k);
                if (ahora.isEmpty() || ahora.equals("Cerrado")) continue;
                assertThat(ahora).as(puesto + " a las " + c.horarios().get(k)).isNotEqualTo(antes);
            }
        }
    }

    @Test
    void unRefuerzoSeSumaALaGenteDeSiempreDelPuesto() {
        PedidoGeneradorDto base = pedido(13);
        List<PedidoGeneradorDto.Fijo> fijos = new java.util.ArrayList<>(base.fijos());
        fijos.add(new PedidoGeneradorDto.Fijo("Casa del zorrito", 1));
        PedidoGeneradorDto p = new PedidoGeneradorDto(base.nombre(), base.afluencia(), 13, base.desde(), base.hasta(), base.cada(),
                "12:00", "13:30", fijos, base.rueda(), base.coberturas(),
                List.of(new PedidoGeneradorDto.AbreTarde("Casa del zorrito", 2, "16:00", "17:00", TipoHorario.REFUERZO)));
        BorradorDto b = generador.generar(p);
        CronogramaDto c = b.cronograma();

        // Números: 1-4 fijos de antes, 5 fijo en la Casa del zorrito, 6-7 el refuerzo del evento.
        assertThat(b.avisos()).isEmpty();
        assertThat(c.puestos()).extracting(PuestoDto::nombre).containsOnlyOnce("Casa del zorrito");
        PuestoDto zorrito = fila(c, "Casa del zorrito");
        assertThat(zorrito.celdas().get(0)).isEqualTo("5");
        assertThat(zorrito.celdas().get(10)).isEqualTo("5-6-7");
        assertThat(zorrito.celdas().get(11)).isEqualTo("5-6-7");
        assertThat(zorrito.celdas().get(12)).isEqualTo("5");
        assertThat(lugar(c, 6, 0)).isNotEqualTo("Casa del zorrito").isNotNull();
    }

    @Test
    void conMenosGenteElQueSobraEnUnFijoPasaACubrirElParque() {
        PedidoGeneradorDto base = pedido(10);
        List<PedidoGeneradorDto.AbreTarde> menosUno = List.of(new PedidoGeneradorDto.AbreTarde("Cajero", 1, "15:00", null, TipoHorario.MENOS));

        // Con la rueda completa no hay hueco que cubrir: el 2 se queda en Cajero.
        PedidoGeneradorDto completa = new PedidoGeneradorDto(base.nombre(), base.afluencia(), 10, base.desde(), base.hasta(), base.cada(),
                "12:00", "13:30", base.fijos(), base.rueda(), base.coberturas(), menosUno);
        assertThat(fila(generador.generar(completa).cronograma(), "Cajero").celdas().subList(8, 15)).containsOnly("1-2");

        // Con un lugar que no se puede cerrar y nadie para él, el 2 sale a cubrirlo desde las 15:00 (k = 8).
        for (String puesto : List.of("Entrada", "Canilla", "Minigolf", "Travesía", "Tobogán", "Carrera")) {
            prioridades.cambiarPrioridad(new PuestoParqueDto(puesto, Prioridad.SIEMPRE));
        }
        PedidoGeneradorDto conHueco = new PedidoGeneradorDto(base.nombre(), base.afluencia(), 9, base.desde(), base.hasta(), base.cada(),
                "12:00", "13:30", base.fijos(), base.rueda(), base.coberturas(), menosUno);
        CronogramaDto c = generador.generar(conHueco).cronograma();
        PuestoDto cajero = fila(c, "Cajero");
        assertThat(cajero.celdas().subList(0, 2)).containsOnly("1-2");
        assertThat(cajero.celdas().subList(8, 15)).containsOnly("1");
        for (int k = 8; k < 15; k++) assertThat(lugar(c, 2, k)).as("k=" + k).isNotNull().isNotEqualTo("Cajero");

        // Si no queda nadie, no se puede: avisa y lo deja como estaba.
        PedidoGeneradorDto todos = new PedidoGeneradorDto(base.nombre(), base.afluencia(), 10, base.desde(), base.hasta(), base.cada(),
                "12:00", "13:30", base.fijos(), base.rueda(), base.coberturas(),
                List.of(new PedidoGeneradorDto.AbreTarde("Cajero", 2, "15:00", null, TipoHorario.MENOS)));
        BorradorDto mal = generador.generar(todos);
        assertThat(mal.avisos()).anyMatch(a -> a.contains("Cajero") && a.contains("más de 2"));
        assertThat(fila(mal.cronograma(), "Cajero").celdas().get(10)).isEqualTo("1-2");
    }

    @Test
    void unFijoDeUnaPersonaNoQuedaVacioMientrasDescansa() {
        prioridades.cambiarPrioridad(new PuestoParqueDto("Carrera", Prioridad.BAJA));
        CronogramaDto c = generador.generar(pedido(10)).cronograma();
        // Encargado y Baños son de una persona: en su descanso (13:00, k = 4) los cubre alguien de la rueda.
        // (A las 12 y 12:30 pueden estar vacíos a propósito: están cubriendo el almuerzo de los que rotan.)
        for (String fijo : List.of("Encargado", "Cajero")) {
            assertThat(fila(c, fijo).celdas().get(4)).as(fijo).isNotEmpty();
        }
        // Sale del lugar de menor prioridad (Carrera, baja). Baños es media como el resto de la rueda: sacar a
        // alguien dejaría vacío algo igual de importante, así que queda como está.
        assertThat(fila(c, "Carrera").celdas().get(4)).isEmpty();
        assertThat(fila(c, "Baños").celdas().get(4)).isEmpty();
    }

    @Test
    void laSegundaCajeraCubreElFijoDeUnaPersonaYPasaAFotos() {
        PedidoGeneradorDto base = pedido(10);
        List<PedidoGeneradorDto.Fijo> fijos = List.of(new PedidoGeneradorDto.Fijo("Cajero", 2), new PedidoGeneradorDto.Fijo("Casa del zorrito", 1));
        PedidoGeneradorDto p = new PedidoGeneradorDto(base.nombre(), base.afluencia(), 10, base.desde(), base.hasta(), base.cada(),
                "12:00", "13:30", fijos, base.rueda(), List.of(),
                List.of(new PedidoGeneradorDto.AbreTarde("Cajero", 1, "16:00", null, TipoHorario.MENOS),
                        new PedidoGeneradorDto.AbreTarde("Fotos", 1, "17:00", null, TipoHorario.ABRE_TARDE)));
        CronogramaDto c = generador.generar(p).cronograma();

        // Números: 1-2 Cajero, 3 Casa del zorrito, 4 Fotos. El 3 descansa cuando están las dos cajeras, y lo
        // cubre una de ellas.
        Map<Integer, Integer> cuando = cuandoDescansa(fila(c, "Descanso"));
        int k = cuando.get(3);
        assertThat(fila(c, "Casa del zorrito").celdas().get(k)).isIn("1", "2");
        assertThat(fila(c, "Cajero").celdas()).allMatch(cel -> !cel.isEmpty());

        // Desde las 17:00 (k = 12) Fotos la hace la que sobra en Cajero; el 4 queda en el parque.
        for (int h = 12; h < 15; h++) {
            assertThat(fila(c, "Fotos").celdas().get(h)).as("k=" + h).isEqualTo("2");
            assertThat(fila(c, "Cajero").celdas().get(h)).isEqualTo("1");
            assertThat(lugar(c, 4, h)).isNotNull().isNotEqualTo("Fotos");
        }
    }

    @Test
    void siFaltaGenteCierraLoDeMenorPrioridad() {
        prioridades.cambiarPrioridad(new PuestoParqueDto("Carrera", Prioridad.BAJA));
        BorradorDto b = generador.generar(pedido(9));

        assertThat(fila(b.cronograma(), "Carrera").celdas()).containsOnly("Cerrado");
        assertThat(b.avisos()).singleElement().asString().contains("Carrera").contains("cerrado");
        assertThat(b.cronograma().empleados()).isEqualTo(9);
    }

    @Test
    void loQueTieneQueEstarAbiertoSiempreNoSeCierra() {
        prioridades.cambiarPrioridad(new PuestoParqueDto("Carrera", Prioridad.SIEMPRE));
        BorradorDto b = generador.generar(pedido(9));
        assertThat(fila(b.cronograma(), "Carrera").celdas()).doesNotContain("Cerrado");
        assertThat(b.avisos()).singleElement().asString().contains("queda cerrado").doesNotContain("Carrera");

        // Si ni siquiera alcanza para los de "siempre", no se cierra ninguno y se avisa.
        for (String p : List.of("Entrada", "Canilla", "Minigolf", "Travesía", "Tobogán")) {
            prioridades.cambiarPrioridad(new PuestoParqueDto(p, Prioridad.SIEMPRE));
        }
        BorradorDto todos = generador.generar(pedido(9));
        assertThat(todos.cronograma().puestos()).allMatch(p -> !p.celdas().contains("Cerrado"));
        assertThat(todos.avisos()).anyMatch(a -> a.contains("abiertos siempre") && a.contains("rotan 5 para 6 lugares"));
    }

    @Test
    void noPuedeHaberMasFijosQueEmpleados() {
        assertThatThrownBy(() -> generador.generar(pedido(3))).isInstanceOf(ValidacionException.class);
    }

    @Test
    void conReglasElAlmuerzoVaEnTandasConLosPuestosDeLaRegla() {
        // Como la planilla del parque: 14 generales + 2 de Tirolesa, y con 9 trabajando se abren estos.
        reglas.guardar(new ReglaAperturaDto(null, 9, Afluencia.ALTA, List.of(new PedidoGeneradorDto.Fijo("Cajero", 2)),
                List.of(List.of("Entrada"), List.of("Minigolf"), List.of("Tobogán"), List.of("Carrera"),
                        List.of("Travesía", "Búsqueda", "Laberinto"), List.of("Canilla"), List.of("Casa del zorrito"))));
        List<PedidoGeneradorDto.Fijo> fijos = java.util.stream.Stream.of("Casa del zorrito", "Flotante de minigolf", "Minigolf",
                "Travesía", "Búsqueda", "Laberinto", "Carrera").map(f -> new PedidoGeneradorDto.Fijo(f, 1)).collect(Collectors.toList());
        fijos.add(0, new PedidoGeneradorDto.Fijo("Cajero", 2));
        List<PedidoGeneradorDto.Lugar> rueda = java.util.stream.Stream.of("Ayudante Tobogán", "Baños", "Entrada", "Canilla", "Tobogán")
                .map(l -> new PedidoGeneradorDto.Lugar(List.of(l), false)).toList();
        BorradorDto b = generador.generar(new PedidoGeneradorDto("Plan", Afluencia.ALTA, 16, "11:00", "18:00", 30, "12:00", "13:30",
                fijos, rueda, List.of(), List.of(new PedidoGeneradorDto.AbreTarde("Tirolesa", 2, "13:00"))));
        CronogramaDto c = b.cronograma();
        Map<Integer, Integer> cuando = cuandoDescansa(fila(c, "Descanso"));

        // Dos tandas parejas a las 12:00 y 12:30; las cajeras (1 y 2) a las 13:00 y 13:30; la Tirolesa antes de abrir.
        assertThat(cuando.values().stream().filter(k -> k == 2).count()).isEqualTo(7);
        assertThat(cuando.values().stream().filter(k -> k == 3).count()).isEqualTo(7);
        assertThat(List.of(cuando.get(1), cuando.get(2))).containsExactlyInAnyOrder(4, 5);
        assertThat(cuando.get(10)).isLessThan(4);
        assertThat(cuando.get(11)).isLessThan(4);

        // En las tandas quedan los de la regla (Travesía, Búsqueda y Laberinto con una persona) y el resto cierra.
        for (int k : List.of(2, 3)) {
            for (String abierto : List.of("Cajero", "Casa del zorrito", "Minigolf", "Carrera", "Entrada", "Canilla", "Tobogán", "Travesía")) {
                assertThat(fila(c, abierto).celdas().get(k)).as(abierto + " " + k).isNotEmpty().isNotEqualTo("Cerrado");
            }
            assertThat(fila(c, "Travesía").celdas().get(k)).isEqualTo(fila(c, "Laberinto").celdas().get(k));
            for (String cerrado : List.of("Flotante de minigolf", "Baños", "Ayudante Tobogán")) {
                assertThat(fila(c, cerrado).celdas().get(k)).as(cerrado + " " + k).isEqualTo("Cerrado");
            }
        }
        assertThat(fila(c, "Cajero").celdas().get(2)).isEqualTo("1-2");
        assertThat(fila(c, "Tirolesa").celdas().get(6)).isEqualTo("10-11");
    }

    @Test
    void enLasTandasLasDosCajerasSeQuedanAunqueLaReglaDigaUna() {
        // Con 9 y poca gente la regla tiene una sola cajera; si vinieron las dos, quedan las dos y se cierra lo último.
        reglas.guardar(new ReglaAperturaDto(null, 9, Afluencia.BAJA, List.of(new PedidoGeneradorDto.Fijo("Cajero", 1)),
                List.of(List.of("Entrada"), List.of("Minigolf"), List.of("Tobogán"), List.of("Carrera"),
                        List.of("Travesía", "Búsqueda", "Laberinto"), List.of("Canilla"), List.of("Casa del zorrito"),
                        List.of("Flotante de minigolf"))));
        List<PedidoGeneradorDto.Fijo> fijos = java.util.stream.Stream.of("Casa del zorrito", "Flotante de minigolf", "Minigolf",
                "Travesía", "Búsqueda", "Laberinto", "Carrera").map(f -> new PedidoGeneradorDto.Fijo(f, 1)).collect(Collectors.toList());
        fijos.add(0, new PedidoGeneradorDto.Fijo("Cajero", 2));
        List<PedidoGeneradorDto.Lugar> rueda = java.util.stream.Stream.of("Pecera", "Baños", "Entrada", "Canilla", "Tobogán")
                .map(l -> new PedidoGeneradorDto.Lugar(List.of(l), false)).toList();
        CronogramaDto c = generador.generar(new PedidoGeneradorDto("Plan", Afluencia.BAJA, 16, "11:00", "18:00", 30, "12:00", "13:30",
                fijos, rueda, List.of(), List.of(new PedidoGeneradorDto.AbreTarde("Tirolesa", 2, "13:00")))).cronograma();

        assertThat(fila(c, "Cajero").celdas().get(2)).isEqualTo("1-2");
        assertThat(fila(c, "Cajero").celdas().get(3)).isEqualTo("1-2");
        assertThat(fila(c, "Flotante de minigolf").celdas().get(2)).isEqualTo("Cerrado");
    }

    @Test
    void enLasTandasBusquedaLaHacenElDeTraviesaYElDeLaberinto() {
        // Búsqueda está en dos lugares: mitad con Travesía y mitad con Laberinto. En el almuerzo también.
        List<List<String>> lugares = List.of(List.of("Entrada"), List.of("Minigolf"), List.of("Travesía", "Búsqueda"),
                List.of("Laberinto", "Búsqueda"), List.of("Tobogán"), List.of("Carrera"));
        reglas.guardar(new ReglaAperturaDto(null, 6, Afluencia.ALTA, List.of(new PedidoGeneradorDto.Fijo("Cajero", 2)), lugares));
        List<PedidoGeneradorDto.Lugar> rueda = lugares.stream().map(l -> new PedidoGeneradorDto.Lugar(l, false)).toList();
        CronogramaDto c = generador.generar(new PedidoGeneradorDto("Plan", Afluencia.ALTA, 16, "11:00", "18:00", 30, "12:00", "13:30",
                List.of(new PedidoGeneradorDto.Fijo("Cajero", 2)), rueda, List.of(), List.of())).cronograma();

        for (int k : List.of(2, 3)) {
            List<Integer> busqueda = PlanRotacion.empleados(fila(c, "Búsqueda").celdas().get(k));
            assertThat(busqueda).as("Búsqueda " + k).hasSize(2)
                    .containsAll(PlanRotacion.empleados(fila(c, "Travesía").celdas().get(k)))
                    .containsAll(PlanRotacion.empleados(fila(c, "Laberinto").celdas().get(k)));
        }
    }

    private static PedidoGeneradorDto pedido(int empleados) {
        List<PedidoGeneradorDto.Lugar> rueda = RUEDA.stream()
                .map(l -> new PedidoGeneradorDto.Lugar(List.of(l.split("\\+")), l.startsWith("Travesía")))
                .toList();
        return new PedidoGeneradorDto("Prueba", Afluencia.MEDIA, empleados, "11:00", "18:00", 30, "12:00", "13:00",
                List.of(new PedidoGeneradorDto.Fijo("Cajero", 2), new PedidoGeneradorDto.Fijo("Encargado", 1),
                        new PedidoGeneradorDto.Fijo("Baños", 1)),
                rueda,
                List.of(new PedidoGeneradorDto.Cobertura("Baños", "Minigolf"), new PedidoGeneradorDto.Cobertura("Encargado", "Travesía")));
    }

    private static PuestoDto fila(CronogramaDto c, String nombre) {
        return c.puestos().stream().filter(p -> p.nombre().equals(nombre)).findFirst().orElseThrow();
    }

    /** El primer puesto de la rueda en que está el número n en el horario k. */
    private static String lugar(CronogramaDto c, int n, int k) {
        return c.puestos().stream()
                .filter(p -> PlanRotacion.empleados(p.celdas().get(k)).contains(n))
                .map(PuestoDto::nombre).findFirst().orElse(null);
    }

    private static Map<Integer, Integer> cuandoDescansa(PuestoDto descanso) {
        return java.util.stream.IntStream.range(0, descanso.celdas().size()).boxed()
                .flatMap(k -> PlanRotacion.empleados(descanso.celdas().get(k)).stream().map(n -> Map.entry(n, k)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
