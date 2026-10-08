package com.eventpay.identity.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {

  @Id
  private UUID id;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(name = "pass_hash", nullable = false)
  private String passHash;

  protected UserEntity() {
  }

  public UserEntity(UUID id, String email, String passHash) {
    this.id = id;
    this.email = email;
    this.passHash = passHash;
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getPassHash() {
    return passHash;
  }
}
