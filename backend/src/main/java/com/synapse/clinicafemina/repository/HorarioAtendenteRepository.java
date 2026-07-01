package com.synapse.clinicafemina.repository;

import com.synapse.clinicafemina.domain.HorarioAtendente;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HorarioAtendenteRepository extends JpaRepository<HorarioAtendente, Long> {

    @Query("""
            SELECT h FROM HorarioAtendente h
            JOIN FETCH h.usuario u
            WHERE u.clinica.id = :clinicaId
              AND h.deletadoEm IS NULL
            ORDER BY u.nome ASC, h.diaSemana ASC, h.horaInicio ASC
            """)
    List<HorarioAtendente> findAtivosByClinicaId(@Param("clinicaId") Long clinicaId);

    @Query("""
            SELECT h FROM HorarioAtendente h
            JOIN FETCH h.usuario u
            WHERE h.id = :id
              AND u.clinica.id = :clinicaId
              AND h.deletadoEm IS NULL
            """)
    Optional<HorarioAtendente> findByIdAndUsuarioClinicaIdAndDeletadoEmIsNull(
            @Param("id") Long id,
            @Param("clinicaId") Long clinicaId
    );
}
