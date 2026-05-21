package com.synapse.clinicafemina.domain;

import com.synapse.clinicafemina.security.crypto.AesGcmConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "mensagem")
@Getter
@Setter
public class Mensagem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atendimento_id", nullable = false)
    private Atendimento atendimento;

    @Column(nullable = false, length = 10)
    private String direcao;

    @Column(nullable = false, length = 20)
    private String remetente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "remetente_usuario_id")
    private Usuario remetenteUsuario;

    @Column(name = "tipo_media", nullable = false, length = 20)
    private String tipoMedia;

    @Convert(converter = AesGcmConverter.class)
    private String conteudo;

    @Column(name = "conteudo_previa", length = 60)
    private String conteudoPrevia;

    @Column(name = "data_hora", nullable = false)
    private OffsetDateTime dataHora;

    @Column(name = "whatsapp_message_id", length = 100, unique = true)
    private String whatsappMessageId;

    @Column(name = "whatsapp_status", length = 20)
    private String whatsappStatus;

    @Column(name = "mensagem_rapida_id")
    private Long mensagemRapidaId;

    @Column(name = "entregue_em")
    private OffsetDateTime entregueEm;

    @Column(name = "lida_em")
    private OffsetDateTime lidaEm;

    @Column(name = "motivo_falha", length = 255)
    private String motivoFalha;

    @Column(name = "chave_criptografia_id", nullable = false, length = 20)
    private String chaveCriptografiaId = "v1";

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @PrePersist
    protected void onCreate() {
        if (dataHora == null) dataHora = OffsetDateTime.now();
        criadoEm = OffsetDateTime.now();
    }
}
