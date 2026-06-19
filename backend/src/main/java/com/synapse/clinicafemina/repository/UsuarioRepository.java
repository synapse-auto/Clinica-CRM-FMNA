package com.synapse.clinicafemina.repository;

import com.synapse.clinicafemina.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /** Busca por email — usado pelo UserDetailsService para autenticação JWT. */
    Optional<Usuario> findByEmail(String email);

    /** Busca usuário ativo por email. */
    @Query("SELECT u FROM Usuario u WHERE u.email = :email AND u.ativo = true AND u.deletadoEm IS NULL")
    Optional<Usuario> findAtivoByEmail(@Param("email") String email);

    @Query("""
            SELECT u FROM Usuario u
            WHERE u.id = :id
              AND u.clinica.id = :clinicaId
              AND u.ativo = true
              AND u.deletadoEm IS NULL
            """)
    Optional<Usuario> findAtivoByIdAndClinicaId(@Param("id") Long id,
                                                @Param("clinicaId") Long clinicaId);

    @Query("""
            SELECT u FROM Usuario u
            WHERE u.clinica.id = :clinicaId
              AND u.perfil = 'MEDICO'
              AND u.ativo = true
              AND u.deletadoEm IS NULL
            ORDER BY u.nome ASC
            """)
    List<Usuario> findMedicosAtivosByClinicaId(@Param("clinicaId") Long clinicaId);

    /** Busca usuários ativos de uma clínica. */
    @Query("SELECT u FROM Usuario u WHERE u.clinica.id = :clinicaId AND u.ativo = true AND u.deletadoEm IS NULL")
    List<Usuario> findAtivosByClinicaId(@Param("clinicaId") Long clinicaId);
}
