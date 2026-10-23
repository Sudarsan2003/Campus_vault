package org.example.cvbackend.service;

import org.example.cvbackend.blockchain.BlockchainService;
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

    public AssetService(AssetRepository assets, BlockchainService chain, LedgerService ledger) {
        this.assets = assets;
        this.chain = chain;
        this.ledger = ledger;
    }

    @Transactional(readOnly = true)
    public List<AssetResponse> list() {
        return assets.findAllByOrderByCreatedAtDesc().stream().map(AssetResponse::from).toList();
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
        TxResult tx = chain.verifyAsset(a.getId());
        a.setStatus(Asset.Status.AVAILABLE);
        ledger.record("Ownership verified", "Verified by admin · " + a.getCode(), a.getId(), tx);
        return AssetResponse.from(a);
    }
}