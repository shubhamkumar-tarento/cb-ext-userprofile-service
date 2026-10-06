package com.igot.cb.masterdata.repository;

import com.igot.cb.masterdata.model.Institute;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InstituteRepository extends JpaRepository<Institute, Long> {

    // Search by name, city, or state (case-insensitive)
    Page<Institute> findByNameContainingIgnoreCaseAndStatus(
            String name,int status, Pageable pageable);
    Page<Institute> findByStatus(int status, Pageable pageable);

    boolean existsByNameIgnoreCase(String name);
    Optional<Institute> findByNameIgnoreCase(String name);
}
