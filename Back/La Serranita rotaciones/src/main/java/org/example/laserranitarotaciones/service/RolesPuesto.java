package org.example.laserranitarotaciones.service;

import org.example.laserranitarotaciones.model.PlanRotacion;
import org.example.laserranitarotaciones.model.dto.FaltanteRolDto;
import org.example.laserranitarotaciones.model.dto.PuestoDto;
import org.example.laserranitarotaciones.model.dto.RenglonDto;
import org.example.laserranitarotaciones.model.dto.TarjetaDto;
import org.example.laserranitarotaciones.model.entity.Prioridad;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Los roles de un puesto (Show del zorrito: Zorrito y Presentador; el resto, Ayudantes). Cada rol lo hace una
 * persona preparada para él; con la gente ya repartida se elige quién hace cada rol en cada horario. Si en el
 * puesto no hay nadie preparado, se trae a alguien de Libre o se lo cambia por uno del puesto que sepa lo que él
 * hacía. Si tampoco, queda como faltante: se elige a mano quién lo cubre.
 */
final class RolesPuesto {

    private RolesPuesto() {
    }

    /** Los roles de un puesto, en orden, y cómo se llama el resto (puede ser null). */
    record Config(String puesto, List<String> roles, String resto) {
    }

    /** Cómo se busca un rol sabido: "clave del puesto|clave del rol". */
    static String clave(String puesto, String rol) {
        return Nombres.clave(puesto) + "|" + Nombres.clave(rol);
    }

    // --- Guardado en la base: "Zorrito=3;Presentador=5"

