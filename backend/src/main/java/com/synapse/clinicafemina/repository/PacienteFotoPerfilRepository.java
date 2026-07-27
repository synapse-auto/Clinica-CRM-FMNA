package com.synapse.clinicafemina.repository;

import com.synapse.clinicafemina.domain.PacienteFotoPerfil;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PacienteFotoPerfilRepository extends JpaRepository<PacienteFotoPerfil, Long> {

    Optional<PacienteFotoPerfil> findByPacienteIdAndClinica_Id(Long pacienteId, Long clinicaId);

}
