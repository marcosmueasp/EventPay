package com.eventpay.identity.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El email no tiene un formato válido")
    String email,

    @NotBlank(message = "La password es obligatoria")
    @Size(min = 8, message = "La password debe tener al menos 8 caracteres")
    String password) {
}
