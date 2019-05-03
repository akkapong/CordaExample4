package com.example.contract

import com.example.state.IOUState
import com.example.state.TestState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

class TestContract: Contract {
    companion object {
        @JvmStatic
        val ID = "com.example.contract.TestContract"
    }

    /**
     * The verify() function of all the states' contracts must not throw an exception for a transaction to be
     * considered valid.
     */
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()
        val setOfSigners = command.signers.toSet()

        when (command.value) {
            is Commands.Create -> verifyCreate(tx, setOfSigners)
            else -> throw IllegalArgumentException("Unrecognised command.")
        }
    }

    private fun verifyCreate(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        // Generic constraints around the IOU transaction.
        "No inputs should be consumed when issuing an IOU." using (tx.inputs.isEmpty())
        "Only one output state should be created." using (tx.outputs.size == 1)

        val iouStates = tx.referenceInputRefsOfType<IOUState>()

        "Must have one IOU." using (iouStates.size == 1)
        val out = tx.outputsOfType<TestState>().single()
        "The lender and the borrower cannot be the same entity." using (out.lender != out.borrower)
        "All of the participants must be signers." using (signers.containsAll(out.participants.map { it.owningKey }))
    }



    /**
     * This contract only implements one command, Create.
     */
    interface Commands : CommandData {
        class Create : Commands
    }
}