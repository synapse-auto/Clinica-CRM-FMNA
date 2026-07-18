package com.synapse.clinicafemina.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.synapse.clinicafemina.config.PerformanceCacheConfig;
import com.synapse.clinicafemina.repository.AgendamentoRepository;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import com.synapse.clinicafemina.repository.MensagemRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import com.synapse.clinicafemina.service.cache.DashboardCacheKey;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = DashboardQueryServiceCacheTest.TestConfig.class)
class DashboardQueryServiceCacheTest {

    @Autowired
    private DashboardQueryService service;

    @Autowired
    private AgendamentoRepository agendamentoRepository;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        reset(agendamentoRepository);
        cacheManager.getCache(PerformanceCacheConfig.DASHBOARD_SERVICES).clear();
    }

    @Test
    void should_cache_same_key_and_isolate_clinic_and_period() {
        when(agendamentoRepository.countServicosByClinicaAndPeriodo(anyLong(), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{"CONSULTA", 2L}));
        DashboardCacheKey first = key(1L, "2026-07-01T00:00:00-03:00");

        service.services(first);
        service.services(first);
        service.services(key(2L, "2026-07-01T00:00:00-03:00"));
        service.services(key(1L, "2026-08-01T00:00:00-03:00"));

        verify(agendamentoRepository, times(3))
                .countServicosByClinicaAndPeriodo(anyLong(), any(), any());
    }

    @Test
    void should_cache_valid_empty_result_but_not_exception() {
        DashboardCacheKey key = key(1L, "2026-07-01T00:00:00-03:00");
        when(agendamentoRepository.countServicosByClinicaAndPeriodo(anyLong(), any(), any()))
                .thenThrow(new IllegalStateException("database unavailable"))
                .thenReturn(List.of());

        assertThrows(IllegalStateException.class, () -> service.services(key));
        assertEquals(List.of(), service.services(key));
        assertEquals(List.of(), service.services(key));
        verify(agendamentoRepository, times(2))
                .countServicosByClinicaAndPeriodo(anyLong(), any(), any());
    }

    @Test
    void should_coalesce_simultaneous_miss_for_same_key() throws Exception {
        CountDownLatch repositoryStarted = new CountDownLatch(1);
        CountDownLatch releaseRepository = new CountDownLatch(1);
        when(agendamentoRepository.countServicosByClinicaAndPeriodo(anyLong(), any(), any()))
                .thenAnswer(invocation -> {
                    repositoryStarted.countDown();
                    releaseRepository.await(2, TimeUnit.SECONDS);
                    return List.<Object[]>of(new Object[]{"CONSULTA", 1L});
                });
        DashboardCacheKey key = key(1L, "2026-07-01T00:00:00-03:00");

        CompletableFuture<?> first = CompletableFuture.supplyAsync(() -> service.services(key));
        repositoryStarted.await(2, TimeUnit.SECONDS);
        List<CompletableFuture<?>> followers = List.of(
                CompletableFuture.supplyAsync(() -> service.services(key)),
                CompletableFuture.supplyAsync(() -> service.services(key)),
                CompletableFuture.supplyAsync(() -> service.services(key))
        );
        releaseRepository.countDown();
        CompletableFuture.allOf(first, followers.get(0), followers.get(1), followers.get(2)).get(3, TimeUnit.SECONDS);

        verify(agendamentoRepository).countServicosByClinicaAndPeriodo(anyLong(), any(), any());
    }

    private DashboardCacheKey key(Long clinicId, String start) {
        OffsetDateTime inicio = OffsetDateTime.parse(start);
        return new DashboardCacheKey(
                clinicId,
                inicio,
                inicio.plusMonths(1),
                "America/Sao_Paulo",
                true
        );
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCaching
    static class TestConfig {

        @Bean
        CacheManager cacheManager() {
            CaffeineCacheManager manager = new CaffeineCacheManager(
                    PerformanceCacheConfig.DASHBOARD_SERVICES);
            manager.setCaffeine(Caffeine.newBuilder().maximumSize(500));
            return manager;
        }

        @Bean
        DashboardQueryService dashboardQueryService(
                UsuarioRepository usuarioRepository,
                PacienteRepository pacienteRepository,
                MensagemRepository mensagemRepository,
                AgendamentoRepository agendamentoRepository,
                AtendimentoRepository atendimentoRepository
        ) {
            return new DashboardQueryService(
                    usuarioRepository,
                    pacienteRepository,
                    mensagemRepository,
                    agendamentoRepository,
                    atendimentoRepository
            );
        }

        @Bean UsuarioRepository usuarioRepository() { return mock(UsuarioRepository.class); }
        @Bean PacienteRepository pacienteRepository() { return mock(PacienteRepository.class); }
        @Bean MensagemRepository mensagemRepository() { return mock(MensagemRepository.class); }
        @Bean AgendamentoRepository agendamentoRepository() { return mock(AgendamentoRepository.class); }
        @Bean AtendimentoRepository atendimentoRepository() { return mock(AtendimentoRepository.class); }
    }
}
