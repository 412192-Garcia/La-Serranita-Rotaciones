package org.example.laserranitarotaciones.model.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "empleados")
@Getter
@Setter
public class Empleado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String nombre;

    /** Puestos que sabe hacer. */
    @ElementCollection
    @CollectionTable(name = "empleado_puestos", joinColumns = @JoinColumn(name = "id_empleado"))
    @Column(name = "puesto", nullable = false, length = 60)
    private Set<String> puestos = new LinkedHashSet<>();

    /** Está aprendiendo: no ocupa un número del cronograma, acompaña a otro. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean enEntrenamiento;

    /** A quién acompaña normalmente mientras está en entrenamiento. */
    private Long idAcompananteHabitual;

    /** Tareas de las que piden saber que sabe hacer. */
    @ElementCollection
    @CollectionTable(name = "empleado_tareas", joinColumns = @JoinColumn(name = "id_empleado"))
    @Column(name = "id_tarea", nullable = false)
    private Set<Long> tareas = new LinkedHashSet<>();

    /** De los que sabe, en cuáles tiene prioridad (ej. la boletera en Boletería). */
    @ElementCollection
    @CollectionTable(name = "empleado_principales", joinColumns = @JoinColumn(name = "id_empleado"))
    @Column(name = "puesto", nullable = false, length = 60)
    private Set<String> principales = new LinkedHashSet<>();
}
