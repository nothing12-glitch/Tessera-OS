package os.tessera.clock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import os.tessera.clock.ClockUiState
import os.tessera.clock.ClockViewModel
import os.tessera.clock.R
import os.tessera.clock.TAB_ALARMS
import os.tessera.clock.TAB_TIMER

@Composable
fun ClockScreen(
    state: ClockUiState,
    viewModel: ClockViewModel,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        bottomBar = {
            NavigationBar {
                NavTab(
                    selected = state.tab == 0,
                    iconRes = R.drawable.ic_tab_stopwatch,
                    labelRes = R.string.tab_stopwatch,
                    onClick = { viewModel.selectTab(0) },
                )
                NavTab(
                    selected = state.tab == TAB_TIMER,
                    iconRes = R.drawable.ic_tab_timer,
                    labelRes = R.string.tab_timer,
                    onClick = { viewModel.selectTab(TAB_TIMER) },
                )
                NavTab(
                    selected = state.tab == TAB_ALARMS,
                    iconRes = R.drawable.ic_tab_alarm,
                    labelRes = R.string.tab_alarms,
                    onClick = { viewModel.selectTab(TAB_ALARMS) },
                )
            }
        },
    ) { innerPadding ->
        val content = Modifier.padding(innerPadding)
        when (state.tab) {
            TAB_TIMER -> TimerScreen(state, viewModel, content)
            TAB_ALARMS -> AlarmsScreen(state, viewModel, content)
            else -> StopwatchScreen(state, viewModel, content)
        }
    }
}

@Composable
private fun RowScope.NavTab(
    selected: Boolean,
    iconRes: Int,
    labelRes: Int,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(painterResource(iconRes), contentDescription = null) },
        label = { Text(stringResource(labelRes)) },
    )
}
