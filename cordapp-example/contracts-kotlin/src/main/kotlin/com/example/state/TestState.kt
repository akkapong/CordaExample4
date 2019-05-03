package com.example.state

import com.example.contract.TestContract
import com.example.schema.IOUSchemaV1
import com.example.schema.TestSchemaV1
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState

/**
 * State Test
 *
 * use for test reference state
 */
@BelongsToContract(TestContract::class)
data class TestState(val refId: String,
                val refType: String,
                val desc: String,
                val lender: Party,
                val borrower: Party,
                override val linearId: UniqueIdentifier = UniqueIdentifier()): LinearState, QueryableState {
    /** The public keys of the involved parties. */
    override val participants: List<AbstractParty> get() = listOf(lender, borrower)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is IOUSchemaV1 -> TestSchemaV1.PersistentTest(
                    this.refId,
                    this.refType,
                    this.lender.name.toString(),
                    this.borrower.name.toString(),
                    this.desc,
                    this.linearId.id
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(IOUSchemaV1)
}