package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Atendimento;
import com.synapse.clinicafemina.domain.AtendimentoLembrete;
import com.synapse.clinicafemina.domain.AtendimentoLembreteStatus;
import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.dto.atendimento.AtendimentoLembreteRequest;
import com.synapse.clinicafemina.dto.atendimento.AtendimentoLembreteResponse;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.repository.AtendimentoLembreteRepository;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AtendimentoLembreteService {

    private static final int MENSAGEM_MAX_LENGTH = 500;
    private static final ZoneId APP_ZONE = ZoneId.of("America/Sao_Paulo");

    private final AtendimentoLembreteRepository lembreteRepository;
    private final AtendimentoRepository atendimentoRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public List<AtendimentoLembreteResponse> listar(Long atendimentoId, Long clinicaId) {
        buscarAtendimento(atendimentoId, clinicaId);
        return lembreteRepository
                .findByAtendimentoIdAndClinicaIdOrderByStatusAscLembrarEmAsc(atendimentoId, clinicaId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AtendimentoLembreteResponse criar(
            Long atendimentoId,
            AtendimentoLembreteRequest request,
            Long clinicaId,
            Long usuarioId
    ) {
        validarRequest(request);
        Atendimento atendimento = buscarAtendimento(atendimentoId, clinicaId);
        Usuario usuario = usuarioRepository.findAtivoByIdAndClinicaId(usuarioId, clinicaId)
                .orElseThrow(() -> new NotFoundException("Usuario nao encontrado"));

        AtendimentoLembrete lembrete = new AtendimentoLembrete();
        lembrete.setClinica(atendimento.getClinica());
        lembrete.setAtendimento(atendimento);
        lembrete.setPaciente(atendimento.getPaciente());
        lembrete.setCriadoPor(usuario);
        lembrete.setMensagem(request.mensagem().trim());
        lembrete.setLembrarEm(request.data().atTime(request.hora()).atZone(APP_ZONE).toOffsetDateTime());
        lembrete.setStatus(AtendimentoLembreteStatus.PENDENTE);
        return toResponse(lembreteRepository.save(lembrete));
    }

    @Transactional
    public AtendimentoLembreteResponse concluir(Long atendimentoId, Long lembreteId, Long clinicaId) {
        AtendimentoLembrete lembrete = buscarLembrete(lembreteId, atendimentoId, clinicaId);
        lembrete.setStatus(AtendimentoLembreteStatus.CONCLUIDO);
        return toResponse(lembreteRepository.save(lembrete));
    }

    @Transactional
    public AtendimentoLembreteResponse cancelar(Long atendimentoId, Long lembreteId, Long clinicaId) {
        AtendimentoLembrete lembrete = buscarLembrete(lembreteId, atendimentoId, clinicaId);
        lembrete.setStatus(AtendimentoLembreteStatus.CANCELADO);
        return toResponse(lembreteRepository.save(lembrete));
    }

    private Atendimento buscarAtendimento(Long atendimentoId, Long clinicaId) {
        return atendimentoRepository.findByIdAndClinicaId(atendimentoId, clinicaId)
                .orElseThrow(() -> new NotFoundException("Atendimento nao encontrado"));
    }

    private AtendimentoLembrete buscarLembrete(Long lembreteId, Long atendimentoId, Long clinicaId) {
        return lembreteRepository.findByIdAndAtendimentoIdAndClinicaId(lembreteId, atendimentoId, clinicaId)
                .orElseThrow(() -> new NotFoundException("Lembrete nao encontrado"));
    }

    private void validarRequest(AtendimentoLembreteRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Informe os dados do lembrete");
        }
        if (request.data() == null) {
            throw new IllegalArgumentException("Data do lembrete e obrigatoria");
        }
        if (request.hora() == null) {
            throw new IllegalArgumentException("Hora do lembrete e obrigatoria");
        }
        if (request.mensagem() == null || request.mensagem().isBlank()) {
            throw new IllegalArgumentException("Mensagem do lembrete e obrigatoria");
        }
        if (request.mensagem().trim().length() > MENSAGEM_MAX_LENGTH) {
            throw new IllegalArgumentException("Mensagem do lembrete deve ter no maximo 500 caracteres");
        }
    }

    private AtendimentoLembreteResponse toResponse(AtendimentoLembrete lembrete) {
        Usuario criadoPor = lembrete.getCriadoPor();
        return new AtendimentoLembreteResponse(
                lembrete.getId(),
                lembrete.getAtendimento().getId(),
                lembrete.getMensagem(),
                lembrete.getLembrarEm(),
                lembrete.getStatus().name(),
                criadoPor != null ? criadoPor.getId() : null,
                criadoPor != null ? criadoPor.getNome() : null,
                lembrete.getCriadoEm(),
                lembrete.getAtualizadoEm()
        );
    }
}
