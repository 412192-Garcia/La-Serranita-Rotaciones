package org.example.laserranitarotaciones.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Quién hizo qué tarea qué día; sirve para no repetir. Se guarda al imprimir las tarjetas. */
@Entity
@Table(name = "tareas_hechas", indexes = @Index(name = "ix_tareas_hechas_fecha", columnList = "fecha"))
@Getter
@Setter
@NoArgsConstructor
public class TareaHecha {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false)
    private Long idTarea;

    @Column(nullable = false)
    private Long idEmpleado;

    public TareaHecha(LocalDate fecha, Long idTarea, Long idEmpleado) {
        this.fecha = fecha;
        this.idTarea = idTarea;
        this.idEmpleado = idEmpleado;
    }
}
