package org.example.cvbackend.service;

import org.example.cvbackend.blockchain.BlockchainService;
import org.example.cvbackend.blockchain.ChainVerifier;
import org.example.cvbackend.blockchain.BlockchainService.TxResult;
import org.example.cvbackend.dto.AssetDtos.LedgerResponse;
import org.example.cvbackend.dto.AssetDtos.WalletResponse;
import org.example.cvbackend.exception.ApiException;
import org.example.cvbackend.model.LedgerEntry;
import org.example.cvbackend.repository.LedgerEntryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class LedgerService {

    private static final long FAUCET_AMOUNT = 5_000;

    private final LedgerEntryRepository entries;
    private final BlockchainService chain;
    private final long depositTokens;
    private final ChainVerifier verifier;

    public LedgerService(LedgerEntryRepository entries, BlockchainService chain, ChainVerifier verifier,
                         @Value("${app.blockchain.deposit-tokens}") long depositTokens) {
        this.entries = entries;
        this.verifier = verifier;
        this.chain = chain;
        this.depositTokens = depositTokens;
    }

    @Transactional
    public void record(String title, String detail, Long assetId, TxResult tx) {
        entries.save(new LedgerEntry(title, detail, assetId, tx.hash(), tx.blockNumber()));
    }

    @Transactional(readOnly = true)
    public List<LedgerResponse> list(Long assetId) {
        List<LedgerEntry> rows = assetId == null
                ? entries.findTop50ByOrderByIdDesc()
                : entries.findByAssetIdOrderByIdDesc(assetId);
        boolean up = verifier.reachable();
        return rows.stream()
                .map(e -> LedgerResponse.from(e, up ? verifier.ledgerEntry(e) : ChainVerifier.Check.unavailable()))
                .toList();
    }

    public WalletResponse wallet(Long userId) {
        return new WalletResponse(chain.balanceOf(userId).longValue(), depositTokens, FAUCET_AMOUNT);
    }

    @Transactional
    public WalletResponse faucet(Long userId) {
        if (chain.balanceOf(userId).longValue() >= FAUCET_AMOUNT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You already have plenty of test tokens.");
        }
        TxResult tx = chain.grantTokens(userId, FAUCET_AMOUNT);
        record("Test tokens granted", String.format(Locale.US, "%,d tokens to user #%d", FAUCET_AMOUNT, userId), null, tx);
        return wallet(userId);
    }
}