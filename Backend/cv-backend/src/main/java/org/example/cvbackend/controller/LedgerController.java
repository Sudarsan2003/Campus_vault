package org.example.cvbackend.controller;

import org.example.cvbackend.dto.AssetDtos.LedgerResponse;
import org.example.cvbackend.dto.AssetDtos.WalletResponse;
import org.example.cvbackend.model.User;
import org.example.cvbackend.service.LedgerService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class LedgerController {

    private final LedgerService ledger;

    public LedgerController(LedgerService ledger) {
        this.ledger = ledger;
    }

    @GetMapping("/ledger")
    public List<LedgerResponse> ledger(@RequestParam(value = "assetId", required = false) Long assetId) {
        return ledger.list(assetId);
    }

    @GetMapping("/wallet")
    public WalletResponse wallet(@AuthenticationPrincipal User user) {
        return ledger.wallet(user.getId());
    }

    @PostMapping("/wallet/faucet")
    public WalletResponse faucet(@AuthenticationPrincipal User user) {
        return ledger.faucet(user.getId());
    }
}