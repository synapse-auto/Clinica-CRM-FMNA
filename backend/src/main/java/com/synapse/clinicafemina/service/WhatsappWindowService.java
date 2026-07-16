package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.exception.WhatsappWindowClosedException;
import com.synapse.clinicafemina.repository.MensagemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class WhatsappWindowService {

    private static final int WINDOW_HOURS = 24;

    private final MensagemRepository mensagemRepository;
    private final Clock clock;

    @Autowired
    public WhatsappWindowService(MensagemRepository mensagemRepository) {
        this(mensagemRepository, Clock.systemUTC());
    }

    WhatsappWindowService(MensagemRepository mensagemRepository, Clock clock) {
        this.mensagemRepository = mensagemRepository;
        this.clock = clock;
    }

    public WindowState avaliar(Long atendimentoId, Long clinicaId) {
        OffsetDateTime ultimaEntrada = mensagemRepository
                .findUltimaMensagemEntradaEm(atendimentoId, clinicaId)
                .orElse(null);
        OffsetDateTime agora = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        OffsetDateTime expiraEm = ultimaEntrada == null ? null : ultimaEntrada.plusHours(WINDOW_HOURS);
        boolean aberta = expiraEm != null && !expiraEm.isBefore(agora);
        boolean aguardando = !aberta && aguardandoResposta(atendimentoId, clinicaId, ultimaEntrada);
        return new WindowState(aberta, expiraEm, ultimaEntrada, aguardando);
    }

    public void exigirAberta(Long atendimentoId, Long clinicaId) {
        if (!avaliar(atendimentoId, clinicaId).aberta()) {
            throw new WhatsappWindowClosedException();
        }
    }

    private boolean aguardandoResposta(Long atendimentoId, Long clinicaId, OffsetDateTime ultimaEntrada) {
        return mensagemRepository.findUltimoTemplateSaidaValidoEm(atendimentoId, clinicaId)
                .filter(templateEm -> ultimaEntrada == null || templateEm.isAfter(ultimaEntrada))
                .isPresent();
    }

    public record WindowState(
            boolean aberta,
            OffsetDateTime expiraEm,
            OffsetDateTime ultimaMensagemEntradaEm,
            boolean aguardandoRespostaTemplate
    ) {
    }
}
