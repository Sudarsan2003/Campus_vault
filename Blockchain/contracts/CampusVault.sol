// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

/// Records asset registration, verification, borrowing and returns.
/// Only the backend (the operator) can write; anyone can read state and events.
/// Deposits are in-contract test tokens, not real money.
contract CampusVault {
    enum AssetStatus { None, Registered, Verified, Borrowed }

    struct Asset { uint256 ownerId; bytes32 dataHash; AssetStatus status; }
    struct Loan  { uint256 assetId; uint256 borrowerId; uint256 deposit; uint256 dueAt; bool open; }

    address public immutable operator;
    mapping(uint256 => Asset) public assets;
    mapping(uint256 => Loan) public loans;
    mapping(uint256 => uint256) public balances; // test tokens per user id

    event AssetRegistered(uint256 indexed assetId, uint256 indexed ownerId, bytes32 dataHash);
    event AssetVerified(uint256 indexed assetId);
    event TokensGranted(uint256 indexed userId, uint256 amount);
    event BorrowApproved(uint256 indexed loanId, uint256 indexed assetId, uint256 indexed borrowerId, uint256 deposit, uint256 dueAt);
    event ReturnVerified(uint256 indexed loanId, uint256 indexed assetId, bool damaged, uint256 deposit);

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
        loans[loanId] = Loan(assetId, borrowerId, deposit, dueAt, true);
        emit BorrowApproved(loanId, assetId, borrowerId, deposit, dueAt);
    }

    /// Deposit goes back to the borrower, or to the owner if the item came back damaged.
    function returnAsset(uint256 loanId, bool damaged) external onlyOperator {
        Loan storage l = loans[loanId];
        require(l.open, "Loan is not open");
        l.open = false;

        Asset storage a = assets[l.assetId];
        a.status = AssetStatus.Verified;

        if (damaged) {
            balances[a.ownerId] += l.deposit;
        } else {
            balances[l.borrowerId] += l.deposit;
        }
        emit ReturnVerified(loanId, l.assetId, damaged, l.deposit);
    }
}