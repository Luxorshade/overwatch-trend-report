package it.luxorshade.owtrend

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val Orange = Color(0xFFF99E1A)
private val Bg = Color(0xFF14161C)
private val Surface = Color(0xFF1F222B)

private val OwColors = darkColorScheme(
    primary = Orange,
    onPrimary = Color(0xFF14161C),
    background = Bg,
    surface = Surface,
    surfaceVariant = Color(0xFF2A2E39),
    onBackground = Color(0xFFECEDEF),
    onSurface = Color(0xFFECEDEF),
    onSurfaceVariant = Color(0xFFB4B8C2),
)

private sealed interface Screen {
    data object List : Screen
    data class Detail(val reportId: String) : Screen
    data object Settings : Screen
}

class MainActivity : ComponentActivity() {

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReportCheckWorker.ensureChannel(this)
        ReportCheckWorker.schedule(this)
        if (Build.VERSION.SDK_INT >= 33) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val repository = Repository(applicationContext)
        setContent {
            MaterialTheme(colorScheme = OwColors) {
                App(repository)
            }
        }
    }
}

@Composable
private fun App(repository: Repository) {
    var screen by remember { mutableStateOf<Screen>(Screen.List) }
    var reports by remember { mutableStateOf<List<Report>>(emptyList()) }
    var config by remember { mutableStateOf<RemoteConfig?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var offline by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            loading = true
            error = null
            try {
                val result = repository.loadAll()
                reports = result.reports
                config = result.config
                offline = !result.online
                result.reports.firstOrNull()?.let { repository.lastSeenId = it.id }
            } catch (e: Exception) {
                error = "Impossibile scaricare i report.\nControlla la connessione o il repository nelle Impostazioni.\n(${e.message})"
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    BackHandler(enabled = screen != Screen.List) { screen = Screen.List }

    when (val s = screen) {
        Screen.List -> ReportListScreen(
            reports = reports,
            loading = loading,
            error = error,
            offline = offline,
            onOpen = { screen = Screen.Detail(it.id) },
            onRefresh = { reload() },
            onSettings = { screen = Screen.Settings },
        )
        is Screen.Detail -> {
            val report = reports.firstOrNull { it.id == s.reportId }
            if (report == null) LaunchedEffect(s) { screen = Screen.List }
            else ReportDetailScreen(report, onBack = { screen = Screen.List })
        }
        Screen.Settings -> SettingsScreen(
            repository = repository,
            config = config,
            onBack = { screen = Screen.List },
            onSaved = { screen = Screen.List; reload() },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OwTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg, titleContentColor = Orange),
    )
}

// ---------------------------------------------------------------- Elenco

@Composable
private fun ReportListScreen(
    reports: List<Report>,
    loading: Boolean,
    error: String?,
    offline: Boolean,
    onOpen: (Report) -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(reports, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) reports else reports.filter { it.searchText.contains(q) }
    }

    Scaffold(
        containerColor = Bg,
        topBar = {
            OwTopBar("OW Trend Report", actions = {
                IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Aggiorna") }
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Impostazioni") }
            })
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("Cerca audio, hashtag, eroi…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (offline) {
                Text(
                    "Sei offline: stai vedendo l'ultima copia salvata.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            when {
                loading && reports.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
                error != null && reports.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Text(error, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                filtered.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Text(
                        if (query.isBlank()) "Nessun report ancora." else "Nessun report contiene \"$query\".",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(filtered, key = { it.id }) { r ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onOpen(r) },
                            colors = CardDefaults.cardColors(containerColor = Surface),
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(r.week, color = Orange, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                Text(r.summary, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "${r.overwatch.size} novità · ${r.audio.size} audio · ${r.hashtags.size} hashtag",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Dettaglio

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = Orange,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun ItemCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
    ) { Column(Modifier.padding(14.dp)) { content() } }
}

@Composable
private fun Idea(text: String) {
    if (text.isBlank()) return
    Spacer(Modifier.height(6.dp))
    Text("💡 $text", style = MaterialTheme.typography.bodySmall, color = Orange)
}

@Composable
private fun ReportDetailScreen(report: Report, onBack: () -> Unit) {
    val uri = LocalUriHandler.current
    Scaffold(containerColor = Bg, topBar = { OwTopBar(report.week, onBack = onBack) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(report.summary, style = MaterialTheme.typography.bodyLarge)

            SectionTitle("🎮 Novità Overwatch")
            report.overwatch.forEach { n ->
                ItemCard {
                    Text(n.title, fontWeight = FontWeight.Bold)
                    if (n.date.isNotBlank()) Text(n.date, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(n.detail, style = MaterialTheme.typography.bodyMedium)
                    Idea(n.idea)
                    if (n.url.isNotBlank()) {
                        Text("Apri fonte", color = Orange, style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 6.dp).clickable { runCatching { uri.openUri(n.url) } })
                    }
                }
            }

            SectionTitle("🎵 Audio di tendenza")
            report.audio.forEachIndexed { i, a ->
                ItemCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}. ${a.title}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (a.stage.isNotBlank()) AssistChip(onClick = {}, label = { Text(a.stage) })
                    }
                    if (a.artist.isNotBlank()) Text(a.artist, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(a.use, style = MaterialTheme.typography.bodyMedium)
                    Idea(a.idea)
                }
            }

            SectionTitle("#️⃣ Hashtag consigliati (max 5)")
            ItemCard {
                Text(report.hashtags.joinToString("  ") { it.tag }, color = Orange, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                report.hashtags.forEach {
                    Text("${it.tag} — ${it.why}", style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 4.dp))
                }
            }

            if (report.notes.isNotBlank()) {
                SectionTitle("📝 Note")
                Text(report.notes, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (report.links.isNotEmpty()) {
                SectionTitle("🔗 Fonti")
                report.links.forEach { l ->
                    Text(l.title, color = Orange, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 6.dp).clickable { runCatching { uri.openUri(l.url) } })
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Generato: ${report.generatedAt} · Fonti dati: ${report.sources.joinToString()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------------------------------------------------------------- Impostazioni

@Composable
private fun SettingsScreen(
    repository: Repository,
    config: RemoteConfig?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    var repo by remember { mutableStateOf(repository.repo) }
    var branch by remember { mutableStateOf(repository.branch) }
    var notify by remember { mutableStateOf(repository.notificationsEnabled) }

    Scaffold(containerColor = Bg, topBar = { OwTopBar("Impostazioni", onBack = onBack) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            SectionTitle("Fonti dati")
            Text(
                "Le fonti usate dall'agent si cambiano nel file data/config.json del repository: " +
                    "qui vedi lo stato attuale.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            SourceRow("Web (siti di tendenze e notizie)", config?.webEnabled ?: true)
            SourceRow("Dashboard Instagram (tramite Chrome)", config?.dashboardEnabled ?: false)
            if (!config?.dashboardNote.isNullOrBlank()) {
                Text(config!!.dashboardNote, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!config?.schedule.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("Programmazione: ${config!!.schedule}", style = MaterialTheme.typography.bodySmall)
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            SectionTitle("Notifiche")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Avvisami quando esce un nuovo report", modifier = Modifier.weight(1f))
                Switch(checked = notify, onCheckedChange = { notify = it })
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            SectionTitle("Repository")
            OutlinedTextField(repo, { repo = it }, label = { Text("proprietario/nome") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(branch, { branch = it }, label = { Text("branch") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = {
                repository.repo = repo
                repository.branch = branch
                repository.notificationsEnabled = notify
                onSaved()
            }) { Text("Salva e ricarica") }
        }
    }
}

@Composable
private fun SourceRow(label: String, enabled: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Text(
            if (enabled) "ATTIVA" else "SPENTA",
            color = if (enabled) Orange else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
