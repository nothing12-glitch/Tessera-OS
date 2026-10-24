package os.tessera.dialer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.app.role.RoleManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import os.tessera.dialer.logic.PhoneNumber
import os.tessera.dialer.ui.CallLogScreen
import os.tessera.dialer.ui.DialpadScreen
import os.tessera.dialer.ui.TesseraDialerTheme

/**
 * Головний екран «Телефона»: набір і журнал.
 *
 * Активність тримає все, що потребує вікна: дозволи, запит ролі основного
 * набору й сам `startActivity(ACTION_CALL)`. Решта — у [DialViewModel].
 */
class DialerActivity : ComponentActivity() {

    private val viewModel: DialViewModel by viewModels()
    private val snackbar = SnackbarHostState()

    private val requestCallPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                pendingNumber?.let(::placeCall)
            } else {
                tellDenied(R.string.perm_call_needed)
            }
            pendingNumber = null
        }

    private val requestReadPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            // без читання журналу екран лишається порожнім — пояснюємо це, а не мовчимо
            viewModel.setCallLogReadable(readLogGranted())
            viewModel.reload()
        }

    private val requestWritePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) pendingDelete?.let { viewModel.deleteRow(it.id) } else tellDenied(R.string.write_call_log_needed)
            pendingDelete = null
        }

    private val requestRoleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.setShowRoleBanner(roleMissing())
            viewModel.reload()
        }

    private var pendingNumber: String? = null
    private var pendingDelete: CallRow? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        askReadPermissionsIfNeeded()

        setContent {
            TesseraDialerTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                Scaffold(
                    modifier = Modifier.imePadding(),
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = state.tab == TAB_DIALPAD,
                                onClick = { viewModel.selectTab(TAB_DIALPAD) },
                                icon = {
                                    Icon(
                                        painterResource(R.drawable.ic_dialpad),
                                        contentDescription = null,
                                    )
                                },
                                label = { Text(stringResource(R.string.tab_dialpad)) },
                            )
                            NavigationBarItem(
                                selected = state.tab == TAB_RECENTS,
                                onClick = { viewModel.selectTab(TAB_RECENTS) },
                                icon = {
                                    Icon(
                                        painterResource(R.drawable.ic_recents),
                                        contentDescription = null,
                                    )
                                },
                                label = { Text(stringResource(R.string.tab_recents)) },
                            )
                        }
                    },
                ) { insets ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(insets),
                    ) {
                        if (state.showRoleBanner) DialerRoleBanner()
                        if (state.tab == TAB_DIALPAD) {
                            DialpadScreen(
                                state = state,
                                onPress = viewModel::press,
                                onBackspace = viewModel::backspace,
                                onCall = viewModel::callFromDialpad,
                                onSuggestion = viewModel::callNumber,
                            )
                        } else {
                            CallLogScreen(
                                state = state,
                                onQueryChange = viewModel::search,
                                onCall = viewModel::callNumber,
                                onDelete = ::requestDelete,
                            )
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.dialRequests.collect { number -> dispatchCall(number) }
            }
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.setCallLogReadable(readLogGranted())
        viewModel.setShowRoleBanner(roleMissing())
        // журнал оновлюємо завжди: дзвінок міг закінчитися, поки ми у фоні
        viewModel.reload()
    }

    // --- дозволи ----------------------------------------------------------

    private fun askReadPermissionsIfNeeded() {
        val needed = listOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS)
            .filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) requestReadPermissions.launch(needed.toTypedArray())
    }

    private fun readLogGranted(): Boolean = granted(Manifest.permission.READ_CALL_LOG)

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun roleMissing(): Boolean {
        val manager = getSystemService(RoleManager::class.java) ?: return false
        // роль може бути недоступна взагалі (планшет без телефонії) — тоді й банера не треба
        return manager.isRoleAvailable(RoleManager.ROLE_DIALER) &&
            !manager.isRoleHeld(RoleManager.ROLE_DIALER)
    }

    // --- дзвінок ----------------------------------------------------------

    private fun handleIntent(intent: Intent?) {
        val action = intent?.action ?: return
        val number = intent.data?.takeIf { it.scheme == "tel" || it.scheme == "sip" }
            ?.schemeSpecificPart
            ?.let { Uri.decode(it) }
            .orEmpty()
        when (action) {
            // ACTION_DIAL лише показує номер: виклик робить користувач
            Intent.ACTION_DIAL, Intent.ACTION_CALL_BUTTON -> if (number.isNotEmpty()) {
                number.forEach { viewModel.press(it) }
                viewModel.selectTab(TAB_DIALPAD)
            }

            Intent.ACTION_CALL -> if (number.isNotEmpty()) dispatchCall(number)
        }
    }

    private fun dispatchCall(number: String) {
        if (!granted(Manifest.permission.CALL_PHONE)) {
            pendingNumber = number
            requestCallPermission.launch(Manifest.permission.CALL_PHONE)
            return
        }
        placeCall(number)
    }

    private fun placeCall(number: String) {
        val cleaned = PhoneNumber.normalize(number)
        if (cleaned.isEmpty()) return
        val intent = Intent(Intent.ACTION_CALL, Uri.fromParts("tel", cleaned, null))
        runCatching { startActivity(intent) }
            .onFailure { tellDenied(R.string.perm_call_needed) }
    }

    private fun tellDenied(resId: Int) {
        val message = getString(resId)
        lifecycleScope.launch { snackbar.showSnackbar(message) }
    }

    private fun askForDialerRole() {
        val manager = getSystemService(RoleManager::class.java)
        if (manager == null) {
            tellDenied(R.string.perm_dialer_unavailable)
            return
        }
        requestRoleLauncher.launch(manager.createRequestRoleIntent(RoleManager.ROLE_DIALER))
    }

    private fun requestDelete(row: CallRow) {
        if (!granted(Manifest.permission.WRITE_CALL_LOG)) {
            pendingDelete = row
            requestWritePermission.launch(Manifest.permission.WRITE_CALL_LOG)
            return
        }
        viewModel.deleteRow(row.id)
    }

    @Composable
    private fun DialerRoleBanner() {
        Card(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(stringResource(R.string.perm_dialer_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.perm_dialer_body),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    TextButton(onClick = ::askForDialerRole) {
                        Text(stringResource(R.string.perm_dialer_action))
                    }
                    TextButton(onClick = { viewModel.setShowRoleBanner(false) }) {
                        Text(stringResource(R.string.perm_dialer_later))
                    }
                }
            }
        }
    }
}
