package org.example.cvbackend.repository;

import org.example.cvbackend.model.Loan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    /** Loans where the user is the borrower or the asset owner. */
    @Query("select l from Loan l where l.borrower.id = :uid or l.asset.owner.id = :uid order by l.createdAt desc")
    List<Loan> findInvolving(@Param("uid") Long userId);
}