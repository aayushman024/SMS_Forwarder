package com.mnivesh.smsforwarder.screens

import android.content.Context
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mnivesh.smsforwarder.api.RetrofitInstance
import com.mnivesh.smsforwarder.api.SmsLogResponse
import com.mnivesh.smsforwarder.managers.AuthManager
import com.mnivesh.smsforwarder.ui.theme.sdp
import com.mnivesh.smsforwarder.ui.theme.ssp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// ─── Palette ────────────────────────────────────────────────────────────────
private val BgDeep       = Color(0xFF060C1A)
private val BgSurface    = Color(0xFF0D1628)
private val BgCard       = Color(0xFF111E35)
private val BgCardAlt    = Color(0xFF0F1A2E)
private val AccentBlue   = Color(0xFF3B82F6)
private val AccentIndigo = Color(0xFF6366F1)
private val AccentCyan   = Color(0xFF22D3EE)
private val TextPrimary  = Color(0xFFEFF6FF)
private val TextSecondary= Color(0xFF94A3B8)
private val TextMuted    = Color(0xFF475569)
private val BorderSubtle = Color(0xFF1E3A5F)
private val DangerRed    = Color(0xFFEF4444)

// ─── Timestamp Helpers (unchanged logic) ────────────────────────────────────
fun parseServerTimestamp(ts: String): Long {
    return ts.toLongOrNull() ?: try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            java.time.Instant.parse(ts).toEpochMilli()
        } else { 0L }
    } catch (e: Exception) { 0L }
}

fun formatDisplayTime(ts: String): String {
    val ms = parseServerTimestamp(ts)
    if (ms == 0L) return "--"
    val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    return sdf.format(Date(ms))
}

