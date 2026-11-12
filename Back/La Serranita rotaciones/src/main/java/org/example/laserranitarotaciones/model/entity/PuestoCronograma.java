package org.example.laserranitarotaciones.model.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
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

/** Fila del cronograma: un puesto y quién está en cada horario. */
@Entity
@Table(name = "puestos_cronograma")
@Getter
@Setter
public class PuestoCronograma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String nombre;

    /** Aclaración que no cambia el puesto ("consultar al encargado"). */
    @Column(length = 100)
    private String nota;

    /** Una por horario: vacía, "3", "1-2" o "Cerrado". */
    @ElementCollection
    @CollectionTable(name = "puesto_cronograma_celdas", joinColumns = @JoinColumn(name = "id_puesto"))
    @OrderColumn(name = "orden")
    @Column(name = "valor", nullable = false, length = 40)
    private List<String> celdas = new ArrayList<>();
}
