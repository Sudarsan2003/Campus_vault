package org.example.cvbackend.blockchain;

import org.example.cvbackend.exception.ApiException;
import org.example.cvbackend.model.Asset;
import org.example.cvbackend.model.LedgerEntry;
import org.example.cvbackend.model.Loan;
import org.example.cvbackend.model.Report;
import org.example.cvbackend.service.FileStorageService;
import org.springframework.stereotype.Service;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

/**
 * Re-checks what the database says against the blockchain, so the app never just claims a record is genuine.
 * Ledger entries: the transaction must exist, have succeeded, be in the stated block, and have been sent by the
 * operator to one of our contracts. Assets and reports: their hash is recomputed from the current database
 * fields (and, for reports, the photo on disk) and compared with the hash stored on-chain.
 */
@Service
public class ChainVerifier {

    public enum Status { VERIFIED, MISMATCH, UNAVAILABLE }

    public record Check(Status status, String detail) {
        static Check ok() { return new Check(Status.VERIFIED, "Matches the blockchain record."); }
        static Check bad(String why) { return new Check(Status.MISMATCH, why); }
        public static Check unavailable() { return new Check(Status.UNAVAILABLE, "The blockchain can't be reached to check this."); }
    }

    private final BlockchainService chain;
    private final LostFoundChain lostFound;
    private final FileStorageService files;

    public ChainVerifier(BlockchainService chain, LostFoundChain lostFound, FileStorageService files) {
        this.chain = chain;
        this.lostFound = lostFound;
        this.files = files;
    }

    /** True if the node answers; lets a list skip 50 slow failures when the chain is down. */
    public boolean reachable() {
        try {
            chain.chainTime();
            return true;
        } catch (ApiException e) {
            return false;
        }
    }

    public Check ledgerEntry(LedgerEntry e) {
        try {
            Optional<TransactionReceipt> r = chain.receipt(e.getTxHash());
            if (r.isEmpty()) return Check.bad("No such transaction on the blockchain.");
            TransactionReceipt rc = r.get();
            if (!rc.isStatusOK()) return Check.bad("The transaction failed on the blockchain.");
            if (rc.getBlockNumber().longValue() != e.getBlockNumber()) {
                return Check.bad("The transaction is in block " + rc.getBlockNumber() + ", not " + e.getBlockNumber() + ".");
            }
            if (!chain.operatorAddress().equalsIgnoreCase(rc.getFrom())) {
                return Check.bad("The transaction wasn't sent by CampusVault.");
            }
            String to = rc.getTo();
            if (to == null || !(to.equalsIgnoreCase(chain.contractAddress()) || to.equalsIgnoreCase(lostFound.address()))) {
                return Check.bad("The transaction wasn't sent to a CampusVault contract.");
            }
            return Check.ok();
        } catch (ApiException ex) {
            return Check.unavailable();
        }
    }

    /** Must use the same fields, in the same order, as AssetService.register. */
    public Check asset(Asset a) {
        String expected = chain.hash(String.join("|", a.getName(), a.getCategory(),
                a.getDescription() == null ? "" : a.getDescription(), String.valueOf(a.getOwner().getId())));
        try {
            List<Type> out = chain.callView(chain.contractAddress(), new Function("assets", List.of(u(a.getId())),
                    List.of(new TypeReference<Uint256>() {}, new TypeReference<Bytes32>() {}, new TypeReference<Uint8>() {})));
            long ownerOnChain = ((BigInteger) out.get(0).getValue()).longValue();
            String hashOnChain = Numeric.toHexString((byte[]) out.get(1).getValue());
            if (ownerOnChain == 0) return Check.bad("This asset isn't on the blockchain.");
            if (ownerOnChain != a.getOwner().getId()) return Check.bad("The owner was changed after it was recorded.");
            if (!hashOnChain.equalsIgnoreCase(expected)) return Check.bad("The details were changed after they were recorded.");
            return Check.ok();
        } catch (ApiException ex) {
            return Check.unavailable();
        }
    }

    /** Must use the same fields, in the same order, as ReportService.create. */
    public Check report(Report r) {
        String photoHash;
        try {
            photoHash = files.sha256Of(r.getImageFile());
        } catch (ApiException ex) {
            return Check.bad("The photo is missing.");
        }
        String expected = chain.hash(String.join("|", r.getKind().name(), r.getTitle(), r.getCategory(),
                r.getDescription() == null ? "" : r.getDescription(), r.getLocation(), r.getEventDate().toString(),
                photoHash, String.valueOf(r.getReporter().getId())));
        try {
            List<Type> out = chain.callView(lostFound.address(), new Function("reports", List.of(u(r.getId())),
                    List.of(new TypeReference<Uint256>() {}, new TypeReference<Bool>() {}, new TypeReference<Bytes32>() {},
                            new TypeReference<Uint8>() {}, new TypeReference<Uint256>() {})));
            long reporterOnChain = ((BigInteger) out.get(0).getValue()).longValue();
            boolean foundOnChain = (Boolean) out.get(1).getValue();
            String hashOnChain = Numeric.toHexString((byte[]) out.get(2).getValue());
            if (((BigInteger) out.get(3).getValue()).intValue() == 0) return Check.bad("This report isn't on the blockchain.");
            if (reporterOnChain != r.getReporter().getId() || foundOnChain != (r.getKind() == Report.Kind.FOUND)) {
                return Check.bad("The reporter or report type was changed after it was recorded.");
            }
            if (!hashOnChain.equalsIgnoreCase(expected)) {
                return Check.bad(photoHash.equalsIgnoreCase(r.getImageHash())
                        ? "The details were changed after they were recorded."
                        : "The photo was replaced after it was recorded.");
            }
            return Check.ok();
        } catch (ApiException ex) {
            return Check.unavailable();
        }
    }

    /**
     * A loan must exist on-chain with the same asset, borrower, deposit and due date, and be open or closed
     * in both places. A loan that is open here but closed on-chain was closed without going through
     * CampusVault - the signature of the access-control attack in contracts/attack/CampusVaultVulnerable.sol.
     */
    public Check loan(Loan l) {
        try {
            List<Type> out = chain.callView(chain.contractAddress(), new Function("loans", List.of(u(l.getId())),
                    List.of(new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {},
                            new TypeReference<Uint256>() {}, new TypeReference<Bool>() {}, new TypeReference<Bool>() {})));
            long assetId = ((BigInteger) out.get(0).getValue()).longValue();
            long borrowerId = ((BigInteger) out.get(1).getValue()).longValue();
            long deposit = ((BigInteger) out.get(2).getValue()).longValue();
            long dueAt = ((BigInteger) out.get(3).getValue()).longValue();
            boolean openOnChain = (Boolean) out.get(4).getValue();
            boolean openHere = l.getStatus() == Loan.Status.OPEN;

            if (assetId == 0) return Check.bad("This loan isn't on the blockchain.");
            if (assetId != l.getAsset().getId() || borrowerId != l.getBorrower().getId()
                    || deposit != l.getDeposit() || dueAt != l.getDueAt().getEpochSecond()) {
                return Check.bad("The loan's details were changed after they were recorded.");
            }
            if (openHere && !openOnChain) {
                return Check.bad("Closed on the blockchain without a return verified in CampusVault, so the item may not have been returned.");
            }
            if (!openHere && openOnChain) return Check.bad("Marked as returned here, but still open on the blockchain.");
            return Check.ok();
        } catch (ApiException ex) {
            return Check.unavailable();
        }
    }

    private static Uint256 u(long v) {
        return new Uint256(BigInteger.valueOf(v));
    }
}
