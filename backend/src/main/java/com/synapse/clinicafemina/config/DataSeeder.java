package com.synapse.clinicafemina.config;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Gestor;
import com.synapse.clinicafemina.repository.ClinicaRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Override
    public void run(String... args) {
        if (clinicaRepository.count() == 0) {
            log.info("Banco de dados vazio. Criando Clínica Padrão...");
            
            Clinica clinica = new Clinica();
            clinica.setNome("Clínica Femina CRM");
            clinica.setRazaoSocial("Clinica Femina CRM LTDA");
            clinica.setCnpj("00.000.000/0001-00");
            clinica.setEmailContato("contato@clinica.com");
            clinica.setTelefoneContato("11999999999");
            clinica.setFusoHorario("America/Sao_Paulo");
            
            clinica = clinicaRepository.save(clinica);

            if (usuarioRepository.count() == 0) {
                log.info("Criando Usuário Gestor padrão...");
                
                Gestor gestor = new Gestor();
                gestor.setClinica(clinica);
                gestor.setNome("Administrador do Sistema");
                gestor.setEmail("gestor@clinica.com");
                gestor.setSenhaHash(passwordEncoder.encode("123456"));
                gestor.setAtivo(true);
                gestor.setTemaPreferencia("CLARO");
                
                usuarioRepository.save(gestor);
                log.info("Usuário Gestor criado com sucesso: gestor@clinica.com / 123456");
            }
        }
    }
}
