const { expect } = require("chai");
const { ethers } = require("hardhat");
const { time } = require("@nomicfoundation/hardhat-network-helpers");

const DAY = 24 * 60 * 60;
const OWNER = 1, BORROWER = 2, ASSET = 1, DEPOSIT = 1000;

describe("CampusVault overdue loans", function () {
  let vault;

  // A verified asset, borrowed for 3 days; returns the loan's due timestamp.
  async function borrowed(loanId = 1) {
    vault = await ethers.deployContract("CampusVault");
    await vault.registerAsset(ASSET, OWNER, ethers.ZeroHash);
    await vault.verifyAsset(ASSET);
    await vault.grantTokens(BORROWER, 5000);
    const due = (await time.latest()) + 3 * DAY;
    await vault.borrow(loanId, ASSET, BORROWER, DEPOSIT, due);
    return due;
  }

  it("charges nothing when returned on time", async function () {
    await borrowed();
    expect(await vault.isOverdue(1)).to.equal(false);
    expect(await vault.lateFee(1)).to.equal(0);
    await expect(vault.returnAsset(1, false)).to.emit(vault, "ReturnVerified").withArgs(1, ASSET, false, DEPOSIT, 0);
    expect(await vault.balances(BORROWER)).to.equal(5000);
  });

  it("charges 10% of the deposit per started day late", async function () {
    const due = await borrowed();
    await time.increaseTo(due + 2 * DAY + 60); // 2 days and a minute late = 3 started days
    expect(await vault.isOverdue(1)).to.equal(true);
    expect(await vault.lateFee(1)).to.equal(300);
    await vault.returnAsset(1, false);
    expect(await vault.balances(BORROWER)).to.equal(5000 - 300);
    expect(await vault.balances(OWNER)).to.equal(300);
  });

  it("caps the late fee at the whole deposit", async function () {
    const due = await borrowed();
    await time.increaseTo(due + 30 * DAY);
    expect(await vault.lateFee(1)).to.equal(DEPOSIT);
  });

  it("gives the whole deposit to the owner when damaged, late or not", async function () {
    const due = await borrowed();
    await time.increaseTo(due + DAY);
    await expect(vault.returnAsset(1, true)).to.emit(vault, "ReturnVerified").withArgs(1, ASSET, true, 0, DEPOSIT);
  });

  it("marks a loan overdue on-chain only once, and only after the due date", async function () {
    const due = await borrowed();
    await expect(vault.markOverdue(1)).to.be.revertedWith("Loan is not overdue");
    await time.increaseTo(due + 1);
    await expect(vault.markOverdue(1)).to.emit(vault, "LoanOverdue").withArgs(1, BORROWER, due);
    await expect(vault.markOverdue(1)).to.be.revertedWith("Already marked overdue");
  });

  it("does not report a returned loan as overdue", async function () {
    const due = await borrowed();
    await vault.returnAsset(1, false);
    await time.increaseTo(due + 5 * DAY);
    expect(await vault.isOverdue(1)).to.equal(false);
    expect(await vault.lateFee(1)).to.equal(0);
  });

  it("only lets the operator mark loans overdue", async function () {
    const due = await borrowed();
    await time.increaseTo(due + DAY);
    const [, stranger] = await ethers.getSigners();
    await expect(vault.connect(stranger).markOverdue(1)).to.be.revertedWith("Only the operator");
  });
});
