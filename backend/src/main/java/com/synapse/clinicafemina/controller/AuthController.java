package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.dto.auth.LoginRequest;
import com.synapse.clinicafemina.dto.auth.LoginResponse;
import com.synapse.clinicafemina.security.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        // Realiza a autenticação com base no UserDetailsService e PasswordEncoder configurados
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getSenha())
        );

        // Se passar daqui, as credenciais são válidas. Recuperamos o usuário real:
        Usuario usuario = (Usuario) authentication.getPrincipal();

        // Extra claims adicionais para colocar no Payload do JWT
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("clinicaId", usuario.getClinica().getId());
        extraClaims.put("perfil", usuario.getPerfil());
        extraClaims.put("nome", usuario.getNome());

        // Gera o JWT
        String jwtToken = jwtService.generateToken(extraClaims, usuario);

        // Constrói e retorna o DTO
        return ResponseEntity.ok(LoginResponse.builder()
                .token(jwtToken)
                .id(usuario.getId())
                .nome(usuario.getNome())
                .email(usuario.getEmail())
                .perfil(usuario.getPerfil())
                .clinicaId(usuario.getClinica().getId())
                .build());
    }
}
