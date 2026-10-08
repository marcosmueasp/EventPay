package com.eventpay.identity.web;

import com.eventpay.identity.service.AuthService;
import com.eventpay.identity.web.dto.LoginRequest;
import com.eventpay.identity.web.dto.LoginResponse;
import com.eventpay.identity.web.dto.RegisterRequest;
import com.eventpay.identity.web.dto.RegisterResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

  private final AuthService service;

  public AuthController(AuthService service) {
    this.service = service;
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
    return service.register(request.email(), request.password());
  }

  @PostMapping("/login")
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
    return service.login(request.email(), request.password());
  }
}
