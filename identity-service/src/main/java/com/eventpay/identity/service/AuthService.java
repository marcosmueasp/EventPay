package com.eventpay.identity.service;

import com.eventpay.identity.persistence.UserEntity;
import com.eventpay.identity.persistence.UserRepository;
import com.eventpay.identity.web.dto.LoginResponse;
import com.eventpay.identity.web.dto.RegisterResponse;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

  private final UserRepository users;
  private final PasswordEncoder passwords;
  private final JwtService jwt;

  public AuthService(UserRepository users, PasswordEncoder passwords, JwtService jwt) {
    this.users = users;
    this.passwords = passwords;
    this.jwt = jwt;
  }

  public RegisterResponse register(String email, String password) {
    String normalized = normalizeEmail(email);
    if (users.existsByEmail(normalized)) {
      throw new DuplicateEmailException();
    }
    try {
      UserEntity saved = users.save(
          new UserEntity(UUID.randomUUID(), normalized, passwords.encode(password)));
      return new RegisterResponse(saved.getId().toString(), saved.getEmail());
    } catch (DataIntegrityViolationException e) {
      throw new DuplicateEmailException();
    }
  }

  public LoginResponse login(String email, String password) {
    UserEntity user = users.findByEmail(normalizeEmail(email)).orElse(null);
    if (user == null || !passwords.matches(password, user.getPassHash())) {
      throw new CredentialsException();
    }
    return new LoginResponse(jwt.generateToken(user.getId()));
  }

  static String normalizeEmail(String email) {
    return email.trim().toLowerCase();
  }
}
