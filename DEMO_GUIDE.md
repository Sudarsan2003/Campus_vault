# CampusVault – Demo Script

A step-by-step demo of every scenario, with the syllabus topic each one shows.
Total time: about 15 minutes (scenarios 1–9 in the app, 10–12 in the terminal).

---

## 0. Before the demo

### Accounts

| Role | Email | Password | Notes |
|---|---|---|---|
| **Admin** | `admin@campus.edu` | `Admin@123` | Created automatically (seed account) |
| **Student A** (owner) | `student@campus.edu` | `Student@123` | Created automatically (seed account) |
| **Student B** (borrower / finder) | register in the app | e.g. `Borrow@123` | Needed because nobody can borrow their own item |

Register Student B on the sign-in page → **Create an account**. Student ID: 4–20 letters/numbers/hyphens (e.g. `CS25-017`). Password: 8+ characters with upper case, lower case, a number and a symbol.

> These are local demo accounts seeded by `DataSeeder.java` (`app.seed.demo-users=true`). Change or disable them before any real deployment.

### Use a demo copy of the chain (recommended)

The overdue demo moves the blockchain clock forward permanently, so run the demo on a copy:

```bash
cd Blockchain
cp anvil-state.json demo-state.json
anvil --state demo-state.json
```

Then start the other three services in their own terminals (see README): backend (`cd Backend/cv-backend && ./mvnw spring-boot:run`), AI (`cd AI && source .venv/bin/activate && uvicorn main:app --port 8000`), frontend (`cd Frontend && npm run dev`). Open http://localhost:5173.

### Terminal helpers for the blockchain checks

From the project root:

```bash
CV=$(grep '^app.blockchain.contract-address' Backend/cv-backend/src/main/resources/application.properties | cut -d= -f2)
LF=$(grep '^app.blockchain.lostfound-address' Backend/cv-backend/src/main/resources/application.properties | cut -d= -f2)
RPC=http://127.0.0.1:8545
```

Have two photos of the **same object** (e.g. your bag, taken from two angles) and one photo of something unrelated.

---

## Part A – In the application

### Scenario 1 – Sign in and roles
1. Sign in as **Student A**, then sign out and sign in as **Admin**: note *Administrator* under the name.
2. Explain: passwords are stored as BCrypt hashes; every request carries a signed JWT; admin-only actions are refused for students.

**Syllabus:** cryptography – hashing (BCrypt), *symmetric* cryptography (JWT signed with HMAC‑SHA, one shared secret).

### Scenario 2 – Register an asset (written to the blockchain)
1. As **Student A** → **Assets** → name `Casio fx-991EX calculator`, category *Calculator* → **Register on blockchain**.
2. It appears as **Awaiting verification**.
3. **Ledger** tab → new entry *Asset registered* with a transaction hash, block number and **Confirmed on chain**.

**Syllabus:** smart contracts (`registerAsset`), Ethereum transactions and blocks, hashing (keccak‑256 hash of the item details stored on-chain), immutability.

### Scenario 3 – Admin verifies ownership (access control)
1. Sign in as **Admin** → **Assets** → **Verify ownership** on the calculator → it becomes **Available**.
2. Explain: only the back end's **operator account** can call the contract; it signs every transaction with its **private key**, and the contract checks `msg.sender == operator` (the address derived from the public key).

**Syllabus:** public and private keys (asymmetric cryptography, ECDSA signatures), smart-contract access control (`onlyOperator`), Ethereum accounts.

### Scenario 4 – Get test tokens
1. Sign in as **Student B** → **Get test tokens** → balance shows **5,000 test tokens**.

**Syllabus:** tokens / value held in a smart contract (`grantTokens`), Ethereum state.

### Scenario 5 – Borrow with a deposit
1. As **Student B** → **Assets** → **Borrow for 7 days** on the calculator.
2. Balance drops to **4,000** (1,000 locked as deposit); the asset shows **Borrowed**.
3. **My loans** shows the due date and deposit.
4. Optional: try to borrow your own item (as Student A) → refused; the contract also rejects it.

**Syllabus:** smart contracts enforcing rules (`borrow`: verified, not the owner, enough tokens, due date in future), escrow/deposit – a blockchain use case in finance, transactions and gas.

