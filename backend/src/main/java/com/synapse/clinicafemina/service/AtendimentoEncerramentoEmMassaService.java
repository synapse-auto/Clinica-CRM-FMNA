package com.synapse.clinicafemina.service;

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

    private final AtendimentoRepository atendimentoRepository;

    @Transactional(readOnly = true)
    public AtendimentosAtivosContagemResponse contarAtivos(Long clinicaId) {
        long total = atendimentoRepository.countByClinicaIdAndStatus(clinicaId, "ATIVO");
        return new AtendimentosAtivosContagemResponse(total);
    }

    @Transactional
    public EncerramentoEmMassaResponse encerrarTodos(
            Long clinicaId,
            EncerramentoEmMassaRequest request
    ) {
        if (!Boolean.TRUE.equals(request.confirmado())) {
            throw new BadRequestException("A confirmação do encerramento em massa é obrigatória.");
        }
        OffsetDateTime dataEncerramento = OffsetDateTime.now();
        String motivo = MotivoEncerramentoAtendimento.sanitizar(
                request.motivo(), MotivoEncerramentoAtendimento.PADRAO_EM_MASSA
        );
        int encerrados = atendimentoRepository.encerrarTodosAtivos(
                clinicaId, dataEncerramento, motivo
        );
        log.info("Encerramento em massa concluído. clinicaId={} encerrados={}", clinicaId, encerrados);
        return new EncerramentoEmMassaResponse(encerrados, dataEncerramento);
    }
}
