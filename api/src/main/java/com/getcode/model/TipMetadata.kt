package com.getcode.model

import com.getcode.solana.keys.PublicKey
import com.getcode.utils.serializer.PublicKeyAsStringSerializer
import kotlinx.serialization.Serializable

@Serializable
sealed interface TipMetadata {
    val platform: String
    val username: String
    @Serializable(with = PublicKeyAsStringSerializer::class)
    val tipAddress: PublicKey
    val imageUrl: String?

    val imageUrlSanitized: String?
}