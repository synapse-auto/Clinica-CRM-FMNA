package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Atendimento;
import com.synapse.clinicafemina.domain.Mensagem;
import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.dto.EnviarTemplateWhatsappRequest;
import com.synapse.clinicafemina.dto.MensagemDTO;
import com.synapse.clinicafemina.dto.WhatsappTemplateDTO;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.exception.WhatsappTemplateSendException;
import com.synapse.clinicafemina.integration.WhatsappOutboundClient;
import com.synapse.clinicafemina.repository.AtendimentoRepository;
import com.synapse.clinicafemina.repository.MensagemRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class WhatsappTemplateService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final Duration DUPLICATE_TTL = Duration.ofSeconds(30);
    private static final int MAX_PAGES = 10;
    private static final int MAX_TEMPLATES = 500;
    private static final int PREVIEW_LIMIT = 60;

    private final AtendimentoRepository atendimentoRepository;
    private final MensagemRepository mensagemRepository;
    private final UsuarioRepository usuarioRepository;
    private final WhatsappOutboundClient whatsappClient;
    private final WhatsappTemplateMapper templateMapper;
    private final Clock clock;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Map<String, OffsetDateTime> recentSends = new ConcurrentHashMap<>();

    @Autowired
    public WhatsappTemplateService(
            AtendimentoRepository atendimentoRepository,
            MensagemRepository mensagemRepository,
            UsuarioRepository usuarioRepository,
            WhatsappOutboundClient whatsappClient,
            WhatsappTemplateMapper templateMapper
    ) {
        this(atendimentoRepository, mensagemRepository, usuarioRepository,
                whatsappClient, templateMapper, Clock.systemUTC());
    }

    WhatsappTemplateService(
            AtendimentoRepository atendimentoRepository,
            MensagemRepository mensagemRepository,
            UsuarioRepository usuarioRepository,
            WhatsappOutboundClient whatsappClient,
            WhatsappTemplateMapper templateMapper,
            Clock clock
    ) {
        this.atendimentoRepository = atendimentoRepository;
        this.mensagemRepository = mensagemRepository;
        this.usuarioRepository = usuarioRepository;
        this.whatsappClient = whatsappClient;
        this.templateMapper = templateMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<WhatsappTemplateDTO> listar(Long atendimentoId, Long clinicaId) {
        buscarAtendimento(atendimentoId, clinicaId);
        return definitions().stream()
                .map(WhatsappTemplateMapper.TemplateDefinition::dto)
                .sorted(templateComparator())
                .toList();
    }

    @Transactional(noRollbackFor = WhatsappTemplateSendException.class)
    public MensagemDTO enviar(
            Long atendimentoId,
            Long clinicaId,
            Long remetenteUsuarioId,
            EnviarTemplateWhatsappRequest request
    ) {
        Atendimento atendimento = buscarAtendimentoAtivo(atendimentoId, clinicaId);
        Usuario remetente = buscarRemetente(remetenteUsuarioId, clinicaId);
        WhatsappTemplateMapper.TemplateDefinition definition = localizar(request.nome(), request.idioma());
        WhatsappTemplateMapper.PreparedTemplate prepared = templateMapper.prepare(
                definition, request.parametros()
        );
        String fingerprint = fingerprint(atendimentoId, definition.dto(), request.parametros());
        reservarEnvio(fingerprint);

        Mensagem mensagem = criarMensagem(atendimento, remetente, definition.dto(), prepared.preview());
        try {
            mensagem = mensagemRepository.save(mensagem);
            atendimento.setUltimaMensagemEm(mensagem.getDataHora());
            atendimentoRepository.save(atendimento);
            String wamid = whatsappClient.enviarTemplate(
                    atendimento.getPaciente().getTelefoneNormalizado(),
                    definition.dto().nome(),
                    definition.dto().idioma(),
                    prepared.metaComponents()
            );
            mensagem.setWhatsappMessageId(wamid);
            mensagem.setWhatsappStatus("ENVIADA");
            return toDTO(mensagemRepository.save(mensagem));
        } catch (Exception exception) {
            recentSends.remove(fingerprint);
            mensagem.setWhatsappStatus("FALHA");
            mensagem.setMotivoFalha("Falha no envio do template pela Meta");
            mensagemRepository.save(mensagem);
            log.error("Falha ao enviar template WhatsApp. atendimentoId={}, mensagemId={}, tipoErro={}",
                    atendimentoId, mensagem.getId(), exception.getClass().getSimpleName());
            throw new WhatsappTemplateSendException(exception);
        }
    }

    public void invalidarCache() {
        cache.clear();
    }

    private List<WhatsappTemplateMapper.TemplateDefinition> definitions() {
        String key = whatsappClient.configuracaoTemplatesKey();
        OffsetDateTime now = now();
        CacheEntry cached = cache.get(key);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.templates();
        }
        List<WhatsappTemplateMapper.TemplateDefinition> loaded = loadAllPages();
        cache.put(key, new CacheEntry(List.copyOf(loaded), now.plus(CACHE_TTL)));
        return loaded;
    }

    private List<WhatsappTemplateMapper.TemplateDefinition> loadAllPages() {
        List<WhatsappTemplateMapper.TemplateDefinition> result = new ArrayList<>();
        Set<String> seenCursors = new HashSet<>();
        String cursor = null;
        for (int pageNumber = 0; pageNumber < MAX_PAGES && result.size() < MAX_TEMPLATES; pageNumber++) {
            WhatsappOutboundClient.TemplatePage page = whatsappClient.listarTemplatesPagina(cursor);
            page.templates().stream()
                    .limit(MAX_TEMPLATES - result.size())
                    .map(templateMapper::map)
                    .forEach(result::add);
            String next = page.after();
            if (next == null || next.isBlank() || !seenCursors.add(next)) {
                break;
            }
            cursor = next;
        }
        return result;
    }

    private WhatsappTemplateMapper.TemplateDefinition localizar(String nome, String idioma) {
        return definitions().stream()
                .filter(item -> item.dto().nome().equals(nome.trim()))
                .filter(item -> item.dto().idioma().equalsIgnoreCase(idioma.trim()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Template ou idioma nao encontrado na conta Meta"));
    }

    private Atendimento buscarAtendimento(Long atendimentoId, Long clinicaId) {
        return atendimentoRepository.findByIdAndClinicaId(atendimentoId, clinicaId)
                .orElseThrow(() -> new NotFoundException("Atendimento nao encontrado"));
    }

    private Atendimento buscarAtendimentoAtivo(Long atendimentoId, Long clinicaId) {
        Atendimento atendimento = buscarAtendimento(atendimentoId, clinicaId);
        if (!"ATIVO".equals(atendimento.getStatus())) {
            throw new IllegalStateException("So e possivel enviar template para atendimento ativo");
        }
        return atendimento;
    }

    private Usuario buscarRemetente(Long usuarioId, Long clinicaId) {
        return usuarioRepository.findAtivoByIdAndClinicaId(usuarioId, clinicaId)
                .orElseThrow(() -> new NotFoundException("Usuario remetente nao encontrado"));
    }

    private Mensagem criarMensagem(
            Atendimento atendimento,
            Usuario remetente,
            WhatsappTemplateDTO template,
            String preview
    ) {
        Mensagem mensagem = new Mensagem();
        mensagem.setAtendimento(atendimento);
        mensagem.setDirecao("SAIDA");
        mensagem.setRemetente("ATENDENTE");
        mensagem.setRemetenteUsuario(remetente);
        mensagem.setTipoMedia("TEMPLATE");
        mensagem.setConteudo(preview);
        mensagem.setConteudoPrevia(limitPreview(preview));
        mensagem.setWhatsappStatus("PENDENTE");
        mensagem.setDataHora(now());
        mensagem.setTemplateNome(template.nome());
        mensagem.setTemplateIdioma(template.idioma());
        return mensagem;
    }

    private MensagemDTO toDTO(Mensagem mensagem) {
        return new MensagemDTO(
                mensagem.getId(),
                mensagem.getDirecao(),
                mensagem.getRemetente(),
                mensagem.getTipoMedia(),
                mensagem.getConteudo(),
                mensagem.getConteudoPrevia(),
                mensagem.getWhatsappStatus(),
                mensagem.getMotivoFalha(),
                mensagem.getDataHora(),
                mensagem.getEntregueEm(),
                mensagem.getLidaEm(),
                null,
                mensagem.getTemplateNome(),
                mensagem.getTemplateIdioma()
        );
    }

    private void reservarEnvio(String fingerprint) {
        OffsetDateTime now = now();
        recentSends.entrySet().removeIf(entry -> entry.getValue().plus(DUPLICATE_TTL).isBefore(now));
        OffsetDateTime existing = recentSends.putIfAbsent(fingerprint, now);
        if (existing != null && !existing.plus(DUPLICATE_TTL).isBefore(now)) {
            throw new IllegalStateException("Este template ja foi enviado recentemente");
        }
    }

    private String fingerprint(
            Long atendimentoId,
            WhatsappTemplateDTO template,
            List<EnviarTemplateWhatsappRequest.Parametro> parameters
    ) {
        StringBuilder source = new StringBuilder()
                .append(atendimentoId).append('|')
                .append(template.nome()).append('|')
                .append(template.idioma());
        parameters.stream()
                .sorted(Comparator.comparing(EnviarTemplateWhatsappRequest.Parametro::componente)
                        .thenComparing(EnviarTemplateWhatsappRequest.Parametro::posicao)
                        .thenComparing(parameter -> parameter.indiceBotao() == null ? -1 : parameter.indiceBotao()))
                .forEach(parameter -> source.append('|')
                        .append(parameter.componente()).append(':')
                        .append(parameter.posicao()).append(':')
                        .append(parameter.indiceBotao()).append(':')
                        .append(parameter.valor()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(source.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Nao foi possivel validar a idempotencia do envio", exception);
        }
    }

    private Comparator<WhatsappTemplateDTO> templateComparator() {
        return Comparator
                .comparing((WhatsappTemplateDTO item) -> "APPROVED".equals(item.status()) ? 0 : 1)
                .thenComparing(WhatsappTemplateDTO::nome, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(WhatsappTemplateDTO::idioma, String.CASE_INSENSITIVE_ORDER);
    }

    private String limitPreview(String preview) {
        return preview.length() <= PREVIEW_LIMIT ? preview : preview.substring(0, PREVIEW_LIMIT - 3) + "...";
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private record CacheEntry(
            List<WhatsappTemplateMapper.TemplateDefinition> templates,
            OffsetDateTime expiresAt
    ) {
    }
}
