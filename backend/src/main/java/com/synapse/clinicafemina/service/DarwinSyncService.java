package com.synapse.clinicafemina.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.DarwinSyncLog;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.dto.darwin.DarwinNoteDTO;
import com.synapse.clinicafemina.dto.darwin.DarwinPageResponse;
import com.synapse.clinicafemina.dto.darwin.DarwinPatientDTO;
import com.synapse.clinicafemina.integration.DarwinClient;
import com.synapse.clinicafemina.repository.ClinicaRepository;
import com.synapse.clinicafemina.repository.DarwinSyncLogRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DarwinSyncService {

    private final DarwinClient darwinClient;
    private final DarwinSyncLogRepository syncLogRepository;
    private final PacienteRepository pacienteRepository;
    private final ClinicaRepository clinicaRepository;
    private final ObjectMapper objectMapper;

    /**
     * Ponto de entrada do Job. Busca o último updated_after e processa as páginas de pacientes e agendamentos.
     */
    public void sync() {
        DarwinSyncLog runLog = new DarwinSyncLog();
        OffsetDateTime updatedAfter = syncLogRepository.findUltimoSucesso()
                .map(DarwinSyncLog::getIniciadoEm) // Usa a data de início do último run de sucesso
                .orElse(null);

        runLog.setUpdatedAfterUtilizado(updatedAfter);
        runLog = syncLogRepository.save(runLog);

        log.info("Iniciando sincronização Darwin (Log ID: {}). UpdatedAfter: {}", runLog.getId(), updatedAfter);

        try {
            // Sincronizar Pacientes (e suas notas)
            int pacientesProcessados = 0;
            int pacientesCriados = 0;
            int pacientesAtualizados = 0;

            String cursor = null;
            boolean hasMore = true;

            // Busca a clínica padrão para associar os pacientes (assumimos ID 1 para este escopo)
            Clinica clinicaPadrao = clinicaRepository.findById(1L)
                    .orElseThrow(() -> new IllegalStateException("Clínica padrão (ID 1) não encontrada"));

            while (hasMore) {
                DarwinPageResponse<DarwinPatientDTO> page = darwinClient.getPatients(updatedAfter, cursor, 50);
                for (DarwinPatientDTO dto : page.data()) {
                    boolean created = processPatient(dto, clinicaPadrao);
                    pacientesProcessados++;
                    if (created) pacientesCriados++;
                    else pacientesAtualizados++;
                }

                hasMore = page.hasMore();
                cursor = page.nextCursor();
            }

            // TODO: Se necessário, processar agendamentos aqui

            runLog.setPacientesProcessados(pacientesProcessados);
            runLog.setPacientesCriados(pacientesCriados);
            runLog.setPacientesAtualizados(pacientesAtualizados);
            runLog.setStatus("SUCESSO");

        } catch (Exception e) {
            log.error("Erro na sincronização do Darwin", e);
            runLog.setStatus("FALHA_TOTAL");
            runLog.setMensagemErro(e.getMessage());
        } finally {
            runLog.setConcluidoEm(OffsetDateTime.now());
            syncLogRepository.save(runLog);
            log.info("Sincronização concluída. Status: {}", runLog.getStatus());
        }
    }

    /**
     * Processa um único paciente com dupla verificação e salva as notas.
     * Retorna true se foi criado, false se foi atualizado.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processPatient(DarwinPatientDTO dto, Clinica clinica) {
        String cpfLimpo = dto.documentNumber() != null ? dto.documentNumber().replaceAll("\\D", "") : null;
        String cpfHash = gerarSha256(cpfLimpo);
        String emailHash = gerarSha256(dto.email());

        // Mecanismo de Upsert: dupla verificação
        Optional<Paciente> optPaciente = pacienteRepository.findByDarwinIdExterno(dto.id());
        if (optPaciente.isEmpty() && cpfHash != null) {
            optPaciente = pacienteRepository.findByCpfHash(cpfHash);
        }

        boolean isNew = optPaciente.isEmpty();
        Paciente paciente = optPaciente.orElseGet(Paciente::new);

        paciente.setClinica(clinica);
        paciente.setDarwinIdExterno(dto.id());
        
        // Mapeamento AesGcmConverter (automático pelos converters na entidade)
        paciente.setNome(dto.fullName());
        paciente.setNomeBusca(normalizarBusca(dto.fullName()));
        paciente.setCpf(cpfLimpo);
        paciente.setCpfHash(cpfHash);
        paciente.setEmail(dto.email());
        paciente.setEmailHash(emailHash);
        paciente.setDataNascimento(dto.birthDate());
        paciente.setTelefone(dto.phone());
        paciente.setTelefoneNormalizado(dto.phone() != null ? dto.phone().replaceAll("\\D", "") : "00000000000"); // fallback simplificado

        if (isNew) {
            paciente.setStatus("EM_ATENDIMENTO");
            paciente.setChaveCriptografiaId("v1");
        }

        // Buscar notas clínicas encapsuladas
        try {
            DarwinPageResponse<DarwinNoteDTO> notesPage = darwinClient.getPatientNotes(dto.id(), null, 100);
            if (notesPage.data() != null && !notesPage.data().isEmpty()) {
                List<Map<String, String>> notasFormatadas = notesPage.data().stream()
                        .map(n -> Map.of(
                                "id", n.id(),
                                "content", n.content(),
                                "createdAt", n.createdAt() != null ? n.createdAt().toString() : ""
                        ))
                        .toList();
                String jsonb = objectMapper.writeValueAsString(Map.of("notasClinicas", notasFormatadas));
                paciente.setDarwinDadosImportados(jsonb);
            }
        } catch (Exception e) {
            log.warn("Falha ao buscar notas para paciente {}: {}", dto.id(), e.getMessage());
        }

        pacienteRepository.save(paciente);
        return isNew;
    }

    private String gerarSha256(String input) {
        if (input == null || input.isBlank()) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(input.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 indisponível", e);
        }
    }

    private String normalizarBusca(String input) {
        if (input == null) return "";
        String ascii = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return ascii.toUpperCase();
    }
}
