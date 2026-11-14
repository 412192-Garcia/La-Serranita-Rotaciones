package org.example.laserranitarotaciones.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** La prioridad de un puesto. Los puestos sin fila acá son de prioridad media. */
@Entity
@Table(name = "puestos")
@Getter
@Setter
public class PuestoParque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    /** Nombres.clave(nombre): para encontrarlo sin importar acentos ni mayúsculas. */
    @Column(nullable = false, unique = true, length = 100)
    private String clave;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Prioridad prioridad;

    /** Si aparece en la columna "Puestos fijos" de los cronogramas. */
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean mostrarEnFijos = true;
}
