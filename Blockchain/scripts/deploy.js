const hre = require("hardhat");

async function main() {
  const vault = await hre.ethers.deployContract("CampusVault");
  await vault.waitForDeployment();
  console.log("CampusVault deployed to:", await vault.getAddress());
}

main().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});