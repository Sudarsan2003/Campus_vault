package org.example.cvbackend.service;

import org.example.cvbackend.blockchain.BlockchainService;
import org.example.cvbackend.blockchain.BlockchainService.ReturnOutcome;
import org.example.cvbackend.blockchain.BlockchainService.TxResult;
import org.example.cvbackend.blockchain.ChainVerifier;
import org.example.cvbackend.dto.AssetDtos.BorrowRequest;
import org.example.cvbackend.dto.AssetDtos.LoanResponse;
import org.example.cvbackend.exception.ApiException;
import org.example.cvbackend.model.Asset;
import org.example.cvbackend.model.Loan;
import org.example.cvbackend.model.Role;
import org.example.cvbackend.model.User;
import org.example.cvbackend.repository.AssetRepository;
import org.example.cvbackend.repository.LoanRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class LoanService {

    private final LoanRepository loans;
    private final AssetRepository assets;
    private final BlockchainService chain;
    private final LedgerService ledger;
    private final long depositTokens;
    private final AssetService assetService;
    private final ChainVerifier verifier;

    public LoanService(LoanRepository loans, AssetRepository assets, BlockchainService chain,
                       LedgerService ledger, AssetService assetService, ChainVerifier verifier,
                       @Value("${app.blockchain.deposit-tokens}") long depositTokens) {
        this.loans = loans;
        this.assetService = assetService;
        this.verifier = verifier;
        this.assets = assets;
        this.chain = chain;
        this.ledger = ledger;
        this.depositTokens = depositTokens;
    }

    @Transactional(readOnly = true)
    public List<LoanResponse> mine(Long userId) {
        return toResponses(loans.findInvolving(userId));
    }

    /** Every open loan past its due date, for administrators. */
    @Transactional(readOnly = true)
    public List<LoanResponse> overdue() {
        Instant now = chain.chainTime();
        return toResponses(loans.findOverdue(now), now);
    }

    @Transactional
    public LoanResponse borrow(User borrower, BorrowRequest req) {
        Asset asset = assets.findForUpdate(req.assetId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Asset not found."));

        if (asset.getOwner().getId().equals(borrower.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You can't borrow your own asset.");
        }
        if (asset.getStatus() == Asset.Status.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "This asset hasn't been verified yet.");
        }
        if (asset.getStatus() == Asset.Status.BORROWED) {
            throw new ApiException(HttpStatus.CONFLICT, "This asset is already borrowed.");
        }
        assetService.requireMatchesChain(asset);
        Instant now = chain.chainTime();
        if (loans.hasOverdue(borrower.getId(), now)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "You have an overdue loan. Return it before borrowing anything else.");
        }
        if (chain.balanceOf(borrower.getId()).compareTo(BigInteger.valueOf(depositTokens)) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "You need " + fmt(depositTokens) + " test tokens for the deposit. Use \"Get test tokens\" first.");
        }

        // Due date is based on chain time, because that is what the contract compares it with.
        Instant due = now.plus(Duration.ofDays(req.days()));
        Loan loan = new Loan();
        loan.setAsset(asset);
        loan.setBorrower(borrower);
        loan.setDeposit(depositTokens);
        loan.setDueAt(due);
        loans.saveAndFlush(loan);

        TxResult tx = chain.borrow(loan.getId(), asset.getId(), borrower.getId(), depositTokens, due.getEpochSecond());
        asset.setStatus(Asset.Status.BORROWED);
        ledger.record("Borrow approved", "Deposit locked · " + fmt(depositTokens) + " test tokens · " + asset.getCode(),
                asset.getId(), tx);
        return LoanResponse.from(loan, now, 0);
    }

    @Transactional
    public LoanResponse verifyReturn(User actor, Long loanId, boolean damaged) {
        Loan loan = loans.findById(loanId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Loan not found."));
        Asset asset = assets.findForUpdate(loan.getAsset().getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Asset not found."));

        boolean isOwner = asset.getOwner().getId().equals(actor.getId());
        if (!isOwner && actor.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the asset owner or an admin can verify a return.");
        }
        if (loan.getStatus() != Loan.Status.OPEN) {
            throw new ApiException(HttpStatus.CONFLICT, "This loan is already closed.");
        }
        ChainVerifier.Check check = verifier.loan(loan);
        if (check.status() == ChainVerifier.Status.MISMATCH) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "This loan doesn't match the blockchain: " + check.detail() + " Ask an administrator to investigate.");
        }

        // The contract decides the split (late fee or damage); we record what it actually did.
        ReturnOutcome out = chain.returnAsset(loan.getId(), damaged);
        loan.setStatus(Loan.Status.RETURNED);
        loan.setReturnedAt(Instant.now());
        loan.setDamaged(damaged);
        loan.setLateFee(damaged ? 0 : out.toOwner());
        asset.setStatus(Asset.Status.AVAILABLE);

        String detail;
        if (damaged) {
            detail = "Deposit forfeited to owner";
        } else if (out.toOwner() > 0) {
            detail = "Returned late · " + fmt(out.toOwner()) + " tokens late fee to owner, "
                    + fmt(out.toBorrower()) + " refunded";
        } else {
            detail = "Deposit released to borrower";
        }
        ledger.record("Return verified", detail + " · " + asset.getCode(), asset.getId(), out.tx());
        return LoanResponse.from(loan, chain.chainTime(), 0);
    }

    /** Records each newly overdue loan on-chain once. Called by {@link OverdueMonitor}. */
    @Transactional
    public int flagNewlyOverdue() {
        int flagged = 0;
        for (Loan loan : loans.findOverdue(chain.chainTime())) {
            if (loan.isOverdueFlagged()) continue;
            TxResult tx;
            try {
                tx = chain.markOverdue(loan.getId());
            } catch (ApiException e) {
                // e.g. the loan was closed on-chain outside CampusVault; skip it so the others still get flagged
                continue;
            }
            loan.setOverdueFlagged(true);
            ledger.record("Loan overdue",
                    loan.getAsset().getCode() + " · borrowed by " + loan.getBorrower().getFullName()
                            + " · was due " + loan.getDueAt().toString().substring(0, 10),
                    loan.getAsset().getId(), tx);
            flagged++;
        }
        return flagged;
    }

    private List<LoanResponse> toResponses(List<Loan> list) {
        return list.isEmpty() ? List.of() : toResponses(list, chain.chainTime());
    }

    private List<LoanResponse> toResponses(List<Loan> list, Instant now) {
        boolean up = verifier.reachable();
        return list.stream()
                .map(l -> {
                    ChainVerifier.Check check = up ? verifier.loan(l) : ChainVerifier.Check.unavailable();
                    long fee = up && l.isOverdueAt(now) ? chain.lateFee(l.getId()) : 0;
                    return LoanResponse.from(l, now, fee, check);
                })
                .toList();
    }

    private static String fmt(long n) {
        return String.format(Locale.US, "%,d", n);
    }
}
