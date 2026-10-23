package org.example.cvbackend.controller;

import jakarta.validation.Valid;
import org.example.cvbackend.dto.AssetDtos.AssetResponse;
import org.example.cvbackend.dto.AssetDtos.CreateAssetRequest;
import org.example.cvbackend.model.User;
import org.example.cvbackend.service.AssetService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assets")
public class AssetController {

    private final AssetService service;

    public AssetController(AssetService service) {
        this.service = service;
    }

    @GetMapping
    public List<AssetResponse> list() {
        return service.list();
    }

    @PostMapping
    public ResponseEntity<AssetResponse> create(@AuthenticationPrincipal User user,
                                                @Valid @RequestBody CreateAssetRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(user, req));
    }
}