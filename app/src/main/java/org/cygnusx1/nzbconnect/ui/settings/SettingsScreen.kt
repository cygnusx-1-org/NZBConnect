package org.cygnusx1.nzbconnect.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.cygnusx1.nzbconnect.R
import org.cygnusx1.nzbconnect.domain.Indexer
import org.cygnusx1.nzbconnect.domain.NzbgetConfig
import org.cygnusx1.nzbconnect.domain.SabConfig
import org.cygnusx1.nzbconnect.ui.AppBrandTitle
import org.cygnusx1.nzbconnect.ui.AppLogoMark
import org.cygnusx1.nzbconnect.ui.AppWordmark
import org.cygnusx1.nzbconnect.ui.IndexerLogo
import org.cygnusx1.nzbconnect.ui.IndexerPresets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Suggested backup filename with a second-precision timestamp, e.g. nzbconnect-backup-2026-06-05_15-30-45.json. */
private fun backupFileName(): String {
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
    return "nzbconnect-backup-$timestamp.json"
}

/** URLs must be typed verbatim, so disable the IME's auto-capitalization and autocorrect. */
private val UrlKeyboardOptions = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    autoCorrectEnabled = false,
    keyboardType = KeyboardType.Uri,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val indexers by viewModel.indexers.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    var editing by remember { mutableStateOf<Indexer?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var showSabDialog by remember { mutableStateOf(false) }
    var showNzbgetDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::exportBackup) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importBackup) }

    state.message?.let { msg ->
        LaunchedEffect(msg) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { AppBrandTitle("Settings") },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = null
                showDialog = true
            }) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = "Add Newznab indexer")
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Indexers", style = MaterialTheme.typography.titleMedium)
                if (indexers.isEmpty()) {
                    Text(
                        "No indexers yet. Tap + to add a Newznab indexer.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                indexers.forEach { indexer ->
                    IndexerRow(
                        indexer = indexer,
                        onEdit = {
                            editing = indexer
                            showDialog = true
                        },
                        onDelete = { viewModel.deleteIndexer(indexer) },
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text("SABnzbd server", style = MaterialTheme.typography.titleMedium)
                SabServerRow(
                    config = state.sab,
                    onEdit = { showSabDialog = true },
                )

                Text("NZBGet server", style = MaterialTheme.typography.titleMedium)
                NzbgetServerRow(
                    config = state.nzbget,
                    onEdit = { showNzbgetDialog = true },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text("Backup & restore", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Save or restore your indexer, SABnzbd and NZBGet settings. The backup file " +
                        "contains your API keys and passwords in plain text — keep it somewhere safe.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { exportLauncher.launch(backupFileName()) }) {
                        Text("Back up")
                    }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
                        Text("Restore")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text("About", style = MaterialTheme.typography.titleMedium)
                AboutSection()
            }
            androidx.compose.material3.SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            ) { data ->
                // Default Snackbar() hardcodes 12.dp padding on all sides; drop the bottom
                // padding so the bar rests flush on top of the navigation/tab bar.
                Snackbar(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp),
                ) {
                    Text(data.visuals.message)
                }
            }
        }
    }

    if (showDialog) {
        IndexerEditDialog(
            initial = editing,
            onDismiss = { showDialog = false },
            onTest = viewModel::testIndexer,
            onSave = { indexer ->
                viewModel.saveIndexer(indexer)
                showDialog = false
            },
        )
    }

    if (showSabDialog) {
        SabEditDialog(
            initial = state.sab,
            onDismiss = { showSabDialog = false },
            onTest = viewModel::testSab,
            onSave = { config ->
                viewModel.saveSab(config)
                showSabDialog = false
            },
        )
    }

    if (showNzbgetDialog) {
        NzbgetEditDialog(
            initial = state.nzbget,
            onDismiss = { showNzbgetDialog = false },
            onTest = viewModel::testNzbget,
            onSave = { config ->
                viewModel.saveNzbget(config)
                showNzbgetDialog = false
            },
        )
    }
}

