package com.synapse.clinicafemina.integration.external;

import com.synapse.clinicafemina.exception.BulkSyncNotSupportedException;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * Provider Darwin: declara explicitamente suas capacidades ao invés de forçar o
 * contrato antigo de sincronização em massa (que a API real não suporta — ver
 * auditoria da coleção Postman "API de integração Darwin" v1.0.9).
 *
 * A API Darwin real oferece apenas consultas pontuais sob demanda (CPF, horários,
 * catálogos) — expostas via {@link com.synapse.clinicafemina.service.DarwinConsultaService}
 * e {@code DarwinClient}, não por este bean. Este componente existe apenas para que
 * {@link ExternalProviderFactory#getProvider(ExternalProviderType)} sempre resolva um
 * bean real para DARWIN, permitindo que {@code ExternalSyncService} consulte
 * {@link #supportsBulkSync()} e rejeite a sincronização em massa de forma explícita —
 * em vez de depender da ausência de bean como mecanismo de controle.
 */
@Component
public class DarwinProvider implements ExternalClinicProvider {

    @Override
    public ExternalProviderType getType() {
        return ExternalProviderType.DARWIN;
    }

    @Override
    public boolean supportsBulkSync() {
        return false;
    }

    @Override
    public boolean supportsOnDemandQueries() {
        return true;
    }

    @Override
    public PageResult<ExternalPatientDTO> getPatients(OffsetDateTime updatedAfter, String cursor, int limit) {
        throw new BulkSyncNotSupportedException();
    }

    @Override
    public PageResult<ExternalAppointmentDTO> getAppointments(OffsetDateTime updatedAfter, String cursor, int limit) {
        throw new BulkSyncNotSupportedException();
    }

    @Override
    public PageResult<ExternalClinicalNoteDTO> getPatientNotes(String externalPatientId, String cursor, int limit) {
        throw new BulkSyncNotSupportedException();
    }
}
