package com.voltwise.core.registration;

import com.voltwise.core.event.AssetRegistrationEvent;

import java.util.concurrent.CompletableFuture;

public interface RegistrationPublisher {
    CompletableFuture<Void> publish(String topic, AssetRegistrationEvent event);
}
