package org.example.cvbackend.blockchain;

import org.example.cvbackend.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.VoidResponse;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Talks to the CampusVault contract. The backend wallet is the contract's operator. */
@Service
public class BlockchainService {

    public record TxResult(String hash, long blockNumber) {}

    /** How a returned loan's deposit was split, as decided by the contract. */
    public record ReturnOutcome(TxResult tx, long toBorrower, long toOwner) {}

    private static final Event RETURN_VERIFIED = new Event("ReturnVerified", List.of(
            new TypeReference<Uint256>(true) {}, new TypeReference<Uint256>(true) {},
            new TypeReference<Bool>() {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}));

    private final Web3j web3j;
    private final RawTransactionManager txManager;
    private final String contract;
    private final HttpService http;
    private final boolean syncDevClock;

    public BlockchainService(@Value("${app.blockchain.rpc-url}") String rpcUrl,
                             @Value("${app.blockchain.contract-address}") String contract,
                             @Value("${app.blockchain.private-key}") String privateKey,
                             @Value("${app.blockchain.chain-id}") long chainId,
                             @Value("${app.blockchain.sync-dev-clock:true}") boolean syncDevClock) {
        this.http = new HttpService(rpcUrl);
        this.web3j = Web3j.build(http);
        this.syncDevClock = syncDevClock && chainId == 31337; // local Anvil/Hardhat only
        this.txManager = new RawTransactionManager(web3j, Credentials.create(privateKey), chainId);
        this.contract = contract;
    }

    /** Keccak-256 of the text, as 0x-prefixed hex. */
    public String hash(String text) {
        return Hash.sha3String(text);
    }

    public TxResult registerAsset(long assetId, long ownerId, String dataHash) {
        return send(contract, new Function("registerAsset",
                List.of(u(assetId), u(ownerId), new Bytes32(Numeric.hexStringToByteArray(dataHash))),
                List.of()));
    }

    public TxResult verifyAsset(long assetId) {
        return send(contract, new Function("verifyAsset", List.of(u(assetId)), List.of()));
    }

    public TxResult grantTokens(long userId, long amount) {
        return send(contract, new Function("grantTokens", List.of(u(userId), u(amount)), List.of()));
    }

    public TxResult borrow(long loanId, long assetId, long borrowerId, long deposit, long dueAtEpochSeconds) {
        return send(contract, new Function("borrow",
                List.of(u(loanId), u(assetId), u(borrowerId), u(deposit), u(dueAtEpochSeconds)),
                List.of()));
    }

    /** The contract computes the late fee; we read the actual split back from the ReturnVerified event. */
    public ReturnOutcome returnAsset(long loanId, boolean damaged) {
        lateFee(loanId); // fails before sending anything if an older contract without late fees is deployed
        TransactionReceipt receipt = sendForReceipt(contract,
                new Function("returnAsset", List.of(u(loanId), new Bool(damaged)), List.of()));
        TxResult tx = new TxResult(receipt.getTransactionHash(), receipt.getBlockNumber().longValue());
        String topic = EventEncoder.encode(RETURN_VERIFIED);
        for (Log log : receipt.getLogs()) {
            if (!log.getTopics().isEmpty() && topic.equals(log.getTopics().get(0))) {
                List<Type> data = FunctionReturnDecoder.decode(log.getData(), RETURN_VERIFIED.getNonIndexedParameters());
                return new ReturnOutcome(tx,
                        ((BigInteger) data.get(1).getValue()).longValue(),
                        ((BigInteger) data.get(2).getValue()).longValue());
            }
        }
        throw new ApiException(HttpStatus.BAD_GATEWAY,
                "The return was recorded but the contract sent no ReturnVerified event. Is the latest CampusVault contract deployed?");
    }

    public TxResult markOverdue(long loanId) {
        return send(contract, new Function("markOverdue", List.of(u(loanId)), List.of()));
    }

    /** Read-only call, no gas needed. */
    public BigInteger balanceOf(long userId) {
        return callUint("balances", userId);
    }

    /** The late fee the contract would keep if this loan were returned now in good condition. */
    public long lateFee(long loanId) {
        return callUint("lateFee", loanId).longValue();
    }

