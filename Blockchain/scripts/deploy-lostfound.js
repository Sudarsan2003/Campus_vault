const hre = require("hardhat");

async function main() {
  const c = await hre.ethers.deployContract("LostFound");
  await c.waitForDeployment();
  console.log("LostFound deployed to:", await c.getAddress());
}

main().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});