    static String guardar(Map<String, Integer> roles) {
        if (roles == null) return "";
        return roles.entrySet().stream().filter(e -> e.getValue() != null)
                .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(";"));
    }

    static Map<String, Integer> leer(String guardado) {
        Map<String, Integer> roles = new LinkedHashMap<>();
        if (guardado == null || guardado.isBlank()) return roles;
        for (String parte : guardado.split(";")) {
            int i = parte.lastIndexOf('=');
            if (i <= 0) continue;
            try {
                roles.put(parte.substring(0, i), Integer.parseInt(parte.substring(i + 1).trim()));
            } catch (NumberFormatException ignorado) {
                // un valor roto no rompe el cronograma
            }
        }
        return roles;
    }

    /** Los roles de una fila, uno por horario, dejando sólo los números que siguen en la celda. */
    static List<Map<String, Integer>> limpiar(List<Map<String, Integer>> roles, List<String> celdas) {
        if (roles == null) return null;
        List<Map<String, Integer>> limpios = new ArrayList<>();
        boolean alguno = false;
        for (int k = 0; k < celdas.size(); k++) {
            List<Integer> nums = numeros(celdas.get(k));
            Map<String, Integer> m = new LinkedHashMap<>();
            if (k < roles.size() && roles.get(k) != null) {
                roles.get(k).forEach((rol, n) -> {
                    if (rol != null && !rol.isBlank() && n != null && nums.contains(n) && !m.containsValue(n)) m.put(rol.trim(), n);
                });
            }
            alguno |= !m.isEmpty();
            limpios.add(m);
        }
        return alguno ? limpios : null;
    }

    // --- Reparto

    /**
     * Elige quién hace cada rol en cada horario. porNumero: quién hace cada número; sabeRol: id del empleado →
     * claves de los roles que sabe (clave()). Puede mover gente: alguien de Libre, o un cambio con otro puesto.
     */
    static List<PuestoDto> asignar(List<PuestoDto> puestos, Map<Integer, AsignadorService.Presente> porNumero,
                                   Map<String, Config> configs, Map<Long, Set<String>> sabeRol, Map<String, Integer> pesos) {
        List<List<String>> celdas = new ArrayList<>();
        puestos.forEach(p -> celdas.add(new ArrayList<>(p.celdas())));
        List<List<Map<String, Integer>>> rolesPorFila = new ArrayList<>();
        for (int i = 0; i < puestos.size(); i++) rolesPorFila.add(null);

        for (int i = 0; i < puestos.size(); i++) {
            Config c = configs.get(Nombres.clave(puestos.get(i).nombre()));
            if (c == null || c.roles().isEmpty()) continue;
            List<Map<String, Integer>> roles = new ArrayList<>();
            Map<String, Integer> anterior = Map.of();
            for (int k = 0; k < celdas.get(i).size(); k++) {
                List<Integer> nums = numeros(celdas.get(i).get(k));
                if (nums.isEmpty()) {
                    roles.add(new LinkedHashMap<>());
                    anterior = Map.of();
                    continue;
                }
                Map<String, Integer> elegido = mejor(c, nums, porNumero, sabeRol, anterior);
                for (String rol : c.roles()) {
                    if (elegido.containsKey(rol)) continue;
                    Integer traido = traer(puestos, celdas, i, k, c, rol, elegido, porNumero, sabeRol, pesos);
                    if (traido != null) elegido.put(rol, traido);
                }
                Map<String, Integer> enOrden = new LinkedHashMap<>();
                for (String rol : c.roles()) if (elegido.containsKey(rol)) enOrden.put(rol, elegido.get(rol));
                roles.add(enOrden);
                anterior = enOrden;
            }
            rolesPorFila.set(i, roles);
        }

        List<PuestoDto> resultado = new ArrayList<>();
        for (int i = 0; i < puestos.size(); i++) {
            PuestoDto p = puestos.get(i);
            List<Map<String, Integer>> roles = rolesPorFila.get(i) != null ? rolesPorFila.get(i) : p.roles();
            resultado.add(new PuestoDto(p.nombre(), p.nota(), p.iniciaRotacion(), celdas.get(i), limpiar(roles, celdas.get(i))));
        }
        return resultado;
    }

    /** Entre los que están en la celda, el reparto de roles que más llena; a igualdad, el que repite al anterior. */
    private static Map<String, Integer> mejor(Config c, List<Integer> nums, Map<Integer, AsignadorService.Presente> porNumero,
                                              Map<Long, Set<String>> sabeRol, Map<String, Integer> anterior) {
        Map<String, Integer> mejor = new LinkedHashMap<>();
        int[] mejorPuntos = {-1};
        probar(c, 0, nums, porNumero, sabeRol, anterior, new LinkedHashMap<>(), 0, mejor, mejorPuntos);
        return mejor;
    }

    private static void probar(Config c, int r, List<Integer> nums, Map<Integer, AsignadorService.Presente> porNumero,
                               Map<Long, Set<String>> sabeRol, Map<String, Integer> anterior, Map<String, Integer> actual,
                               int puntos, Map<String, Integer> mejor, int[] mejorPuntos) {
        if (r == c.roles().size()) {
            if (puntos > mejorPuntos[0]) {
                mejorPuntos[0] = puntos;
                mejor.clear();
                mejor.putAll(actual);
            }
            return;
        }
        String rol = c.roles().get(r);
        for (int n : nums) {
            if (actual.containsValue(n) || !puede(porNumero.get(n), c, rol, sabeRol)) continue;
            actual.put(rol, n);
            probar(c, r + 1, nums, porNumero, sabeRol, anterior, actual,
                    puntos + 10 + (Integer.valueOf(n).equals(anterior.get(rol)) ? 1 : 0), mejor, mejorPuntos);
            actual.remove(rol);
        }
        probar(c, r + 1, nums, porNumero, sabeRol, anterior, actual, puntos, mejor, mejorPuntos);
    }

    static boolean puede(AsignadorService.Presente p, Config c, String rol, Map<Long, Set<String>> sabeRol) {
        return p != null && p.hoy().contains(Nombres.clave(c.puesto()))
                && sabeRol.getOrDefault(p.id(), Set.of()).contains(clave(c.puesto(), rol));
    }

    /**
     * Trae a la celda (fila i, horario k) a alguien preparado para el rol: primero de Libre; si no, de otro puesto,
     * a cambio de uno del puesto sin rol que sepa lo que el otro hacía; o, si nadie del puesto lo sabe, lo cubre
     * uno de Libre que sí (y uno del puesto sin rol pasa a Libre). Sacar a alguien de su estrella, sólo si no hay
     * otra; nunca del almuerzo. Devuelve el número traído o null.
     */
    private static Integer traer(List<PuestoDto> puestos, List<List<String>> celdas, int i, int k, Config c, String rol,
                                 Map<String, Integer> elegido, Map<Integer, AsignadorService.Presente> porNumero,
                                 Map<Long, Set<String>> sabeRol, Map<String, Integer> pesos) {
        List<Integer> aca = numeros(celdas.get(i).get(k));
        List<Integer> sinRol = aca.stream().filter(n -> !elegido.containsValue(n)).toList();
        int mejorFila = -1;
        int mejorX = -1;
        int mejorY = -1;
        int mejorCosto = Integer.MAX_VALUE;
        int mejorZ = -1; // de Libre, cubre lo que deja el traído
        int filaLibre = -1;
        for (int j = 0; j < puestos.size(); j++) if (Nombres.esLibre(puestos.get(j).nombre()) && k < celdas.get(j).size()) filaLibre = j;
        for (int j = 0; j < puestos.size(); j++) {
            if (j == i) continue;
            String nombre = puestos.get(j).nombre();
            if (Nombres.esDescanso(nombre) || k >= celdas.get(j).size()) continue;
            boolean libre = Nombres.esLibre(nombre);
            String claveJ = Nombres.clave(nombre);
            for (int x : numeros(celdas.get(j).get(k))) {
                AsignadorService.Presente px = porNumero.get(x);
                if (aca.contains(x) || !puede(px, c, rol, sabeRol)) continue;
                if (libre) {
                    int y = sinRol.isEmpty() ? -1 : sinRol.get(0);
                    if (mejorCosto > 0) {
                        mejorCosto = 0;
                        mejorFila = j;
                        mejorX = x;
                        mejorY = y;
                        mejorZ = -1;
                    }
                    continue;
                }
                // Sacarlo de su estrella es lo último (la de la Casa del zorrito presenta el show y la cajera la cubre).
                int costo = pesos.getOrDefault(claveJ, Prioridad.MEDIA.peso()) + (px.principales().contains(claveJ) ? 10_000 : 0);
                if (costo >= mejorCosto) continue;
                boolean hallado = false;
                for (int y : sinRol) {
                    AsignadorService.Presente py = porNumero.get(y);
                    if (py == null || !py.sabe().contains(claveJ) || !py.hoy().contains(claveJ)) continue;
                    mejorCosto = costo;
                    mejorFila = j;
                    mejorX = x;
                    mejorY = y;
                    mejorZ = -1;
                    hallado = true;
                    break;
                }
                if (hallado || filaLibre < 0 || costo + 1 >= mejorCosto) continue;
                // Nadie del puesto sabe lo que él hacía: lo cubre uno de Libre que sí (la otra cajera en la Casa).
                for (int z : numeros(celdas.get(filaLibre).get(k))) {
                    AsignadorService.Presente pz = porNumero.get(z);
                    if (pz == null || !pz.sabe().contains(claveJ) || !pz.hoy().contains(claveJ)) continue;
                    mejorCosto = costo + 1;
                    mejorFila = j;
                    mejorX = x;
                    mejorY = sinRol.isEmpty() ? -1 : sinRol.get(0);
                    mejorZ = z;
                    break;
                }
            }
        }
        if (mejorFila < 0) return null;
        if (mejorZ < 0) {
            mover(celdas, mejorFila, i, k, mejorX, mejorY);
            return mejorX;
        }
        // x deja su puesto y va al de roles; z sale de Libre y cubre el puesto de x; y (sin rol) pasa a Libre.
        mover(celdas, mejorFila, i, k, mejorX, -1);
        mover(celdas, filaLibre, mejorFila, k, mejorZ, -1);
        if (mejorY >= 0) mover(celdas, i, filaLibre, k, mejorY, -1);
        return mejorX;
    }

    /** x pasa de la fila desde a la fila hasta; y (si no es -1) hace el camino contrario. */
    private static void mover(List<List<String>> celdas, int desde, int hasta, int k, int x, int y) {
        List<Integer> origen = new ArrayList<>(numeros(celdas.get(desde).get(k)));
        List<Integer> destino = new ArrayList<>(numeros(celdas.get(hasta).get(k)));
        origen.remove(Integer.valueOf(x));
        destino.add(x);
        if (y >= 0) {
            destino.remove(Integer.valueOf(y));
            origen.add(y);
        }
        celdas.get(desde).set(k, texto(origen));
        celdas.get(hasta).set(k, texto(destino));
    }

    // --- Faltantes y cubrir a mano

    /** Los roles que quedaron sin nadie, con los horarios en que el puesto está abierto. */
    static List<FaltanteRolDto> faltantes(List<PuestoDto> puestos, Map<String, Config> configs, List<String> horarios) {
        List<FaltanteRolDto> faltantes = new ArrayList<>();
        for (PuestoDto p : puestos) {
            Config c = configs.get(Nombres.clave(p.nombre()));
            if (c == null) continue;
            for (String rol : c.roles()) {
                List<Integer> ks = new ArrayList<>();
                for (int k = 0; k < p.celdas().size(); k++) {
                    List<Integer> nums = numeros(p.celdas().get(k));
                    if (nums.isEmpty()) continue;
                    Integer quien = p.roles() == null || k >= p.roles().size() || p.roles().get(k) == null ? null : p.roles().get(k).get(rol);
                    if (quien == null || !nums.contains(quien)) ks.add(k);
                }
                if (!ks.isEmpty()) faltantes.add(new FaltanteRolDto(p.nombre(), rol, ks, ks.stream().map(horarios::get).toList()));
            }
        }
        return faltantes;
    }

    static String aviso(FaltanteRolDto f) {
        return "Nadie de los que vinieron puede ser " + f.rol() + " en " + f.puesto() + " (" + RotacionService.enumerar(f.horas()) + ").";
    }

    /**
     * El empleado del número elegido hace el rol en esas celdas. Si estaba en otro puesto, se cambia con uno del
     * puesto sin rol que sepa lo que él hacía; si no hay, sólo se pasa (salvo que su puesto quede vacío).
     */
    static List<PuestoDto> cubrir(List<PuestoDto> puestos, Config c, String rol, List<Integer> ks, int numero,
                                  String nombre, Map<Integer, AsignadorService.Presente> porNumero, List<String> horarios) {
        int i = -1;
        for (int j = 0; j < puestos.size(); j++) if (Nombres.clave(puestos.get(j).nombre()).equals(Nombres.clave(c.puesto()))) i = j;
        if (i < 0) throw new ValidacionException(c.puesto() + " no está en el cronograma.");
        String claveRol = Nombres.clave(rol);
        String rolExacto = c.roles().stream().filter(r -> Nombres.clave(r).equals(claveRol)).findFirst()
                .orElseThrow(() -> new ValidacionException(c.puesto() + " no tiene el rol " + rol + "."));

        List<List<String>> celdas = new ArrayList<>();
        puestos.forEach(p -> celdas.add(new ArrayList<>(p.celdas())));
        PuestoDto fila = puestos.get(i);
        List<Map<String, Integer>> roles = new ArrayList<>();
        for (int k = 0; k < fila.celdas().size(); k++) {
            Map<String, Integer> m = fila.roles() != null && k < fila.roles().size() && fila.roles().get(k) != null
                    ? new LinkedHashMap<>(fila.roles().get(k)) : new LinkedHashMap<>();
            roles.add(m);
        }
        for (int k : ks == null ? List.<Integer>of() : ks) {
            if (k < 0 || k >= fila.celdas().size()) continue;
            Map<String, Integer> m = roles.get(k);
            List<Integer> aca = numeros(celdas.get(i).get(k));
            if (!aca.contains(numero)) {
                int desde = -1;
                for (int j = 0; j < puestos.size(); j++) {
                    if (j != i && k < celdas.get(j).size() && numeros(celdas.get(j).get(k)).contains(numero)) desde = j;
                }
                if (desde >= 0 && Nombres.esDescanso(puestos.get(desde).nombre())) {
                    throw new ValidacionException(nombre + " está almorzando a las " + horarios.get(k) + ".");
                }
                String claveDesde = desde < 0 ? null : Nombres.clave(puestos.get(desde).nombre());
                List<Integer> sinRol = aca.stream().filter(n -> !m.containsValue(n)).toList();
                int y = -1;
                if (desde >= 0) {
                    // Si nadie del puesto sabe lo que él hacía y ahí queda otro, sólo se pasa: no se manda a nadie a
                    // algo que no sabe. Si se quedaría vacío, va igual el primero sin rol.
                    boolean quedaOtro = numeros(celdas.get(desde).get(k)).size() > 1;
                    y = sinRol.stream().filter(n -> {
                        AsignadorService.Presente p = porNumero.get(n);
                        return p != null && p.sabe().contains(claveDesde) && p.hoy().contains(claveDesde);
                    }).findFirst().orElse(sinRol.isEmpty() || quedaOtro || Nombres.esLibre(puestos.get(desde).nombre()) ? -1 : sinRol.get(0));
                    mover(celdas, desde, i, k, numero, y);
                } else {
                    List<Integer> nuevos = new ArrayList<>(aca);
                    nuevos.add(numero);
                    celdas.get(i).set(k, texto(nuevos));
                }
            }
            m.values().removeIf(n -> n == numero);
            m.put(rolExacto, numero);
            Map<String, Integer> enOrden = new LinkedHashMap<>();
            for (String r : c.roles()) if (m.containsKey(r)) enOrden.put(r, m.get(r));
            roles.set(k, enOrden);
        }

        List<PuestoDto> resultado = new ArrayList<>();
        for (int j = 0; j < puestos.size(); j++) {
            PuestoDto p = puestos.get(j);
            List<Map<String, Integer>> r = j == i ? roles : p.roles();
            resultado.add(new PuestoDto(p.nombre(), p.nota(), p.iniciaRotacion(), celdas.get(j), limpiar(r, celdas.get(j))));
        }
        return resultado;
    }

    // --- Tarjetas

    static List<TarjetaDto> conRoles(List<TarjetaDto> tarjetas, List<PuestoDto> puestos, List<String> horarios) {
        return conRoles(tarjetas, puestos, horarios, Map.of());
    }

    /**
     * Quién hace qué en una celda: los que tienen rol, con el suyo; los demás, en orden, primero los roles que
     * faltan (con dos en el Show: Zorrito y Presentador, aunque el segundo no esté preparado) y después el resto
     * (Ayudante). Sin rol si no hay más nombres.
     */
    static Map<Integer, String> rolesDeCelda(Config c, Map<String, Integer> asignados, String celda) {
        Map<Integer, String> roles = new LinkedHashMap<>();
        List<Integer> nums = numeros(celda);
        if (asignados != null) asignados.forEach((rol, n) -> {
            if (n != null && nums.contains(n)) roles.put(n, rol);
        });
        List<String> faltan = new ArrayList<>(c.roles().stream().filter(r -> !roles.containsValue(r)).toList());
        for (int n : nums) {
            if (roles.containsKey(n)) continue;
            if (!faltan.isEmpty()) roles.put(n, faltan.remove(0));
            else if (c.resto() != null && !c.resto().isBlank()) roles.put(n, c.resto());
        }
        return roles;
    }

    /**
     * En la tarjeta, al lado del puesto va el rol: "Show del zorrito (Zorrito)". configs: clave del puesto → sus
     * roles; los de la celda sin rol llevan el que falta o el del resto (rolesDeCelda).
     */
    static List<TarjetaDto> conRoles(List<TarjetaDto> tarjetas, List<PuestoDto> puestos, List<String> horarios,
                                     Map<String, Config> configs) {
        if (tarjetas == null) return null;
        Map<Integer, List<String[]>> marcas = new HashMap<>(); // número → (hora, puesto, rol) a marcar
        for (PuestoDto p : puestos) {
            Config c = configs.get(Nombres.clave(p.nombre()));
            if (c == null && p.roles() == null) continue;
            for (int k = 0; k < p.celdas().size() && k < horarios.size(); k++) {
                Map<String, Integer> m = p.roles() == null || k >= p.roles().size() ? null : p.roles().get(k);
                // Sin configuración (un cronograma viejo): sólo los roles que ya tiene.
                Config este = c != null ? c : new Config(p.nombre(), m == null ? List.of() : List.copyOf(m.keySet()), null);
                String hora = horarios.get(k);
                String nombre = p.nombre();
                rolesDeCelda(este, m, p.celdas().get(k))
                        .forEach((n, rol) -> marcas.computeIfAbsent(n, x -> new ArrayList<>()).add(new String[]{hora, nombre, rol}));
            }
        }
        if (marcas.isEmpty()) return tarjetas;
        List<TarjetaDto> resultado = new ArrayList<>();
        for (TarjetaDto t : tarjetas) {
            List<String[]> deEste = marcas.get(t.empleado());
            if (deEste == null) {
                resultado.add(t);
                continue;
            }
            List<RenglonDto> renglones = new ArrayList<>();
            for (RenglonDto r : t.renglones()) {
                String texto = r.texto();
                for (String[] m : deEste) {
                    if (!m[0].equals(r.hora())) continue;
                    int at = texto.lastIndexOf(m[1]);
                    if (at < 0 || texto.startsWith(" (", at + m[1].length())) continue;
                    texto = texto.substring(0, at + m[1].length()) + " (" + m[2] + ")" + texto.substring(at + m[1].length());
                }
                renglones.add(new RenglonDto(r.hora(), texto));
            }
            resultado.add(new TarjetaDto(t.empleado(), t.descanso(), renglones));
        }
        return resultado;
    }

    // --- Celdas

    private static List<Integer> numeros(String celda) {
        return PlanRotacion.esCerrado(celda) ? List.of() : PlanRotacion.empleados(celda);
    }

    private static String texto(List<Integer> nums) {
        return nums.stream().map(String::valueOf).collect(Collectors.joining("-"));
    }
}
