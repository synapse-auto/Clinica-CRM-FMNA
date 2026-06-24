package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.dto.paciente.PacienteResumoDTO;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import com.synapse.clinicafemina.repository.PacienteRepository;
import java.time.OffsetDateTime;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PacienteServiceTest {

    @Mock
    private PacienteRepository pacienteRepository;

    private PacienteService service;
    private Clinica clinica;

    @BeforeEach
    void setUp() {
        service = new PacienteService(pacienteRepository);
        clinica = new Clinica();
        clinica.setId(1L);
        clinica.setNome("Clínica Femina");
        clinica.setSlug("fmna");
    }

    @Test
    void listar_retornaListaVaziaQuandoNaoHaPacientes() {
        when(pacienteRepository.findDisponiveisByClinicaId(clinica.getId()))
                .thenReturn(List.of());

        List<PacienteResumoDTO> result = service.listar(clinica);

        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    void listar_retornaPacientesAtivosOrdenadosPorNomeBusca() {
        Paciente p1 = pacienteComNome("Ana Lima", "55119999-0001");
        Paciente p2 = pacienteComNome("Beatriz Souza", "55119999-0002");
        when(pacienteRepository.findDisponiveisByClinicaId(clinica.getId()))
                .thenReturn(List.of(p1, p2));

        List<PacienteResumoDTO> result = service.listar(clinica);

        assertEquals(2, result.size());
        assertEquals("Ana Lima", result.get(0).nome());
        assertEquals("Beatriz Souza", result.get(1).nome());
    }

    @Test
    void listar_mapeiaTelefoneNormalizadoNaResposta() {
        Paciente paciente = pacienteComNome("Maria Silva", "5511988887777");
        when(pacienteRepository.findDisponiveisByClinicaId(clinica.getId()))
                .thenReturn(List.of(paciente));

        List<PacienteResumoDTO> result = service.listar(clinica);

        assertEquals("5511988887777", result.get(0).telefone());
    }

    @Test
    void listar_incluiExternalSourceQuandoDisponivel() {
        Paciente paciente = pacienteComNome("Julia Costa", "5511977776666");
        paciente.setExternalSource(ExternalProviderType.MEDWARE);
        paciente.setExternalId("MW-1001");
        when(pacienteRepository.findDisponiveisByClinicaId(clinica.getId()))
                .thenReturn(List.of(paciente));

        List<PacienteResumoDTO> result = service.listar(clinica);

        assertEquals("MEDWARE", result.get(0).externalSource());
        assertEquals("MW-1001", result.get(0).externalId());
    }

    @Test
    void buscarPorId_retornaPacienteExistente() {
        Paciente paciente = pacienteComNome("Clara Dias", "5511966665555");
        paciente.setId(10L);
        when(pacienteRepository.findByIdAndClinicaId(10L, clinica.getId()))
                .thenReturn(Optional.of(paciente));

        PacienteResumoDTO result = service.buscarPorId(10L, clinica);

        assertNotNull(result);
        assertEquals("Clara Dias", result.nome());
        assertEquals(10L, result.id());
    }

    @Test
    void buscarPorId_lancaNotFoundQuandoInexistente() {
        when(pacienteRepository.findByIdAndClinicaId(999L, clinica.getId()))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.buscarPorId(999L, clinica));
    }

    @Test
    void buscarPorId_lancaNotFoundQuandoPacienteEstaDeleteado() {
        Paciente deletado = pacienteComNome("Paciente Deletado", "5511911112222");
        deletado.setId(20L);
        deletado.setDeletadoEm(OffsetDateTime.now());
        when(pacienteRepository.findByIdAndClinicaId(20L, clinica.getId()))
                .thenReturn(Optional.of(deletado));

        assertThrows(NotFoundException.class, () -> service.buscarPorId(20L, clinica));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Paciente pacienteComNome(String nome, String telefone) {
        Paciente paciente = new Paciente();
        paciente.setNome(nome);
        paciente.setNomeBusca(nome.toLowerCase());
        paciente.setTelefoneNormalizado(telefone);
        paciente.setStatus("EM_ATENDIMENTO");
        paciente.setCriadoEm(OffsetDateTime.now());
        return paciente;
    }
}
