package com.synapse.clinicafemina.domain;

import com.synapse.clinicafemina.integration.whatsapp.WhatsappProviderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "paciente_foto_perfil")
@Getter
@Setter
public class PacienteFotoPerfil {

    @Id
    @Column(name = "paciente_id")
    private Long pacienteId;

    @MapsId
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id")
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinica_id", nullable = false)
    private Clinica clinica;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WhatsappProviderType provider;

    @Column
    private byte[] conteudo;

    @Column(name = "content_type", length = 30)
    private String contentType;

    @Column(length = 64)
    private String sha256;

    @Column(name = "tamanho_bytes")
    private Long tamanhoBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PacienteFotoStatus status;

    @Column(nullable = false)
    private Integer tentativas = 0;

    @Column(name = "ultima_tentativa_em")
    private OffsetDateTime ultimaTentativaEm;

    @Column(name = "proxima_tentativa_em")
    private OffsetDateTime proximaTentativaEm;

    @Column(name = "obtida_em")
    private OffsetDateTime obtidaEm;

    @Column(name = "motivo_ultima_falha", length = 100)
    private String motivoUltimaFalha;

    @Column(name = "atualizada_em", nullable = false)
    private OffsetDateTime atualizadaEm;
}
