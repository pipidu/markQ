package com.markq.data.local

object AttachmentLookup {
    fun indexById(rows: List<AttachmentEntity>): Map<String, AttachmentEntity> =
        rows.associateBy { it.id }
}