/** App identity, version and a short description shown at the bottom of Settings. */
@Composable
private fun AboutSection() {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppLogoMark(modifier = Modifier.size(72.dp))
        Column {
            AppWordmark()
            if (versionName.isNotBlank()) {
                Text(
                    "Version $versionName",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    Text(
        "A Usenet download manager for SABnzbd and NZBGet.",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun IndexerRow(
    indexer: Indexer,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IndexerPresets.presetFor(indexer.baseUrl)?.let { preset ->
                IndexerLogo(preset.logoRes, tint = preset.tintLogo, modifier = Modifier.size(32.dp).padding(end = 4.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(indexer.name, fontWeight = FontWeight.SemiBold)
                Text(
                    indexer.baseUrl,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!indexer.enabled) {
                    Text("Disabled", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
            IconButton(onClick = onEdit) { Icon(painterResource(R.drawable.ic_edit), contentDescription = "Edit") }
            IconButton(onClick = onDelete) { Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete") }
        }
    }
}

@Composable
private fun SabServerRow(
    config: SabConfig,
    onEdit: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (config.isConfigured) {
                    Text(config.baseUrl, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = config.defaultCategory.takeIf { it.isNotBlank() }
                            ?.let { "Default category: $it" }
                            ?: "No default category",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("Not configured", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Tap edit to connect your SABnzbd server.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onEdit) { Icon(painterResource(R.drawable.ic_edit), contentDescription = "Edit") }
        }
    }
}

@Composable
private fun SabEditDialog(
    initial: SabConfig,
    onDismiss: () -> Unit,
    onTest: suspend (SabConfig) -> String,
    onSave: (SabConfig) -> Unit,
) {
    var url by remember { mutableStateOf(initial.baseUrl) }
    var key by remember { mutableStateOf(initial.apiKey) }
    var cat by remember { mutableStateOf(initial.defaultCategory) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SABnzbd server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Base URL (e.g. http://192.168.1.5:8080)") },
                    singleLine = true,
                    keyboardOptions = UrlKeyboardOptions,
                    modifier = Modifier.fillMaxWidth(),
                )
                ApiKeyField(value = key, onValueChange = { key = it })
                OutlinedTextField(
                    value = cat,
                    onValueChange = { cat = it },
                    label = { Text("Default category (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                DialogTestRow(enabled = url.isNotBlank() && key.isNotBlank()) {
                    onTest(SabConfig(url.trim(), key.trim(), cat.trim()))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = url.isNotBlank() && key.isNotBlank(),
                onClick = { onSave(SabConfig(url.trim(), key.trim(), cat.trim())) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NzbgetServerRow(
    config: NzbgetConfig,
    onEdit: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (config.isConfigured) {
                    Text(config.baseUrl, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = config.defaultCategory.takeIf { it.isNotBlank() }
                            ?.let { "Default category: $it" }
                            ?: "No default category",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("Not configured", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Tap edit to connect your NZBGet server.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onEdit) { Icon(painterResource(R.drawable.ic_edit), contentDescription = "Edit") }
        }
    }
}

@Composable
private fun NzbgetEditDialog(
    initial: NzbgetConfig,
    onDismiss: () -> Unit,
    onTest: suspend (NzbgetConfig) -> String,
    onSave: (NzbgetConfig) -> Unit,
) {
    var url by remember { mutableStateOf(initial.baseUrl) }
    var user by remember { mutableStateOf(initial.username) }
    var pass by remember { mutableStateOf(initial.password) }
    var cat by remember { mutableStateOf(initial.defaultCategory) }

    fun current() = NzbgetConfig(url.trim(), user.trim(), pass, cat.trim())

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("NZBGet server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Base URL (e.g. https://host:6791)") },
                    singleLine = true,
                    keyboardOptions = UrlKeyboardOptions,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text("Username") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                SecretField(value = pass, onValueChange = { pass = it }, label = "Password")
                OutlinedTextField(
                    value = cat,
                    onValueChange = { cat = it },
                    label = { Text("Default category (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                DialogTestRow(enabled = url.isNotBlank() && user.isNotBlank()) { onTest(current()) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = url.isNotBlank() && user.isNotBlank(),
                onClick = { onSave(current()) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** An API-key text field that masks its value but can reveal it via an eye toggle. */
@Composable
private fun ApiKeyField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) = SecretField(value = value, onValueChange = onValueChange, label = "API key", modifier = modifier)

/** A masked secret field (API key, password) that can reveal its value via an eye toggle. */
@Composable
private fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false,
        ),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    painter = painterResource(if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                    contentDescription = if (visible) "Hide $label" else "Show $label",
                )
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * A "Test connection" button used inside the edit dialogs so credentials can be verified
 * before saving. Runs [test] (the entered values) and shows its ✓/✗ result inline.
 */
@Composable
private fun DialogTestRow(enabled: Boolean, test: suspend () -> String) {
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                enabled = enabled && !testing,
                onClick = {
                    scope.launch {
                        testing = true
                        result = null
                        result = test()
                        testing = false
                    }
                },
            ) { Text("Test connection") }
            if (testing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
        result?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.labelMedium,
                color = if (message.startsWith("✓")) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
    }
}

@Composable
private fun IndexerEditDialog(
    initial: Indexer?,
    onDismiss: () -> Unit,
    onTest: suspend (Indexer) -> String,
    onSave: (Indexer) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var url by remember { mutableStateOf(initial?.baseUrl.orEmpty()) }
    var key by remember { mutableStateOf(initial?.apiKey.orEmpty()) }
    var enabled by remember { mutableStateOf(initial?.enabled ?: true) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Newznab Indexer" else "Edit Newznab Indexer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (IndexerPresets.ALL.isNotEmpty()) {
                    Text("Quick add", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IndexerPresets.ALL.forEach { preset ->
                            AssistChip(
                                onClick = {
                                    name = preset.name
                                    url = preset.baseUrl
                                },
                                label = { Text(preset.name) },
                                leadingIcon = {
                                    IndexerLogo(preset.logoRes, tint = preset.tintLogo, modifier = Modifier.size(20.dp))
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Base URL") },
                    singleLine = true,
                    keyboardOptions = UrlKeyboardOptions,
                    modifier = Modifier.fillMaxWidth(),
                )
                ApiKeyField(value = key, onValueChange = { key = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                    Text("Enabled", modifier = Modifier.padding(start = 8.dp))
                }
                DialogTestRow(enabled = url.isNotBlank() && key.isNotBlank()) {
                    onTest(
                        Indexer(
                            id = initial?.id ?: 0,
                            name = name.trim(),
                            baseUrl = url.trim(),
                            enabled = enabled,
                            apiKey = key.trim(),
                        ),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && url.isNotBlank(),
                onClick = {
                    onSave(
                        Indexer(
                            id = initial?.id ?: 0,
                            name = name.trim(),
                            baseUrl = url.trim(),
                            enabled = enabled,
                            apiKey = key.trim(),
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
