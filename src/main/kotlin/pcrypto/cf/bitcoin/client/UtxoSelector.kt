/*
 * Copyright (c) 2019 ChainFront LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package pcrypto.cf.bitcoin.client

import org.bitcoinj.core.*
import org.bitcoinj.wallet.CoinSelection
import org.bitcoinj.wallet.DefaultCoinSelector
import java.math.BigDecimal
import java.util.*


class UtxoSelector {

    fun selectUtxos(
        amount: BigDecimal,
        candidates: List<UTXO>,
        blockChainHeight: Long?
    ): CoinSelection {

        val coinSelector = DefaultCoinSelector()
        val target = Coin.valueOf(amount.toLong())

        val candidateTransactions = ArrayList<TransactionOutput>()
        for (candidate in candidates) {
            val transactionOutput = FreeStandingTransactionOutput(networkParams, candidate, blockChainHeight!!.toInt())
            candidateTransactions.add(transactionOutput)
        }

        return coinSelector.select(target, candidateTransactions)
    }

    /**
     * Adapter class to make a UTXO appear as a bitcoinj "TransactionOutput" object
     */
    private class FreeStandingTransactionOutput
    /**
     * Construct a free standing Transaction Output.
     *
     * @param params The network parameters.
     * @param output The stored output (free standing).
     */
        (
        params: NetworkParameters?,
        /**
         * Get the [UTXO].
         *
         * @return The stored output.
         */
        val utxo: UTXO,
        private val chainHeight: Int
    ) : TransactionOutput(params, null, utxo.value, utxo.script.program) {

        /**
         * Get the depth withing the chain of the parent tx, depth is 1 if it the output height is the height of
         * the latest block.
         *
         * @return The depth.
         */
        override fun getParentTransactionDepthInBlocks(): Int {
            return chainHeight - utxo.height + 1
        }

        override fun getIndex(): Int {
            return utxo.index.toInt()
        }

        override fun getParentTransactionHash(): Sha256Hash? {
            return utxo.hash
        }
    }

    companion object {
        private val networkParams = NetworkParameters.fromID(NetworkParameters.ID_TESTNET)
    }
}
