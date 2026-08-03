package iuh.fit.se.contractservice.repository;

import iuh.fit.se.contractservice.domain.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, UUID> {
}