// ─── Main Screen ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onLogout: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    val authManager = remember { AuthManager(context) }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val userName  = remember { authManager.getUserName()  ?: "User" }
    val userEmail = remember { authManager.getUserEmail() ?: "No Email" }
    val userDept  = remember { authManager.getDepartment() ?: "No Dept" }

    var whitelist by remember {
        val s = prefs.getStringSet("whitelist_senders", emptySet()) ?: emptySet()
        mutableStateOf(s.toList())
    }

    var smsLogs        by remember { mutableStateOf<List<SmsLogResponse>>(emptyList()) }
    var isLoadingLogs  by remember { mutableStateOf(true) }
    var isRefreshing   by remember { mutableStateOf(false) }
    var currentTime    by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showAddDialog  by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    // global ticker
    LaunchedEffect(Unit) {
        while (true) { delay(1000); currentTime = System.currentTimeMillis() }
    }

    val fetchLogs: () -> Unit = {
        scope.launch {
            isRefreshing = true
            val token = authManager.getToken()
            if (!token.isNullOrEmpty()) {
                try {
                    val response = RetrofitInstance.api.getSmsLogs("Bearer $token")
                    if (response.isSuccessful) smsLogs = response.body() ?: emptyList()
                } catch (e: Exception) { Log.e("HomeScreen", "Error fetching logs", e) }
            }
            isLoadingLogs = false
            isRefreshing  = false
        }
    }

    LaunchedEffect(Unit) { fetchLogs() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) fetchLogs()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val activeLogs = smsLogs.filter { log ->
        val ts = parseServerTimestamp(log.timestamp)
        (ts + 5 * 60 * 1000) > currentTime
    }.sortedByDescending { parseServerTimestamp(it.timestamp) }

    // spinning animation for refresh
    val infiniteAnim = rememberInfiniteTransition(label = "spin")
    val rotation by infiniteAnim.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "rotation"
    )

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            TopAppBar(
                modifier = Modifier
                    .drawBehind {
                        drawLine(
                            color = BorderSubtle,
                            start = Offset(0f, size.height),
                            end   = Offset(size.width, size.height),
                            strokeWidth = 1f
                        )
                    },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.sdp())
                                .background(AccentCyan, CircleShape)
                        )
                        Spacer(Modifier.width(10.sdp()))
                        Text(
                            "mRelay",
                            color = TextPrimary,
                            fontSize = 18.ssp(),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                        Spacer(Modifier.width(6.sdp()))
                        Text(
                            "DASHBOARD",
                            color = TextMuted,
                            fontSize = 11.ssp(),
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 2.ssp()
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDeep),
                actions = {
                    // Refresh button / spinner
                    if (isRefreshing && !isLoadingLogs) {
                        Box(
                            modifier = Modifier
                                .padding(end = 4.sdp())
                                .size(40.sdp()),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = AccentBlue,
                                modifier = Modifier.size(18.sdp()),
                                strokeWidth = 2.sdp()
                            )
                        }
                    } else {
                        IconButton(onClick = fetchLogs) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = "Refresh",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.sdp())
                            )
                        }
                    }

                    // Logout
                    TextButton(
                        onClick = { authManager.logout(); onLogout() },
                        colors = ButtonDefaults.textButtonColors(contentColor = DangerRed.copy(alpha = 0.8f)),
                        contentPadding = PaddingValues(horizontal = 12.sdp(), vertical = 8.sdp())
                    ) {
                        Icon(Icons.Rounded.ExitToApp, contentDescription = null, modifier = Modifier.size(16.sdp()))
                        Spacer(Modifier.width(6.sdp()))
                        Text("Sign out", fontSize = 13.ssp(), fontWeight = FontWeight.Medium)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.sdp()),
            verticalArrangement = Arrangement.spacedBy(0.sdp())
        ) {

            // ── HEADER ──────────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(20.sdp()))
                EnterpriseHeader(name = userName, email = userEmail, dept = userDept)
                Spacer(Modifier.height(28.sdp()))
            }

            // ── ACTIVE MESSAGES SECTION HEADER ───────────────────────────
            item {
                SectionHeader(
                    title = "Live Messages",
                    subtitle = "Auto-expires in 5 minutes",
                    badge = if (activeLogs.isNotEmpty()) "${activeLogs.size}" else null
                )
                Spacer(Modifier.height(12.sdp()))
            }

            // ── ACTIVE LOGS ──────────────────────────────────────────────
            if (isLoadingLogs) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.sdp()),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = AccentBlue,
                                modifier = Modifier.size(28.sdp()),
                                strokeWidth = 2.5.dp
                            )
                            Spacer(Modifier.height(12.sdp()))
                            Text("Fetching messages…", color = TextMuted, fontSize = 13.ssp())
                        }
                    }
                }
            } else if (activeLogs.isEmpty()) {
                item { EmptyState(message = "No active messages right now", sub = "New messages will appear here when forwarded") }
            } else {
                items(activeLogs) { log ->
                    ActiveLogItem(log = log, currentTime = currentTime)
                    Spacer(Modifier.height(10.sdp()))
                }
            }

            // ── WHITELIST SECTION ────────────────────────────────────────
            item {
                Spacer(Modifier.height(28.sdp()))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionHeader(
                            title = "Sender Whitelist",
                            subtitle = "Control which senders get forwarded",
                            badge = if (whitelist.isNotEmpty()) "${whitelist.size}" else null
                        )
                        Spacer(Modifier.width(6.sdp()))
                        IconButton(
                            onClick = { showInfoDialog = true },
                            modifier = Modifier.size(32.sdp())
                        ) {
                            Icon(
                                Icons.Rounded.Info,
                                contentDescription = "How whitelisting works",
                                tint = TextMuted,
                                modifier = Modifier.size(20.sdp())
                            )
                        }
                    }
                    FilledTonalButton(
                        onClick = { showAddDialog = true },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = AccentBlue.copy(alpha = 0.15f),
                            contentColor   = AccentBlue
                        ),
                        contentPadding = PaddingValues(horizontal = 14.sdp(), vertical = 8.sdp()),
                        shape = RoundedCornerShape(10.sdp())
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.sdp()))
                        Spacer(Modifier.width(6.sdp()))
                        Text("Add Sender", fontSize = 13.ssp(), fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(20.sdp()))
            }

            // ── WHITELIST ITEMS ──────────────────────────────────────────
            if (whitelist.isEmpty()) {
                item { EmptyState(message = "No senders whitelisted yet", sub = "Tap \"Add Sender\" to whitelist your first sender ID") }
            } else {
                items(whitelist) { sender ->
                    WhitelistItem(
                        sender = sender,
                        onDelete = {
                            val upd = whitelist.toMutableSet().apply { remove(sender) }
                            prefs.edit().putStringSet("whitelist_senders", upd).apply()
                            whitelist = upd.toList()
                        }
                    )
                    Spacer(Modifier.height(8.sdp()))
                }
            }

            item { Spacer(Modifier.height(40.sdp())) }
        }
    }

    // ── ADD DIALOG ───────────────────────────────────────────────────────────
    if (showAddDialog) {
        var newSender by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor   = BgCard,
            shape            = RoundedCornerShape(20.sdp()),
            title = {
                Column {
                    Text("Add to Whitelist", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.ssp())
                    Text(
                        "Enter a partial or full sender ID. Matching is fuzzy.",
                        color = TextSecondary,
                        fontSize = 13.ssp(),
                        lineHeight = 18.ssp(),
                        modifier = Modifier.padding(top = 4.sdp())
                    )
                }
            },
            text = {
                OutlinedTextField(
                    value          = newSender,
                    onValueChange  = { newSender = it },
                    label          = { Text("Sender ID  e.g. HDFC, PMHDFC-S") },
                    singleLine     = true,
                    shape          = RoundedCornerShape(12.sdp()),
                    colors         = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = AccentBlue,
                        unfocusedBorderColor = BorderSubtle,
                        cursorColor          = AccentBlue,
                        focusedLabelColor    = AccentBlue,
                        focusedTextColor     = TextPrimary,
                        unfocusedTextColor   = TextPrimary,
                        unfocusedLabelColor  = TextSecondary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newSender.isNotBlank()) {
                            val t = newSender.trim()
                            val upd = whitelist.toMutableSet().apply { add(t) }
                            prefs.edit().putStringSet("whitelist_senders", upd).apply()
                            whitelist = upd.toList()
                        }
                        showAddDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    shape  = RoundedCornerShape(10.sdp())
                ) { Text("Save", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddDialog = false },
                    colors  = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
                ) { Text("Cancel") }
            }
        )
    }

    // ── INFO DIALOG ──────────────────────────────────────────────────────────
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            containerColor   = BgCard,
            shape            = RoundedCornerShape(20.sdp()),
            icon = {
                Box(
                    Modifier
                        .size(44.sdp())
                        .background(AccentBlue.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Info, null, tint = AccentBlue, modifier = Modifier.size(22.sdp()))
                }
            },
            title = {
                Text(
                    "How Whitelisting Works",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.ssp()
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.sdp())) {
                    InfoPoint(
                        emoji = "🔒",
                        text  = "Matched messages are securely relayed to the server and automatically deleted after 5 minutes."
                    )
                    InfoPoint(
                        emoji = "🔍",
                        text  = "Matching is partial — whitelisting \"HDFC\" also captures variations like \"PMHDFC-S\" or \"HDFC-T\"."
                    )
                    InfoPoint(
                        emoji = "➕",
                        text  = "Add partial sender IDs to cast a wider net, or full IDs for precise targeting."
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showInfoDialog = false },
                    colors  = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    shape   = RoundedCornerShape(10.sdp())
                ) { Text("Got it", fontWeight = FontWeight.SemiBold) }
            }
        )
    }
}

