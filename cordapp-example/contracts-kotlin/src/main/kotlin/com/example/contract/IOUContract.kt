package com.example.contract

import com.example.state.IOUState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

/**
 * A implementation of a basic smart contract in Corda.
 *
 * This contract enforces rules regarding the creation of a valid [IOUState], which in turn encapsulates an [IOUState].
 *
 * For a new [IOUState] to be issued onto the ledger, a transaction is required which takes:
 * - Zero input states.
 * - One output state: the new [IOUState].
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
            else -> throw IllegalArgumentException("Unrecognised command.")
        }
    }

    private fun verifyCreate(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        // Generic constraints around the IOU transaction.
        "No inputs should be consumed when issuing an IOU." using (tx.inputs.isEmpty())
        "Only one output state should be created." using (tx.outputs.size == 1)
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
        "Only one input state should be updated." using (tx.inputs.size == 1)
        "Only one output state should be updated." using (tx.outputs.size == 1)
        val input = tx.inputsOfType<IOUState>().single()
        val out = tx.outputsOfType<IOUState>().single()
        "The lender and the borrower cannot be the same entity." using (out.lender != out.borrower)
        val allSigners = (input.participants.map { it.owningKey } + out.participants.map { it.owningKey }).toSet()
        "All of the participants must be signers." using (signers.containsAll(allSigners))

        "Only value can changed." using (out == input.copy(value = out.value))

        // IOU-specific constraints.
        "The IOU's value must be non-negative." using (out.value > 0)
    }

    /**
     * This contract only implements one command, Create.
     */
    interface Commands : CommandData {
        class Create : Commands
        class Update : Commands
    }
}
