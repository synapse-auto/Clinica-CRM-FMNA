package com.synapse.clinicafemina.service.audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Service
public class UsuarioPermissionAuditService {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void registrar(UsuarioPermissionAuditEvent event) {
        log.info(
                "auditoriaAdministrativa acao={} executorId={} usuarioAlvoId={} clinicaId={} valorAnterior={} valorNovo={} ocorridoEm={}",
                event.acao(),
                event.executorId(),
                event.usuarioAlvoId(),
                event.clinicaId(),
                event.valorAnterior(),
                event.valorNovo(),
                event.ocorridoEm()
        );
    }
}
