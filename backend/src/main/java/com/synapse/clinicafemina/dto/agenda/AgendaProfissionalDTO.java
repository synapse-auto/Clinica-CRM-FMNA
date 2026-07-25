package com.synapse.clinicafemina.dto.agenda;

/**
 * Profissional normalizado, independente do provider (Medware/Darwin).
 */
public record AgendaProfissionalDTO(String id, String nome, String origem) {}
