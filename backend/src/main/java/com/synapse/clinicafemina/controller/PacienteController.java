package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.dto.paciente.PacienteResumoDTO;
import com.synapse.clinicafemina.service.ClinicaConfigService;
import com.synapse.clinicafemina.service.PacienteService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de leitura de pacientes.
 * Acesso somente leitura para todos os perfis autenticados da clínica.
 * Criação e edição manual de pacientes não fazem parte do escopo v1
 * — pacientes são populados via integração externa (Medware / Darwin).
 */
@RestController
@RequestMapping("/api/pacientes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('GESTOR', 'MEDICO', 'RECEPCIONISTA')")
public class PacienteController {

    private final ClinicaConfigService clinicaConfigService;
    private final PacienteService pacienteService;

    /**
     * GET /api/pacientes
     * Lista todos os pacientes ativos da clínica (sem paginação — volume clínico típico &lt; 5k).
     */
    @GetMapping
    public List<PacienteResumoDTO> listar() {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return pacienteService.listar(clinica);
    }

    /**
     * GET /api/pacientes/{id}
     * Retorna um paciente específico da clínica.
     */
    @GetMapping("/{id}")
    public PacienteResumoDTO buscarPorId(@PathVariable Long id) {
        Clinica clinica = clinicaConfigService.obterClinicaAtual();
        return pacienteService.buscarPorId(id, clinica);
    }
}
