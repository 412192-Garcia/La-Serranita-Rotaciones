package org.example.laserranitarotaciones.repository;

import org.example.laserranitarotaciones.model.entity.TareaHecha;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface TareaHechaRepository extends JpaRepository<TareaHecha, Long> {

    List<TareaHecha> findByFechaBetween(LocalDate desde, LocalDate hasta);

    void deleteByFecha(LocalDate fecha);

    void deleteByIdTarea(Long idTarea);
}
