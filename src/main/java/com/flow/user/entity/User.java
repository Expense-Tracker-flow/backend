package com.flow.user.entity;

import com.flow.common.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class User extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Builder.Default
    @Column(nullable = false, length = 10)
    private String currency = "INR";

    // IANA zone name such as "Asia/Kolkata", used to send reminders at the user's local evening
    @Builder.Default
    @Column(nullable = false, length = 64)
    private String timezone = "UTC";
}
