package com.flow.reminder.entity;

import com.flow.common.entity.BaseAuditEntity;
import com.flow.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "payment_reminders")
public class PaymentReminder extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    // Day of the month the payment is due (1 - 31)
    @Column(name = "due_day", nullable = false)
    private int dueDay;

    // How many days before the due date the alert is sent (0 = on the due date)
    @Column(name = "remind_days_before", nullable = false)
    private int remindDaysBefore;

    @Column(name = "next_due_date", nullable = false)
    private LocalDate nextDueDate;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}
