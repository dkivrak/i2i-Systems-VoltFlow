package com.voltwise.simulator.generator;

import java.math.BigDecimal;

public class ApplianceSimulationState {

    private String phase = "";
    private int cyclesInPhase;
    private long generatedCycles;
    private BigDecimal lastPowerWatts = BigDecimal.ZERO;
    private BigDecimal nominalPowerWatts = BigDecimal.ZERO;
    private int anomalyCyclesRemaining;
    private int anomalyCooldownRemaining;
    private boolean anomalyCycleConsumed;

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
}
