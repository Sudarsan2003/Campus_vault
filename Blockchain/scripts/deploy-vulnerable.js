// SECURITY EXPERIMENT ONLY: deploys the copy of CampusVault with the injected access-control flaw,
// so the attack can be shown against the running app. Never use this address in normal operation.
//   npx hardhat run scripts/deploy-vulnerable.js --network localhost
const hre = require("hardhat");

async function main() {
  const c = await hre.ethers.deployContract("CampusVaultVulnerable");
  await c.waitForDeployment();
  console.log("CampusVaultVulnerable deployed to:", await c.getAddress());
}

main().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
