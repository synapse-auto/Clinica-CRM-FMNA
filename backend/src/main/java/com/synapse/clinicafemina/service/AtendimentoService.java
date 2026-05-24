package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.config.RabbitMQConfig;
import com.synapse.clinicafemina.domain.Atendimento;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.dto.AtendimentoDetalheDTO;
import com.synapse.clinicafemina.dto.AtendimentoResumoDTO;
import com.synapse.clinicafemina.dto.TransferirAtendimentoRequest;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.messaging.MensagemEntradaEvent;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import com.synapse.clinicafemina.repository.ClinicaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AtendimentoService {

    private final AtendimentoRepository atendimentoRepository;
    private final ClinicaRepository clinicaRepository;
    private final RabbitTemplate rabbitTemplate;
    private final RealtimeBroadcastService broadcastService;

    // ─── Listagem ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AtendimentoResumoDTO> listar(Long clinicaId, String status,
                                              String tipo, Pageable pageable) {
        Boolean tratadoPorIa = switch (tipo != null ? tipo.toUpperCase() : "TODOS") {
            case "IA"    -> true;
            case "HUMANO"-> false;
            default      -> null;
        };

        return atendimentoRepository
                .findByClinica(clinicaId, status, tratadoPorIa, pageable)
                .map(this::toResumoDTO);
    }

    // ─── Detalhe ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AtendimentoDetalheDTO buscarPorId(Long id) {
        Atendimento a = buscarOuFalhar(id);
        return toDetalheDTO(a);
    }

    // ─── Transferência ────────────────────────────────────────────────────

    @Transactional
    public AtendimentoDetalheDTO transferir(Long id, TransferirAtendimentoRequest req,
                                             Long clinicaId) {
        Atendimento atendimento = buscarOuFalhar(id);
        Usuario antigoAtendente = atendimento.getAtendentePrincipal();

        // Busca o novo atendente — deve pertencer à mesma clínica (verificação simplificada)
        // Em produção: buscar por UsuarioRepository + validar clinicaId
        throw new UnsupportedOperationException(
                "Implementação completa após criação do UsuarioRepository");
    }

    // ─── Encerramento ─────────────────────────────────────────────────────

    @Transactional
    public AtendimentoDetalheDTO encerrar(Long id, String motivo) {
        Atendimento atendimento = buscarOuFalhar(id);

        if ("ENCERRADO".equals(atendimento.getStatus())) {
            throw new IllegalStateException("Atendimento já encerrado");
        }

        atendimento.setStatus("ENCERRADO");
        atendimento.setDataEncerramento(OffsetDateTime.now());
        atendimento.setMotivoEncerramento(motivo);
        atendimentoRepository.save(atendimento);

        log.info("Atendimento {} encerrado. Motivo: {}", id, motivo);
        return toDetalheDTO(atendimento);
    }

    // ─── Helpers privados ─────────────────────────────────────────────────

    private Atendimento buscarOuFalhar(Long id) {
        return atendimentoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Atendimento não encontrado: " + id));
    }

    private AtendimentoResumoDTO toResumoDTO(Atendimento a) {
        Paciente p = a.getPaciente();
        Usuario u  = a.getAtendentePrincipal();
        return new AtendimentoResumoDTO(
                a.getId(), a.getStatus(), a.getTratadoPorIa(),
                a.getUltimaMensagemEm(), a.getNaoLidas(),
                new AtendimentoResumoDTO.PacienteResumoDTO(
                        p.getId(), p.getNomeBusca(), p.getTelefoneNormalizado()),
                u != null ? new AtendimentoResumoDTO.AtendenteDTO(u.getId(), u.getNome()) : null
        );
    }

    private AtendimentoDetalheDTO toDetalheDTO(Atendimento a) {
        Paciente p = a.getPaciente();
        Usuario u  = a.getAtendentePrincipal();
        return new AtendimentoDetalheDTO(
                a.getId(), a.getStatus(), a.getTratadoPorIa(),
                a.getDataInicio(), a.getDataEncerramento(), a.getNaoLidas(),
                new AtendimentoDetalheDTO.PacienteDetalheDTO(
                        p.getId(), p.getNome(), p.getTelefone(),
                        p.getEmail(), p.getStatus(), p.getUltimaInteracaoEm()),
                u != null ? new AtendimentoDetalheDTO.AtendenteDTO(
                        u.getId(), u.getNome(), u.getPerfil()) : null
        );
    }
}
