package os.tessera.messaging.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import os.tessera.messaging.R
import os.tessera.messaging.logic.SmsAddress

/**
 * Екран нового діалогу: поле номера й кнопка.
 *
 * Список контактів сюди не винесено навмисно: він потребував би власного
 * екрана з пошуком і права на читання телефонної книги, а розмова все одно
 * можлива — ввести номер руками або відкрити «Контакти» й звідти
 * `ACTION_SENDTO`, який ця активність уже обробляє.
 */
@Composable
internal fun NewThreadScreen(
    address: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        OutlinedTextField(
            value = address,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            singleLine = true,
            label = { Text(stringResource(R.string.new_thread)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onNext = { onConfirm() }),
        )
        Text(
            text = SmsAddress.display(address).takeIf { address.isNotBlank() }
                ?: stringResource(R.string.new_thread_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onConfirm,
            enabled = address.filter { it.isDigit() }.length >= MIN_DIGITS,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
        ) {
            Text(stringResource(R.string.continue_button))
        }
    }
}

private const val MIN_DIGITS = 7
