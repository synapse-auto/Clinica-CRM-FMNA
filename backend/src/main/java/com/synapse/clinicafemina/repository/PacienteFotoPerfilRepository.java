package com.synapse.clinicafemina.repository;

import com.synapse.clinicafemina.domain.PacienteFotoPerfil;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface PacienteFotoPerfilRepository extends JpaRepository<PacienteFotoPerfil, Long> {

    Optional<PacienteFotoPerfil> findByPacienteIdAndClinica_Id(Long pacienteId, Long clinicaId);

    @Query(value = """
            SELECT p.id
            FROM paciente p
            LEFT JOIN paciente_foto_perfil foto ON foto.paciente_id = p.id
            WHERE p.clinica_id = :clinicaId
              AND p.deletado_em IS NULL
              AND p.telefone_normalizado IS NOT NULL
              AND BTRIM(p.telefone_normalizado) <> ''
              AND (foto.conteudo IS NULL)
              AND (
                    foto.paciente_id IS NULL
                    OR foto.motivo_ultima_falha = 'HOST_DE_FOTO_NAO_AUTORIZADO'
                    OR (foto.status <> 'PENDING' AND foto.proxima_tentativa_em <= :agora)
              )
            ORDER BY CASE WHEN foto.motivo_ultima_falha = 'HOST_DE_FOTO_NAO_AUTORIZADO' THEN 0 ELSE 1 END,
                     p.id
            LIMIT :limite
            """, nativeQuery = true)
    List<Long> findPacientesElegiveisParaReprocessamento(
            @Param("clinicaId") Long clinicaId,
            @Param("agora") OffsetDateTime agora,
            @Param("limite") int limite
    );

}
