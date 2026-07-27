package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.domain.PacienteFotoPerfil;
import com.synapse.clinicafemina.domain.PacienteFotoStatus;
import com.synapse.clinicafemina.exception.NotFoundException;
import com.synapse.clinicafemina.integration.whatsapp.WhatsappProviderType;
import com.synapse.clinicafemina.integration.whatsapp.uazap.UazapProfilePhotoImageValidator;
import com.synapse.clinicafemina.repository.PacienteFotoPerfilRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class PacienteFotoPerfilService {

    private static final Duration CLAIM_LEASE = Duration.ofMinutes(5);
    private static final Duration SUCCESS_REFRESH = Duration.ofDays(7);
    private static final Duration NO_PHOTO_COOLDOWN = Duration.ofDays(7);
    private static final Duration PERMANENT_FAILURE_COOLDOWN = Duration.ofDays(30);
    private static final Duration TEMPORARY_FAILURE_BASE = Duration.ofMinutes(15);
    private static final Duration TEMPORARY_FAILURE_MAX = Duration.ofHours(24);

    private final PacienteRepository pacienteRepository;
    private final PacienteFotoPerfilRepository fotoRepository;
    private final Clock clock;

    @Autowired
    public PacienteFotoPerfilService(
            PacienteRepository pacienteRepository,
            PacienteFotoPerfilRepository fotoRepository
    ) {
        this(pacienteRepository, fotoRepository, Clock.systemUTC());
    }

    PacienteFotoPerfilService(
            PacienteRepository pacienteRepository,
            PacienteFotoPerfilRepository fotoRepository,
            Clock clock
    ) {
        this.pacienteRepository = pacienteRepository;
        this.fotoRepository = fotoRepository;
        this.clock = clock;
    }

    @Transactional
    public Optional<TentativaFoto> iniciar(Long pacienteId, Long clinicaId, boolean forcar) {
        Paciente paciente = pacienteRepository
                .findForPhotoUpdateByIdAndClinicaId(pacienteId, clinicaId)
                .orElse(null);
        if (paciente == null || paciente.getTelefoneNormalizado() == null
                || paciente.getTelefoneNormalizado().isBlank()) {
            return Optional.empty();
        }

        OffsetDateTime agora = OffsetDateTime.now(clock);
        PacienteFotoPerfil foto = fotoRepository
                .findByPacienteIdAndClinica_Id(pacienteId, clinicaId)
                .orElseGet(() -> novoEstado(paciente, agora));
        boolean pendingAtivo = foto.getStatus() == PacienteFotoStatus.PENDING
                && foto.getUltimaTentativaEm() != null
                && !foto.getUltimaTentativaEm().isBefore(agora.minus(CLAIM_LEASE));
        boolean cooldownAtivo = foto.getProximaTentativaEm() != null
                && foto.getProximaTentativaEm().isAfter(agora);
        if (pendingAtivo || (!forcar && cooldownAtivo)) {
            return Optional.empty();
        }

        int tentativas = Math.max(0, foto.getTentativas()) + 1;
        foto.setStatus(PacienteFotoStatus.PENDING);
        foto.setTentativas(tentativas);
        foto.setUltimaTentativaEm(agora);
        foto.setProximaTentativaEm(agora.plus(CLAIM_LEASE));
        foto.setAtualizadaEm(agora);
        fotoRepository.save(foto);
        return Optional.of(new TentativaFoto(
                pacienteId,
                clinicaId,
                paciente.getTelefoneNormalizado(),
                tentativas
        ));
    }

    private PacienteFotoPerfil novoEstado(Paciente paciente, OffsetDateTime agora) {
        PacienteFotoPerfil foto = new PacienteFotoPerfil();
        foto.setPacienteId(paciente.getId());
        foto.setPaciente(paciente);
        foto.setClinica(paciente.getClinica());
        foto.setProvider(WhatsappProviderType.UAZAP);
        foto.setStatus(PacienteFotoStatus.NO_PHOTO);
        foto.setTentativas(0);
        foto.setProximaTentativaEm(agora);
        foto.setAtualizadaEm(agora);
        return foto;
    }

    @Transactional
    public String salvarSucesso(
            TentativaFoto tentativa,
            UazapProfilePhotoImageValidator.ValidatedImage image
    ) {
        Paciente paciente = pacienteRepository
                .findByIdAndClinicaId(tentativa.pacienteId(), tentativa.clinicaId())
                .orElseThrow(() -> new NotFoundException("Paciente nao encontrado"));
        PacienteFotoPerfil foto = estado(tentativa);
        OffsetDateTime agora = OffsetDateTime.now(clock);
        String sha256 = sha256(image.bytes());

        foto.setProvider(WhatsappProviderType.UAZAP);
        foto.setConteudo(image.bytes().clone());
        foto.setContentType(image.contentType());
        foto.setSha256(sha256);
        foto.setTamanhoBytes((long) image.bytes().length);
        foto.setStatus(PacienteFotoStatus.SUCCESS);
        foto.setTentativas(0);
        foto.setObtidaEm(agora);
        foto.setProximaTentativaEm(agora.plus(SUCCESS_REFRESH));
        foto.setMotivoUltimaFalha(null);
        foto.setAtualizadaEm(agora);
        fotoRepository.save(foto);

        String urlInterna = "/api/pacientes/" + paciente.getId() + "/foto?v=" + sha256.substring(0, 12);
        paciente.setFotoUrl(urlInterna);
        pacienteRepository.save(paciente);
        return urlInterna;
    }

    @Transactional
    public void registrarSemFoto(TentativaFoto tentativa, String motivo) {
        atualizarFalha(tentativa, PacienteFotoStatus.NO_PHOTO, motivo, NO_PHOTO_COOLDOWN);
    }

    @Transactional
    public void registrarFalha(TentativaFoto tentativa, String motivo, boolean temporaria) {
        Duration cooldown = temporaria
                ? backoffTemporario(tentativa.tentativas())
                : PERMANENT_FAILURE_COOLDOWN;
        atualizarFalha(
                tentativa,
                temporaria ? PacienteFotoStatus.TEMPORARY_FAILURE : PacienteFotoStatus.PERMANENT_FAILURE,
                motivo,
                cooldown
        );
    }

    @Transactional(readOnly = true)
    public FotoArmazenada obter(Long pacienteId, Long clinicaId) {
        pacienteRepository.findByIdAndClinicaId(pacienteId, clinicaId)
                .filter(item -> item.getDeletadoEm() == null)
                .orElseThrow(() -> new NotFoundException("Foto nao encontrada"));
        PacienteFotoPerfil foto = fotoRepository.findByPacienteIdAndClinica_Id(pacienteId, clinicaId)
                .filter(item -> item.getConteudo() != null)
                .orElseThrow(() -> new NotFoundException("Foto nao encontrada"));
        return new FotoArmazenada(
                foto.getConteudo().clone(),
                foto.getContentType(),
                foto.getSha256()
        );
    }

    private void atualizarFalha(
            TentativaFoto tentativa,
            PacienteFotoStatus status,
            String motivo,
            Duration cooldown
    ) {
        PacienteFotoPerfil foto = estado(tentativa);
        OffsetDateTime agora = OffsetDateTime.now(clock);
        foto.setStatus(status);
        foto.setProximaTentativaEm(agora.plus(cooldown));
        foto.setMotivoUltimaFalha(sanitizarMotivo(motivo));
        foto.setAtualizadaEm(agora);
        fotoRepository.save(foto);
    }

    private PacienteFotoPerfil estado(TentativaFoto tentativa) {
        return fotoRepository.findByPacienteIdAndClinica_Id(
                        tentativa.pacienteId(),
                        tentativa.clinicaId()
                )
                .orElseThrow(() -> new IllegalStateException("Estado da foto nao encontrado"));
    }

    private Duration backoffTemporario(int tentativas) {
        int exponent = Math.max(0, Math.min(tentativas - 1, 10));
        long minutes = TEMPORARY_FAILURE_BASE.toMinutes() * (1L << exponent);
        return Duration.ofMinutes(Math.min(minutes, TEMPORARY_FAILURE_MAX.toMinutes()));
    }

    private String sanitizarMotivo(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            return "FALHA_NAO_CLASSIFICADA";
        }
        String normalized = motivo.toUpperCase()
                .replaceAll("[^A-Z0-9_]", "_")
                .replaceAll("_+", "_");
        return normalized.substring(0, Math.min(normalized.length(), 100));
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 indisponivel: " + StandardCharsets.US_ASCII.name(),
                    exception
            );
        }
    }

    public record TentativaFoto(
            Long pacienteId,
            Long clinicaId,
            String telefoneNormalizado,
            int tentativas
    ) {
    }

    public record FotoArmazenada(byte[] conteudo, String contentType, String sha256) {
        public FotoArmazenada {
            conteudo = conteudo.clone();
        }

        @Override
        public byte[] conteudo() {
            return conteudo.clone();
        }
    }
}
