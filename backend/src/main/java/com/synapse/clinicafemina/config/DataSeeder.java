package com.synapse.clinicafemina.config;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Gestor;
import com.synapse.clinicafemina.domain.TipoClinica;
import com.synapse.clinicafemina.integration.external.ExternalProviderType;
import com.synapse.clinicafemina.repository.ClinicaRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final ClinicaRepository clinicaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.dev-seed.enabled:false}")
    private boolean seedEnabled;

    @Value("${app.dev-seed.email:}")
    private String seedEmail;

    @Value("${app.dev-seed.password:}")
    private String seedPassword;

    @Value("${app.clinic.slug:fmna}")
    private String clinicSlug;

    @Value("${app.clinic.name:Clínica Femina CRM}")
    private String clinicName;

    @Value("${app.clinic.external-provider:DARWIN}")
    private String externalProvider;

    @Value("${app.clinic.whatsapp-phone-number-id:}")
    private String whatsappPhoneNumberId;

    @Override
    public void run(String... args) {
        if (!seedEnabled) {
            log.debug("Seed de desenvolvimento desabilitado.");
            return;
        }

        if (seedEmail == null || seedEmail.isBlank() || seedPassword == null || seedPassword.isBlank()) {
            throw new IllegalStateException("DEV_SEED_EMAIL e DEV_SEED_PASSWORD são obrigatórios quando DEV_SEED_ENABLED=true");
        }

        String slug = blankToDefault(clinicSlug, "fmna");
        String nome = blankToDefault(clinicName, "Clínica Femina CRM");
        ExternalProviderType provider = parseClinicProvider(externalProvider);

        Clinica clinica = clinicaRepository.findBySlug(slug).orElse(null);
        if (clinica == null) {
            log.info("Banco de dados vazio. Criando clínica de desenvolvimento.");
            
            clinica = new Clinica();
            clinica.setNome(nome);
            clinica.setSlug(slug);
            clinica.setTipoClinica(provider == ExternalProviderType.MEDWARE ? TipoClinica.ULTRASSONOGRAFIA : TipoClinica.PRE_NATAL);
            clinica.setExternalProvider(provider);
            clinica.setRazaoSocial(nome + " LTDA");
            clinica.setCnpj("00.000.000/0001-00");
            clinica.setEmailContato("contato@clinica.com");
            clinica.setTelefoneContato("11999999999");
            clinica.setFusoHorario("America/Sao_Paulo");
            clinica.setUsaCirurgiasNaAgenda(provider == ExternalProviderType.DARWIN);
            clinica.setWhatsappPhoneNumberId(blankToNull(whatsappPhoneNumberId));
            
            clinica = clinicaRepository.save(clinica);
        }

        if (usuarioRepository.count() == 0) {
            log.info("Criando usuário gestor de desenvolvimento.");

            Gestor gestor = new Gestor();
            gestor.setClinica(clinica);
            gestor.setNome("Administrador do Sistema");
            gestor.setEmail(seedEmail);
            gestor.setSenhaHash(passwordEncoder.encode(seedPassword));
            gestor.setAtivo(true);
            gestor.setTemaPreferencia("CLARO");

            usuarioRepository.save(gestor);
            log.info("Usuário gestor de desenvolvimento criado.");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private ExternalProviderType parseClinicProvider(String value) {
        ExternalProviderType provider = ExternalProviderType.valueOf(blankToDefault(value, "DARWIN").toUpperCase());
        if (provider != ExternalProviderType.DARWIN && provider != ExternalProviderType.MEDWARE) {
            throw new IllegalStateException("EXTERNAL_PROVIDER deve ser DARWIN ou MEDWARE para a clínica atual");
        }
        return provider;
    }
}
