package org.example.laserranitarotaciones.model.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    /** Si abre más tarde: a qué hora ("13:00"). null = abierto todo el día. */
    @Column(length = 5)
    private String abre;

    /** Hasta qué hora; null = hasta el cierre. */
    @Column(length = 5)
    private String hasta;

    /** Cuántas personas lleva según la afluencia (sólo si abre tarde). */
    private Integer personasBaja;
    private Integer personasMedia;
    private Integer personasAlta;

    /** El horario es un refuerzo: fuera de él el puesto sigue abierto con su gente de siempre. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean refuerzo;

    /** En el horario el puesto tiene personas de menos (las que se indican). */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean menos;

    /** Lugar en el orden de rotación predeterminado (0 = primero); null = no está en el orden. */
    @Column(name = "orden_rotacion")
    private Integer ordenRotacion;

    /**
     * Los roles que necesitan a alguien preparado, uno por persona y en orden de importancia (Show del zorrito:
     * Zorrito, Presentador). Vacío = el puesto no tiene roles.
     */
    @ElementCollection
    @CollectionTable(name = "puesto_roles", joinColumns = @JoinColumn(name = "id_puesto"))
    @OrderColumn(name = "orden")
    @Column(name = "rol", nullable = false, length = 40)
    private List<String> roles = new ArrayList<>();

    /** Cómo se llama el resto de la gente del puesto (Ayudante); lo hace cualquiera que sepa el puesto. */
    @Column(length = 40)
    private String rolResto;

    public TipoHorario getTipo() {
        return menos ? TipoHorario.MENOS : refuerzo ? TipoHorario.REFUERZO : TipoHorario.ABRE_TARDE;
    }

    public void setTipo(TipoHorario tipo) {
        refuerzo = tipo == TipoHorario.REFUERZO;
        menos = tipo == TipoHorario.MENOS;
    }
}
