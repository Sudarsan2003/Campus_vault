package org.example.cvbackend.security;

import org.example.cvbackend.blockchain.ChainVerifier;
import org.example.cvbackend.exception.ApiException;
import org.example.cvbackend.service.LoanService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.gas.DefaultGasProvider;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Admin "Security lab": runs the injected access-control attack against fresh, throwaway copies of
 * CampusVaultVulnerable (returnAsset without onlyOperator) and the real CampusVault, and reports the state
 * before and after. Uses two of Anvil's public development accounts, never the app's operator key or its
 * real contracts, and only runs on the local development chain (chain ID 31337).
 */
@Service
public class SecurityDemoService {

    private static final long OWNER = 1, BORROWER = 2, ASSET = 1, LOAN = 1, DEPOSIT = 1000, GRANT = 5000;
    private static final String[] STATUS = {"None", "Registered", "Verified (available)", "Borrowed"};

    public record State(boolean loanOpen, String assetStatus, long borrowerTokens, long ownerTokens) {}

    /**
     * What CampusVault would show afterwards. The attack bypasses the app, so its database still holds the loan as
     * open; the real chain verifier compares that record with the contract, and a mismatch blocks settling it.
     */
    public record Detection(String databaseSays, String chainSays, ChainVerifier.Status status, String detail,
                            boolean settleRefused, String settleMessage) {}

    public record Run(String contract, String description, String contractAddress, String operator, String attacker,
                      State before, State after, boolean attackSucceeded, String outcome, String txHash,
                      Long blockNumber, Long gasUsed, Detection detection) {}

    public record Result(String attack, List<Run> runs) {}

    private final Web3j web3j;
    private final long chainId;
    private final boolean enabled;
    private final Credentials demoOperator;
    private final Credentials attacker;
    private final ChainVerifier verifier;

    public SecurityDemoService(ChainVerifier verifier,
                               @Value("${app.blockchain.rpc-url}") String rpcUrl,
                               @Value("${app.blockchain.chain-id}") long chainId,
                               @Value("${app.security-demo.enabled:true}") boolean enabled,
                               @Value("${app.security-demo.operator-key}") String operatorKey,
                               @Value("${app.security-demo.attacker-key}") String attackerKey) {
        this.verifier = verifier;
        this.web3j = Web3j.build(new HttpService(rpcUrl));
        this.chainId = chainId;
        this.enabled = enabled;
        this.demoOperator = Credentials.create(operatorKey);
        this.attacker = Credentials.create(attackerKey);
    }

    /** One run at a time: both runs share the two demo accounts and their nonces. */
    public synchronized Result run() {
        if (!enabled || chainId != 31337) {
            throw new ApiException(HttpStatus.FORBIDDEN, "The security lab only runs on the local development chain.");
        }
        return new Result("Improper access control: an account that is not the operator calls returnAsset(loanId, false) "
                + "directly on the contract, closing a loan whose item was never returned.",
                List.of(
                        scenario("CampusVaultVulnerable", "Copy with the injected flaw: returnAsset() has no onlyOperator guard."),
                        scenario("CampusVault", "The real contract: every state-changing function is onlyOperator.")));
    }

    private Run scenario(String contractName, String description) {
        RawTransactionManager op = new RawTransactionManager(web3j, demoOperator, chainId);
        String address = deploy(op, contractName);

        // Normal use, done by the operator: register, verify, fund the borrower, lend for 3 days.
        send(op, address, fn("registerAsset", u(ASSET), u(OWNER), new Bytes32(new byte[32])));
        send(op, address, fn("verifyAsset", u(ASSET)));
        send(op, address, fn("grantTokens", u(BORROWER), u(GRANT)));
        long dueAt = chainTime() + 3 * 86_400;
        send(op, address, fn("borrow", u(LOAN), u(ASSET), u(BORROWER), u(DEPOSIT), u(dueAt)));
        State before = state(address);

        // The attack: a different account calls returnAsset directly, bypassing the app.
        Function attack = fn("returnAsset", u(LOAN), new Bool(false));
        String data = FunctionEncoder.encode(attack);
        String revert = simulate(attacker.getAddress(), address, data);
        if (revert != null) {
            State after = state(address);
            return new Run(contractName, description, address, demoOperator.getAddress(), attacker.getAddress(),
                    before, after, false, "Attack blocked: " + revert, null, null, null, detect(address, dueAt, after));
        }
        TransactionReceipt r = send(new RawTransactionManager(web3j, attacker, chainId), address, attack);
        State after = state(address);
        return new Run(contractName, description, address, demoOperator.getAddress(), attacker.getAddress(),
                before, after, true, "Attack succeeded", r.getTransactionHash(),
                r.getBlockNumber().longValue(), r.getGasUsed().longValue(), detect(address, dueAt, after));
    }

