package org.cygnusx1.nzbconnect.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    filter: ResultFilter,
    breadcrumb: String,
    onApply: (ResultFilter) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var minGb by remember { mutableStateOf(filter.minGb.toFloat()) }
    var maxGb by remember { mutableStateOf(filter.maxGb.toFloat()) }
    var bluRay by remember { mutableStateOf(filter.bluRay) }
    var fourK by remember { mutableStateOf(filter.fourK) }
    var hdr by remember { mutableStateOf(filter.hdr) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Size", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            RangeSlider(
                value = minGb..maxGb,
                onValueChange = { range ->
                    minGb = range.start
                    maxGb = range.endInclusive
                },
                valueRange = 0f..FILTER_MAX_GB.toFloat(),
                steps = FILTER_MAX_GB - 1,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Min: ${minGb.toInt()}GB", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                val maxLabel = if (maxGb.toInt() >= FILTER_MAX_GB) "${FILTER_MAX_GB}GB+" else "${maxGb.toInt()}GB"
                Text("Max: $maxLabel", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }

            Text(
                "Qualities",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
            )
            QualityRow("Blu-Ray", bluRay) { bluRay = it }
            QualityRow("4K", fourK) { fourK = it }
            QualityRow("HDR", hdr) { hdr = it }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(onClick = onClear) { Text("Clear Filters") }
                Button(
                    onClick = {
                        onApply(
                            ResultFilter(
                                minGb = minGb.toInt(),
                                maxGb = maxGb.toInt(),
                                bluRay = bluRay,
                                fourK = fourK,
                                hdr = hdr,
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (breadcrumb.isBlank()) "Apply" else "Apply to $breadcrumb")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QualityRow(label: String, rule: QualityRule, onChange: (QualityRule) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        SingleChoiceSegmentedButtonRow {
            QualityRule.entries.forEachIndexed { index, value ->
                SegmentedButton(
                    selected = rule == value,
                    onClick = { onChange(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, QualityRule.entries.size),
                ) {
                    Text(value.name.lowercase().replaceFirstChar { it.uppercase() })
                }
            }
        }
    }
}
