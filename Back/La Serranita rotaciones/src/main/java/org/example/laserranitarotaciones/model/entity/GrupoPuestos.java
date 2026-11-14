package org.example.laserranitarotaciones.model.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
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

/** Varios puestos con un nombre: el que sabe "Parque" sabe todos los de la rueda. */
@Entity
@Table(name = "grupos_puestos")
@Getter
@Setter
public class GrupoPuestos {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String nombre;

    /** Nombres.clave(nombre). */
    @Column(nullable = false, unique = true, length = 60)
    private String clave;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "grupo_puestos_items", joinColumns = @JoinColumn(name = "id_grupo"))
    @OrderColumn(name = "orden")
    @Column(name = "puesto", nullable = false, length = 60)
    private List<String> puestos = new ArrayList<>();
}
