package com.voltflow.core.notification;

import com.voltflow.core.domain.TriggerType;

public record DomainNotificationRequest(Long homeId, TriggerType triggerType, Long triggerReferenceId) {}
