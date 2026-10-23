package org.example.cvbackend.controller;

import org.example.cvbackend.dto.AssetDtos.AssetResponse;
import org.example.cvbackend.service.AssetService;
import org.springframework.web.bind.annotation.*;

/** Everything under /api/admin is restricted to ADMIN in SecurityConfig. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AssetService assets;

    public AdminController(AssetService assets) {
        this.assets = assets;
    }

    @PostMapping("/assets/{id}/verify")
    public AssetResponse verify(@PathVariable("id") Long id) {
        return assets.verify(id);
    }
}