### Scenario 6 – Return: on time or damaged
1. Sign in as **Student A** (owner) → **My loans** → **Verify return**.
2. Student B's balance goes back to **5,000** (full refund). Ledger: *Return verified – Deposit released to borrower*.
3. Borrow again as Student B, then as owner click **Returned damaged** → the whole 1,000 goes to the owner.

**Syllabus:** smart-contract settlement – the contract itself decides the split and emits the `ReturnVerified` **event**, which the back end reads from the transaction receipt.

### Scenario 7 – Overdue loan and late fee (time-based smart contract)
1. As **Student B**, click **Borrow for 7 days** on the calculator.
2. Move the blockchain clock forward 8 days (one day past the due date):
   ```bash
   cast rpc evm_increaseTime 691200 --rpc-url $RPC
   cast rpc evm_mine --rpc-url $RPC
   ```
3. Refresh **My loans** (Student B): red banner, **Overdue · N days**, *Late fee so far*; borrowing anything else is blocked.
4. Sign in as **Admin** → **My loans** → **Overdue across campus** lists the loan. Within a minute the **Ledger** shows *Loan overdue* (recorded on-chain by the overdue monitor).
5. As owner or admin click **Verify return** → the contract keeps 10% per started day late for the owner (capped at the deposit) and refunds the rest.

**Syllabus:** smart contracts using **block timestamp** (due dates and fees use chain time), events (`LoanOverdue`), theoretical aspects – contracts enforce terms automatically.

### Scenario 8 – Lost and found with AI matching
1. As **Student A** → **Lost & found** → **I lost something** → title *Black backpack*, category *Bag*, location, date, photo 1 → **Submit report**.
2. As **Student B** → **I found something** → same category *Bag*, a later date, photo 2 (same bag, other angle) → **Submit report**.
3. Optional: as Admin, report a found item with the unrelated photo.
4. As **Student A** → on the lost report click **Find possible matches**: Student B's bag appears first with a **Strong / Likely** label and a similarity %; the unrelated item is hidden.
5. Click **This is mine** → both reports become **Matched** (on-chain `matchReports`).
6. As **Student B** → **Confirm item handed back** → both become **Returned** (on-chain `confirmReturn`).

**Syllabus:** smart contracts (`LostFound.sol`: `reportItem`, `matchReports`, `confirmReturn`), hashing (SHA‑256 fingerprint of the photo inside the on-chain hash), transactions/ledger. (AI part: CLIP embeddings with cosine similarity – an application layer on top of the blockchain.)

### Scenario 9 – Ledger and on-chain verification
1. Open the **Ledger** tab: every action has a tx hash, block number and **Confirmed on chain** – the back end re-checked the receipt on the blockchain, it is not just a label.
2. Assets and loans show an **On-chain** badge for the same reason.

**Syllabus:** blockchain features – immutability, transparency, verifiability; transactions and blocks.

---

## Part B – In the terminal

### Scenario 10 – Tamper with the database (immutability)
1. Edit a record behind the application's back. The id is the asset code minus 1000 (CV-1001 → 1):
   ```bash
   psql campusvault -c "UPDATE assets SET name = 'Tampered calculator' WHERE id = 1;"
   ```
2. Refresh **Assets**: the item shows **Doesn't match chain** – *The details were changed after they were recorded* – and it can no longer be verified or borrowed.
3. Explain: the app recomputes the keccak‑256 hash from the database and compares it with the hash stored on-chain; the blockchain copy cannot be edited.
4. Put it back: `psql campusvault -c "UPDATE assets SET name = 'Casio fx-991EX calculator' WHERE id = 1;"` → **On-chain** again.

**Syllabus:** immutability, hashing as tamper evidence, decentralised verification (anyone can read the chain and check).

### Scenario 11 – Look inside the blockchain with Foundry `cast`
```bash
cast block latest --rpc-url $RPC                     # a block: number, hash, parent hash, timestamp
cast call $CV "operator()(address)" --rpc-url $RPC   # the only account allowed to write
cast call $CV "assets(uint256)(uint256,bytes32,uint8)" 1 --rpc-url $RPC        # owner id, data hash, status
cast call $CV "loans(uint256)(uint256,uint256,uint256,uint256,bool,bool)" 1 --rpc-url $RPC
cast call $CV "balances(uint256)(uint256)" 1 --rpc-url $RPC                    # tokens of user #1
cast receipt <tx hash from the Ledger tab> --rpc-url $RPC                      # from, to, gasUsed, logs
```
Explain: blocks are linked by the parent hash; reads (`cast call`) are free, writes are transactions that cost gas; the receipt's `from` is the operator address and its logs are the contract events.

