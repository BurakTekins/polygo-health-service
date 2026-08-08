package com.polygo.health.infrastructure.persistence.jpa.repository;

import com.polygo.health.domain.model.WalletBalanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WalletBalanceStatusRepository extends JpaRepository<WalletBalanceStatus, Long> {

    Optional<WalletBalanceStatus> findByVenueCode(String venueCode);
}
