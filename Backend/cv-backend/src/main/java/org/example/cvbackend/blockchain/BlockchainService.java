package org.example.cvbackend.blockchain;

import org.example.cvbackend.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
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
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

/** Talks to the CampusVault contract. The backend wallet is the contract's operator. */
@Service
public class BlockchainService {

    public record TxResult(String hash, long blockNumber) {}

    private final Web3j web3j;
    private final RawTransactionManager txManager;
    private final String contract;

    public BlockchainService(@Value("${app.blockchain.rpc-url}") String rpcUrl,
                             @Value("${app.blockchain.contract-address}") String contract,
                             @Value("${app.blockchain.private-key}") String privateKey,
                             @Value("${app.blockchain.chain-id}") long chainId) {
        this.web3j = Web3j.build(new HttpService(rpcUrl));
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

    public TxResult returnAsset(long loanId, boolean damaged) {
        return send(contract, new Function("returnAsset", List.of(u(loanId), new Bool(damaged)), List.of()));
    }

    /** Read-only call, no gas needed. */
    public BigInteger balanceOf(long userId) {
        Function fn = new Function("balances", List.of(u(userId)), List.of(new TypeReference<Uint256>() {}));
        try {
            EthCall res = web3j.ethCall(
                    Transaction.createEthCallTransaction(txManager.getFromAddress(), contract, FunctionEncoder.encode(fn)),
                    DefaultBlockParameterName.LATEST).send();
            if (res.hasError() || res.getValue() == null) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Could not read from the smart contract.");
            }
            List<Type> out = FunctionReturnDecoder.decode(res.getValue(), fn.getOutputParameters());
            if (out.isEmpty()) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                        "No smart contract found at the configured address. Deploy it again and update app.blockchain.contract-address.");
            }
            return (BigInteger) out.get(0).getValue();
        } catch (IOException e) {
            throw offline();
        }
    }

    private static Uint256 u(long value) {
        return new Uint256(BigInteger.valueOf(value));
    }

    // synchronized so two requests never reuse the same transaction nonce
    public synchronized TxResult send(String to, Function fn) {
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
                    return new TxResult(hash, receipt.get().getBlockNumber().longValue());
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