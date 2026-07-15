package com.synapse.clinicafemina.service;

final class ExternalSyncProgress {

    private int pacientesProcessados;
    private int pacientesCriados;
    private int pacientesAtualizados;
    private int agendamentosProcessados;
    private int agendamentosCriados;
    private int agendamentosAtualizados;
    private int agendamentosIgnorados;

    void registrarPaciente(boolean criado) {
        pacientesProcessados++;
        if (criado) {
            pacientesCriados++;
        } else {
            pacientesAtualizados++;
        }
    }

    void registrarPacienteMinimoCriado() {
        pacientesCriados++;
    }

    void registrarAgendamento(boolean criado) {
        agendamentosProcessados++;
        if (criado) {
            agendamentosCriados++;
        } else {
            agendamentosAtualizados++;
        }
    }

    void registrarAgendamentoIgnorado() {
        agendamentosIgnorados++;
    }

    ExternalSyncResult toResult(String status) {
        return new ExternalSyncResult(
                pacientesProcessados,
                pacientesCriados,
                pacientesAtualizados,
                agendamentosProcessados,
                agendamentosCriados,
                agendamentosAtualizados,
                agendamentosIgnorados,
                status
        );
    }

    int getPacientesProcessados() {
        return pacientesProcessados;
    }

    int getPacientesCriados() {
        return pacientesCriados;
    }

    int getPacientesAtualizados() {
        return pacientesAtualizados;
    }

    int getAgendamentosProcessados() {
        return agendamentosProcessados;
    }

    int getAgendamentosCriados() {
        return agendamentosCriados;
    }

    int getAgendamentosAtualizados() {
        return agendamentosAtualizados;
    }

    int getAgendamentosIgnorados() {
        return agendamentosIgnorados;
    }
}
