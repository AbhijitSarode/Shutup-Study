package com.abhijit.shutupNstudy

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object Home : NavKey

@Serializable
data class JoinGate(val roomId: String) : NavKey

@Serializable
data object CreateSetup : NavKey

@Serializable
data class ActiveSession(
    val roomId: String,
    val userName: String,
    val isLeader: Boolean
) : NavKey

@Serializable
data object SoloActiveSession : NavKey
