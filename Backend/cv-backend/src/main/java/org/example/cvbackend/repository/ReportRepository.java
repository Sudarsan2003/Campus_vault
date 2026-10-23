package org.example.cvbackend.repository;

import jakarta.persistence.LockModeType;
import org.example.cvbackend.model.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findAllByOrderByCreatedAtDesc();
    List<Report> findByKindAndStatus(Report.Kind kind, Report.Status status);
    /** Row lock, so two people can't match or return the same report at once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Report r where r.id = :id")
    Optional<Report> findForUpdate(@Param("id") Long id);
}