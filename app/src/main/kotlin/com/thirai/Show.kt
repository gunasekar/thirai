package com.thirai

import kotlinx.serialization.Serializable

@Serializable
data class ShowConfig(
    val shows: List<Show> = emptyList()
)

@Serializable
data class Show(
    val id: String = "",
    val title: String = "",
    val image_url: String = "",
    val deep_link: String = ""
)
