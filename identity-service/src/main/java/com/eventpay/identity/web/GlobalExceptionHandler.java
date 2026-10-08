package com.eventpay.identity.web;

import com.eventpay.identity.service.CredentialsException;
import com.eventpay.identity.service.DuplicateEmailException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorBody validation(MethodArgumentNotValidException e) {
    String message = e.getBindingResult().getFieldErrors().stream()
        .map(error -> error.getDefaultMessage())
        .findFirst()
        .orElse("Petición inválida");
    return new ErrorBody("bad_request", message);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorBody malformed() {
    return new ErrorBody("bad_request", "El cuerpo de la petición no es válido");
  }

  @ExceptionHandler(DuplicateEmailException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ErrorBody duplicate() {
    return new ErrorBody("conflict", "El email ya está registrado");
  }

  @ExceptionHandler(CredentialsException.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public ErrorBody credentials() {
    return new ErrorBody("unauthorized", "Credenciales inválidas");
  }

  public record ErrorBody(String error, String message) {
  }
}