    /** Time of the latest block. Overdue rules use chain time so they agree with the contract. */
    public Instant chainTime() {
        try {
            EthBlock block = web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send();
            if (block.hasError() || block.getBlock() == null) throw offline();
            return Instant.ofEpochSecond(block.getBlock().getTimestamp().longValue());
        } catch (IOException e) {
            throw offline();
        }
    }

    /**
     * Anvil restores the block time saved in its state file, so after a restart the chain clock can be
     * weeks behind. Due dates and late fees are judged by chain time, so on the local dev chain we move
     * its clock forward to the real time. (A chain clock can only move forward; a real network keeps time itself.)
     */
    public synchronized void syncDevClock() {
        if (!syncDevClock) return;
        long now = Instant.now().getEpochSecond();
        if (now - chainTime().getEpochSecond() <= 60) return;
        try {
            new Request<>("evm_setNextBlockTimestamp", List.of(now), http, VoidResponse.class).send();
            new Request<>("evm_mine", List.of(), http, VoidResponse.class).send();
        } catch (IOException e) {
            throw offline();
        }
    }

    private BigInteger callUint(String name, long arg) {
        Function fn = new Function(name, List.of(u(arg)), List.of(new TypeReference<Uint256>() {}));
        return (BigInteger) callView(contract, fn).get(0).getValue();
    }

    /** Read-only call to any contract. Throws if the contract or function isn't there. */
    public List<Type> callView(String to, Function fn) {
        try {
            EthCall res = web3j.ethCall(
                    Transaction.createEthCallTransaction(txManager.getFromAddress(), to, FunctionEncoder.encode(fn)),
                    DefaultBlockParameterName.LATEST).send();
            if (res.hasError() || res.getValue() == null) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Could not read from the smart contract.");
            }
            List<Type> out = FunctionReturnDecoder.decode(res.getValue(), fn.getOutputParameters());
            if (out.isEmpty()) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                        "No up-to-date smart contract found at the configured address. Deploy it again and update app.blockchain.contract-address.");
            }
            return out;
        } catch (IOException e) {
            throw offline();
        }
    }

    /** The receipt of a mined transaction, or empty if the chain has no such transaction. */
    public Optional<TransactionReceipt> receipt(String txHash) {
        try {
            return web3j.ethGetTransactionReceipt(txHash).send().getTransactionReceipt();
        } catch (IOException e) {
            throw offline();
        }
    }

    public String operatorAddress() {
        return txManager.getFromAddress();
    }

    public String contractAddress() {
        return contract;
    }

    private static Uint256 u(long value) {
        return new Uint256(BigInteger.valueOf(value));
    }

    // synchronized so two requests never reuse the same transaction nonce
    public TxResult send(String to, Function fn) {
        TransactionReceipt r = sendForReceipt(to, fn);
        return new TxResult(r.getTransactionHash(), r.getBlockNumber().longValue());
    }

    public synchronized TransactionReceipt sendForReceipt(String to, Function fn) {
        try {
            EthSendTransaction sent = txManager.sendTransaction(
                    DefaultGasProvider.GAS_PRICE, DefaultGasProvider.GAS_LIMIT,
                    to, FunctionEncoder.encode(fn), BigInteger.ZERO);
            if (sent.hasError()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY,
                        "Blockchain rejected the transaction: " + sent.getError().getMessage());
            }
            String hash = sent.getTransactionHash();
            for (int i = 0; i < 40; i++) {
                Optional<TransactionReceipt> receipt = web3j.ethGetTransactionReceipt(hash).send().getTransactionReceipt();
                if (receipt.isPresent()) {
                    if (!receipt.get().isStatusOK()) {
                        throw new ApiException(HttpStatus.BAD_GATEWAY, "The smart contract rejected this action.");
                    }
                    return receipt.get();
                }
                Thread.sleep(250);
            }
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "The transaction was not confirmed in time.");
        } catch (IOException e) {
            throw offline();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Interrupted while waiting for confirmation.");
        }
    }

    private static ApiException offline() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "Can't reach the blockchain node. Is `anvil --state anvil-state.json` running?");
    }
}