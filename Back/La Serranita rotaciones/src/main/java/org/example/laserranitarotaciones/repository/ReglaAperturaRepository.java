package org.example.laserranitarotaciones.repository;

import org.example.laserranitarotaciones.model.entity.Afluencia;
import org.example.laserranitarotaciones.model.entity.ReglaApertura;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReglaAperturaRepository extends JpaRepository<ReglaApertura, Long> {

    Optional<ReglaApertura> findByEmpleadosAndAfluencia(int empleados, Afluencia afluencia);
}
