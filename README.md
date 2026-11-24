# Campus Vault

A blockchain-based campus asset lending and lost & found system.

## Project Structure

- `Blockchain/` - Solidity contracts (`CampusVault`, `LostFound`), Hardhat scripts
- `Backend/` - Spring Boot API with PostgreSQL
- `Frontend/` - React + Vite web UI
- `AI/` - Python AI module

## Prerequisites

- Node.js 18+
- Java 17+ and Maven
- PostgreSQL
- Python 3.9+
- Foundry (for `anvil`)
- MetaMask browser extension

## Setup and Run

Open a separate terminal for each service and run them in this order.

### 1. Start the local blockchain

```bash
cd Blockchain
anvil --state anvil-state.json --state-interval 5
```

The chain state is saved to `Blockchain/anvil-state.json` every 5 seconds. Always start Anvil from the `Blockchain` folder.

### 2. Deploy the smart contracts

Only do this on a fresh chain. Do not redeploy once Anvil has loaded a saved state.

```bash
cd Blockchain
npm install
npx hardhat run scripts/deploy-lostfound.js --network localhost
npx hardhat run scripts/deploy.js --network localhost
```

Run them in this order. On a fresh Anvil the printed addresses are:

```
LostFound deployed to: 0x5FbDB2315678afecb367f032d93F642f64180aa3
CampusVault deployed to: 0xe7f1725E7734CE288F8367e1Bb143E90bb3F0512
```

If your addresses differ, copy them into `Backend/cv-backend/src/main/resources/application.properties`:

```properties
app.blockchain.contract-address=<address printed by deploy.js>
app.blockchain.lostfound-address=<address printed by deploy-lostfound.js>
```

#### Upgrading an existing chain (late fees and overdue loans)

The `CampusVault` contract gained late fees and overdue tracking, so an existing chain needs both contracts deployed again. With Anvil running from `Blockchain` (step 1) and the backend stopped:

```bash
cd Blockchain
npx hardhat run scripts/deploy-lostfound.js --network localhost
npx hardhat run scripts/deploy.js --network localhost
psql campusvault -c "TRUNCATE loans, ledger_entries, assets, reports RESTART IDENTITY CASCADE;"
```

On the saved `anvil-state.json`, run before anything else touches the chain, these print `0xc6e7DF5E7b4f2A278906862b61205850344D4e7d` (LostFound) and `0x59b670e9fA9D0A427751Af201D676719a970857b` (CampusVault), which are already in `application.properties`. If yours differ, paste them in. Redeploy both contracts and truncate the tables together: the database restarts its ids at 1, and a contract that already holds those ids rejects them ("Report id already used").

Contract tests (late fees, overdue rules):

```bash
cd Blockchain
npm test
```

### 3. Set up the database

```bash
createdb campusvault
```

To reset all data after redeploying contracts:

```bash
psql campusvault -c "TRUNCATE loans, ledger_entries, assets, reports RESTART IDENTITY CASCADE;"
```

### 4. Start the backend

```bash
cd Backend/cv-backend
./mvnw spring-boot:run
```

On start, the backend moves the local chain's clock up to the real time. Anvil restores the old block time from `anvil-state.json`, and due dates are judged by chain time. Set `app.blockchain.sync-dev-clock=false` to turn this off; it never runs on a chain other than 31337.

### 5. Start the AI module

```bash
cd AI
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --port 8000
```

### 6. Start the frontend

```bash
cd Frontend
cp .env.example .env
npm install
npm run dev
```

Open the URL shown in the terminal (usually http://localhost:5173).

### 7. Connect MetaMask

Add a custom network:

- RPC URL: `http://127.0.0.1:8545`
- Chain ID: `31337`
- Currency symbol: `ETH`

Then click **Get test tokens** in the app.

## Overdue loans

- Due dates and late fees use chain time, so the contract and the backend always agree.
- Every minute (`app.loans.overdue-check-ms`) the backend records each newly overdue loan on-chain once (`markOverdue`, shown in the Ledger as "Loan overdue").
- Returning late keeps 10% of the deposit per started day past the due date, up to the whole deposit, for the owner. The contract computes this in `returnAsset`; a damaged item still forfeits the whole deposit.
- A borrower with an overdue loan can't borrow anything else until it's returned.
- Admins see every overdue loan under **My loans → Overdue across campus** (`GET /api/admin/loans/overdue`).

## On-chain verification

The ledger, asset list and lost & found list come from the database, so the backend re-checks every record against the blockchain before showing it (`ChainVerifier`):

- **Ledger entries:** the transaction must exist, have succeeded, be in the stated block, and have been sent by the operator to one of the two contracts.
- **Assets and reports:** the hash is recomputed from the current database fields (and, for reports, the photo on disk) and compared with the hash and owner stored on-chain.

Each record shows **On-chain**, **Doesn't match chain** (with the reason, e.g. "The details were changed after they were recorded"), or **Can't check chain** if the node is down. An asset that fails the check can't be verified or borrowed.

## Security experiment: injected access-control attack

`Blockchain/contracts/attack/CampusVaultVulnerable.sol` is a copy of `CampusVault` with one deliberately injected flaw: `returnAsset()` has lost its `onlyOperator` guard, so any Ethereum account can close any loan. It exists only to demonstrate the attack; the real contract is unchanged.

Show the attack and the fix (in-memory chain, touches nothing else):

```bash
cd Blockchain
npx hardhat run scripts/attack-demo.js
```

On the vulnerable copy, an outside wallet closes a loan whose item was never returned: the loan is closed, the deposit is refunded and the item is shown as available. The real contract rejects the same call with "Only the operator". `npm test` includes these attack tests, plus a regression test that every state-changing function rejects non-operators.

In the running app, the chain verifier also compares every loan with `loans(id)` on the contract. A loan that is open in the database but closed on-chain is shown as **Doesn't match chain** ("Closed on the blockchain without a return verified in CampusVault"), and settling it is refused. To reproduce live, deploy `scripts/deploy-vulnerable.js` to a throwaway Anvil chain, point `app.blockchain.contract-address` at it, borrow an item, and call `returnAsset` from another Anvil account with `cast send`.

## AI match threshold

Photo similarity (CLIP cosine) a found item needs before it's suggested for a lost item:

| Setting | Default | Meaning |
|---|---|---|
| `app.ai.min-score` | 0.70 | Same category |
| `app.ai.cross-category-min-score` | 0.80 | Different category (in case someone picked the wrong one) |
| `app.ai.strong-score` / `app.ai.likely-score` | 0.88 / 0.80 | Labels shown to the user |

Found items dated more than a day before the loss are never suggested. The defaults come from `AI/calibrate_threshold.py`: at the old 0.60, 58% of unrelated photos from other categories were suggested; at 0.80, none were. To re-tune on real campus photos, put them in a folder named `<category>_<name>.jpg` and run `python calibrate_threshold.py <folder>` from `AI` with the venv active.

## Environment Variables

`Frontend/.env`:

- `VITE_API_URL` - backend URL
- `VITE_USE_MOCK` - set to `true` to use mock data

## Troubleshooting

- **Paste errors in zsh:** don't paste commands with trailing `# comments`, zsh passes them as arguments.
- **Connection error on deploy:** Anvil isn't running.
- **Contract addresses changed:** update `application.properties`, restart the backend and truncate the tables.
