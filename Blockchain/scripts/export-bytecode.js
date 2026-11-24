// Copies the compiled bytecode of CampusVault and CampusVaultVulnerable into the backend, so the
// admin "Security lab" can deploy throwaway copies of both and run the access-control attack.
// Run after changing either contract:  npm run export-bytecode
const fs = require("fs");
const path = require("path");

const ART = path.join(__dirname, "..", "artifacts", "contracts");
const OUT = path.join(__dirname, "..", "..", "Backend", "cv-backend", "src", "main", "resources", "security-demo");

const contracts = {
  "CampusVault.bin": path.join(ART, "CampusVault.sol", "CampusVault.json"),
  "CampusVaultVulnerable.bin": path.join(ART, "attack", "CampusVaultVulnerable.sol", "CampusVaultVulnerable.json"),
};

fs.mkdirSync(OUT, { recursive: true });
for (const [file, artifact] of Object.entries(contracts)) {
  const { bytecode } = JSON.parse(fs.readFileSync(artifact, "utf8"));
  fs.writeFileSync(path.join(OUT, file), bytecode.trim());
  console.log(`wrote ${file} (${(bytecode.length - 2) / 2} bytes)`);
}
