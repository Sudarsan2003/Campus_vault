package org.example.cvbackend.service;

import org.example.cvbackend.blockchain.BlockchainService;
import org.example.cvbackend.blockchain.ChainVerifier;
import org.example.cvbackend.blockchain.BlockchainService.TxResult;
import org.example.cvbackend.dto.AssetDtos.AssetResponse;
import org.example.cvbackend.dto.AssetDtos.CreateAssetRequest;
import org.example.cvbackend.exception.ApiException;
import org.example.cvbackend.model.Asset;
import org.example.cvbackend.model.User;
import org.example.cvbackend.repository.AssetRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AssetService {

    private final AssetRepository assets;
    private final BlockchainService chain;
    private final LedgerService ledger;
    private final ChainVerifier verifier;

    public AssetService(AssetRepository assets, BlockchainService chain, LedgerService ledger, ChainVerifier verifier) {
        this.assets = assets;
        this.verifier = verifier;
        this.chain = chain;
        this.ledger = ledger;
    }

    /** Refuses to act on an asset whose database record no longer matches what was written on-chain. */
    public void requireMatchesChain(Asset a) {
        ChainVerifier.Check c = verifier.asset(a);
        if (c.status() == ChainVerifier.Status.MISMATCH) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "This asset's record doesn't match the blockchain (" + c.detail().toLowerCase().replace(".", "")
                            + "), so it can't be verified or borrowed.");
        }
    }

    @Transactional(readOnly = true)
    public List<AssetResponse> list() {
        boolean up = verifier.reachable();
        return assets.findAllByOrderByCreatedAtDesc().stream()
                .map(a -> AssetResponse.from(a, up ? verifier.asset(a) : ChainVerifier.Check.unavailable()))
                .toList();
    }

    @Transactional
    public AssetResponse register(User owner, CreateAssetRequest req) {
        String description = req.description() == null ? "" : req.description().trim();

        Asset a = new Asset();
        a.setName(req.name().trim());
        a.setCategory(req.category().trim());
        a.setDescription(description);
        a.setOwner(owner);
        a.setDataHash(chain.hash(String.join("|", a.getName(), a.getCategory(), description, String.valueOf(owner.getId()))));
        assets.saveAndFlush(a); // we need the generated id for the chain call

        // If the chain call fails, the exception rolls the database insert back too.
        TxResult tx = chain.registerAsset(a.getId(), owner.getId(), a.getDataHash());
        ledger.record("Asset registered", a.getCode() + " · " + a.getName(), a.getId(), tx);
        return AssetResponse.from(a);
    }

    @Transactional
    public AssetResponse verify(Long id) {
        Asset a = assets.findForUpdate(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Asset not found."));
        if (a.getStatus() != Asset.Status.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "This asset is already verified.");
        }
        requireMatchesChain(a);
        TxResult tx = chain.verifyAsset(a.getId());
        a.setStatus(Asset.Status.AVAILABLE);
        ledger.record("Ownership verified", "Verified by admin · " + a.getCode(), a.getId(), tx);
        return AssetResponse.from(a);
    }
}