package com.eventpay.identity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eventpay.identity.persistence.UserEntity;
import com.eventpay.identity.persistence.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock
  private UserRepository users;

  @Mock
  private PasswordEncoder passwords;

  @Mock
  private JwtService jwt;

  @InjectMocks
  private AuthService service;

  @Test
  void registroGuardaEmailEnMinusculasYPasswordCifrada() {
    when(users.existsByEmail("a@x.com")).thenReturn(false);
    when(passwords.encode("secreta01")).thenReturn("HASH");
    UserEntity saved = new UserEntity(UUID.randomUUID(), "a@x.com", "HASH");
    when(users.save(any(UserEntity.class))).thenReturn(saved);

    var response = service.register("A@X.com", "secreta01");

    assertEquals("a@x.com", response.email());
    assertEquals(saved.getId().toString(), response.userId());
    verify(passwords).encode("secreta01");
  }

  @Test
  void registroDuplicadoLanza409SinGuardar() {
    when(users.existsByEmail("a@x.com")).thenReturn(true);

    assertThrows(DuplicateEmailException.class, () -> service.register("a@x.com", "secreta01"));

    verify(users, never()).save(any(UserEntity.class));
  }

  @Test
  void registroConcurrenteResponde409PorConstraint() {
    when(users.existsByEmail("a@x.com")).thenReturn(false);
    when(passwords.encode("secreta01")).thenReturn("HASH");
    when(users.save(any(UserEntity.class))).thenThrow(new DataIntegrityViolationException("duplicado"));

    assertThrows(DuplicateEmailException.class, () -> service.register("a@x.com", "secreta01"));
  }

  @Test
  void loginDevuelveTokenDelUsuario() {
    UserEntity user = new UserEntity(UUID.randomUUID(), "a@x.com", "HASH");
    when(users.findByEmail("a@x.com")).thenReturn(Optional.of(user));
    when(passwords.matches("secreta01", "HASH")).thenReturn(true);
    when(jwt.generateToken(user.getId())).thenReturn("TOKEN");

    var response = service.login("A@X.com", "secreta01");

    assertEquals("TOKEN", response.token());
  }

  @Test
  void loginConPasswordErroneaLanza401() {
    UserEntity user = new UserEntity(UUID.randomUUID(), "a@x.com", "HASH");
    when(users.findByEmail("a@x.com")).thenReturn(Optional.of(user));
    when(passwords.matches("mala", "HASH")).thenReturn(false);

    assertThrows(CredentialsException.class, () -> service.login("a@x.com", "mala"));
  }

  @Test
  void loginConEmailInexistenteLanzaElMismo401() {
    when(users.findByEmail("nadie@x.com")).thenReturn(Optional.empty());

    assertThrows(CredentialsException.class, () -> service.login("nadie@x.com", "secreta01"));
  }
}
