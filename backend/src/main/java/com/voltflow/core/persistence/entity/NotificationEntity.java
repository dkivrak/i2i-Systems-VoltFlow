package com.voltflow.core.persistence.entity;

import com.voltflow.core.domain.NotificationChannel;
import com.voltflow.core.domain.NotificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "notifications")
public class NotificationEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "home_id", nullable = false)
    private HomeEntity home;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "recommendation_id")
    private RecommendationEntity recommendation;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private NotificationChannel channel;
    @Column(nullable = false, length = 320)
    private String recipient;
    @Column(nullable = false, length = 255)
    private String subject;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private NotificationStatus status;
    @Column(name = "failure_reason", length = 1000)
    private String failureReason;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "sent_at")
    private Instant sentAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public HomeEntity getHome() { return home; }
    public void setHome(HomeEntity home) { this.home = home; }

    public RecommendationEntity getRecommendation() { return recommendation; }
    public void setRecommendation(RecommendationEntity recommendation) { this.recommendation = recommendation; }

    public NotificationChannel getChannel() { return channel; }
    public void setChannel(NotificationChannel channel) { this.channel = channel; }

    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public NotificationStatus getStatus() { return status; }
    public void setStatus(NotificationStatus status) { this.status = status; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
}
