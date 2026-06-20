package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Atendimento;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.dto.ConvenioReviewRequest;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class ConvenioReviewService {

    private final AtendimentoRepository atendimentoRepository;
    private final PacienteRepository pacienteRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional
    public Paciente revisar(Long atendimentoId, Long clinicaId, Long usuarioId, ConvenioReviewRequest request) {
        Atendimento atendimento = atendimentoRepository.findByIdAndClinicaId(atendimentoId, clinicaId)
                .orElseThrow(() -> new NotFoundException("Atendimento não encontrado"));
        Usuario usuario = usuarioRepository.findAtivoByIdAndClinicaId(usuarioId, clinicaId)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));

        Paciente paciente = atendimento.getPaciente();
        paciente.setConvenioStatus(request.resultado());
        paciente.setConvenioRevisadoEm(OffsetDateTime.now());
        paciente.setConvenioRevisadoPor(usuario);
        paciente.setRequerRevisao("PENDENTE".equals(request.resultado()));
        return pacienteRepository.save(paciente);
    }
}
