package com.example.schema

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

/**
 * The family of schemas for IOUState.
 */
object TestSchema

/**
 * An IOUState schema.
 */
object TestSchemaV1 : MappedSchema(
        schemaFamily = TestSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentTest::class.java)) {
    @Entity
    @Table(name = "test_states")
    class PersistentTest(
            @Column(name = "ref_id")
            var refId: String,

            @Column(name = "ref_type")
            var refType: String,

            @Column(name = "lender")
            var lenderName: String,

            @Column(name = "borrower")
            var borrowerName: String,

            @Column(name = "desc")
            var desc: String,

            @Column(name = "linear_id")
            var linearId: UUID
    ) : PersistentState() {
        // Default constructor required by hibernate.
        constructor(): this("", "", "", "", "", UUID.randomUUID())
    }
}