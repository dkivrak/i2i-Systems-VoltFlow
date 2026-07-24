package com.voltflow.core.registration;

import com.voltflow.core.event.AssetRegistrationEvent;

import java.util.concurrent.CompletableFuture;

public interface RegistrationPublisher {
    CompletableFuture<Void> publish(String topic, AssetRegistrationEvent event);
}
