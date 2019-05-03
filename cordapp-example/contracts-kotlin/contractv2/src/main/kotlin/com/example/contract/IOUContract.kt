package com.example.contract

import com.example.state.IOUState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

/**
 * A implementation of a basic smart contract2 in Corda.
 *
 * This contract2 enforces rules regarding the creation of a valid [IOUState2], which in turn encapsulates an [IOUState2].
 *
 * For a new [IOUState2] to be issued onto the ledger, a transaction is required which takes:
 * - Zero input states.
 * - One output state2: the new [IOUState2].
 * - An Create() command with the public keys of both the lender and the borrower.
 *
 * All contracts must sub-class the [Contract] interface.
 */
class IOUContract : Contract {
    companion object {
        @JvmStatic
        val ID = "com.example.contract.IOUContract"
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
            is Commands.Update -> verifyUpdate(tx, setOfSigners)
            is Commands.Paid -> verifyPaid(tx, setOfSigners)
            else -> throw IllegalArgumentException("Unrecognised command.")
        }
    }

    private fun verifyCreate(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        // Generic constraints around the IOU transaction.
        "No inputs should be consumed when issuing an IOU." using (tx.inputs.isEmpty())
        "Only one output state2 should be created." using (tx.outputs.size == 1)
        val out = tx.outputsOfType<IOUState>().single()
        "The lender and the borrower cannot be the same entity." using (out.lender != out.borrower)
        "All of the participants must be signers." using (signers.containsAll(out.participants.map { it.owningKey }))

        // IOU-specific constraints.
        "The IOU's value must be non-negative." using (out.value > 0)
    }

    private fun verifyUpdate(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        // Generic constraints around the IOU transaction.
        "Must have inputs consumed when updating an IOU." using (tx.inputs.isNotEmpty())
        "Must have outputs should be produced when updating an IOU." using (tx.outputs.isNotEmpty())
        "Only one input state2 should be updated." using (tx.inputs.size == 1)
        "Only one output state2 should be updated." using (tx.outputs.size == 1)
        val input = tx.inputsOfType<IOUState>().single()
        val out = tx.outputsOfType<IOUState>().single()
        "The lender and the borrower cannot be the same entity." using (out.lender != out.borrower)
        val allSigners = (input.participants.map { it.owningKey } + out.participants.map { it.owningKey }).toSet()
        "All of the participants must be signers." using (signers.containsAll(allSigners))

        "Only value can changed." using (out == input.copy(value = out.value))

        // IOU-specific constraints.
        "The IOU's value must be non-negative." using (out.value > 0)
    }

    private fun verifyPaid(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        // Generic constraints around the IOU transaction.
        "Must have inputs consumed when updating an IOU." using (tx.inputs.isNotEmpty())
        "Must have outputs should be produced when updating an IOU." using (tx.outputs.isNotEmpty())
        "Only one IOUState input state2 should be updated." using (tx.inputsOfType<IOUState>().size == 1)
        "Only one IOUState output state2 should be updated." using (tx.outputsOfType<IOUState>().size == 1)
        val input = tx.inputsOfType<IOUState>().single()
        val out = tx.outputsOfType<IOUState>().single()
        "The lender and the borrower cannot be the same entity." using (out.lender != out.borrower)
        val allSigners = (input.participants.map { it.owningKey } + out.participants.map { it.owningKey }).toSet()
        "All of the participants must be signers." using (signers.containsAll(allSigners))

        "Only paid flag can changed." using (out == input.copy(paid = out.paid))
    }

    /**
     * This contract2 only implements one command, Create.
     */
    interface Commands : CommandData {
        class Create : Commands
        class Update : Commands
        class Paid : Commands
    }
}
