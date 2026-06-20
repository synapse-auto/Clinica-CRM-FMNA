package com.synapse.clinicafemina.repository;

import com.synapse.clinicafemina.domain.MidiaMensagem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MidiaMensagemRepository extends JpaRepository<MidiaMensagem, Long> {

    Optional<MidiaMensagem> findByMensagemId(Long mensagemId);

    @Query("""
            SELECT mm FROM MidiaMensagem mm
            WHERE mm.mensagem.id = :mensagemId
              AND mm.mensagem.atendimento.id = :atendimentoId
              AND mm.mensagem.atendimento.clinica.id = :clinicaId
            """)
    Optional<MidiaMensagem> findAutorizada(
            @Param("mensagemId") Long mensagemId,
            @Param("atendimentoId") Long atendimentoId,
            @Param("clinicaId") Long clinicaId
    );
}
