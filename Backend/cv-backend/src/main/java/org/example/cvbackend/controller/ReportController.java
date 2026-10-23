package org.example.cvbackend.controller;

import jakarta.validation.Valid;
import org.example.cvbackend.dto.ReportDtos.MatchRequest;
import org.example.cvbackend.dto.ReportDtos.ReportResponse;
import org.example.cvbackend.model.User;
import org.example.cvbackend.service.ReportService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.example.cvbackend.dto.ReportDtos.SuggestionResponse;
import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping
    public List<ReportResponse> list() {
        return service.list();
    }
    @GetMapping("/{id}/suggestions")
    public List<SuggestionResponse> suggestions(@AuthenticationPrincipal User user, @PathVariable("id") Long id) {
        return service.suggestions(user, id);
    }
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReportResponse> create(
            @AuthenticationPrincipal User user,
            @RequestParam(value = "kind", required = false) String kind,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "eventDate", required = false) String eventDate,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(user, kind, title, category, description, location, eventDate, image));
    }

    /** {id} is the lost report; the body names the found report being claimed. */
    @PostMapping("/{id}/match")
    public ReportResponse match(@AuthenticationPrincipal User user,
                                @PathVariable("id") Long id,
                                @Valid @RequestBody MatchRequest req) {
        return service.match(user, id, req.foundId());
    }

    @PostMapping("/{id}/return")
    public ReportResponse confirmReturn(@AuthenticationPrincipal User user, @PathVariable("id") Long id) {
        return service.confirmReturn(user, id);
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable("id") Long id) {
        ReportService.ImageData img = service.image(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(img.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .body(img.bytes());
    }
}