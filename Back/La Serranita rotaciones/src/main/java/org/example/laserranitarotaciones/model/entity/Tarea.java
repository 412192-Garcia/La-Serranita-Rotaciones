package org.example.laserranitarotaciones.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Algo que se hace antes de abrir (pasar la sopladora), sin horario, con cierta cantidad de gente. */
@Entity
@Table(name = "tareas")
@Getter
@Setter
public class Tarea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String nombre;

    @Column(nullable = false)
    private int cantidad;

    /** Si es true, sólo la hacen los que la tienen marcada en su ficha. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean requiereSaber;
}