@Composable
fun InfoPoint(emoji: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(emoji, fontSize = 15.ssp(), modifier = Modifier.padding(top = 1.sdp()))
        Spacer(Modifier.width(10.sdp()))
        Text(text, color = TextSecondary, fontSize = 13.ssp(), lineHeight = 18.ssp())
    }
}

// ─── Section Header ──────────────────────────────────────────────────────────
@Composable
fun SectionHeader(title: String, subtitle: String, badge: String? = null) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                color      = TextPrimary,
                fontSize   = 16.ssp(),
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp
            )
            if (badge != null) {
                Spacer(Modifier.width(8.sdp()))
                Box(
                    modifier = Modifier
                        .background(AccentBlue.copy(alpha = 0.2f), RoundedCornerShape(6.sdp()))
                        .padding(horizontal = 7.sdp(), vertical = 2.sdp())
                ) {
                    Text(badge, color = AccentBlue, fontSize = 11.ssp(), fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(subtitle, color = TextMuted, fontSize = 12.ssp(), modifier = Modifier.padding(top = 2.sdp()))
    }
}

// ─── Enterprise Header ───────────────────────────────────────────────────────
@Composable
fun EnterpriseHeader(name: String, email: String, dept: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.sdp()))
            .background(
                Brush.linearGradient(
                    0f    to Color(0xFF0D1628),
                    0.6f  to Color(0xFF0F1E38),
                    1f    to Color(0xFF071020)
                )
            )
            .border(1.sdp(), BorderSubtle, RoundedCornerShape(18.sdp()))
            .padding(20.sdp())
    ) {
        // subtle grid lines for enterprise feel
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    val step = 28.dp.toPx()
                    val lineColor = Color(0x08FFFFFF)
                    var x = 0f
                    while (x < size.width) {
                        drawLine(lineColor, Offset(x, 0f), Offset(x, size.height))
                        x += step
                    }
                }
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(52.sdp())
                    .background(
                        Brush.linearGradient(listOf(AccentBlue, AccentIndigo)),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    name.take(1).uppercase(),
                    color      = Color.White,
                    fontSize   = 22.ssp(),
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.width(16.sdp()))

            Column(modifier = Modifier.weight(1f)) {
                Text("Welcome back", color = TextMuted, fontSize = 11.ssp(), letterSpacing = 0.5.sp)
                Text(
                    name,
                    color      = TextPrimary,
                    fontSize   = 20.ssp(),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Spacer(Modifier.height(10.sdp()))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.sdp())
                ) {
                    MetaBadge(icon = { Icon(Icons.Rounded.Email, null, tint = TextMuted, modifier = Modifier.size(11.sdp())) }, text = email)
                    MetaBadge(icon = { Icon(Icons.Rounded.Badge, null, tint = TextMuted, modifier = Modifier.size(11.sdp())) }, text = dept)
                }
            }
        }
    }
}

