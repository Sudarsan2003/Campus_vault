package org.example.cvbackend.service;

import org.example.cvbackend.ai.EmbeddingClient;
import org.example.cvbackend.blockchain.BlockchainService.TxResult;
import org.example.cvbackend.blockchain.ChainVerifier;
import org.example.cvbackend.blockchain.LostFoundChain;
import org.example.cvbackend.dto.ReportDtos.ReportResponse;
import org.example.cvbackend.dto.ReportDtos.SuggestionResponse;
import org.example.cvbackend.exception.ApiException;
import org.example.cvbackend.model.Report;
import org.example.cvbackend.model.Role;
import org.example.cvbackend.model.User;
import org.example.cvbackend.repository.ReportRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class ReportService {

    public record ImageData(byte[] bytes, String contentType) {}

    private static final int MAX_SUGGESTIONS = 5;

    private final ReportRepository reports;
    private final FileStorageService files;
    private final LostFoundChain chain;
    private final LedgerService ledger;
    private final EmbeddingClient embeddings;
    private final ChainVerifier verifier;
    private final double minScore;
    private final double crossCategoryMinScore;
    private final double strongScore;
    private final double likelyScore;

    public ReportService(ReportRepository reports, FileStorageService files, LostFoundChain chain,
                         LedgerService ledger, EmbeddingClient embeddings, ChainVerifier verifier,
                         @Value("${app.ai.min-score}") double minScore,
                         @Value("${app.ai.cross-category-min-score}") double crossCategoryMinScore,
                         @Value("${app.ai.strong-score}") double strongScore,
                         @Value("${app.ai.likely-score}") double likelyScore) {
        this.reports = reports;
        this.files = files;
        this.chain = chain;
        this.ledger = ledger;
        this.embeddings = embeddings;
        this.verifier = verifier;
        this.minScore = minScore;
        this.crossCategoryMinScore = crossCategoryMinScore;
        this.strongScore = strongScore;
        this.likelyScore = likelyScore;
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> list() {
        boolean up = verifier.reachable();
        return reports.findAllByOrderByCreatedAtDesc().stream()
                .map(r -> ReportResponse.from(r, up ? verifier.report(r) : ChainVerifier.Check.unavailable()))
                .toList();
    }

    @Transactional
    public ReportResponse create(User reporter, String kindRaw, String titleRaw, String categoryRaw,
                                 String descriptionRaw, String locationRaw, String dateRaw, MultipartFile image) {
        Report.Kind kind;
        try {
            kind = Report.Kind.valueOf(clean(kindRaw).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw bad("Choose whether the item is lost or found.");
        }
        String title = clean(titleRaw);
        String category = clean(categoryRaw);
        String description = clean(descriptionRaw);
        String location = clean(locationRaw);

        if (title.isEmpty() || title.length() > 120) throw bad("Enter a title of up to 120 characters.");
        if (category.isEmpty() || category.length() > 40) throw bad("Choose a category.");
        if (location.isEmpty() || location.length() > 120) throw bad("Enter where it was lost or found.");
        if (description.length() > 500) throw bad("Keep the description under 500 characters.");

        LocalDate date;
        try {
            date = LocalDate.parse(clean(dateRaw));
        } catch (DateTimeParseException e) {
            throw bad("Enter a valid date.");
        }
        if (date.isAfter(LocalDate.now())) throw bad("The date can't be in the future.");

        FileStorageService.Stored stored = files.store(image);
        try {
            Report r = new Report();
            r.setKind(kind);
            r.setTitle(title);
            r.setCategory(category);
            r.setDescription(description);
            r.setLocation(location);
            r.setEventDate(date);
            r.setReporter(reporter);
            r.setImageFile(stored.filename());
            r.setImageHash(stored.sha256());
            r.setDataHash(chain.hash(String.join("|", kind.name(), title, category, description, location,
                    date.toString(), stored.sha256(), String.valueOf(reporter.getId()))));

            // If the matching service is down, the report is still saved; the embedding is computed later.
            embeddings.embed(files.load(stored.filename()), files.contentTypeOf(stored.filename()))
                    .ifPresent(v -> r.setEmbedding(EmbeddingClient.toText(v)));

            reports.saveAndFlush(r); // we need the generated id for the chain call

            TxResult tx = chain.reportItem(r.getId(), reporter.getId(), kind == Report.Kind.FOUND, r.getDataHash());
            ledger.record(kind == Report.Kind.LOST ? "Lost item reported" : "Found item reported",
                    r.getCode() + " · " + title, null, tx);
            return ReportResponse.from(r);
        } catch (RuntimeException e) {
            files.delete(stored.filename()); // the database insert rolls back, so drop the photo too
            throw e;
        }
    }

    /**
     * Open reports of the opposite kind, ranked by how similar their photos look.
     * A candidate is shown only if
     *  - its photo similarity reaches app.ai.min-score (same category) or
     *    app.ai.cross-category-min-score (different category, in case someone picked the wrong one), and
     *  - the dates make sense: an item can't be found more than a day before it was lost.
     */
    @Transactional
    public List<SuggestionResponse> suggestions(User actor, Long id) {
        Report r = reports.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Report not found."));
        if (!r.getReporter().getId().equals(actor.getId()) && actor.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the person who made this report can see its matches.");
        }
        if (r.getStatus() != Report.Status.OPEN) return List.of();

        float[] base = vector(r);
        if (base == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The image matching service isn't running. Start it from the AI folder, then try again.");
        }

        Report.Kind wanted = r.getKind() == Report.Kind.LOST ? Report.Kind.FOUND : Report.Kind.LOST;
        return reports.findByKindAndStatus(wanted, Report.Status.OPEN).stream()
                .filter(c -> !c.getReporter().getId().equals(r.getReporter().getId()))
                .filter(c -> datesPlausible(r, c))
                .map(c -> {
                    float[] v = vector(c);
                    return v == null ? null : new Scored(c, EmbeddingClient.cosine(base, v));
                })
                .filter(s -> s != null && s.score >= threshold(r, s.report))
                .sorted(Comparator.comparingDouble((Scored s) -> s.score).reversed())
                .limit(MAX_SUGGESTIONS)
                .map(s -> {
                    int pct = (int) Math.round(s.score * 100);
                    String strength = s.score >= strongScore ? "Strong" : s.score >= likelyScore ? "Likely" : "Possible";
                    return new SuggestionResponse(ReportResponse.from(s.report), pct, strength);
                })
                .toList();
    }

    private double threshold(Report a, Report b) {
        return a.getCategory().equalsIgnoreCase(b.getCategory()) ? minScore : crossCategoryMinScore;
    }

    /** The found date may be at most one day before the lost date (allowing for a mistyped date). */
    private static boolean datesPlausible(Report a, Report b) {
        Report lost = a.getKind() == Report.Kind.LOST ? a : b;
        Report found = lost == a ? b : a;
        return !found.getEventDate().isBefore(lost.getEventDate().minusDays(1));
    }

    private record Scored(Report report, double score) {}

    /** The stored embedding, or a fresh one (saved for next time). Null if it can't be computed. */
    private float[] vector(Report r) {
        if (r.getEmbedding() != null) {
            try {
                return EmbeddingClient.parse(r.getEmbedding());
            } catch (RuntimeException ignored) {
                // corrupt value: fall through and compute it again
            }
        }
        byte[] bytes;
        try {
            bytes = files.load(r.getImageFile());
        } catch (ApiException e) {
            return null; // photo missing on disk
        }
        Optional<float[]> v = embeddings.embed(bytes, files.contentTypeOf(r.getImageFile()));
        v.ifPresent(x -> r.setEmbedding(EmbeddingClient.toText(x)));
        return v.orElse(null);
    }

    /** The owner of a lost report claims an open found report. */
    @Transactional
    public ReportResponse match(User actor, Long lostId, Long foundId) {
        if (lostId.equals(foundId)) throw bad("Choose a different report to match with.");

        Report a = lock(Math.min(lostId, foundId)); // always lock in id order to avoid deadlocks
        Report b = lock(Math.max(lostId, foundId));
        Report lost = a.getId().equals(lostId) ? a : b;
        Report found = lost == a ? b : a;

        if (lost.getKind() != Report.Kind.LOST || found.getKind() != Report.Kind.FOUND) {
            throw bad("A match needs one lost report and one found report.");
        }
        if (!lost.getReporter().getId().equals(actor.getId()) && actor.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the person who lost the item can claim a match.");
        }
        if (lost.getReporter().getId().equals(found.getReporter().getId())) {
            throw bad("You can't match with your own found report.");
        }
        if (lost.getStatus() != Report.Status.OPEN || found.getStatus() != Report.Status.OPEN) {
            throw new ApiException(HttpStatus.CONFLICT, "One of these reports is already matched.");
        }

        TxResult tx = chain.matchReports(lost.getId(), found.getId());
        lost.setStatus(Report.Status.MATCHED);
        found.setStatus(Report.Status.MATCHED);
        lost.setMatchedWith(found);
        found.setMatchedWith(lost);
        ledger.record("Items matched", lost.getCode() + " ↔ " + found.getCode(), null, tx);
        return ReportResponse.from(lost);
    }

    /** The finder (or an admin) confirms the item was handed back. */
    @Transactional
    public ReportResponse confirmReturn(User actor, Long reportId) {
        Report seen = reports.findById(reportId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Report not found."));
        if (seen.getMatchedWith() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "This report isn't matched yet.");
        }

        long otherId = seen.getMatchedWith().getId();
        Report a = lock(Math.min(reportId, otherId));
        Report b = lock(Math.max(reportId, otherId));
        Report lost = a.getKind() == Report.Kind.LOST ? a : b;
        Report found = lost == a ? b : a;

        if (a.getStatus() != Report.Status.MATCHED || b.getStatus() != Report.Status.MATCHED) {
            throw new ApiException(HttpStatus.CONFLICT, "This handover is already confirmed.");
        }
        boolean isFinder = found.getReporter().getId().equals(actor.getId());
        if (!isFinder && actor.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the person who found the item (or an admin) can confirm the handover.");
        }

        TxResult tx = chain.confirmReturn(lost.getId(), found.getId());
        lost.setStatus(Report.Status.RETURNED);
        found.setStatus(Report.Status.RETURNED);
        ledger.record("Item returned", "Handed back · " + lost.getCode() + " ↔ " + found.getCode(), null, tx);
        return ReportResponse.from(reportId.equals(a.getId()) ? a : b);
    }

    @Transactional(readOnly = true)
    public ImageData image(Long id) {
        Report r = reports.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Report not found."));
        return new ImageData(files.load(r.getImageFile()), files.contentTypeOf(r.getImageFile()));
    }

    private Report lock(Long id) {
        return reports.findForUpdate(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Report not found."));
    }

    private static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }
}