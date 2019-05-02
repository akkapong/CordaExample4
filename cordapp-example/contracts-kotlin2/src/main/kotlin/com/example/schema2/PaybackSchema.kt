package com.example.schema2

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

/**
 * The family of schemas for PaybackState.
 */
object PaybackSchema

/**
 * An IOUState2 schema2.
 */
object PaybackSchemaV1 : MappedSchema(
        schemaFamily = PaybackSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentPayback::class.java)) {
    @Entity
    @Table(name = "pay_back_states")
    class PersistentPayback(
            @Column(name = "lender")
            var lenderName: String,

            @Column(name = "borrower")
            var borrowerName: String,

            @Column(name = "iou_linear_id")
            var iouLinearId: UUID,

            @Column(name = "linear_id")
            var linearId: UUID
    ) : PersistentState() {
        // Default constructor required by hibernate.
        constructor(): this("", "", UUID.randomUUID(), UUID.randomUUID())
    }
}