@Composable
fun MetaBadge(icon: @Composable () -> Unit, text: String) {
    Row(
        modifier = Modifier
            .background(Color(0xFF1E3A5F).copy(alpha = 0.5f), RoundedCornerShape(6.sdp()))
            .padding(horizontal = 8.sdp(), vertical = 4.sdp()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(4.sdp()))
        Text(text, color = TextSecondary, fontSize = 11.ssp(), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ─── Info Banner ─────────────────────────────────────────────────────────────
@Composable
fun InfoBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.sdp()))
            .background(Color(0xFF1E3A5F).copy(alpha = 0.35f))
            .border(1.sdp(), Color(0xFF2A4A70).copy(alpha = 0.6f), RoundedCornerShape(12.sdp()))
            .padding(14.sdp()),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(28.sdp())
                .background(AccentBlue.copy(alpha = 0.15f), RoundedCornerShape(8.sdp())),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Info, null, tint = AccentBlue, modifier = Modifier.size(14.sdp()))
        }
        Spacer(Modifier.width(12.sdp()))
        Column {
            Text("How whitelisting works", color = TextPrimary, fontSize = 13.ssp(), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.sdp()))
            Text(
                "Matched messages are securely relayed to the server and deleted after 5 minutes. Matching is partial — whitelisting \"HDFC\" also captures \"PMHDFC-S\", \"HDFC-T\", etc.",
                color = TextSecondary,
                fontSize = 12.ssp(),
                lineHeight = 17.ssp()
            )
        }
    }
}

// ─── Empty State ─────────────────────────────────────────────────────────────
@Composable
fun EmptyState(message: String, sub: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.sdp()))
            .background(BgCard)
            .border(1.sdp(), BorderSubtle, RoundedCornerShape(14.sdp()))
            .padding(vertical = 28.sdp()),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(40.sdp())
                    .background(TextMuted.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Info, null, tint = TextMuted, modifier = Modifier.size(18.sdp()))
            }
            Spacer(Modifier.height(10.sdp()))
            Text(message, color = TextSecondary, fontSize = 14.ssp(), fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.sdp()))
            Text(sub, color = TextMuted, fontSize = 12.ssp())
        }
    }
}

