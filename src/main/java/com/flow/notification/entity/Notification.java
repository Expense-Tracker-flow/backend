package com.flow.notification.entity;

import com.flow.common.entity.BaseAuditEntity;
import com.flow.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "notifications")
public class Notification extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 500)
    private String message;

    // Stops the same alert being stored twice, for example one "no entry today" per day
    @Column(name = "dedupe_key", length = 120)
    private String dedupeKey;

    @Column(name = "read_at")
    private Instant readAt;
}
