package org.example.cvbackend.repository;

import org.example.cvbackend.model.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    List<LedgerEntry> findTop50ByOrderByIdDesc();
    List<LedgerEntry> findByAssetIdOrderByIdDesc(Long assetId);
}