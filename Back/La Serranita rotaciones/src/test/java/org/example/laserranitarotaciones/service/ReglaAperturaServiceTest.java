package org.example.laserranitarotaciones.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.laserranitarotaciones.model.dto.PedidoGeneradorDto;
import org.example.laserranitarotaciones.model.dto.ReglaAperturaDto;
import org.example.laserranitarotaciones.model.entity.Afluencia;
import org.example.laserranitarotaciones.repository.ReglaAperturaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ReglaAperturaServiceTest {

    @Autowired
    private ReglaAperturaService reglas;
    @Autowired
    private ReglaAperturaRepository repositorio;
    @Autowired
    private CronogramaService cronogramas;

    @BeforeEach
    void limpiar() {
        repositorio.deleteAll();
        reglas.guardarOrdenRotacion(List.of());
    }

    @Test
    void elOrdenDeRotacionReordenaLaRuedaDeTodasLasReglas() throws Exception {
        reglas.importar(excel());
        // Sin elegir, el de las planillas.
        assertThat(reglas.ordenRotacion()).startsWith("Carrera");

        reglas.guardarOrdenRotacion(List.of("Tobogán", "Minigolf", "Entrada", "Carrera"));
        assertThat(reglas.ordenRotacion()).containsExactly("Tobogán", "Minigolf", "Entrada", "Carrera");
        // La de 6 sigue arrancando en Travesía, Búsqueda y Laberinto; el resto, en el orden elegido.
        assertThat(regla(6, Afluencia.BAJA).lugares()).containsExactly(List.of("Travesía", "Búsqueda", "Laberinto"), List.of("Tobogán"),
                List.of("Minigolf"), List.of("Entrada"), List.of("Carrera"));
        assertThatThrownBy(() -> reglas.guardarOrdenRotacion(List.of("Nada"))).isInstanceOf(ValidacionException.class);
    }

    @Test
    void laReglaGuardaLaHoraDeLaTirolesaYLosHorariosDelDia() {
        ReglaAperturaDto r = reglas.guardar(new ReglaAperturaDto(null, 9, Afluencia.MEDIA,
                List.of(new PedidoGeneradorDto.Fijo("Cajero", 2), new PedidoGeneradorDto.Fijo("Tirolesa", 3, "14:00")),
                List.of(List.of("Entrada"), List.of("Minigolf"), List.of("Tobogán"), List.of("Carrera")),
                List.of(new ReglaAperturaDto.Horario("Cajero", org.example.laserranitarotaciones.model.entity.TipoHorario.MENOS, 1, "16:00", null),
                        new ReglaAperturaDto.Horario("Show del zorrito", org.example.laserranitarotaciones.model.entity.TipoHorario.ABRE_TARDE, 3, "16", "17:00"))));

        ReglaAperturaDto leida = reglas.listar().stream().filter(x -> x.id().equals(r.id())).findFirst().orElseThrow();
        assertThat(leida.fijos()).contains(new PedidoGeneradorDto.Fijo("Tirolesa", 3, "14:00"));
        assertThat(leida.horarios()).containsExactly(
                new ReglaAperturaDto.Horario("Cajero", org.example.laserranitarotaciones.model.entity.TipoHorario.MENOS, 1, "16:00", null),
                new ReglaAperturaDto.Horario("Show del zorrito", org.example.laserranitarotaciones.model.entity.TipoHorario.ABRE_TARDE, 3, "16:00", "17:00"));

        assertThatThrownBy(() -> reglas.guardar(new ReglaAperturaDto(r.id(), 9, Afluencia.MEDIA, leida.fijos(), leida.lugares(),
                List.of(new ReglaAperturaDto.Horario("Fotos", org.example.laserranitarotaciones.model.entity.TipoHorario.ABRE_TARDE, 1, "17:00", "16:00")))))
                .hasMessage("Fotos: tiene que terminar después de empezar.");
    }

    @Test
    void importaLaTablaDePuestosAbiertos() throws Exception {
        ReglaAperturaService.Importacion r = reglas.importar(excel());
        assertThat(r.avisos()).isEmpty();
        // 5 filas (tres de poca gente, dos de mucha) más la media de cada una de poca.
        assertThat(r.reglas()).isEqualTo(8);

        // 6, poca gente: una persona hace Travesía, Búsqueda y Laberinto.
        ReglaAperturaDto seis = regla(6, Afluencia.BAJA);
        assertThat(seis.fijos()).containsExactly(new PedidoGeneradorDto.Fijo("Cajero", 1));
        // La rueda arranca en el último que se agregó y sigue el recorrido de las planillas.
        assertThat(seis.lugares()).containsExactly(List.of("Travesía", "Búsqueda", "Laberinto"), List.of("Carrera"), List.of("Entrada"),
                List.of("Minigolf"), List.of("Tobogán"));

        // 9, poca gente: Travesía y Laberinto colaboran con Búsqueda (Búsqueda en dos lugares).
        ReglaAperturaDto nueve = regla(9, Afluencia.BAJA);
        assertThat(nueve.lugares()).contains(List.of("Travesía", "Búsqueda"), List.of("Laberinto", "Búsqueda"))
                .doesNotContain(List.of("Búsqueda"));

        // 10, mucha gente: dos cajeras y la Casa del zorrito fijas; Baños rota y arranca (es el último).
        ReglaAperturaDto diez = regla(10, Afluencia.ALTA);
        assertThat(diez.fijos()).containsExactly(new PedidoGeneradorDto.Fijo("Cajero", 2), new PedidoGeneradorDto.Fijo("Casa del Zorrito", 1));
        assertThat(diez.lugares()).hasSize(7).startsWith(List.of("Baños"));
        assertThat(regla(10, Afluencia.BAJA).lugares()).startsWith(List.of("Flotante de minigolf"));

        // Ayudante Tobogán es la Pérgola. Con 12 o más, Baños es fijo (con 10 rota, ver arriba).
        ReglaAperturaDto catorce = regla(14, Afluencia.ALTA);
        assertThat(catorce.lugares().get(0)).containsExactly("Pérgola");
        assertThat(catorce.fijos()).extracting(PedidoGeneradorDto.Fijo::puesto).contains("Baños");
        assertThat(catorce.lugares()).doesNotContain(List.of("Baños"));

        // La media empieza igual que la baja, y los puestos nuevos quedan en la lista.
        assertThat(regla(6, Afluencia.MEDIA).lugares()).isEqualTo(seis.lugares());
        assertThat(cronogramas.puestos()).contains("Cajero", "Casa del Zorrito", "Flotante de minigolf");
    }

    private ReglaAperturaDto regla(int empleados, Afluencia afluencia) {
        return reglas.listar().stream().filter(x -> x.empleados() == empleados && x.afluencia() == afluencia).findFirst().orElseThrow();
    }

    private static MockMultipartFile excel() throws Exception {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet h = libro.createSheet("Puestos abiertos");
            fila(h, 0, "PARQUE LA SERRANITA  |  PUESTOS ABIERTOS");
            fila(h, 1, "Empleados", "Afluencia", "Puestos abiertos", "Puestos distintos", "Puestos compartidos / refuerzos");
            fila(h, 2, 6, "Poca gente", "Cajera · Entrada · Minigolf · Tobogán · Carrera · Travesía · Búsqueda · Laberinto", 8,
                    "1 empleado comparte Travesía, Búsqueda y Laberinto");
            fila(h, 3, 9, "Poca gente", "Cajera · Entrada · Minigolf · Tobogán · Carrera · Travesía · Búsqueda · Casa del Zorrito · Canilla · Laberinto",
                    10, "Travesía y Laberinto colaboran con Búsqueda (2 empleados)");
            fila(h, 4, 10, "Poca gente", "Cajera · Entrada · Minigolf · Tobogán · Carrera · Travesía · Búsqueda · Casa del Zorrito · Canilla · Laberinto · Flotante",
                    11, "Travesía y Laberinto colaboran con Búsqueda (2 empleados)");
            fila(h, 5, 10, "Mucha gente", "Cajera · Entrada · Minigolf · Tobogán · Carrera · Travesía · Búsqueda · Laberinto · Canilla · Casa del Zorrito · Baños",
                    11, "1 empleado comparte Travesía, Búsqueda y Laberinto | 2 empleados en Cajera");
            fila(h, 6, 14, "Mucha gente", "Cajera · Entrada · Minigolf · Tobogán · Carrera · Travesía · Canilla · Casa del Zorrito · Baños · Laberinto · Flotante · Búsqueda · Ayudante Tobogán",
                    13, "2 empleados en Cajera");
            fila(h, 7, "Nota: Tirolesa no se incluye en esta lista.");
            libro.write(salida);
            return new MockMultipartFile("archivo", "reglas.xlsx", null, salida.toByteArray());
        }
    }

    private static void fila(Sheet hoja, int indice, Object... valores) {
        Row f = hoja.createRow(indice);
        for (int i = 0; i < valores.length; i++) {
            if (valores[i] instanceof Integer n) f.createCell(i).setCellValue(n);
            else f.createCell(i).setCellValue((String) valores[i]);
        }
    }
}
