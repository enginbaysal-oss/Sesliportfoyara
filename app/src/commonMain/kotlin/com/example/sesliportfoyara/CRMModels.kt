package com.example.sesliportfoyara

import kotlinx.serialization.Serializable

@Serializable
enum class ClientType {
    BUYER,  // Alıcı
    SELLER  // Satıcı
}

@Serializable
data class CRMReminder(
    val dateTime: String = "",
    val note: String = ""
)

@kotlinx.serialization.Serializable
data class SharedPortfolioLink(
    val url: String = "",
    val note: String = ""
)

@Serializable
data class Client(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val type: ClientType = ClientType.BUYER,
    val dealType: String = "Satılık", // Satılık, Kiralık
    val propertyType: String = "Daire", // Daire, Arsa, Villa, İşyeri, Tarla
    val il: String = "",
    val ilce: String = "",
    val mahalle: String = "",
    val preferredPriceMax: String = "",
    val preferredRooms: String = "",
    val note: String = "",
    val createdAt: Long = 0,
    val reminders: List<CRMReminder> = emptyList(),
    val sharedPortfolioLinks: List<SharedPortfolioLink> = emptyList())
