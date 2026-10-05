package com.example.multas.repository;

import com.example.multas.model.EstadoMulta;
import com.example.multas.model.Multa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MultaRepository extends JpaRepository<Multa, Long> {

    List<Multa> findByEstudianteId(String estudianteId);

    // Punto de decision 2: consulta agregada COUNT en el motor de BD,
    // mas eficiente que traer todos los registros a memoria y filtrar con streams.
    long countByEstudianteIdAndEstado(String estudianteId, EstadoMulta estado);
}
