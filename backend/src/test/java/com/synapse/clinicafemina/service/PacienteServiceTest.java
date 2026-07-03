package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Tag;
import com.synapse.clinicafemina.dto.paciente.PacienteResumoDTO;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.repository.PacienteRepository;
import com.synapse.clinicafemina.repository.PacienteTagRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PacienteServiceTest {

    @Mock
    private PacienteRepository pacienteRepository;

    @Mock
    private PacienteTagRepository pacienteTagRepository;

    private PacienteService service;
    private Clinica clinica;

    @BeforeEach
    void setUp() {
        service = new PacienteService(pacienteRepository, pacienteTagRepository);
        clinica = new Clinica();
        clinica.setId(1L);
        clinica.setNome("Clínica Femina");
        clinica.setSlug("fmna");
    }

    @Test
    void listar_retornaListaVaziaQuandoNaoHaPacientes() {
        when(pacienteRepository.findResumosDisponiveisByClinicaId(clinica.getId()))
                .thenReturn(List.of());

        List<PacienteResumoDTO> result = service.listar(clinica);

        assertNotNull(result);
        assertEquals(0, result.size());
        verify(pacienteRepository, never()).findDisponiveisByClinicaId(clinica.getId());
    }

    @Test
    void listar_retornaPacientesAtivosOrdenadosPorNomeBuscaSemCarregarEntidadeCriptografada() {
        PacienteRepository.PacienteResumoProjection p1 = resumoProjection(1L, "ana lima", "551199990001");
        PacienteRepository.PacienteResumoProjection p2 = resumoProjection(2L, "beatriz souza", "551199990002");
        when(pacienteRepository.findResumosDisponiveisByClinicaId(clinica.getId()))
                .thenReturn(List.of(p1, p2));

        List<PacienteResumoDTO> result = service.listar(clinica);

        assertEquals(2, result.size());
        assertEquals("ana lima", result.get(0).nome());
        assertEquals("beatriz souza", result.get(1).nome());
        verify(pacienteRepository, never()).findDisponiveisByClinicaId(clinica.getId());
    }

    @Test
    void listar_mapeiaTelefoneNormalizadoNaResposta() {
        PacienteRepository.PacienteResumoProjection paciente = resumoProjection(3L, "maria silva", "5511988887777");
        when(pacienteRepository.findResumosDisponiveisByClinicaId(clinica.getId()))
                .thenReturn(List.of(paciente));

        List<PacienteResumoDTO> result = service.listar(clinica);

        assertEquals("5511988887777", result.get(0).telefone());
    }

    @Test
    void listar_incluiExternalSourceQuandoDisponivel() {
        PacienteRepository.PacienteResumoProjection paciente = resumoProjection(
                4L,
                "julia costa",
                "5511977776666",
                "MEDWARE",
                "MW-1001"
        );
        when(pacienteRepository.findResumosDisponiveisByClinicaId(clinica.getId()))
                .thenReturn(List.of(paciente));

        List<PacienteResumoDTO> result = service.listar(clinica);

        assertEquals("MEDWARE", result.get(0).externalSource());
        assertEquals("MW-1001", result.get(0).externalId());
    }

    @Test
    void listar_incluiTagsDoPacienteNaClinicaAtual() {
        PacienteRepository.PacienteResumoProjection paciente = resumoProjection(6L, "nina costa", "5511955554444");
        Tag tag = new Tag();
        tag.setId(15L);
        tag.setNome("VIP");
        tag.setCor("#0d9488");
        tag.setAtivo(true);
        tag.setCriadoEm(OffsetDateTime.parse("2026-06-15T12:00:00Z"));
        tag.setAtualizadoEm(OffsetDateTime.parse("2026-06-15T12:00:00Z"));
        when(pacienteRepository.findResumosDisponiveisByClinicaId(clinica.getId()))
                .thenReturn(List.of(paciente));
        when(pacienteTagRepository.findTagsByPacienteIdAndClinicaId(6L, clinica.getId()))
                .thenReturn(List.of(tag));

        List<PacienteResumoDTO> result = service.listar(clinica);

        assertEquals(1, result.get(0).tags().size());
        assertEquals("VIP", result.get(0).tags().get(0).nome());
    }

    @Test
    void listar_converteTimestampsInstantDaProjecaoParaOffsetDateTimeUtc() {
        Instant criadoEm = Instant.parse("2026-06-29T15:44:03Z");
        Instant ultimaInteracaoEm = Instant.parse("2026-06-29T16:10:00Z");
        PacienteRepository.PacienteResumoProjection paciente = resumoProjection(
                5L,
                "lucas rezende",
                "5511966660000",
                null,
                null,
                criadoEm,
                ultimaInteracaoEm
        );
        when(pacienteRepository.findResumosDisponiveisByClinicaId(clinica.getId()))
                .thenReturn(List.of(paciente));

        List<PacienteResumoDTO> result = service.listar(clinica);

        assertEquals(OffsetDateTime.ofInstant(criadoEm, ZoneOffset.UTC), result.get(0).criadoEm());
        assertEquals(OffsetDateTime.ofInstant(ultimaInteracaoEm, ZoneOffset.UTC), result.get(0).ultimaInteracaoEm());
    }

    @Test
    void buscarPorId_retornaPacienteExistenteSemCarregarEntidadeCriptografada() {
        PacienteRepository.PacienteResumoProjection paciente = resumoProjection(10L, "clara dias", "5511966665555");
        when(pacienteRepository.findResumoByIdAndClinicaId(10L, clinica.getId()))
                .thenReturn(Optional.of(paciente));

        PacienteResumoDTO result = service.buscarPorId(10L, clinica);

        assertNotNull(result);
        assertEquals("clara dias", result.nome());
        assertEquals(10L, result.id());
        verify(pacienteRepository, never()).findByIdAndClinicaId(10L, clinica.getId());
    }

    @Test
    void buscarPorId_lancaNotFoundQuandoInexistenteOuDeletado() {
        when(pacienteRepository.findResumoByIdAndClinicaId(999L, clinica.getId()))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.buscarPorId(999L, clinica));
    }

    private PacienteRepository.PacienteResumoProjection resumoProjection(Long id, String nomeBusca, String telefone) {
        return resumoProjection(id, nomeBusca, telefone, null, null);
    }

    private PacienteRepository.PacienteResumoProjection resumoProjection(
            Long id,
            String nomeBusca,
            String telefone,
            String externalSource,
            String externalId
    ) {
        return resumoProjection(id, nomeBusca, telefone, externalSource, externalId, Instant.now(), null);
    }

    private PacienteRepository.PacienteResumoProjection resumoProjection(
            Long id,
            String nomeBusca,
            String telefone,
            String externalSource,
            String externalId,
            Instant criadoEm,
            Instant ultimaInteracaoEm
    ) {
        return new PacienteRepository.PacienteResumoProjection() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public Long getClinicaId() {
                return clinica.getId();
            }

            @Override
            public String getNomeBusca() {
                return nomeBusca;
            }

            @Override
            public String getTelefoneNormalizado() {
                return telefone;
            }

            @Override
            public String getStatus() {
                return "EM_ATENDIMENTO";
            }

            @Override
            public String getExternalSource() {
                return externalSource;
            }

            @Override
            public String getExternalId() {
                return externalId;
            }

            @Override
            public Instant getCriadoEm() {
                return criadoEm;
            }

            @Override
            public Instant getUltimaInteracaoEm() {
                return ultimaInteracaoEm;
            }
        };
    }
}
