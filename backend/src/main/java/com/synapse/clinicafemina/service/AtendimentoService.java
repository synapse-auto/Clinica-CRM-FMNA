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
import com.synapse.clinicafemina.repository.UsuarioRepository;
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
    private final UsuarioRepository usuarioRepository;
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
    public AtendimentoDetalheDTO buscarPorId(Long id, Long clinicaId) {
        Atendimento a = buscarOuFalhar(id, clinicaId);
        return toDetalheDTO(a);
    }

    // ─── Transferência ────────────────────────────────────────────────────

    @Transactional
    public AtendimentoDetalheDTO transferir(Long id, TransferirAtendimentoRequest req,
                                             Long clinicaId) {
        Atendimento atendimento = buscarOuFalhar(id, clinicaId);

        if (!"ATIVO".equals(atendimento.getStatus())) {
            throw new IllegalStateException("Só é possível transferir atendimentos ATIVOS");
        }

        Usuario novoAtendente = usuarioRepository.findById(req.novoAtendenteId())
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado: " + req.novoAtendenteId()));

        // Garante que o novo atendente pertence à mesma clínica
        if (!novoAtendente.getClinica().getId().equals(clinicaId)) {
            throw new IllegalStateException("O atendente não pertence à mesma clínica");
        }

        Usuario antigoAtendente = atendimento.getAtendentePrincipal();
        atendimento.setAtendentePrincipal(novoAtendente);
        atendimento.setTratadoPorIa(false); // Ao transferir para humano, sai do modo IA
        atendimentoRepository.save(atendimento);

        log.info("Atendimento {} transferido de {} para {}",
                id,
                antigoAtendente != null ? antigoAtendente.getId() : "IA",
                novoAtendente.getId());

        // Broadcast STOMP para o novo atendente
        Paciente paciente = atendimento.getPaciente();
        broadcastService.broadcastTransferencia(
                novoAtendente.getId(),
                atendimento.getId(),
                antigoAtendente != null ? antigoAtendente.getId() : 0L,
                antigoAtendente != null ? antigoAtendente.getNome() : "IA",
                paciente.getId(),
                paciente.getNomeBusca(),
                req.motivo()
        );

        return toDetalheDTO(atendimento);
    }

    // ─── Encerramento ─────────────────────────────────────────────────────

    @Transactional
    public AtendimentoDetalheDTO encerrar(Long id, Long clinicaId, String motivo) {
        Atendimento atendimento = buscarOuFalhar(id, clinicaId);

        if ("ENCERRADO".equals(atendimento.getStatus())) {
            throw new IllegalStateException("Atendimento já encerrado");
        }

        atendimento.setStatus("ENCERRADO");
        atendimento.setDataEncerramento(OffsetDateTime.now());
        atendimento.setMotivoEncerramento(motivo);
        atendimentoRepository.save(atendimento);

        log.info("Atendimento {} encerrado", id);
        return toDetalheDTO(atendimento);
    }

    // ─── Helpers privados ─────────────────────────────────────────────────

    private Atendimento buscarOuFalhar(Long id, Long clinicaId) {
        return atendimentoRepository.findByIdAndClinicaId(id, clinicaId)
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
