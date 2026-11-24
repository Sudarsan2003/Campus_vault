package org.example.cvbackend.controller;

import org.example.cvbackend.dto.AssetDtos.AssetResponse;
import org.example.cvbackend.dto.AssetDtos.LoanResponse;
import org.example.cvbackend.service.AssetService;
import org.example.cvbackend.service.LoanService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Everything under /api/admin is restricted to ADMIN in SecurityConfig. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AssetService assets;
    private final LoanService loans;

    public AdminController(AssetService assets, LoanService loans) {
        this.assets = assets;
        this.loans = loans;
    }

    /** All open loans past their due date, oldest first. */
    @GetMapping("/loans/overdue")
    public List<LoanResponse> overdueLoans() {
        return loans.overdue();
    }

    @PostMapping("/assets/{id}/verify")
    public AssetResponse verify(@PathVariable("id") Long id) {
        return assets.verify(id);
    }
}