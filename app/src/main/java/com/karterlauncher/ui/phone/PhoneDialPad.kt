package com.karterlauncher.ui.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.karterlauncher.R

private val DIAL_KEYS = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")

@Composable
fun PhoneDialPadScreen(
    onPlaceCall: (String) -> Unit,
    modifier: Modifier = Modifier,
    number: String,
    onNumberChange: (String) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = number.ifBlank { " " },
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(DIAL_KEYS) { key ->
                OutlinedButton(
                    onClick = { onNumberChange(number + key) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(key, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    if (number.isNotEmpty()) onNumberChange(number.dropLast(1))
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = stringResource(R.string.phone_hub_dialpad_backspace),
                )
            }
            Button(
                onClick = { if (number.isNotBlank()) onPlaceCall(number) },
                enabled = number.isNotBlank(),
                modifier = Modifier.weight(2f),
            ) {
                Icon(Icons.Filled.Call, contentDescription = null)
                Text(
                    text = stringResource(R.string.phone_hub_dialpad_call),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
