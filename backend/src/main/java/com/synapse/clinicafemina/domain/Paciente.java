package com.synapse.clinicafemina.domain;

import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import com.synapse.clinicafemina.security.crypto.AesGcmConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "paciente")
@Getter
@Setter
public class Paciente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clinica_id", nullable = false)
    private Clinica clinica;

    @Column(nullable = false)
    @Convert(converter = AesGcmConverter.class)
    private String nome;

    @Column(name = "nome_busca", nullable = false, length = 200)
    private String nomeBusca;

    @Convert(converter = AesGcmConverter.class)
    private String cpf;

    @Column(name = "cpf_hash", length = 64, unique = true)
    private String cpfHash;

    @Column(name = "data_nascimento")
    @Convert(converter = AesGcmConverter.class)
    private String dataNascimento;

    @Convert(converter = AesGcmConverter.class)
    private String email;

    @Column(name = "email_hash", length = 64)
    private String emailHash;

    @Column(name = "foto_url", length = 500)
    private String fotoUrl;

    @Column(nullable = false)
    @Convert(converter = AesGcmConverter.class)
    private String telefone;

    @Column(name = "telefone_normalizado", nullable = false, length = 20)
    private String telefoneNormalizado;

    @Convert(converter = AesGcmConverter.class)
    private String endereco;

    @Column(name = "horario_preferencial", length = 20)
    private String horarioPreferencial;

    @Column(name = "notas_internas")
    @Convert(converter = AesGcmConverter.class)
    private String notasInternas;

    @Column(nullable = false, length = 20)
    private String status = "EM_ATENDIMENTO";

    @Column(name = "follow_up_ativo", nullable = false)
    private Boolean followUpAtivo = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atendente_principal_id")
    private Usuario atendentePrincipal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medico_principal_id")
    private Usuario medicoPrincipal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atendimento_atual_id")
    private Atendimento atendimentoAtual;

    @Column(name = "valor_total")
    private BigDecimal valorTotal = BigDecimal.ZERO;

    @Column(name = "chave_criptografia_id", nullable = false, length = 20)
    private String chaveCriptografiaId = "v1";

    @Enumerated(EnumType.STRING)
    @Column(name = "external_source", length = 20)
    private ExternalProviderType externalSource;

    @Column(name = "external_id", length = 100)
    private String externalId;

    @Column(name = "external_payload", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String externalPayload;

    @Column(name = "google_drive_folder_id", length = 255)
    private String googleDriveFolderId;

    @Column(name = "requer_revisao", nullable = false)
    private Boolean requerRevisao = false;

    @Column(name = "convenio_status", length = 20)
    private String convenioStatus;

    @Column(name = "convenio_revisado_em")
    private OffsetDateTime convenioRevisadoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "convenio_revisado_por")
    private Usuario convenioRevisadoPor;

    @Column(name = "ultima_interacao_em")
    private OffsetDateTime ultimaInteracaoEm;

    @Column(name = "deletado_em")
    private OffsetDateTime deletadoEm;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    /** Usuário que criou o registro — auditoria LGPD. Coluna: criado_por */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criado_por")
    private Usuario criadoPor;

    /** Último usuário que alterou o registro — auditoria LGPD. Coluna: atualizado_por */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atualizado_por")
    private Usuario atualizadoPor;

    @PrePersist
    protected void onCreate() {
        criadoEm = OffsetDateTime.now();
        atualizadoEm = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        atualizadoEm = OffsetDateTime.now();
    }
}
