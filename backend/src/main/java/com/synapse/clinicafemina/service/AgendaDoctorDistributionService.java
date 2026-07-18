package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.config.PerformanceCacheConfig;
import com.synapse.clinicafemina.domain.Agendamento;
import com.synapse.clinicafemina.dto.agendamento.AgendaOptionResponse;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import com.synapse.clinicafemina.repository.AgendamentoRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import com.synapse.clinicafemina.service.cache.AgendaDoctorCacheKey;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgendaDoctorDistributionService {

    private final AgendamentoRepository agendamentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AgendaExternalDoctorResolver externalDoctorResolver;

    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = PerformanceCacheConfig.AGENDA_DOCTOR_DISTRIBUTION,
            key = "#key",
            sync = true
    )
    public List<AgendaOptionResponse> list(AgendaDoctorCacheKey key) {
        if (key.provider() == ExternalProviderType.MEDWARE) {
            Map<String, AgendaExternalDoctorResolver.ExternalDoctor> doctors = new LinkedHashMap<>();
            List<Agendamento> appointments = key.inicio() != null && key.fim() != null
                    ? agendamentoRepository
                    .findByClinicaIdAndDataHoraInicioGreaterThanEqualAndDataHoraInicioLessThanOrderByDataHoraInicioAsc(
                            key.clinicId(), key.inicio(), key.fim())
                    : agendamentoRepository.findByClinicaIdOrderByDataHoraInicioAsc(key.clinicId());
            appointments.stream()
                    .map(externalDoctorResolver::resolve)
                    .flatMap(Optional::stream)
                    .forEach(doctor -> doctors.putIfAbsent(externalDoctorResolver.key(doctor), doctor));
            return doctors.values().stream()
                    .map(doctor -> new AgendaOptionResponse(
                            null, doctor.nome(), doctor.codigoExterno(), "MEDWARE"))
                    .toList();
        }
        return usuarioRepository.findMedicosAtivosByClinicaId(key.clinicId())
                .stream()
                .map(medico -> new AgendaOptionResponse(medico.getId(), medico.getNome()))
                .toList();
    }
}
