package com.voltwise.core.notification;

import com.voltwise.core.domain.TriggerType;

public record DomainNotificationRequest(Long homeId, TriggerType triggerType, Long triggerReferenceId) {}
