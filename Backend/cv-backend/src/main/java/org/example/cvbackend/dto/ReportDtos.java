package org.example.cvbackend.dto;

import jakarta.validation.constraints.NotNull;
import org.example.cvbackend.blockchain.ChainVerifier.Check;
import org.example.cvbackend.model.Report;

import java.time.Instant;
import java.time.LocalDate;

public final class ReportDtos {
    private ReportDtos() {}

    public record MatchRequest(@NotNull(message = "Choose a found report") Long foundId) {}
    public record SuggestionResponse(ReportResponse report, int similarity, String strength) {}
    public record ReportResponse(Long id, String code, String kind, String title, String category,
                                 String description, String location, LocalDate eventDate, String status,
                                 Long reporterId, String reporterName,
                                 Long matchedWithId, String matchedWithCode,
                                 String dataHash, Instant createdAt,
                                 String chainStatus, String chainDetail) {
        public static ReportResponse from(Report r) {
            return from(r, null);
        }

        /** chainStatus: VERIFIED, MISMATCH or UNAVAILABLE; null when not checked. */
        public static ReportResponse from(Report r, Check check) {
            Report m = r.getMatchedWith();
            return new ReportResponse(r.getId(), r.getCode(), r.getKind().name(), r.getTitle(), r.getCategory(),
                    r.getDescription(), r.getLocation(), r.getEventDate(), r.getStatus().name(),
                    r.getReporter().getId(), r.getReporter().getFullName(),
                    m == null ? null : m.getId(), m == null ? null : m.getCode(),
                    r.getDataHash(), r.getCreatedAt(),
                    check == null ? null : check.status().name(), check == null ? null : check.detail());
        }
    }
}