**Syllabus:** blockchain structure (blocks, hashes, chaining), Ethereum architecture (accounts, contract storage, gas, receipts, logs), public addresses.

### Scenario 12 – Security attack: improper access control
**In the app (recommended for the presentation):** sign in as **Admin** → **Security lab** → **Run attack**. Two cards appear side by side: **CampusVaultVulnerable – Attack succeeded** (loan open Yes → No, asset Borrowed → Verified (available), borrower tokens 4,000 → 5,000, changes highlighted in red) and **CampusVault – Attack blocked** (*Only the operator*, nothing changes). Fresh throwaway contracts are deployed each run, so real data is never touched.

**In the terminal (same attack):**
```bash
cd Blockchain
npx hardhat run scripts/attack-demo.js
```

1. **CampusVaultVulnerable** (copy with `onlyOperator` removed from `returnAsset`): an outside account closes a loan whose item was never returned → *ATTACK SUCCEEDED* – loan closed, deposit refunded, item shown as available again.
2. **CampusVault** (real contract): the same call → *ATTACK BLOCKED: Only the operator*.
3. Run all contract tests (overdue rules + 5 attack tests): `npm test` → **12 passing**.
4. Explain detection: if a loan is ever closed on-chain outside the app, the chain verifier flags it *Doesn't match chain* and blocks settlement (shown live in the report, Fig. 6.2).

**Syllabus:** smart-contract security (theoretical aspects), public/private keys – the attack works because the contract stopped checking *which key* signed the transaction.

---

## Syllabus coverage

| Syllabus topic | Covered by | Depth |
|---|---|---|
| Blockchain definition, features (immutability, transparency) | Scenarios 2, 9, 10, 11 | Implemented |
| Types of blockchain | Local private network (Anvil); report discusses permissioned / public / layer-2 | Discussed |
| Smart contracts – definition and theoretical aspects | Scenarios 2–8, 12 (`CampusVault.sol`, `LostFound.sol`) | **Core of the project** |
| Ethereum 101 – design, architecture, technical concepts | Scenarios 5–7, 11 (EVM, accounts, transactions, gas, events, receipts, block time) | Implemented |
| Public and private keys (asymmetric cryptography) | Scenarios 3, 11, 12 (operator key signs every transaction; `msg.sender` check) | Implemented |
| Symmetric cryptography | Scenario 1 (JWT signed with HMAC) | Indirect |
| Hashing / theoretical foundations of cryptography | Scenarios 2, 8, 10 (keccak‑256, SHA‑256, BCrypt) | Implemented |
| Blockchain in finance (use cases) | Scenarios 4–7 (token deposits, escrow, late fees) | Partly |
| Decentralization – methods, platforms | Data is publicly verifiable on-chain, but writes go through one operator | Partly |
| Consensus | Anvil mines instantly on one node – no PoW/PoS exercised | Concept only |
| Bitcoin, Bitcoin transactions and payments | Not used (Ethereum chosen because it supports full smart contracts) | Not implemented |
| CAP theorem | Not addressed in code | Concept only |
| Ricardian contracts | Not implemented; lending terms are human-readable in the app *and* code-enforced | Concept only |
| Oracles | Not used; physical events (item returned/damaged) enter via the admin/owner – a trusted, centralised oracle | Concept only |
| Hyperledger, alternative blockchains | Not used; mentioned as a permissioned option | Not implemented |

### Likely viva questions

- **Why Ethereum and not Bitcoin?** Bitcoin's script is limited; the lending rules (deposit, due date, late fee, damage) need a full smart-contract platform.
- **Where are the keys?** One operator private key in the back end signs all transactions; the contract stores the operator address and rejects every other sender.
- **What is stored on-chain?** Only hashes, ids, amounts and dates; descriptions and photos stay off-chain (cost), and their hashes prove they were not changed.
- **What consensus does it use?** Anvil is a single-node development chain; on a public network the same transactions would be ordered by Ethereum's proof-of-stake.
- **How does the contract know an item was returned?** It can't by itself – the owner or admin confirms it through the app, which acts as a trusted oracle; that is a known limitation.
- **What did the attack show?** Removing one `onlyOperator` modifier let any wallet close loans; the real contract blocks it, a regression test guards it, and the verifier detects it.
