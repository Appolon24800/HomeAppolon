package dev.appolon.homelab.arr.feature.lidarr.addnew.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** One selectable row of a [ChoiceDropdown], with optional secondary text. */
internal data class Choice<T>(val value: T, val label: String, val secondary: String? = null)

/**
 * Read-only labeled dropdown with pre-resolved choice labels, mirroring the
 * web client's select inputs; both add sheets and the studio picker read
 * identically. Menu rows meet the 48dp minimum target.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> ChoiceDropdown(
    label: String,
    selectedLabel: String,
    choices: List<Choice<T>>,
    selected: T?,
    onSelectedChange: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(
                    androidx.compose.material3.MenuAnchorType.PrimaryNotEditable,
                )
                .fillMaxWidth(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(choice.label)
                            choice.secondary?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelectedChange(choice.value)
                    },
                )
            }
        }
    }
}
