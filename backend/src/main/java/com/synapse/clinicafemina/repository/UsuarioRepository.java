package com.synapse.clinicafemina.repository;

import com.synapse.clinicafemina.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /** Busca por email — usado pelo UserDetailsService para autenticação JWT. */
    Optional<Usuario> findByEmail(String email);

    /** Busca usuário ativo por email. */
    @Query("SELECT u FROM Usuario u WHERE u.email = :email AND u.ativo = true AND u.deletadoEm IS NULL")
    Optional<Usuario> findAtivoByEmail(@Param("email") String email);

    /** Busca usuários ativos de uma clínica. */
    @Query("SELECT u FROM Usuario u WHERE u.clinica.id = :clinicaId AND u.ativo = true AND u.deletadoEm IS NULL")
    java.util.List<Usuario> findAtivosByClinicaId(@Param("clinicaId") Long clinicaId);
}
