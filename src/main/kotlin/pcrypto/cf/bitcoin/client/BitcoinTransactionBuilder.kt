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
import org.bitcoinj.script.Script
import org.slf4j.LoggerFactory
import pcrypto.cf.exception.BlockchainServiceException
import pcrypto.cf.exception.InsufficentBalanceException
import java.io.IOException
import java.math.BigDecimal
import java.util.*


class BitcoinTransactionBuilder(
    val bitcoindClient: BitcoindClient?,
    val params: NetworkParameters?,
    val sourceAddress: String?,
    val destinationAddress: String?,
    val amount: BigDecimal?,
    val memo: String?
) {

    fun build(): Transaction {
        val utxos = calculateSpendCandidates(
            sourceAddress ?: "",
            amount ?: BigDecimal(0.0)
        )
        if (null == utxos || utxos.isEmpty()) {
            throw InsufficentBalanceException("Source account does not have a sufficient available balance for this payment.")
        }

        // Construct the unsigned transaction
        val transaction = Transaction(params!!)

        // Source of funds (the utxos)
        var totalSpend = Coin.ZERO
        for (utxo in utxos) {
            val value = utxo.value
            totalSpend = totalSpend.add(value)
            transaction.addInput(utxo)
        }

        // Where the primary funds should be sent
        val fundsToTransfer = Coin.valueOf(amount!!.toLong())
        transaction.addOutput(fundsToTransfer, Address.fromString(params, destinationAddress!!))

        // Set a memo (TODO: this doesn't work) and a purpose
        transaction.memo = memo
        transaction.purpose = Transaction.Purpose.USER_PAYMENT

        // Calculate the fees
        val txSize = transaction.unsafeBitcoinSerialize().size
        val feePerByte: BigDecimal
        try {
            feePerByte = bitcoindClient!!.feePerByte
        } catch (e: IOException) {
            throw BlockchainServiceException("Unable to estimate current bitcoin miner fee.", e)
        }

        // Check for excessive miner fees
        if (BigDecimal(200).compareTo(feePerByte) < 0) {
            throw BlockchainServiceException("Current bitcoin miner fee (" + feePerByte.toLong() + " satoshis/byte) is too expensive (200 satoshis/byte). Please try again later.")
        }

        val estimatedFee = feePerByte.multiply(BigDecimal(txSize)).abs()
        val fee = Coin.valueOf(estimatedFee.toLong())
        log.info("Estimated fee: $feePerByte/byte  =>  $estimatedFee for tx of $txSize bytes.")

        // Send the change back to the payer
        val change = totalSpend.minus(fundsToTransfer).minus(fee)
        transaction.addOutput(change, Address.fromString(params, sourceAddress!!))

        return transaction
    }


    /**
     * Obtain a set of UTXOs to use for the desired spend.
     *
     * @param address
     * @param satoshis
     * @return
     */
    private fun calculateSpendCandidates(
        address: String,
        satoshis: BigDecimal
    ): Collection<TransactionOutput>? {

        // Get all the unspent transaction outputs for this address
        val utxos = bitcoindClient!!.getUtxos(address)

        // Select optimal set of outputs to pay the desired amount
        val candidates = ArrayList<UTXO>()
        for (utxo in utxos!!) {
            val bitcoinjUtxo = UTXO(
                Sha256Hash.wrap(utxo.txid),
                utxo.vout!!.toLong(),
                Coin.valueOf(utxo.satoshis?.toLong() ?: 0),
                utxo.height!!.toInt(),
                false,
                Script(Utils.HEX.decode(utxo.scriptPubKey!!))
            )
            candidates.add(bitcoinjUtxo)
        }

        val blockChainHeight: Long?
        try {
            blockChainHeight = bitcoindClient.blockChainHeight
        } catch (e: IOException) {
            throw BlockchainServiceException("Unable to obtain current blockchain height", e)
        }

        val coinSelection = UtxoSelector().selectUtxos(satoshis, candidates, blockChainHeight)
        return coinSelection.gathered
    }

    companion object {

        private val log = LoggerFactory.getLogger(BitcoinTransactionBuilder::class.java)

    }
}
