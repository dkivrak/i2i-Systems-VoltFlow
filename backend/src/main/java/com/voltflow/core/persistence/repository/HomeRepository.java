package com.voltflow.core.persistence.repository;

import com.voltflow.core.persistence.entity.HomeEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface HomeRepository extends JpaRepository<HomeEntity, Long> {
    @EntityGraph(attributePaths = "appliances")
    @Query("select h from HomeEntity h where h.id = :id")
    Optional<HomeEntity> findDetailedById(@Param("id") Long id);
}
