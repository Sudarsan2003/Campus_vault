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

If your addresses differ, copy them into `Backend/src/main/resources/application.properties`:

```properties
app.blockchain.contract-address=<address printed by deploy.js>
app.blockchain.lostfound-address=<address printed by deploy-lostfound.js>
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
cd Backend
mvn spring-boot:run
```

### 5. Start the AI module

```bash
cd AI
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python app.py
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

## Environment Variables

`Frontend/.env`:

- `VITE_API_URL` - backend URL
- `VITE_USE_MOCK` - set to `true` to use mock data

## Troubleshooting

- **Paste errors in zsh:** don't paste commands with trailing `# comments`, zsh passes them as arguments.
- **Connection error on deploy:** Anvil isn't running.
- **Contract addresses changed:** update `application.properties`, restart the backend and truncate the tables.
