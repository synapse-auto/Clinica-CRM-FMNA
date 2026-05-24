package com.synapse.clinicafemina.domain;

import com.synapse.clinicafemina.security.crypto.AesGcmConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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

    @Column(name = "darwin_id_externo", length = 100, unique = true)
    private String darwinIdExterno;

    @Column(name = "darwin_dados_importados", columnDefinition = "jsonb")
    private String darwinDadosImportados;

    @Column(name = "requer_revisao", nullable = false)
    private Boolean requerRevisao = false;

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
