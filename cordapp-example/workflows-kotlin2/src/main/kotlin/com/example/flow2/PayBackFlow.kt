package com.example.flow2

import co.paralleluniverse.fibers.Suspendable
import com.example.contract2.PayBackContract
import com.example.state2.PayBackState
import com.example.contract2.IOUContractV2 as IOUContract
import com.example.state2.IOUState2 as IOUState
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.DEFAULT_PAGE_NUM
import net.corda.core.node.services.vault.MAX_PAGE_SIZE
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.util.*

object PayBackFlow {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val iouLinearId: String) : FlowLogic<SignedTransaction>() {
        /**
         * The progress tracker checkpoints each stage of the flow2 and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on new IOU.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract2 constraints.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        /**
         * The flow2 logic is encapsulated within the call() method.
         */
        @Suspendable
        override fun call(): SignedTransaction {
            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION
            // Generate an unsigned transaction.
            val iouLinearId = UniqueIdentifier(id = UUID.fromString(iouLinearId))
            val iouStateIn = queryIOU(iouLinearId)
            //update value
            val iouStateOut = iouStateIn.state.data.copy(paid = true)
            //new payback state2
            val payBackStateOut = PayBackState(
                    iouLinearId = iouLinearId,
                    borrower = iouStateOut.borrower,
                    lender = iouStateOut.lender
            )
            val txCommandIou = Command(IOUContract.Commands.Paid(), iouStateIn.state.data.participants.map { it.owningKey })
            val txCommandPayBack = Command(PayBackContract.Commands.Create(), payBackStateOut.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addInputState(iouStateIn)
                    .addOutputState(iouStateOut, IOUContract.ID)
                    .addOutputState(payBackStateOut, PayBackContract.ID)
                    .addCommand(txCommandIou)
                    .addCommand(txCommandPayBack)

            // Stage 2.
            progressTracker.currentStep = VERIFYING_TRANSACTION
            // Verify that the transaction is valid.
            txBuilder.verify(serviceHub)

            // Stage 3.
            progressTracker.currentStep = SIGNING_TRANSACTION
            // Sign the transaction.
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            // Stage 4.
            progressTracker.currentStep = GATHERING_SIGS
            // Send the state2 to the counterparty, and receive it back with their signature.
            val otherPartySession = initiateFlow(iouStateOut.borrower)
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(otherPartySession), GATHERING_SIGS.childProgressTracker()))

            // Stage 5.
            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in both parties' vaults.
            return subFlow(FinalityFlow(fullySignedTx, setOf(otherPartySession), FINALISING_TRANSACTION.childProgressTracker()))
        }

        private fun queryIOU(linearId: UniqueIdentifier): StateAndRef<IOUState> {
            val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
            val linearCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))

            val queryCriteria = linearCriteria
                    .and(generalCriteria)


            val results = serviceHub.vaultService.queryBy<IOUState>(queryCriteria,
                    paging = PageSpecification(DEFAULT_PAGE_NUM, MAX_PAGE_SIZE)).states

            return results.first()
        }
    }

    @InitiatedBy(Initiator::class)
    class Acceptor(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val iouOutput = stx.tx.outputsOfType<IOUState>()
                    val payBackOutput = stx.tx.outputsOfType<PayBackState>()
                    "This must be an IOU transaction." using (iouOutput.size == 1)
                    "This must be an PayBack transaction." using (payBackOutput.size == 1)
                }
            }
            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }
    }
}