package org.example.laserranitarotaciones.repository;

import org.example.laserranitarotaciones.model.entity.PuestoParque;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PuestoParqueRepository extends JpaRepository<PuestoParque, Long> {

    Optional<PuestoParque> findByClave(String clave);
}
