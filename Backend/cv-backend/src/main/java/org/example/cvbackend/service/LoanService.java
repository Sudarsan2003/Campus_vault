package org.example.cvbackend.service;

import org.example.cvbackend.blockchain.BlockchainService;
import org.example.cvbackend.blockchain.BlockchainService.TxResult;
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

    public LoanService(LoanRepository loans, AssetRepository assets, BlockchainService chain,
                       LedgerService ledger, @Value("${app.blockchain.deposit-tokens}") long depositTokens) {
        this.loans = loans;
        this.assets = assets;
        this.chain = chain;
        this.ledger = ledger;
        this.depositTokens = depositTokens;
    }

    @Transactional(readOnly = true)
    public List<LoanResponse> mine(Long userId) {
        return loans.findInvolving(userId).stream().map(LoanResponse::from).toList();
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
        if (chain.balanceOf(borrower.getId()).compareTo(BigInteger.valueOf(depositTokens)) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "You need " + fmt(depositTokens) + " test tokens for the deposit. Use \"Get test tokens\" first.");
        }

        Instant due = Instant.now().plus(Duration.ofDays(req.days()));
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
        return LoanResponse.from(loan);
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

        TxResult tx = chain.returnAsset(loan.getId(), damaged);
        loan.setStatus(Loan.Status.RETURNED);
        loan.setReturnedAt(Instant.now());
        loan.setDamaged(damaged);
        asset.setStatus(Asset.Status.AVAILABLE);
        ledger.record("Return verified",
                (damaged ? "Deposit forfeited to owner" : "Deposit released to borrower") + " · " + asset.getCode(),
                asset.getId(), tx);
        return LoanResponse.from(loan);
    }

    private static String fmt(long n) {
        return String.format(Locale.US, "%,d", n);
    }
}