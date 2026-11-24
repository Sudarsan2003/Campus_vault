// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

/// SECURITY EXPERIMENT - DO NOT DEPLOY FOR REAL USE.
/// A copy of CampusVault with one deliberately injected flaw (improper access control):
/// returnAsset() has lost its onlyOperator guard, so ANY Ethereum account can close any loan.
/// Used by test/AccessControlAttack.test.js and scripts/attack-demo.js to show the attack and its fix.
contract CampusVaultVulnerable {
    enum AssetStatus { None, Registered, Verified, Borrowed }

    struct Asset { uint256 ownerId; bytes32 dataHash; AssetStatus status; }
    struct Loan  { uint256 assetId; uint256 borrowerId; uint256 deposit; uint256 dueAt; bool open; bool flaggedOverdue; }

    /// Late fee: 10% of the deposit for each started day past the due date, capped at the whole deposit.
    uint256 public constant LATE_FEE_PERCENT_PER_DAY = 10;

    address public immutable operator;
    mapping(uint256 => Asset) public assets;
    mapping(uint256 => Loan) public loans;
    mapping(uint256 => uint256) public balances; // test tokens per user id

    event AssetRegistered(uint256 indexed assetId, uint256 indexed ownerId, bytes32 dataHash);
    event AssetVerified(uint256 indexed assetId);
    event TokensGranted(uint256 indexed userId, uint256 amount);
    event BorrowApproved(uint256 indexed loanId, uint256 indexed assetId, uint256 indexed borrowerId, uint256 deposit, uint256 dueAt);
    event LoanOverdue(uint256 indexed loanId, uint256 indexed borrowerId, uint256 dueAt);
    /// toBorrower + toOwner always equals the deposit.
    event ReturnVerified(uint256 indexed loanId, uint256 indexed assetId, bool damaged, uint256 toBorrower, uint256 toOwner);

    modifier onlyOperator() {
        require(msg.sender == operator, "Only the operator");
        _;
    }

    constructor() {
        operator = msg.sender;
    }

    function registerAsset(uint256 assetId, uint256 ownerId, bytes32 dataHash) external onlyOperator {
        require(assets[assetId].status == AssetStatus.None, "Already registered");
        assets[assetId] = Asset(ownerId, dataHash, AssetStatus.Registered);
        emit AssetRegistered(assetId, ownerId, dataHash);
    }

    function verifyAsset(uint256 assetId) external onlyOperator {
        require(assets[assetId].status == AssetStatus.Registered, "Not awaiting verification");
        assets[assetId].status = AssetStatus.Verified;
        emit AssetVerified(assetId);
    }

    function grantTokens(uint256 userId, uint256 amount) external onlyOperator {
        balances[userId] += amount;
        emit TokensGranted(userId, amount);
    }

    function borrow(uint256 loanId, uint256 assetId, uint256 borrowerId, uint256 deposit, uint256 dueAt)
        external onlyOperator
    {
        require(loans[loanId].assetId == 0, "Loan id already used");
        Asset storage a = assets[assetId];
        require(a.status == AssetStatus.Verified, "Asset is not available");
        require(a.ownerId != borrowerId, "Cannot borrow your own asset");
        require(balances[borrowerId] >= deposit, "Insufficient tokens for deposit");
        require(dueAt > block.timestamp, "Due date must be in the future");

        balances[borrowerId] -= deposit;
        a.status = AssetStatus.Borrowed;
        loans[loanId] = Loan(assetId, borrowerId, deposit, dueAt, true, false);
        emit BorrowApproved(loanId, assetId, borrowerId, deposit, dueAt);
    }

    function isOverdue(uint256 loanId) public view returns (bool) {
        Loan storage l = loans[loanId];
        return l.open && block.timestamp > l.dueAt;
    }

    /// The fee that would be kept from the deposit if the item came back now in good condition.
    function lateFee(uint256 loanId) public view returns (uint256) {
        Loan storage l = loans[loanId];
        if (!l.open || block.timestamp <= l.dueAt) return 0;
        uint256 daysLate = (block.timestamp - l.dueAt + 1 days - 1) / 1 days; // round up
        uint256 fee = l.deposit * LATE_FEE_PERCENT_PER_DAY * daysLate / 100;
        return fee > l.deposit ? l.deposit : fee;
    }

    /// Records on-chain that a loan passed its due date. Can only happen once per loan.
    function markOverdue(uint256 loanId) external onlyOperator {
        Loan storage l = loans[loanId];
        require(isOverdue(loanId), "Loan is not overdue");
        require(!l.flaggedOverdue, "Already marked overdue");
        l.flaggedOverdue = true;
        emit LoanOverdue(loanId, l.borrowerId, l.dueAt);
    }

    /// Damaged: the whole deposit goes to the owner.
    /// Otherwise the borrower gets the deposit back minus any late fee, which goes to the owner.
    // INJECTED VULNERABILITY: the onlyOperator modifier was removed from this function.
    function returnAsset(uint256 loanId, bool damaged) external {
        Loan storage l = loans[loanId];
        require(l.open, "Loan is not open");

        uint256 toOwner = damaged ? l.deposit : lateFee(loanId);
        uint256 toBorrower = l.deposit - toOwner;
        l.open = false;

        Asset storage a = assets[l.assetId];
        a.status = AssetStatus.Verified;
        balances[a.ownerId] += toOwner;
        balances[l.borrowerId] += toBorrower;
        emit ReturnVerified(loanId, l.assetId, damaged, toBorrower, toOwner);
    }
}