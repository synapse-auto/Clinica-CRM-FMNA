package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.config.RabbitMQConfig;
import com.synapse.clinicafemina.dto.MensagemDTO;
import com.synapse.clinicafemina.messaging.MensagemEntradaEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Consome eventos do RabbitMQ e os converte em frames STOMP para os clientes conectados.
 *
 * Mapeamento de filas → destinos STOMP (conforme {@code stomp-topics.md}):
 * <ul>
 *   <li>{@code mensagem.entrada}  → {@code /user/{atendenteId}/queue/mensagens}</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Recebe evento de nova mensagem inbound e faz o push STOMP para o
     * atendente responsável via fila pessoal {@code /user/queue/mensagens}.
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_MENSAGEM_ENTRADA)
    public void onMensagemEntrada(MensagemEntradaEvent event) {
        log.info("Broadcast STOMP → atendente {} | atendimento {}",
                event.atendenteResponsavelId(), event.atendimentoId());

        // Payload conforme o contrato stomp-topics.md /user/queue/mensagens
        Map<String, Object> payload = Map.of(
                "tipo", "MENSAGEM_ENTRADA",
                "atendimentoId", event.atendimentoId(),
                "mensagem", Map.of(
                        "id", event.mensagemId(),
                        "direcao", "ENTRADA",
                        "remetente", "PACIENTE",
                        "tipoMedia", event.tipoMedia(),
                        "conteudo", event.conteudoPrevia(),
                        "dataHora", event.dataHora().toString()
                ),
                "paciente", Map.of(
                        "id", event.pacienteId(),
                        "nome", event.pacienteNomeBusca()
                ),
                "naoLidas", event.naoLidas()
        );

        if (event.atendenteResponsavelId() != null) {
            // Push direcionado ao atendente responsável
            messagingTemplate.convertAndSendToUser(
                    event.atendenteResponsavelId().toString(),
                    "/queue/mensagens",
                    payload
            );
        } else {
            // Nenhum atendente atribuído: broadcast para o tópico da clínica
            messagingTemplate.convertAndSend(
                    "/topic/dashboard/" + event.clinicaId(),
                    Map.of("codigo", "NOVA_MENSAGEM_SEM_ATENDENTE",
                           "atendimentoId", event.atendimentoId())
            );
        }
    }

    /**
     * Publica atualização de status de mensagem outbound para o atendente
     * no canal {@code /user/queue/status-mensagem}.
     */
    public void broadcastStatusMensagem(Long atendenteId, Long mensagemId,
                                        Long atendimentoId, String status) {
        Map<String, Object> payload = Map.of(
                "tipo", "STATUS_MENSAGEM",
                "mensagemId", mensagemId,
                "atendimentoId", atendimentoId,
                "status", status
        );
        messagingTemplate.convertAndSendToUser(
                atendenteId.toString(), "/queue/status-mensagem", payload);
    }

    /**
     * Publica notificação de transferência de atendimento para o novo atendente.
     */
    public void broadcastTransferencia(Long novoAtendenteId, Long atendimentoId,
                                       Long antigoAtendenteId, String antigoAtendenteNome,
                                       Long pacienteId, String pacienteNome, String motivo) {
        Map<String, Object> payload = Map.of(
                "tipo", "ATENDIMENTO_TRANSFERIDO",
                "atendimentoId", atendimentoId,
                "de", Map.of("id", antigoAtendenteId, "nome", antigoAtendenteNome),
                "paciente", Map.of("id", pacienteId, "nome", pacienteNome),
                "motivo", motivo != null ? motivo : ""
        );
        messagingTemplate.convertAndSendToUser(
                novoAtendenteId.toString(), "/queue/transferencias", payload);
    }

    /**
     * Broadcast de presença de equipe para todos os conectados.
     */
    public void broadcastPresenca(Long usuarioId, String nome, boolean online) {
        Map<String, Object> payload = Map.of(
                "usuarioId", usuarioId,
                "nome", nome,
                "online", online
        );
        messagingTemplate.convertAndSend("/topic/presenca-equipe", payload);
    }
}
