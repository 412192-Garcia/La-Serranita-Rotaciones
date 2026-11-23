package org.example.laserranitarotaciones.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Quien puede entrar a la app. Todos pueden todo (no hay roles). */
@Entity
@Table(name = "usuarios")
@Getter
@Setter
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Con el que se entra; sin mayúsculas ni espacios. */
    @Column(nullable = false, unique = true, length = 40)
    private String usuario;

    /** La contraseña ya cifrada (BCrypt). */
    @Column(nullable = false, length = 100)
    private String clave;

    /** Cómo se lo nombra en la app ("Pau"). */
    @Column(nullable = false, length = 60)
    private String nombre;
}
