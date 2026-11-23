package org.example.laserranitarotaciones.model.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Qué puestos se abren con tantos empleados (todos) y tal afluencia: los fijos (la Tirolesa con su gente y su
 * hora) y los lugares de la rueda. Viene de la tabla "Puestos abiertos" del parque.
 */
@Entity
@Table(name = "reglas_apertura")
@Getter
@Setter
public class ReglaApertura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int empleados;

    // Como texto (no enum de la base) para poder agregar valores sin migrar.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(10)")
    private Afluencia afluencia;

    /** "Cajero|2": puesto y cuántas personas; "Tirolesa|3|13:00": y desde qué hora, si abre tarde. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "regla_apertura_fijos", joinColumns = @JoinColumn(name = "id_regla"))
    @OrderColumn(name = "orden")
    @Column(name = "fijo", nullable = false, length = 80)
    private List<String> fijos = new ArrayList<>();

    /** "Travesía+Búsqueda+Laberinto": los puestos que hace la misma persona, en el orden de la rueda. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "regla_apertura_lugares", joinColumns = @JoinColumn(name = "id_regla"))
    @OrderColumn(name = "orden")
    @Column(name = "lugar", nullable = false, length = 250)
    private List<String> lugares = new ArrayList<>();

    /** "Show del zorrito|ABRE_TARDE|3|16:00|17:00", "Cajero|MENOS|1|16:00|": lo que cambia en el día. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "regla_apertura_horarios", joinColumns = @JoinColumn(name = "id_regla"))
    @OrderColumn(name = "orden")
    @Column(name = "horario", nullable = false, length = 120)
    private List<String> horarios = new ArrayList<>();
}
