package com.synapse.clinicafemina.repository;

import com.synapse.clinicafemina.domain.Atendimento;
import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Mensagem;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.domain.Recepcionista;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.clinic.slug=fmna"
})
class MensagemRepositoryTest {

    @Autowired
    private MensagemRepository mensagemRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void should_fetch_atendimento_and_atendente_for_whatsapp_status_broadcast() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        String whatsappMessageId = "wamid-" + UUID.randomUUID();

        Long clinicaId = tx.execute(status -> {
            Clinica clinica = criarClinica();
            entityManager.persist(clinica);

            Recepcionista atendente = criarRecepcionista(clinica);
            entityManager.persist(atendente);

            Paciente paciente = criarPaciente(clinica);
            entityManager.persist(paciente);

            Atendimento atendimento = new Atendimento();
            atendimento.setClinica(clinica);
            atendimento.setPaciente(paciente);
            atendimento.setAtendentePrincipal(atendente);
            atendimento.setStatus("ATIVO");
            atendimento.setTratadoPorIa(false);
            atendimento.setNaoLidas(0);
            entityManager.persist(atendimento);

            Mensagem mensagem = new Mensagem();
            mensagem.setAtendimento(atendimento);
            mensagem.setDirecao("SAIDA");
            mensagem.setRemetente("IA");
            mensagem.setTipoMedia("TEXTO");
            mensagem.setConteudo("Resposta");
            mensagem.setConteudoPrevia("Resposta");
            mensagem.setWhatsappMessageId(whatsappMessageId);
            mensagem.setWhatsappStatus("SENT");
            mensagem.setDataHora(OffsetDateTime.parse("2026-07-03T12:00:00Z"));
            entityManager.persist(mensagem);

            entityManager.flush();
            entityManager.clear();
            return clinica.getId();
        });

        Mensagem found = tx.execute(status -> mensagemRepository
                .findByClinicaIdAndWhatsappMessageId(clinicaId, whatsappMessageId)
                .orElseThrow());

        assertDoesNotThrow(() -> found.getAtendimento().getAtendentePrincipal().getId());
        assertEquals("SENT", found.getWhatsappStatus());
    }

    private Clinica criarClinica() {
        Clinica clinica = new Clinica();
        clinica.setNome("Clinica Teste");
        clinica.setSlug("fmna-" + UUID.randomUUID());
        clinica.setRazaoSocial("Clinica Teste LTDA");
        clinica.setCnpj(UUID.randomUUID().toString().substring(0, 18));
        clinica.setEmailContato("teste-" + UUID.randomUUID() + "@clinica.local");
        clinica.setTelefoneContato("44999999999");
        return clinica;
    }

    private Recepcionista criarRecepcionista(Clinica clinica) {
        Recepcionista usuario = new Recepcionista();
        usuario.setClinica(clinica);
        usuario.setNome("Atendente");
        usuario.setEmail("atendente-" + UUID.randomUUID() + "@clinica.local");
        usuario.setSenhaHash("$2a$12$123456789012345678901u1234567890123456789012345678901234");
        usuario.setAtivo(true);
        return usuario;
    }

    private Paciente criarPaciente(Clinica clinica) {
        Paciente paciente = new Paciente();
        paciente.setClinica(clinica);
        paciente.setNome("Paciente");
        paciente.setNomeBusca("PACIENTE");
        paciente.setTelefone("44999999999");
        paciente.setTelefoneNormalizado("554499999999");
        paciente.setExternalSource(ExternalProviderType.WHATSAPP);
        paciente.setExternalId("554499999999");
        return paciente;
    }
}
