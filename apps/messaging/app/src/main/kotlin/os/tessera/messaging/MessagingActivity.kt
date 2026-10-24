package os.tessera.messaging

import android.Manifest
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import os.tessera.messaging.ui.Composer
import os.tessera.messaging.ui.ConversationScreen
import os.tessera.messaging.ui.NewThreadScreen
import os.tessera.messaging.ui.TesseraMessagingTheme
import os.tessera.messaging.ui.ThreadListScreen

/**
 * Головний екран «Повідомлень».
 *
 * Активність тримає все, що потребує вікна або системи: дозволи, запит ролі
 * основного застосунку SMS, обробку `smsto:`/«Поділитися» й підписку на зміни
 * провайдера. Решта — у [MessagingViewModel].
 */
class MessagingActivity : ComponentActivity() {

    private val viewModel: MessagingViewModel by viewModels()
    private val snackbar = SnackbarHostState()

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            viewModel.reload()
        }

    private val requestRoleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            syncDefaultState()
        }

    /**
     * Реєстр змінюємо не в `onCreate`, а окремо: provider пише рядки після
     * того, як модем їх прийме, а не в момент нашого запиту.
     */
    private val smsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfObserving: Boolean) = viewModel.reload()
    }

    private val screenOffReceiver = object : BroadcastReceiver() {
        // «Прочитано» стає видимим лише після вимкнення екрана, тому оновлюємось і тоді
        override fun onReceive(context: Context, intent: Intent) = viewModel.reload()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(TRANSPARENT_BAR, TRANSPARENT_BAR),
            navigationBarStyle = SystemBarStyle.auto(TRANSPARENT_BAR, TRANSPARENT_BAR),
        )
        super.onCreate(savedInstanceState)
        askPermissionsIfNeeded()

        setContent {
            TesseraMessagingTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                var confirmDelete by remember { mutableStateOf(false) }
                Scaffold(
                    modifier = Modifier.imePadding(),
                    snackbarHost = { SnackbarHost(snackbar) },
                    topBar = {
                        TopBar(
                            state = state,
                            onBack = viewModel::back,
                            onDelete = { confirmDelete = true },
                        )
                    },
                ) { insets ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(insets),
                    ) {
                        if (!state.isDefaultApp) DefaultAppBanner()
                        if (!state.smsReadable) UnreadableBanner()
                        when (state.screen) {
                            MessagingScreen.Threads -> ThreadListScreen(
                                rows = state.rows,
                                query = state.query,
                                onQueryChange = viewModel::search,
                                onOpen = viewModel::openThread,
                                onNew = viewModel::startNewConversation,
                                modifier = Modifier.weight(1f),
                            )

                            MessagingScreen.Conversation -> ConversationScreen(
                                sections = state.daySections,
                                modifier = Modifier.weight(1f),
                            )

                            MessagingScreen.NewConversation -> NewThreadScreen(
                                address = state.openAddress,
                                onValueChange = viewModel::setAddress,
                                onConfirm = viewModel::confirmAddress,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (state.showComposer) {
                            Composer(
                                value = state.draft,
                                budget = state.budget,
                                canSend = state.canSend,
                                onValueChange = viewModel::setDraft,
                                onSend = viewModel::send,
                            )
                        }
                    }
                }

                if (confirmDelete) {
                    AlertDialog(
                        onDismissRequest = { confirmDelete = false },
                        title = { Text(stringResource(R.string.delete_thread_title)) },
                        text = { Text(stringResource(R.string.delete_thread_body, state.header)) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    confirmDelete = false
                                    viewModel.deleteOpenThread()
                                },
                            ) { Text(stringResource(R.string.delete)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmDelete = false }) {
                                Text(stringResource(R.string.cancel))
                            }
                        },
                    )
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.roleRequests.collect { askForSmsRole() } }
                launch {
                    viewModel.state.collect { state ->
                        val message = state.notice ?: return@collect
                        viewModel.consumeNotice()
                        snackbar.showSnackbar(message)
                    }
                }
            }
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        syncDefaultState()
        contentResolver.registerContentObserver(
            Telephony.Sms.CONTENT_URI,
            true,
            smsObserver,
        )
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        viewModel.reload()
    }

    override fun onPause() {
        super.onPause()
        contentResolver.unregisterContentObserver(smsObserver)
        runCatching { unregisterReceiver(screenOffReceiver) }
    }

    // --- роль і дозволи ---------------------------------------------------

    private fun syncDefaultState() {
        viewModel.setIsDefault(isDefaultApp())
    }

    private fun isDefaultApp(): Boolean =
        Telephony.Sms.getDefaultSmsPackage(this) == packageName

    private fun askPermissionsIfNeeded() {
        val needed = listOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_SMS,
            Manifest.permission.POST_NOTIFICATIONS,
        ).filterNot { granted(it) }
        if (needed.isNotEmpty()) requestPermissions.launch(needed.toTypedArray())
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * Запит ролі. `ROLE_SMS` дає дозволи й app-ops одним діалогом, але на
     * пристроях без телефонії роли немає взагалі — тоді просимо замінити
     * застосунок SMS старим інтентом, який є всюди.
     */
    private fun askForSmsRole() {
        val manager = getSystemService(RoleManager::class.java)
        if (manager != null && manager.isRoleAvailable(RoleManager.ROLE_SMS)) {
            requestRoleLauncher.launch(manager.createRequestRoleIntent(RoleManager.ROLE_SMS))
            return
        }
        val change = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
        runCatching { startActivity(change) }
            .onFailure { showNotice(R.string.role_unavailable) }
    }

    // --- зовнішні інтенти -------------------------------------------------

    private fun handleIntent(intent: Intent?) {
        val action = intent?.action ?: return
        val data = intent.data
        when (action) {
            Intent.ACTION_SENDTO -> {
                val scheme = data?.scheme.orEmpty()
                if (!scheme.startsWith("sms") && !scheme.startsWith("mms")) return
                openAddress(data?.schemeSpecificPart?.substringBefore(';').orEmpty())
                val body = intent.getStringExtra("sms_body") ?: intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!body.isNullOrEmpty()) viewModel.setDraft(body)
            }

            Intent.ACTION_SEND -> if (intent.type == "text/plain") {
                viewModel.startNewConversation()
                viewModel.setDraft(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
            }
        }
    }

    /**
     * Виклик з «Контактів» приносить номер, якого ще може не бути в базі.
     * [MessagingViewModel.confirmAddress] сам вирішує, чи це вже розмова:
     * повідомлення шукаються за адресою, а не за ідентифікатором нитки.
     */
    private fun openAddress(address: String) {
        viewModel.startNewConversation()
        if (address.isBlank()) return
        viewModel.setAddress(address)
        viewModel.confirmAddress()
    }

    private fun showNotice(resId: Int) {
        val message = getString(resId)
        lifecycleScope.launch { snackbar.showSnackbar(message) }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun TopBar(
        state: MessagingUiState,
        onBack: () -> Unit,
        onDelete: () -> Unit,
    ) {
        TopAppBar(
            title = {
                Text(
                    text = if (state.screen == MessagingScreen.Threads) {
                        stringResource(R.string.app_name)
                    } else {
                        state.header.ifBlank { stringResource(R.string.new_thread) }
                    },
                )
            },
            navigationIcon = {
                if (state.screen != MessagingScreen.Threads) {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_back),
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                }
            },
            actions = {
                if (state.screen == MessagingScreen.Conversation) {
                    // Одна дія — одна іконка: меню з одним пунктом лише ховає
                    // небезпечну дію на один тап далі, а захист тут уже є —
                    // діалог підтвердження.
                    IconButton(onClick = onDelete) {
                        Icon(
                            painterResource(R.drawable.ic_delete),
                            contentDescription = stringResource(R.string.cd_delete),
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        )
    }

    @Composable
    private fun DefaultAppBanner() {
        Card(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(stringResource(R.string.role_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.role_body),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    TextButton(onClick = viewModel::requestRole) {
                        Text(stringResource(R.string.role_action))
                    }
                }
            }
        }
    }

    @Composable
    private fun UnreadableBanner() {
        Card(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) {
            Text(
                text = stringResource(R.string.no_sms_permission),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(20.dp),
            )
        }
    }

    private companion object {
        const val TRANSPARENT_BAR = android.graphics.Color.TRANSPARENT
    }
}
