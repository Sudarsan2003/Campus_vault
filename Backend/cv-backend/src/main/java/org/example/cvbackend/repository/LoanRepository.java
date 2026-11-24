package org.example.cvbackend.repository;

import org.example.cvbackend.model.Loan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    /** Loans where the user is the borrower or the asset owner. */
    @Query("select l from Loan l where l.borrower.id = :uid or l.asset.owner.id = :uid order by l.createdAt desc")
    List<Loan> findInvolving(@Param("uid") Long userId);

    /** Open loans past their due date, oldest due date first. */
    @Query("select l from Loan l where l.status = org.example.cvbackend.model.Loan.Status.OPEN and l.dueAt < :now order by l.dueAt")
    List<Loan> findOverdue(@Param("now") Instant now);

    @Query("select count(l) > 0 from Loan l where l.borrower.id = :uid and l.status = org.example.cvbackend.model.Loan.Status.OPEN and l.dueAt < :now")
    boolean hasOverdue(@Param("uid") Long userId, @Param("now") Instant now);
}