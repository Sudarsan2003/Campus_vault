package org.example.cvbackend.controller;

import jakarta.validation.Valid;
import org.example.cvbackend.dto.AssetDtos.BorrowRequest;
import org.example.cvbackend.dto.AssetDtos.LoanResponse;
import org.example.cvbackend.dto.AssetDtos.ReturnRequest;
import org.example.cvbackend.model.User;
import org.example.cvbackend.service.LoanService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/loans")
public class LoanController {

    private final LoanService service;

    public LoanController(LoanService service) {
        this.service = service;
    }

    @GetMapping("/mine")
    public List<LoanResponse> mine(@AuthenticationPrincipal User user) {
        return service.mine(user.getId());
    }

    @PostMapping
    public ResponseEntity<LoanResponse> borrow(@AuthenticationPrincipal User user,
                                               @Valid @RequestBody BorrowRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.borrow(user, req));
    }

    @PostMapping("/{id}/return")
    public LoanResponse verifyReturn(@AuthenticationPrincipal User user,
                                     @PathVariable("id") Long id,
                                     @RequestBody ReturnRequest req) {
        return service.verifyReturn(user, id, req.damaged());
    }
}