package com.synapse.clinicafemina.dto.darwin;

/**
 * Corpo de resposta padrão de sucesso das rotas de escrita Darwin
 * (POST schedules/create, POST schedules/create/fitin, PUT schedules/update, DELETE schedules/delete).
 */
public record DarwinWriteMessageResponse(String message) {}
