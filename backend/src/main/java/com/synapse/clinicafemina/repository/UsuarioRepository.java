package com.synapse.clinicafemina.repository;

import com.synapse.clinicafemina.domain.Usuario;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    @Query("SELECT u FROM Usuario u WHERE u.email = :email AND u.ativo = true AND u.deletadoEm IS NULL")
    Optional<Usuario> findAtivoByEmail(@Param("email") String email);

    @Query("""
            SELECT u FROM Usuario u
            WHERE u.id = :id
              AND u.clinica.id = :clinicaId
              AND u.ativo = true
              AND u.deletadoEm IS NULL
            """)
    Optional<Usuario> findAtivoByIdAndClinicaId(
            @Param("id") Long id,
            @Param("clinicaId") Long clinicaId
    );

    @Query("""
            SELECT u FROM Usuario u
            WHERE u.clinica.id = :clinicaId
              AND u.perfil = 'MEDICO'
              AND u.ativo = true
              AND u.deletadoEm IS NULL
            ORDER BY u.nome ASC
            """)
    List<Usuario> findMedicosAtivosByClinicaId(@Param("clinicaId") Long clinicaId);

    @Query("""
            SELECT u FROM Usuario u
            WHERE u.clinica.id = :clinicaId
              AND u.ativo = true
              AND u.deletadoEm IS NULL
            """)
    List<Usuario> findAtivosByClinicaId(@Param("clinicaId") Long clinicaId);

    @Query("""
            SELECT u FROM Usuario u
            WHERE u.clinica.id = :clinicaId
              AND u.ativo = true
              AND u.adminInterno = false
              AND u.deletadoEm IS NULL
            ORDER BY u.nome ASC
            """)
    List<Usuario> findAtivosVisiveisByClinicaId(@Param("clinicaId") Long clinicaId);

    @Query("""
            SELECT u FROM Usuario u
            WHERE u.clinica.id = :clinicaId
              AND u.perfil IN ('GESTOR', 'RECEPCIONISTA')
              AND u.ativo = true
              AND u.adminInterno = false
              AND u.deletadoEm IS NULL
            ORDER BY u.nome ASC
            """)
    List<Usuario> findAtendentesVisiveisByClinicaId(@Param("clinicaId") Long clinicaId);

    @Query("""
            SELECT u.perfil, COUNT(u) FROM Usuario u
            WHERE u.clinica.id = :clinicaId
              AND u.ativo = true
              AND u.adminInterno = false
              AND u.deletadoEm IS NULL
            GROUP BY u.perfil
            ORDER BY u.perfil ASC
            """)
    List<Object[]> countAtivosVisiveisPorPerfil(@Param("clinicaId") Long clinicaId);
}
