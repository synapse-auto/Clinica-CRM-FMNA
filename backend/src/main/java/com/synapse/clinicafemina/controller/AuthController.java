package com.synapse.clinicafemina.controller;

import com.synapse.clinicafemina.domain.Usuario;
import com.synapse.clinicafemina.dto.auth.AuthUserResponse;
import com.synapse.clinicafemina.dto.auth.ChangePasswordRequest;
import com.synapse.clinicafemina.dto.auth.LoginRequest;
import com.synapse.clinicafemina.dto.auth.LoginResponse;
import com.synapse.clinicafemina.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticação", description = "Login público e dados da sessão JWT do CRM.")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Autenticar no CRM", description = "Endpoint público que retorna o JWT do CRM.")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    @Operation(summary = "Consultar sessão atual", security = @SecurityRequirement(name = "bearerAuth"))
    public AuthUserResponse me(@AuthenticationPrincipal Usuario usuario) {
        return AuthUserResponse.from(usuario);
    }

    @PatchMapping("/change-password")
    @Operation(summary = "Alterar senha", security = @SecurityRequirement(name = "bearerAuth"))
    public LoginResponse changePassword(
            @AuthenticationPrincipal Usuario usuario,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        return authService.changePassword(usuario, request);
    }
}
