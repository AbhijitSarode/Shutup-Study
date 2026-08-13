package com.abhijit.shutupNstudy

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.abhijit.shutupNstudy.ui.screens.*

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Home)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Home> {
          HomeScreen(
            onNavigate = { navKey -> backStack.add(navKey) },
            modifier = Modifier.safeDrawingPadding()
          )
        }
        entry<JoinGate> { key ->
          JoinGateScreen(
            roomId = key.roomId,
            onNavigate = { navKey -> backStack.add(navKey) },
            modifier = Modifier.safeDrawingPadding()
          )
        }
        entry<CreateSetup> {
          CreateSetupScreen(
            onNavigate = { navKey -> backStack.add(navKey) },
            modifier = Modifier.safeDrawingPadding()
          )
        }
        entry<ActiveSession> { key ->
          ActiveSessionScreen(
            roomId = key.roomId,
            userName = key.userName,
            isLeader = key.isLeader,
            onNavigate = { navKey ->
              if (navKey == Home) {
                backStack.clear()
                backStack.add(Home)
              } else {
                backStack.add(navKey)
              }
            },
            modifier = Modifier.safeDrawingPadding()
          )
        }
        entry<SoloActiveSession> {
          SoloActiveSessionScreen(
            onNavigate = { navKey ->
              if (navKey == Home) {
                backStack.clear()
                backStack.add(Home)
              } else {
                backStack.add(navKey)
              }
            },
            modifier = Modifier.safeDrawingPadding()
          )
        }
      },
  )
}
