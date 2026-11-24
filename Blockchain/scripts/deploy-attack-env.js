// SECURITY EXPERIMENT ONLY: sets up the "attack environment" so the injected access-control attack can be
// shown between two real accounts in the running app. Deploys a fresh LostFound and CampusVaultVulnerable
// (returnAsset without onlyOperator) from the app's operator account, and writes their addresses to
// Backend/cv-backend/attack-env.properties, which the backend's "attack" profile reads.
// The real contracts and the real database are not touched.
//   npx hardhat run scripts/deploy-attack-env.js --network localhost
const fs = require("fs");
const path = require("path");
const hre = require("hardhat");

async function main() {
  const lostFound = await hre.ethers.deployContract("LostFound");
  await lostFound.waitForDeployment();
  const vault = await hre.ethers.deployContract("CampusVaultVulnerable");
  await vault.waitForDeployment();

  const lf = await lostFound.getAddress();
  const cv = await vault.getAddress();
  const out = path.join(__dirname, "..", "..", "Backend", "cv-backend", "attack-env.properties");
  fs.writeFileSync(out,
    "# Written by Blockchain/scripts/deploy-attack-env.js. Attack environment only.\n" +
    `app.blockchain.contract-address=${cv}\n` +
    `app.blockchain.lostfound-address=${lf}\n`);

  console.log("LostFound (attack env) deployed to:", lf);
  console.log("CampusVaultVulnerable deployed to:  ", cv);
  console.log("Wrote", path.relative(process.cwd(), out));
}

main().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
