package com.example.state

import com.example.contract.PayBackContract
import com.example.schema.PaybackSchemaV1
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import com.example.contract.IOUContract as IOUContract

@BelongsToContract(PayBackContract::class)
data class PayBackState (val iouLinearId: UniqueIdentifier,
                         val lender: Party,
                         val borrower: Party,
                         override val linearId: UniqueIdentifier = UniqueIdentifier()): LinearState, QueryableState {
    /** The public keys of the involved parties. */
    override val participants: List<AbstractParty> get() = listOf(lender, borrower)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is PaybackSchemaV1 -> PaybackSchemaV1.PersistentPayback(
                    this.lender.name.toString(),
                    this.borrower.name.toString(),
                    this.iouLinearId.id,
                    this.linearId.id
            )
            else -> throw IllegalArgumentException("Unrecognised schema2 $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(PaybackSchemaV1)
}
