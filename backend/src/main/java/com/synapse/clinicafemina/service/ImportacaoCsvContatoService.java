package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Clinica;
import com.synapse.clinicafemina.domain.Paciente;
import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.dto.paciente.importacao.ImportacaoCsvContatoAmostra;
import com.synapse.clinicafemina.dto.paciente.importacao.ImportacaoCsvContatoMapping;
import com.synapse.clinicafemina.dto.paciente.importacao.ImportacaoCsvContatoPreview;
import com.synapse.clinicafemina.dto.paciente.importacao.ImportacaoCsvContatoResultado;
import com.synapse.clinicafemina.dto.paciente.importacao.ImportacaoCsvContatoResumo;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.repository.ClinicaRepository;
import com.synapse.clinicafemina.repository.PacienteRepository;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportacaoCsvContatoService {

    static final String ORIGEM_IMPORTACAO = "IMPORTACAO";
    private static final int SAVE_BATCH_SIZE = 500;
    private static final Set<String> NAME_HEADERS = Set.of("nome", "nome completo", "paciente", "contato", "name");
    private static final Set<String> PHONE_HEADERS = Set.of("telefone", "celular", "whatsapp", "whats app", "fone", "phone");

    private final ImportacaoCsvContatoParser parser;
    private final PacienteRepository pacienteRepository;
    private final ClinicaRepository clinicaRepository;
    private final UsuarioRepository usuarioRepository;
    private final WhatsappPhoneIdentityService phoneIdentityService;

    @Transactional(readOnly = true)
    public ImportacaoCsvContatoPreview preview(
            MultipartFile file,
            ImportacaoCsvContatoMapping requestedMapping,
            Clinica clinica,
            Long usuarioId
    ) {
        validarUsuarioAtivo(clinica, usuarioId);
        ImportacaoCsvContatoParser.ArquivoCsvLido csv = parser.parse(file);
        ImportacaoCsvContatoMapping suggested = sugerirMapeamento(csv.headers());
        ImportacaoCsvContatoMapping mapping = requestedMapping == null ? suggested : requestedMapping;
        List<String> warnings = new ArrayList<>();
        ImportacaoCsvContatoResumo summary;
        if (mapping.nameColumn() == null || mapping.phoneColumn() == null) {
            warnings.add("Selecione as colunas de nome e telefone para validar a importação.");
            summary = resumoVazio(csv.rows().size());
        } else {
            summary = analisar(csv, mapping, clinica.getId()).summary();
        }
        return new ImportacaoCsvContatoPreview(
                csv.hash(), csv.fileName(), csv.encoding(), String.valueOf(csv.delimiter()), csv.rows().size(),
                csv.headers(), suggested,
                csv.rows().stream().limit(20)
                        .map(row -> new ImportacaoCsvContatoAmostra(row.rowNumber(), row.values())).toList(),
                List.copyOf(warnings), summary
        );
    }

    @Transactional
    public ImportacaoCsvContatoResultado importar(
            MultipartFile file,
            String expectedFileHash,
            ImportacaoCsvContatoMapping mapping,
            Clinica clinica,
            Long usuarioId
    ) {
        Instant startedAt = Instant.now();
        ImportacaoCsvContatoParser.ArquivoCsvLido csv = parser.parse(file);
        validarHash(csv.hash(), expectedFileHash);
        Usuario usuario = validarUsuarioAtivo(clinica, usuarioId);
        Clinica clinicaBloqueada = clinicaRepository.findByIdForUpdate(clinica.getId())
                .orElseThrow(() -> new AccessDeniedException("Acesso negado."));
        AnaliseImportacao analysis = analisar(csv, mapping, clinicaBloqueada.getId());
        salvarEmLotes(analysis.toCreate(), clinicaBloqueada, usuario);
        ImportacaoCsvContatoResumo summary = analysis.summary();
        log.info(
                "Importação CSV concluída. clinicaId={} usuarioId={} hash={} total={} criados={} existentes={} "
                        + "duplicados={} invalidos={} duracaoMs={}",
                clinicaBloqueada.getId(), usuario.getId(), csv.hash().substring(0, 12), summary.totalRows(),
                summary.toCreate(), summary.existing(), summary.duplicateInFile(), summary.invalid(),
                Duration.between(startedAt, Instant.now()).toMillis()
        );
        return new ImportacaoCsvContatoResultado(
                summary.totalRows(), summary.toCreate(), summary.existing(), summary.duplicateInFile(), summary.invalid(),
                summary.totalErrors(), summary.errorsTruncated(), summary.errors()
        );
    }

    private AnaliseImportacao analisar(
            ImportacaoCsvContatoParser.ArquivoCsvLido csv,
            ImportacaoCsvContatoMapping mapping,
            Long clinicaId
    ) {
        IndicesMapping indices = validarMapeamento(csv.headers(), mapping);
        ImportacaoCsvContatoErros errors = new ImportacaoCsvContatoErros();
        List<LinhaValida> candidates = new ArrayList<>();
        Set<String> aliasesSeenInFile = new HashSet<>();
        int invalid = 0;
        int duplicates = 0;
        for (ImportacaoCsvContatoParser.LinhaCsv row : csv.rows()) {
            LinhaValidacao validation = validarLinha(row, indices, errors);
            if (validation == null) {
                invalid++;
                continue;
            }
            if (validation.identity().aliases().stream().anyMatch(aliasesSeenInFile::contains)) {
                duplicates++;
                errors.add(row.rowNumber(), "telefone", "DUPLICATE_IN_FILE", "Telefone duplicado no arquivo.");
                continue;
            }
            aliasesSeenInFile.addAll(validation.identity().aliases());
            candidates.add(new LinhaValida(row.rowNumber(), validation.name(), validation.identity()));
        }
        Set<String> existingPhones = buscarTelefonesExistentes(clinicaId, candidates);
        List<LinhaValida> toCreate = candidates.stream()
                .filter(row -> row.identity().aliases().stream().noneMatch(existingPhones::contains))
                .toList();
        int existing = candidates.size() - toCreate.size();
        return new AnaliseImportacao(
                new ImportacaoCsvContatoResumo(
                        csv.rows().size(), csv.rows().size() - invalid, existing, duplicates, invalid, toCreate.size(),
                        errors.total(), errors.truncated(), errors.details()
                ),
                toCreate
        );
    }

    private LinhaValidacao validarLinha(
            ImportacaoCsvContatoParser.LinhaCsv row,
            IndicesMapping indices,
            ImportacaoCsvContatoErros errors
    ) {
        String rawName = valueAt(row.values(), indices.nameIndex());
        String rawPhone = valueAt(row.values(), indices.phoneIndex());
        if (isBlank(rawName) && isBlank(rawPhone) && row.values().stream().allMatch(this::isBlank)) {
            errors.add(row.rowNumber(), "linha", "EMPTY_ROW", "Linha vazia.");
            return null;
        }
        String name = sanitizeName(rawName, row.rowNumber(), errors);
        WhatsappPhoneIdentityService.PhoneIdentity identity = identifyPhone(rawPhone, row.rowNumber(), errors);
        return name == null || identity == null ? null : new LinhaValidacao(name, identity);
    }

    private String sanitizeName(String rawName, int rowNumber, ImportacaoCsvContatoErros errors) {
        if (isBlank(rawName)) {
            errors.add(rowNumber, "nome", "MISSING_NAME", "Nome obrigatório.");
            return null;
        }
        String name = rawName.trim().replaceAll("\\s+", " ");
        if (name.matches(".*<[^>]+>.*") || name.length() > 200) {
            errors.add(rowNumber, "nome", "INVALID_NAME", "Nome inválido.");
            return null;
        }
        return name;
    }

    private WhatsappPhoneIdentityService.PhoneIdentity identifyPhone(
            String rawPhone,
            int rowNumber,
            ImportacaoCsvContatoErros errors
    ) {
        if (isBlank(rawPhone)) {
            errors.add(rowNumber, "telefone", "MISSING_PHONE", "Telefone obrigatório.");
            return null;
        }
        try {
            return phoneIdentityService.identify(rawPhone.trim());
        } catch (BadRequestException exception) {
            errors.add(rowNumber, "telefone", "INVALID_PHONE", "Telefone inválido.");
            return null;
        }
    }

    private Set<String> buscarTelefonesExistentes(Long clinicaId, List<LinhaValida> candidates) {
        List<String> aliases = candidates.stream()
                .flatMap(row -> row.identity().aliases().stream())
                .distinct()
                .toList();
        Set<String> existing = new HashSet<>();
        for (int offset = 0; offset < aliases.size(); offset += SAVE_BATCH_SIZE) {
            List<String> batch = aliases.subList(offset, Math.min(offset + SAVE_BATCH_SIZE, aliases.size()));
            pacienteRepository.findByClinicaIdAndTelefoneNormalizadoIn(clinicaId, batch)
                    .forEach(patient -> existing.add(patient.getTelefoneNormalizado()));
        }
        return existing;
    }

    private void salvarEmLotes(List<LinhaValida> rows, Clinica clinica, Usuario usuario) {
        for (int offset = 0; offset < rows.size(); offset += SAVE_BATCH_SIZE) {
            List<Paciente> batch = rows.subList(offset, Math.min(offset + SAVE_BATCH_SIZE, rows.size())).stream()
                    .map(row -> novoPaciente(row, clinica, usuario))
                    .toList();
            pacienteRepository.saveAll(batch);
        }
    }

    private Paciente novoPaciente(LinhaValida row, Clinica clinica, Usuario usuario) {
        Paciente patient = new Paciente();
        patient.setClinica(clinica);
        patient.setNome(row.name());
        patient.setNomeBusca(row.name().toUpperCase(Locale.ROOT));
        patient.setTelefone("+" + row.identity().normalized());
        patient.setTelefoneNormalizado(row.identity().normalized());
        patient.setOrigem(ORIGEM_IMPORTACAO);
        patient.setCriadoPor(usuario);
        patient.setAtualizadoPor(usuario);
        return patient;
    }

    private Usuario validarUsuarioAtivo(Clinica clinica, Long usuarioId) {
        if (usuarioId == null) {
            throw new AccessDeniedException("Acesso negado.");
        }
        return usuarioRepository.findAtivoByIdAndClinicaId(usuarioId, clinica.getId())
                .orElseThrow(() -> new AccessDeniedException("Acesso negado."));
    }

    private void validarHash(String actualHash, String expectedHash) {
        if (isBlank(expectedHash) || !MessageDigest.isEqual(
                actualHash.getBytes(StandardCharsets.US_ASCII), expectedHash.trim().getBytes(StandardCharsets.US_ASCII))) {
            throw new BadRequestException("O arquivo enviado é diferente do arquivo visualizado.");
        }
    }

    private ImportacaoCsvContatoMapping sugerirMapeamento(List<String> headers) {
        return new ImportacaoCsvContatoMapping(
                findHeader(headers, NAME_HEADERS),
                findHeader(headers, PHONE_HEADERS)
        );
    }

    private String findHeader(List<String> headers, Set<String> candidates) {
        return headers.stream()
                .filter(header -> candidates.contains(ImportacaoCsvContatoParser.normalizarHeader(header)))
                .findFirst()
                .orElse(null);
    }

    private IndicesMapping validarMapeamento(List<String> headers, ImportacaoCsvContatoMapping mapping) {
        if (mapping == null || isBlank(mapping.nameColumn()) || isBlank(mapping.phoneColumn())) {
            throw new BadRequestException("Selecione as colunas de nome e telefone.");
        }
        if (mapping.nameColumn().equals(mapping.phoneColumn())) {
            throw new BadRequestException("Nome e telefone devem usar colunas diferentes.");
        }
        Map<String, Integer> positions = java.util.stream.IntStream.range(0, headers.size()).boxed()
                .collect(Collectors.toMap(headers::get, Function.identity()));
        Integer nameIndex = positions.get(mapping.nameColumn());
        Integer phoneIndex = positions.get(mapping.phoneColumn());
        if (nameIndex == null || phoneIndex == null) {
            throw new BadRequestException("O mapeamento aponta para um cabeçalho inexistente.");
        }
        return new IndicesMapping(nameIndex, phoneIndex);
    }

    private ImportacaoCsvContatoResumo resumoVazio(int totalRows) {
        return new ImportacaoCsvContatoResumo(totalRows, 0, 0, 0, 0, 0, 0, false, List.of());
    }

    private String valueAt(List<String> values, int index) {
        return index < values.size() ? values.get(index) : null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record IndicesMapping(int nameIndex, int phoneIndex) {
    }

    private record LinhaValidacao(String name, WhatsappPhoneIdentityService.PhoneIdentity identity) {
    }

    private record LinhaValida(int rowNumber, String name, WhatsappPhoneIdentityService.PhoneIdentity identity) {
    }

    private record AnaliseImportacao(ImportacaoCsvContatoResumo summary, List<LinhaValida> toCreate) {
    }

}