// ─── Active Log Item ─────────────────────────────────────────────────────────
@Composable
fun ActiveLogItem(log: SmsLogResponse, currentTime: Long) {
    val timestampMs  = parseServerTimestamp(log.timestamp)
    val expiryTime   = timestampMs + (5 * 60 * 1000)
    val remainingMs  = (expiryTime - currentTime).coerceAtLeast(0)
    val minutes      = TimeUnit.MILLISECONDS.toMinutes(remainingMs)
    val seconds      = TimeUnit.MILLISECONDS.toSeconds(remainingMs) % 60
    val timeString   = String.format("%02d:%02d", minutes, seconds)

    // Calculate remaining fraction for colors (1f down to 0f)
    val remainingFraction = (remainingMs / (5f * 60 * 1000)).coerceIn(0f, 1f)
    // Calculate elapsed fraction for the bar width (0f up to 1f)
    val elapsedFraction   = 1f - remainingFraction

    // colour shifts from green → orange → red based on time left
    val timerColor = when {
        remainingFraction > 0.5f -> Color(0xFF22C55E)
        remainingFraction > 0.25f -> Color(0xFFF59E0B)
        else -> DangerRed
    }

    Surface(
        color  = BgCard,
        shape  = RoundedCornerShape(16.sdp()),
        border = BorderStroke(1.sdp(), BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.sdp())) {

            // Top row: sender name + countdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.sdp())
                            .background(AccentBlue.copy(alpha = 0.15f), RoundedCornerShape(10.sdp())),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            log.sender.take(2).uppercase(),
                            color = AccentBlue,
                            fontSize = 13.ssp(),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(10.sdp()))
                    Column {
                        Text(
                            log.sender,
                            color = TextPrimary,
                            fontSize = 15.ssp(),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text("SMS Sender", color = TextMuted, fontSize = 11.ssp())
                    }
                }

                // Countdown badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(timerColor.copy(alpha = 0.12f), RoundedCornerShape(8.sdp()))
                        .padding(horizontal = 10.sdp(), vertical = 6.sdp())
                ) {
                    Icon(
                        Icons.Rounded.Timer,
                        contentDescription = null,
                        tint = timerColor,
                        modifier = Modifier.size(13.sdp())
                    )
                    Spacer(Modifier.width(5.sdp()))
                    Text(
                        timeString,
                        color      = timerColor,
                        fontSize   = 13.ssp(),
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(Modifier.height(12.sdp()))

            // Progress bar (TTL indicator) - Now grows from 0 to full
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.sdp())
                    .clip(RoundedCornerShape(2.sdp()))
                    .background(TextMuted.copy(alpha = 0.15f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(elapsedFraction) // <-- Fills based on elapsed time
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.sdp()))
                        .background(
                            Brush.horizontalGradient(listOf(timerColor, timerColor.copy(alpha = 0.6f)))
                        )
                )
            }

            Spacer(Modifier.height(12.sdp()))

            // Message body
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.sdp()))
                    .background(BgDeep.copy(alpha = 0.6f))
                    .padding(12.sdp())
            ) {
                Text(
                    text     = log.message,
                    color    = TextPrimary.copy(alpha = 0.9f),
                    fontSize = 13.ssp(),
                    lineHeight = 19.ssp(),
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(12.sdp()))

            // Footer row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AccountCircle, null, tint = TextMuted, modifier = Modifier.size(13.sdp()))
                    Spacer(Modifier.width(4.sdp()))
                    Text(log.uploadedBy, color = TextMuted, fontSize = 12.ssp())
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Schedule, null, tint = TextMuted, modifier = Modifier.size(12.sdp()))
                    Spacer(Modifier.width(4.sdp()))
                    Text(formatDisplayTime(log.timestamp), color = TextMuted, fontSize = 12.ssp())
                }
            }
        }
    }
}

// ─── Whitelist Item ───────────────────────────────────────────────────────────
@Composable
fun WhitelistItem(sender: String, onDelete: () -> Unit) {
    Surface(
        color  = BgCard,
        shape  = RoundedCornerShape(12.sdp()),
        border = BorderStroke(1.sdp(), BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.sdp(), vertical = 13.sdp()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.sdp())
                        .background(AccentIndigo.copy(alpha = 0.15f), RoundedCornerShape(8.sdp())),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        sender.take(2).uppercase(),
                        color = AccentIndigo,
                        fontSize = 12.ssp(),
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(12.sdp()))
                Column {
                    Text(sender, color = TextPrimary, fontSize = 15.ssp(), fontWeight = FontWeight.Medium)
                    Text("Whitelisted sender", color = TextMuted, fontSize = 11.ssp())
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(36.sdp())
                    .background(DangerRed.copy(alpha = 0.08f), RoundedCornerShape(8.sdp()))
            ) {
                Icon(Icons.Default.Delete, "Remove", tint = DangerRed.copy(alpha = 0.7f), modifier = Modifier.size(16.sdp()))
            }
        }
    }
}