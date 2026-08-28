package com.islamshariful.authservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Exchanges a refresh token for a new token pair")
public record RefreshTokenRequest(@NotBlank @Size(max = 512) String refreshToken) {}
