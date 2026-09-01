package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.dto.atendimento.AtendimentosAtivosContagemResponse;
import com.synapse.clinicafemina.dto.atendimento.EncerramentoEmMassaRequest;
import com.synapse.clinicafemina.dto.atendimento.EncerramentoEmMassaResponse;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AtendimentoEncerramentoEmMassaService {

    private static final String CONFIRMACAO_EXIGIDA = "ENCERRAR TODOS";

    private final AtendimentoRepository atendimentoRepository;

    @Transactional(readOnly = true)
    public AtendimentosAtivosContagemResponse contarAtivos(Long clinicaId) {
        long total = atendimentoRepository.countByClinicaIdAndStatus(clinicaId, "ATIVO");
        return new AtendimentosAtivosContagemResponse(total);
    }

    @Transactional
    public EncerramentoEmMassaResponse encerrarTodos(
            Long clinicaId,
            Usuario usuario,
            EncerramentoEmMassaRequest request
    ) {
        if (!Boolean.TRUE.equals(request.confirmado())) {
            throw new BadRequestException("A confirmação do encerramento em massa é obrigatória.");
        }
        if (!CONFIRMACAO_EXIGIDA.equals(request.confirmacao())) {
            throw new BadRequestException("Digite exatamente ENCERRAR TODOS para confirmar o encerramento em massa.");
        }
        log.info("Solicitação de encerramento em massa. clinicaId={}, usuarioId={}, perfil={}",
                clinicaId, usuario.getId(), usuario.getPerfil());
        OffsetDateTime dataEncerramento = OffsetDateTime.now();
        String motivo = MotivoEncerramentoAtendimento.sanitizar(
                request.motivo(), MotivoEncerramentoAtendimento.PADRAO_EM_MASSA
        );
        int encerrados = atendimentoRepository.encerrarTodosAtivos(
                clinicaId, dataEncerramento, motivo
        );
        log.info("Encerramento em massa concluído. clinicaId={}, usuarioId={}, perfil={}, encerrados={}",
                clinicaId, usuario.getId(), usuario.getPerfil(), encerrados);
        return new EncerramentoEmMassaResponse(encerrados, dataEncerramento);
    }
}
