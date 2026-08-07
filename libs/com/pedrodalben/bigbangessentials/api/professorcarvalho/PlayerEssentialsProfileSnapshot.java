package com.pedrodalben.bigbangessentials.api.professorcarvalho;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public class PlayerEssentialsProfileSnapshot {
    public Optional<String> rankDisplayName() { return Optional.empty(); }
    public Optional<Double> playtimeSeconds() { return Optional.empty(); }
    public List<JobProgressSnapshot> jobs() { return List.of(); }
    public Optional<BigDecimal> coinBalance() { return Optional.empty(); }
    public Optional<Integer> gemBalance() { return Optional.empty(); }
}
