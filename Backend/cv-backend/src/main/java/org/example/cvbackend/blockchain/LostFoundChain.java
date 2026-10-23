package org.example.cvbackend.blockchain;

import org.example.cvbackend.blockchain.BlockchainService.TxResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.List;

/** Calls the LostFound contract through the shared operator wallet. */
@Service
public class LostFoundChain {

    private final BlockchainService chain;
    private final String address;

    public LostFoundChain(BlockchainService chain, @Value("${app.blockchain.lostfound-address}") String address) {
        this.chain = chain;
        this.address = address;
    }

    public String hash(String text) {
        return chain.hash(text);
    }

    public TxResult reportItem(long reportId, long reporterId, boolean found, String dataHash) {
        return chain.send(address, new Function("reportItem",
                List.of(u(reportId), u(reporterId), new Bool(found), new Bytes32(Numeric.hexStringToByteArray(dataHash))),
                List.of()));
    }

    public TxResult matchReports(long lostId, long foundId) {
        return chain.send(address, new Function("matchReports", List.of(u(lostId), u(foundId)), List.of()));
    }

    public TxResult confirmReturn(long lostId, long foundId) {
        return chain.send(address, new Function("confirmReturn", List.of(u(lostId), u(foundId)), List.of()));
    }

    private static Uint256 u(long v) {
        return new Uint256(BigInteger.valueOf(v));
    }
}