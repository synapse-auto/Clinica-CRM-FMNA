package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.config.PerformanceCacheConfig;
import com.synapse.clinicafemina.repository.AgendamentoRepository;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import com.synapse.clinicafemina.repository.MensagemRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import com.synapse.clinicafemina.service.cache.DashboardCacheKey;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = DashboardQueryServiceNoCacheTest.TestConfig.class)
class DashboardQueryServiceNoCacheTest {

    @Autowired
    private DashboardQueryService service;

    @Autowired
    private AgendamentoRepository agendamentoRepository;

    @Test
    void should_execute_query_every_time_when_cache_is_disabled() {
        when(agendamentoRepository.countServicosByClinicaAndPeriodo(anyLong(), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{"CONSULTA", 1L}));
        OffsetDateTime start = OffsetDateTime.parse("2026-07-01T00:00:00-03:00");
        DashboardCacheKey key = new DashboardCacheKey(
                1L, start, start.plusMonths(1), "America/Sao_Paulo", true);

        service.services(key);
        service.services(key);

        verify(agendamentoRepository, times(2))
                .countServicosByClinicaAndPeriodo(anyLong(), any(), any());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCaching
    static class TestConfig {

        @Bean
        CacheManager cacheManager() {
            return new PerformanceCacheConfig().noOpPerformanceCacheManager();
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
