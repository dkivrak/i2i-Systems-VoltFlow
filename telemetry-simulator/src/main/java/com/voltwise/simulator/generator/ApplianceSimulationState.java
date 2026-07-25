package com.voltwise.simulator.generator;

import java.math.BigDecimal;
import java.time.Instant;

public class ApplianceSimulationState {

    private String phase = "";
    private int cyclesInPhase;
    private long generatedCycles;
    private BigDecimal lastPowerWatts = BigDecimal.ZERO;
    private BigDecimal nominalPowerWatts = BigDecimal.ZERO;
    private int anomalyCyclesRemaining;
    private int anomalyCooldownRemaining;
    private boolean anomalyCycleConsumed;

    // Stateful appliance lifecycle additions:
    private OperationalState operationalState = OperationalState.OFF;
    private Instant phaseStartedAt;
    private Instant faultStartedAt;
    private Instant lastActiveAt;
    private BigDecimal sessionBaseWatts = BigDecimal.ZERO;

    public void initialize(OperationalState initialState, String initialPhase, Instant now) {
        if (this.phase == null || this.phase.isEmpty()) {
            this.operationalState = initialState;
            this.phase = initialPhase;
            this.phaseStartedAt = now;
            this.cyclesInPhase = 0;
        }
    }

    public void transitionTo(OperationalState newState, String newPhase, Instant now) {
        if (newState == null) {
            throw new IllegalArgumentException("OperationalState cannot be null");
        }
        if (newPhase == null || newPhase.isBlank()) {
            throw new IllegalArgumentException("Simulation phase cannot be blank");
        }
        if (newState != this.operationalState || !newPhase.equals(this.phase)) {
            this.operationalState = newState;
            this.phase = newPhase;
            this.phaseStartedAt = now;
            this.cyclesInPhase = 0;
        }
    }

    public void initializePhase(String initialPhase) {
        if (phase.isEmpty()) {
            transitionTo(initialPhase);
        }
    }

    public void transitionTo(String newPhase) {
        if (newPhase == null || newPhase.isBlank()) {
            throw new IllegalArgumentException("Simulation phase cannot be blank");
        }
        if (!newPhase.equals(phase)) {
            phase = newPhase;
            cyclesInPhase = 0;
        }
    }

    public void completeCycle(BigDecimal powerWatts) {
        lastPowerWatts = powerWatts;
        cyclesInPhase++;
        generatedCycles++;
        if (anomalyCooldownRemaining > 0 && anomalyCyclesRemaining == 0 && !anomalyCycleConsumed) {
            anomalyCooldownRemaining--;
        }
        anomalyCycleConsumed = false;
    }

    public void beginAnomalyBurst(int cycles) {
        if (cycles < 1) {
            throw new IllegalArgumentException("Anomaly burst must contain at least one cycle");
        }
        anomalyCyclesRemaining = cycles;
    }

    public void consumeAnomalyCycle(int cooldownCycles) {
        if (anomalyCyclesRemaining < 1) {
            throw new IllegalStateException("No anomaly cycle is active");
        }
        anomalyCyclesRemaining--;
        anomalyCycleConsumed = true;
        if (anomalyCyclesRemaining == 0) {
            anomalyCooldownRemaining = cooldownCycles;
        }
    }

    public String getPhase() {
        return phase;
    }

    public int getCyclesInPhase() {
        return cyclesInPhase;
    }

    public long getGeneratedCycles() {
        return generatedCycles;
    }

    public BigDecimal getLastPowerWatts() {
        return lastPowerWatts;
    }

    public BigDecimal getNominalPowerWatts() {
        return nominalPowerWatts;
    }

    public void setNominalPowerWatts(BigDecimal nominalPowerWatts) {
        this.nominalPowerWatts = nominalPowerWatts;
    }

    public int getAnomalyCyclesRemaining() {
        return anomalyCyclesRemaining;
    }

    public int getAnomalyCooldownRemaining() {
        return anomalyCooldownRemaining;
    }

    // Getters and setters for new stateful fields:
    public OperationalState getOperationalState() {
        return operationalState;
    }

    public void setOperationalState(OperationalState operationalState) {
        this.operationalState = operationalState;
    }

    public Instant getPhaseStartedAt() {
        return phaseStartedAt;
    }

    public void setPhaseStartedAt(Instant phaseStartedAt) {
        this.phaseStartedAt = phaseStartedAt;
    }

    public Instant getFaultStartedAt() {
        return faultStartedAt;
    }

    public void setFaultStartedAt(Instant faultStartedAt) {
        this.faultStartedAt = faultStartedAt;
    }

    public Instant getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(Instant lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }

    public BigDecimal getSessionBaseWatts() {
        return sessionBaseWatts;
    }

    public void setSessionBaseWatts(BigDecimal sessionBaseWatts) {
        this.sessionBaseWatts = sessionBaseWatts;
    }
}