    /**
     * Runs the same loan check My loans uses (ChainVerifier) on this contract, against the record CampusVault's
     * database would hold: the loan as the operator created it, still open, because the attack never went through
     * the app. Then applies the same rule as verifying a return: a mismatch refuses settlement.
     */
    private Detection detect(String address, long dueAt, State after) {
        ChainVerifier.Check check = verifier.loanAt(address, LOAN, ASSET, BORROWER, DEPOSIT, dueAt, true);
        boolean refused = check.status() == ChainVerifier.Status.MISMATCH;
        return new Detection("On loan (open)", after.loanOpen() ? "Open" : "Closed",
                check.status(), check.detail(), refused,
                refused ? LoanService.settleRefusal(check) : "Verify return allowed: the loan matches the blockchain.");
    }

    private State state(String address) {
        List<Type> loan = call(address, new Function("loans", List.of(u(LOAN)), List.of(
                new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {},
                new TypeReference<Uint256>() {}, new TypeReference<Bool>() {}, new TypeReference<Bool>() {})));
        List<Type> asset = call(address, new Function("assets", List.of(u(ASSET)), List.of(
                new TypeReference<Uint256>() {}, new TypeReference<Bytes32>() {}, new TypeReference<Uint8>() {})));
        int status = ((BigInteger) asset.get(2).getValue()).intValue();
        return new State((Boolean) loan.get(4).getValue(), STATUS[status], balance(address, BORROWER), balance(address, OWNER));
    }

    private long balance(String address, long user) {
        return ((BigInteger) call(address, new Function("balances", List.of(u(user)),
                List.of(new TypeReference<Uint256>() {}))).get(0).getValue()).longValue();
    }

    // ---------------- web3j plumbing ----------------

    private String deploy(RawTransactionManager tm, String contractName) {
        String bytecode;
        try {
            bytecode = new ClassPathResource("security-demo/" + contractName + ".bin")
                    .getContentAsString(StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Contract bytecode missing; run 'npm run export-bytecode' in Blockchain.");
        }
        try {
            EthSendTransaction sent = tm.sendTransaction(DefaultGasProvider.GAS_PRICE, DefaultGasProvider.GAS_LIMIT,
                    null, bytecode, BigInteger.ZERO, true);
            TransactionReceipt r = await(sent);
            return r.getContractAddress();
        } catch (IOException e) {
            throw offline();
        }
    }

    private TransactionReceipt send(RawTransactionManager tm, String to, Function fn) {
        try {
            TransactionReceipt r = await(tm.sendTransaction(DefaultGasProvider.GAS_PRICE, DefaultGasProvider.GAS_LIMIT,
                    to, FunctionEncoder.encode(fn), BigInteger.ZERO));
            if (!r.isStatusOK()) throw new ApiException(HttpStatus.BAD_GATEWAY, "Demo transaction " + fn.getName() + " failed.");
            return r;
        } catch (IOException e) {
            throw offline();
        }
    }

    private TransactionReceipt await(EthSendTransaction sent) throws IOException {
        if (sent.hasError()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Blockchain rejected a demo transaction: " + sent.getError().getMessage());
        }
        for (int i = 0; i < 40; i++) {
            Optional<TransactionReceipt> r = web3j.ethGetTransactionReceipt(sent.getTransactionHash()).send().getTransactionReceipt();
            if (r.isPresent()) return r.get();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "A demo transaction was not confirmed in time.");
    }

    /** Dry-runs the call as `from`; returns the revert reason, or null if it would succeed. */
    private String simulate(String from, String to, String data) {
        try {
            EthCall res = web3j.ethCall(Transaction.createEthCallTransaction(from, to, data),
                    DefaultBlockParameterName.LATEST).send();
            if (res.isReverted()) return res.getRevertReason();
            if (res.hasError()) return res.getError().getMessage();
            return null;
        } catch (IOException e) {
            throw offline();
        }
    }

    private List<Type> call(String to, Function fn) {
        try {
            EthCall res = web3j.ethCall(Transaction.createEthCallTransaction(demoOperator.getAddress(), to,
                    FunctionEncoder.encode(fn)), DefaultBlockParameterName.LATEST).send();
            if (res.hasError()) throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not read the demo contract.");
            return FunctionReturnDecoder.decode(res.getValue(), fn.getOutputParameters());
        } catch (IOException e) {
            throw offline();
        }
    }

    private long chainTime() {
        try {
            return web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send()
                    .getBlock().getTimestamp().longValue();
        } catch (IOException e) {
            throw offline();
        }
    }

    private static Function fn(String name, Type<?>... args) {
        return new Function(name, List.of(args), List.of());
    }

    private static Uint256 u(long v) {
        return new Uint256(BigInteger.valueOf(v));
    }

    private static ApiException offline() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Can't reach the blockchain node. Is Anvil running?");
    }
}
