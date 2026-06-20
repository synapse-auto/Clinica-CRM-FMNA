package com.synapse.clinicafemina.service;

import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.dto.auth.ChangePasswordRequest;
import com.synapse.clinicafemina.dto.auth.LoginRequest;
import com.synapse.clinicafemina.dto.auth.LoginResponse;
import com.synapse.clinicafemina.exception.BadRequestException;
import com.synapse.clinicafemina.repository.UsuarioRepository;
import com.synapse.clinicafemina.security.JwtService;
import com.synapse.clinicafemina.security.PasswordPolicy;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioRepository usuarioRepository;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        Usuario usuario = (Usuario) authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getSenha())
        ).getPrincipal();
        usuario.setUltimoLoginEm(OffsetDateTime.now());
        usuarioRepository.save(usuario);
        return responseFor(usuario);
    }

    @Transactional
    public LoginResponse changePassword(Usuario usuario, ChangePasswordRequest request) {
        if (!passwordEncoder.matches(request.senhaAtual(), usuario.getSenhaHash())) {
            throw new BadRequestException("Senha atual inválida.");
        }
        if (!request.novaSenha().equals(request.confirmacaoNovaSenha())) {
            throw new BadRequestException("As senhas não coincidem.");
        }
        if (!PasswordPolicy.isStrong(request.novaSenha())) {
            throw new BadRequestException(
                    "A nova senha deve ter ao menos 12 caracteres, com maiúscula, minúscula, número e símbolo."
            );
        }
        if (passwordEncoder.matches(request.novaSenha(), usuario.getSenhaHash())) {
            throw new BadRequestException("A nova senha deve ser diferente da senha atual.");
        }

        usuario.setSenhaHash(passwordEncoder.encode(request.novaSenha()));
        usuario.setMustChangePassword(false);
        usuarioRepository.save(usuario);
        return responseFor(usuario);
    }

    private LoginResponse responseFor(Usuario usuario) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("clinicaId", usuario.getClinica().getId());
        extraClaims.put("perfil", usuario.getPerfil());
        extraClaims.put("nome", usuario.getNome());

        return LoginResponse.builder()
                .token(jwtService.generateToken(extraClaims, usuario))
                .id(usuario.getId())
                .nome(usuario.getNome())
                .email(usuario.getEmail())
                .perfil(usuario.getPerfil())
                .clinicaId(usuario.getClinica().getId())
                .mustChangePassword(usuario.getMustChangePassword())
                .build();
    }
}
