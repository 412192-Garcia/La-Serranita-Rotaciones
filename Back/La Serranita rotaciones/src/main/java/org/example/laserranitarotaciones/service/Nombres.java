package org.example.laserranitarotaciones.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Para comparar nombres de puestos sin que importen mayúsculas, acentos ni espacios de más. */
public final class Nombres {

    private static final Pattern CON_NOTA = Pattern.compile("^(.*?)\\s*\\((.+)\\)$");

    private Nombres() {
    }

    public static String clave(String nombre) {
        if (nombre == null) return "";
        String sinAcentos = Normalizer.normalize(nombre, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinAcentos.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** Lo escrito en la celda del puesto: "*Canilla (consultar al encargado)". */
    public record NombrePuesto(String nombre, String nota, boolean iniciaRotacion) {
    }

    /** "*Canilla (consultar al encargado)" → Canilla, nota "consultar al encargado", inicia la rotación. */
    public static NombrePuesto separar(String texto) {
        String t = texto == null ? "" : texto.trim();
        boolean inicia = t.startsWith("*");
        String[] nombreYNota = separarNota(t);
        return new NombrePuesto(nombreYNota[0], nombreYNota[1], inicia);
    }

    /** "*Canilla (consultar al encargado)" → ["Canilla", "consultar al encargado"]. Sin nota, la 2ª es null. */
    public static String[] separarNota(String texto) {
        String t = texto == null ? "" : texto.trim().replaceFirst("^\\*+\\s*", "");
        Matcher m = CON_NOTA.matcher(t);
        if (m.matches() && !m.group(1).isBlank()) return new String[]{m.group(1).trim(), m.group(2).trim()};
        return new String[]{t, null};
    }

    public static boolean esDescanso(String nombre) {
        return clave(nombre).equals("descanso");
    }

    /** La fila de los que sobran en ese horario: están disponibles, sin puesto. */
    public static final String LIBRE = "Libre";

    public static boolean esLibre(String nombre) {
        return clave(nombre).equals("libre");
    }

    /** Descanso y Libre son filas del cronograma pero no puestos (nadie tiene que saberlos). */
    public static boolean noEsPuesto(String nombre) {
        return esDescanso(nombre) || esLibre(nombre);
    }
}
