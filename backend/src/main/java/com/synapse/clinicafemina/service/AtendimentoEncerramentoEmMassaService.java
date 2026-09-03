package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.dto.atendimento.AtendimentosAtivosContagemResponse;
import com.synapse.clinicafemina.dto.atendimento.EncerramentoEmMassaRequest;
import com.synapse.clinicafemina.dto.atendimento.EncerramentoEmMassaResponse;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AtendimentoEncerramentoEmMassaService {

    private final AtendimentoRepository atendimentoRepository;
    private final Set<Long> usuariosBloqueados;

    public AtendimentoEncerramentoEmMassaService(
            AtendimentoRepository atendimentoRepository,
            @Value("${app.atendimentos.bulk-close-blocked-user-ids:}") String usuariosBloqueados
    ) {
        this.atendimentoRepository = atendimentoRepository;
        this.usuariosBloqueados = parseUsuariosBloqueados(usuariosBloqueados);
    }

    public boolean podeExecutar(Usuario usuario) {
        return usuario != null
                && "GESTOR".equals(usuario.getPerfil())
                && !this.usuariosBloqueados.contains(usuario.getId());
    }

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
        if (usuario != null && usuariosBloqueados.contains(usuario.getId())) {
            log.warn("Tentativa de encerramento em massa bloqueada. clinicaId={}, usuarioId={}, perfil={}",
                    clinicaId, usuario.getId(), usuario.getPerfil());
            throw new AccessDeniedException("Usuário bloqueado para encerramento em massa.");
        }
        if (!Boolean.TRUE.equals(request.confirmado())) {
            throw new BadRequestException("A confirmação do encerramento em massa é obrigatória.");
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

    private Set<Long> parseUsuariosBloqueados(String configuracao) {
        if (configuracao == null || configuracao.isBlank()) {
            return Set.of();
        }
        try {
            return Arrays.stream(configuracao.split(","))
                    .map(String::trim)
                    .filter(valor -> !valor.isBlank())
                    .map(Long::parseLong)
                    .filter(id -> id > 0)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                    "ATENDIMENTOS_BULK_CLOSE_BLOCKED_USER_IDS deve conter somente IDs numéricos separados por vírgula",
                    exception
            );
        }
    }
}
