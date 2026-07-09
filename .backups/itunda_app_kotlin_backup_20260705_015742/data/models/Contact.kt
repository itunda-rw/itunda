package com.itunda.app.data.models

data class Contact(
    val id: String,
    val name: String,
    val phone: String,
    val avatar: String?,
    val isFavorite: Boolean,
    val recentAmount: Double?
)

data class ContactsResponse(
    val success: Boolean,
    val contacts: List<Contact>
)
