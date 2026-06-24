package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.dto.paciente.PacienteResumoDTO;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.repository.PacienteRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PacienteService {

    private final PacienteRepository pacienteRepository;

    /**
     * Retorna todos os pacientes ativos da clínica, ordenados por nomeBusca ASC.
     * Pacientes com deletadoEm preenchido são excluídos.
     */
    @Transactional(readOnly = true)
    public List<PacienteResumoDTO> listar(Clinica clinica) {
        return pacienteRepository.findDisponiveisByClinicaId(clinica.getId())
                .stream()
                .map(this::toResumo)
                .toList();
    }

    /**
     * Busca um paciente por ID dentro da clínica.
     * Lança NotFoundException se inexistente ou deletado.
     */
    @Transactional(readOnly = true)
    public PacienteResumoDTO buscarPorId(Long id, Clinica clinica) {
        Paciente paciente = pacienteRepository.findByIdAndClinicaId(id, clinica.getId())
                .filter(p -> p.getDeletadoEm() == null)
                .orElseThrow(() -> new NotFoundException("Paciente não encontrado"));
        return toResumo(paciente);
    }

    private PacienteResumoDTO toResumo(Paciente paciente) {
        String externalSource = paciente.getExternalSource() != null
                ? paciente.getExternalSource().name()
                : null;
        return new PacienteResumoDTO(
                paciente.getId(),
                paciente.getNome(),
                paciente.getTelefoneNormalizado(),
                paciente.getStatus(),
                externalSource,
                paciente.getExternalId(),
                paciente.getCriadoEm(),
                paciente.getUltimaInteracaoEm()
        );
    }
}
