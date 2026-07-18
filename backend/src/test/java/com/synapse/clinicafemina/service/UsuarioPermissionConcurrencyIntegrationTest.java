package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Gestor;
import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.dto.equipe.PermissaoGerenciamentoRequest;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.repository.ClinicaRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.security.jwt.secret=test-only-secret-key-with-at-least-32-bytes",
        "app.initial-users.enabled=false"
})
class UsuarioPermissionConcurrencyIntegrationTest {

    @Autowired
    private EquipeService equipeService;
    @Autowired
    private ClinicaRepository clinicaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;

    @Test
    void concurrent_revocations_should_keep_one_authorized_manager() throws Exception {
        Clinica clinica = new Clinica();
        clinica.setNome("Clínica concorrência");
        clinica.setSlug("concorrencia-permissao");
        clinica.setRazaoSocial("Clínica concorrência LTDA");
        clinica.setCnpj("44.444.444/0001-44");
        clinica.setEmailContato("concorrencia@clinica.test");
        clinica.setTelefoneContato("44977777777");
        clinica = clinicaRepository.saveAndFlush(clinica);

        Gestor gestorA = saveManager(clinica, "Gestor A", "gestor.a@concorrencia.test");
        Gestor gestorB = saveManager(clinica, "Gestor B", "gestor.b@concorrencia.test");
        Long clinicaId = clinica.getId();
        Long gestorAId = gestorA.getId();
        Long gestorBId = gestorB.getId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = executor.submit(() -> revokeAfterSignal(gestorAId, start));
            Future<Boolean> second = executor.submit(() -> revokeAfterSignal(gestorBId, start));
            start.countDown();

            List<Boolean> results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

            assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
            assertEquals(1L, usuarioRepository.countGestoresAutorizadosAtivosByClinicaId(clinicaId));
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean revokeAfterSignal(Long managerId, CountDownLatch start) throws InterruptedException {
        start.await(5, TimeUnit.SECONDS);
        Usuario principal = usuarioRepository.findById(managerId).orElseThrow();
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                principal.getAuthorities()
        );
        try {
            equipeService.alterarPermissaoGerenciamento(
                    managerId,
                    new PermissaoGerenciamentoRequest(false),
                    authentication
            );
            return true;
        } catch (BadRequestException exception) {
            assertTrue(exception.getMessage().contains("ao menos um gestor"));
            return false;
        }
    }

    private Gestor saveManager(Clinica clinica, String name, String email) {
        Gestor manager = new Gestor();
        manager.setClinica(clinica);
        manager.setNome(name);
        manager.setEmail(email);
        manager.setSenhaHash("$2a$12$test-only-not-used-in-authentication-000000000000000000000000");
        manager.setAtivo(true);
        manager.setAdminInterno(false);
        manager.setMustChangePassword(false);
        manager.setPodeGerenciarUsuarios(true);
        return usuarioRepository.saveAndFlush(manager);
    }
}
