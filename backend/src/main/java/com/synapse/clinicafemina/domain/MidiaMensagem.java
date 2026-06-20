package com.synapse.clinicafemina.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "midia_mensagem")
@Getter
@Setter
public class MidiaMensagem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mensagem_id", nullable = false)
    private Mensagem mensagem;

    @Column(name = "tipo_media", nullable = false, length = 20)
    private String tipoMedia;

    @Column(name = "mime_type", nullable = false, length = 120)
    private String mimeType;

    @Column(name = "s3_bucket", length = 100)
    private String s3Bucket;

    @Column(name = "s3_chave")
    private byte[] s3Chave;

    @Column(name = "tamanho_bytes", nullable = false)
    private Long tamanhoBytes = 0L;

    @Column(name = "duracao_segundos")
    private Integer duracaoSegundos;

    @Column(name = "whatsapp_media_id", length = 100)
    private String whatsappMediaId;

    @Column(name = "nome_arquivo", length = 255)
    private String nomeArquivo;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @PrePersist
    protected void onCreate() {
        criadoEm = OffsetDateTime.now();
    }
}
