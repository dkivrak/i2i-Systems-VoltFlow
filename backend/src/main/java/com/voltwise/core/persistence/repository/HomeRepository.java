package com.voltwise.core.persistence.repository;

import com.voltwise.core.persistence.entity.HomeEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface HomeRepository extends JpaRepository<HomeEntity, Long> {
    @EntityGraph(attributePaths = "appliances")
    @Query("select h from HomeEntity h where h.id = :id")
    Optional<HomeEntity> findDetailedById(@Param("id") Long id);

    Page<HomeEntity> findAllByOwnerEmail(String ownerEmail, Pageable pageable);

    @Query("select h.id from HomeEntity h where h.ownerEmail = :ownerEmail")
    List<Long> findIdsByOwnerEmail(@Param("ownerEmail") String ownerEmail);
}
