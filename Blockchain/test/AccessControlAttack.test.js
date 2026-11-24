// Security experiment: an improper-access-control flaw is injected into a copy of the contract
// (CampusVaultVulnerable: returnAsset() without onlyOperator) and exploited by an outside account.
// The same attack is then run against the real CampusVault contract, where it must fail.
const { expect } = require("chai");
const { ethers } = require("hardhat");
const { time } = require("@nomicfoundation/hardhat-network-helpers");

const DAY = 24 * 60 * 60;
const OWNER = 1, BORROWER = 2, ASSET = 1, LOAN = 1, DEPOSIT = 1000;

// Operator registers and verifies an asset, gives the borrower tokens, and lends it for 3 days.
async function lentAsset(contractName) {
  const [operator, attacker] = await ethers.getSigners();
  const vault = await ethers.deployContract(contractName, operator);
  await vault.registerAsset(ASSET, OWNER, ethers.ZeroHash);
  await vault.verifyAsset(ASSET);
  await vault.grantTokens(BORROWER, 5000);
  await vault.borrow(LOAN, ASSET, BORROWER, DEPOSIT, (await time.latest()) + 3 * DAY);
  return { vault, attacker };
}

describe("Injected attack: improper access control on returnAsset()", function () {
  describe("against the VULNERABLE contract", function () {
    it("lets an outside account close a loan the owner never verified (item kept, deposit refunded)", async function () {
      const { vault, attacker } = await lentAsset("CampusVaultVulnerable");
      expect(await vault.balances(BORROWER)).to.equal(4000); // 1,000 locked as deposit

      await vault.connect(attacker).returnAsset(LOAN, false); // attacker is not the operator

      expect((await vault.loans(LOAN)).open).to.equal(false); // loan closed on-chain
      expect((await vault.assets(ASSET)).status).to.equal(2n); // Verified = shown as available again
      expect(await vault.balances(BORROWER)).to.equal(5000); // deposit refunded, item never returned
    });

    it("lets an outside account wipe out a borrower's deposit by marking an on-time return as damaged", async function () {
      const { vault, attacker } = await lentAsset("CampusVaultVulnerable");
      await vault.connect(attacker).returnAsset(LOAN, true);
      expect(await vault.balances(BORROWER)).to.equal(4000); // the 1,000 deposit is gone
      expect(await vault.balances(OWNER)).to.equal(DEPOSIT);
    });

    it("lets an overdue borrower dodge part of the late fee by closing the loan early", async function () {
      const { vault, attacker } = await lentAsset("CampusVaultVulnerable");
      await time.increase(4 * DAY - 3600); // within the 1st day late -> fee 100; it keeps growing daily
      await vault.connect(attacker).returnAsset(LOAN, false); // closes the loan with the item still out
      expect(await vault.balances(BORROWER)).to.equal(4900);
      expect(await vault.lateFee(LOAN)).to.equal(0); // no more fees can ever accrue
    });
  });

  describe("against the REAL (fixed) contract", function () {
    it("rejects the same calls from an outside account", async function () {
      const { vault, attacker } = await lentAsset("CampusVault");
      await expect(vault.connect(attacker).returnAsset(LOAN, false)).to.be.revertedWith("Only the operator");
      await expect(vault.connect(attacker).returnAsset(LOAN, true)).to.be.revertedWith("Only the operator");
      expect((await vault.loans(LOAN)).open).to.equal(true);
      expect(await vault.balances(BORROWER)).to.equal(4000);
    });

    it("guards every state-changing function with onlyOperator", async function () {
      const { vault, attacker } = await lentAsset("CampusVault");
      const v = vault.connect(attacker);
      await expect(v.registerAsset(9, 1, ethers.ZeroHash)).to.be.revertedWith("Only the operator");
      await expect(v.verifyAsset(ASSET)).to.be.revertedWith("Only the operator");
      await expect(v.grantTokens(BORROWER, 1)).to.be.revertedWith("Only the operator");
      await expect(v.borrow(9, ASSET, BORROWER, 1, 0)).to.be.revertedWith("Only the operator");
      await expect(v.markOverdue(LOAN)).to.be.revertedWith("Only the operator");
      await expect(v.returnAsset(LOAN, false)).to.be.revertedWith("Only the operator");
    });
  });
});
