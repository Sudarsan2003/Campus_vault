// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

/// Records lost and found reports, matches between them, and confirmed handovers.
/// Only the backend (the operator) writes; anyone can read state and events.
contract LostFound {
    enum Status { None, Open, Matched, Returned }

    struct Report {
        uint256 reporterId;
        bool found;          // false = lost report, true = found report
        bytes32 dataHash;    // hash of the details and the photo hash
        Status status;
        uint256 matchedWith; // report ids start at 1, so 0 means "not matched"
    }

    address public immutable operator;
    mapping(uint256 => Report) public reports;

    event ItemReported(uint256 indexed reportId, uint256 indexed reporterId, bool found, bytes32 dataHash);
    event ReportsMatched(uint256 indexed lostId, uint256 indexed foundId);
    event ItemReturned(uint256 indexed lostId, uint256 indexed foundId);

    modifier onlyOperator() {
        require(msg.sender == operator, "Only the operator");
        _;
    }

    constructor() {
        operator = msg.sender;
    }

    function reportItem(uint256 reportId, uint256 reporterId, bool found, bytes32 dataHash) external onlyOperator {
        require(reports[reportId].status == Status.None, "Report id already used");
        reports[reportId] = Report(reporterId, found, dataHash, Status.Open, 0);
        emit ItemReported(reportId, reporterId, found, dataHash);
    }

    function matchReports(uint256 lostId, uint256 foundId) external onlyOperator {
        Report storage l = reports[lostId];
        Report storage f = reports[foundId];
        require(l.status == Status.Open && f.status == Status.Open, "Both reports must be open");
        require(!l.found && f.found, "Need one lost and one found report");

        l.status = Status.Matched;
        f.status = Status.Matched;
        l.matchedWith = foundId;
        f.matchedWith = lostId;
        emit ReportsMatched(lostId, foundId);
    }

    function confirmReturn(uint256 lostId, uint256 foundId) external onlyOperator {
        Report storage l = reports[lostId];
        Report storage f = reports[foundId];
        require(l.status == Status.Matched && l.matchedWith == foundId, "Reports are not matched");

        l.status = Status.Returned;
        f.status = Status.Returned;
        emit ItemReturned(lostId, foundId);
    }
}