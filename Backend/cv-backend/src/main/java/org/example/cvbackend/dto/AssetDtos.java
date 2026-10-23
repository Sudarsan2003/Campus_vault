package org.example.cvbackend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.cvbackend.model.Asset;
import org.example.cvbackend.model.LedgerEntry;
import org.example.cvbackend.model.Loan;

import java.time.Instant;

public final class AssetDtos {
    private AssetDtos() {}

    public record CreateAssetRequest(
            @NotBlank(message = "Name is required")
            @Size(max = 120, message = "Keep the name under 120 characters")
            String name,

            @NotBlank(message = "Category is required")
            @Size(max = 40, message = "Category is too long")
            String category,

            @Size(max = 500, message = "Keep the description under 500 characters")
            String description
    ) {}

    public record BorrowRequest(
            @NotNull(message = "Choose an asset") Long assetId,
            @Min(value = 1, message = "Borrow for 1 to 30 days")
            @Max(value = 30, message = "Borrow for 1 to 30 days")
            int days
    ) {}

    public record ReturnRequest(boolean damaged) {}

    public record AssetResponse(Long id, String code, String name, String category, String description,
                                String status, Long ownerId, String ownerName, String dataHash, Instant createdAt) {
        public static AssetResponse from(Asset a) {
            return new AssetResponse(a.getId(), a.getCode(), a.getName(), a.getCategory(), a.getDescription(),
                    a.getStatus().name(), a.getOwner().getId(), a.getOwner().getFullName(),
                    a.getDataHash(), a.getCreatedAt());
        }
    }

    public record LoanResponse(Long id, Long assetId, String assetCode, String assetName,
                               Long borrowerId, String borrowerName, Long ownerId,
                               long deposit, Instant dueAt, String status, boolean damaged, Instant returnedAt) {
        public static LoanResponse from(Loan l) {
            Asset a = l.getAsset();
            return new LoanResponse(l.getId(), a.getId(), a.getCode(), a.getName(),
                    l.getBorrower().getId(), l.getBorrower().getFullName(), a.getOwner().getId(),
                    l.getDeposit(), l.getDueAt(), l.getStatus().name(), l.isDamaged(), l.getReturnedAt());
        }
    }

    public record LedgerResponse(Long id, String title, String detail, Long assetId, String assetCode,
                                 String txHash, long blockNumber, Instant createdAt) {
        public static LedgerResponse from(LedgerEntry e) {
            return new LedgerResponse(e.getId(), e.getTitle(), e.getDetail(), e.getAssetId(),
                    e.getAssetId() == null ? null : Asset.codeFor(e.getAssetId()),
                    e.getTxHash(), e.getBlockNumber(), e.getCreatedAt());
        }
    }

    public record WalletResponse(long balance, long depositPerLoan, long faucetAmount) {}
}