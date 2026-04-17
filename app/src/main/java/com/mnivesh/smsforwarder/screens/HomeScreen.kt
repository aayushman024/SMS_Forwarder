package com.mnivesh.smsforwarder.screens

import android.content.Context
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mnivesh.smsforwarder.api.EmployeeDirectory
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

// ─── Premium Corporate Light Palette ────────────────────────────────────────
private val BgDeep        = Color(0xFFF0F4F8)   // cool slate background
private val BgSurface     = Color(0xFFF8FAFC)   // near-white surface
private val BgCard        = Color(0xFFFFFFFF)   // pure white cards
private val BgCardAlt     = Color(0xFFF1F5F9)   // off-white alternate
private val AccentBlue    = Color(0xFF1D4ED8)   // deep corporate blue
private val AccentIndigo  = Color(0xFF4338CA)   // corporate indigo
private val AccentCyan    = Color(0xFF0369A1)   // corporate steel blue
private val TextPrimary   = Color(0xFF0F172A)   // near-black
private val TextSecondary = Color(0xFF475569)   // medium slate
private val TextMuted     = Color(0xFF94A3B8)   // light muted
private val BorderSubtle  = Color(0xFFE2E8F0)   // hairline border
private val BorderMedium  = Color(0xFFCBD5E1)   // slightly more visible border
private val DangerRed     = Color(0xFFDC2626)   // corporate red
private val SuccessGreen  = Color(0xFF16A34A)   // corporate green

// ─── Timestamp Helpers ──────────────────────────────────────────────────────
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

    var whitelist          by remember { mutableStateOf<List<com.mnivesh.smsforwarder.api.WhitelistResponse>>(emptyList()) }
    var smsLogs            by remember { mutableStateOf<List<SmsLogResponse>>(emptyList()) }
    var employeesDirectory by remember { mutableStateOf<List<EmployeeDirectory>>(emptyList()) }

    var isLoadingLogs  by remember { mutableStateOf(true) }
    var isRefreshing   by remember { mutableStateOf(false) }
    var currentTime    by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showAddSheet   by remember { mutableStateOf(false) }
    var editEntry      by remember { mutableStateOf<com.mnivesh.smsforwarder.api.WhitelistResponse?>(null) }
    var showInfoDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) { delay(1000); currentTime = System.currentTimeMillis() }
    }

    val fetchLogs: () -> Unit = {
        scope.launch {
            isRefreshing = true
            val token = authManager.getToken()
            if (!token.isNullOrEmpty()) {
                val bearerToken = "Bearer $token"

                try {
                    val response = RetrofitInstance.api.getSmsLogs(bearerToken)
                    if (response.isSuccessful) smsLogs = response.body() ?: emptyList()
                } catch (e: Exception) { Log.e("HomeScreen", "Error fetching logs", e) }

                try {
                    val empRes = RetrofitInstance.api.getEmployeePhoneDetails(bearerToken)
                    if (empRes.isSuccessful) employeesDirectory = empRes.body() ?: emptyList()
                } catch (e: Exception) { Log.e("HomeScreen", "Error fetching directory", e) }

                try {
                    val wlRes = RetrofitInstance.api.getWhitelist(bearerToken)
                    if (wlRes.isSuccessful) {
                        whitelist = wlRes.body() ?: emptyList()

                        // sync local prefs so SmsReceiver can intercept
                        val senderIds = whitelist.map { it.senderID }.toSet()
                        prefs.edit().putStringSet("whitelist_senders", senderIds).apply()
                    }
                } catch (e: Exception) { Log.e("HomeScreen", "Error fetching whitelist", e) }
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

    val pullRefreshState = rememberPullToRefreshState()

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            TopAppBar(
                modifier = Modifier
                    .shadow(elevation = 1.dp, spotColor = BorderSubtle)
                    .drawBehind {
                        drawLine(
                            color = BorderSubtle,
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgCard),
                actions = {
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
                                "Refresh",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.sdp())
                            )
                        }
                    }
                    TextButton(
                        onClick = { authManager.logout(); onLogout() },
                        colors = ButtonDefaults.textButtonColors(contentColor = DangerRed.copy(alpha = 0.85f)),
                        contentPadding = PaddingValues(horizontal = 12.sdp(), vertical = 8.sdp())
                    ) {
                        Icon(Icons.Rounded.ExitToApp, null, modifier = Modifier.size(16.sdp()))
                        Spacer(Modifier.width(6.sdp()))
                        Text("Sign out", fontSize = 13.ssp(), fontWeight = FontWeight.Medium)
                    }
                }
            )
        }
    ) { padding ->

        // ── Pull-to-Refresh wraps the entire scrollable content ──────────────
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { fetchLogs() },
            state = pullRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.sdp()),
                verticalArrangement = Arrangement.spacedBy(0.sdp())
            ) {
                item {
                    Spacer(Modifier.height(20.sdp()))
                    EnterpriseHeader(name = userName, email = userEmail, dept = userDept)
                    Spacer(Modifier.height(28.sdp()))
                }

                item {
                    SectionHeader(
                        "Live Messages",
                        "Auto-expires in 5 minutes",
                        if (activeLogs.isNotEmpty()) "${activeLogs.size}" else null
                    )
                    Spacer(Modifier.height(12.sdp()))
                }

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
                    item {
                        EmptyState(
                            "No active messages right now",
                            "New messages will appear here when forwarded"
                        )
                    }
                } else {
                    items(activeLogs) { log ->
                        ActiveLogItem(log = log, currentTime = currentTime)
                        Spacer(Modifier.height(10.sdp()))
                    }
                }

                item {
                    Spacer(Modifier.height(28.sdp()))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionHeader(
                                "Sender Whitelist",
                                "Control which senders get forwarded",
                                if (whitelist.isNotEmpty()) "${whitelist.size}" else null
                            )
                            Spacer(Modifier.width(6.sdp()))
                            IconButton(
                                onClick = { showInfoDialog = true },
                                modifier = Modifier.size(32.sdp())
                            ) {
                                Icon(
                                    Icons.Rounded.Info,
                                    "How whitelisting works",
                                    tint = TextMuted,
                                    modifier = Modifier.size(20.sdp())
                                )
                            }
                        }
                        FilledTonalButton(
                            onClick = {
                                editEntry = null
                                showAddSheet = true
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = AccentBlue.copy(alpha = 0.1f),
                                contentColor = AccentBlue
                            ),
                            contentPadding = PaddingValues(horizontal = 14.sdp(), vertical = 8.sdp()),
                            shape = RoundedCornerShape(10.sdp())
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.sdp()))
                            Spacer(Modifier.width(6.sdp()))
                            Text("Add Sender", fontSize = 13.ssp(), fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(20.sdp()))
                }

                if (whitelist.isEmpty()) {
                    item {
                        EmptyState(
                            "No senders whitelisted yet",
                            "Tap \"Add Sender\" to whitelist your first sender ID"
                        )
                    }
                } else {
                    items(whitelist) { entry ->
                        WhitelistItem(
                            entry = entry,
                            onEdit = {
                                editEntry = entry
                                showAddSheet = true
                            },
                            onDelete = {
                                scope.launch {
                                    val token = authManager.getToken()
                                    if (!token.isNullOrEmpty()) {
                                        try {
                                            val res = RetrofitInstance.api.deleteWhitelistEntry(
                                                "Bearer $token", entry._id
                                            )
                                            if (res.isSuccessful) {
                                                whitelist = whitelist.filter { it._id != entry._id }

                                                // update local prefs on delete
                                                val senderIds = whitelist.map { it.senderID }.toSet()
                                                prefs.edit().putStringSet("whitelist_senders", senderIds).apply()
                                            }
                                        } catch (e: Exception) {
                                            Log.e("HomeScreen", "Error deleting whitelist", e)
                                        }
                                    }
                                }
                            }
                        )
                        Spacer(Modifier.height(8.sdp()))
                    }
                }

                item { Spacer(Modifier.height(40.sdp())) }
            }
        }
    }

    // ── ADD BOTTOM SHEET ─────────────────────────────────────────────────────
    if (showAddSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        var newSender by remember { mutableStateOf(editEntry?.senderID ?: "") }
        var selectedEmployees by remember {
            mutableStateOf(
                if (editEntry != null) {
                    employeesDirectory.filter { emp -> editEntry!!.applicableEmployees.contains(emp.email) }
                } else {
                    emptyList()
                }
            )
        }
        var searchQuery by remember { mutableStateOf("") }
        var showDropdown by remember { mutableStateOf(false) }

        val filteredEmployees = employeesDirectory.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.email.contains(searchQuery, ignoreCase = true)
        }.filterNot { it in selectedEmployees }

        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState       = sheetState,
            containerColor   = BgCard,
            dragHandle       = { BottomSheetDefaults.DragHandle(color = BorderMedium) },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.sdp())
                    .padding(bottom = 24.sdp())
            ) {
                Text(
                    text = if (editEntry != null) "Edit Whitelist Sender" else "Add to Whitelist",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.ssp(),
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Enter a partial or full sender ID. Matching is fuzzy.",
                    color = TextSecondary,
                    fontSize = 13.ssp(),
                    lineHeight = 18.ssp(),
                    modifier = Modifier.padding(top = 4.sdp())
                )

                Spacer(Modifier.height(24.sdp()))

                OutlinedTextField(
                    value          = newSender,
                    onValueChange  = { newSender = it },
                    label          = { Text("Sender ID (e.g. HDFC, PMHDFC-S)") },
                    singleLine     = true,
                    enabled        = editEntry == null,
                    shape          = RoundedCornerShape(12.sdp()),
                    colors         = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = AccentBlue,
                        unfocusedBorderColor = BorderMedium,
                        cursorColor          = AccentBlue,
                        focusedLabelColor    = AccentBlue,
                        focusedTextColor     = TextPrimary,
                        unfocusedTextColor   = TextPrimary,
                        unfocusedLabelColor  = TextSecondary,
                        disabledBorderColor  = BorderSubtle,
                        disabledTextColor    = TextSecondary,
                        disabledLabelColor   = TextMuted
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(24.sdp()))

                Text(
                    text = "Visible to",
                    color = TextPrimary,
                    fontSize = 14.ssp(),
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.sdp()))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 52.sdp())
                        .border(1.sdp(), if (showDropdown) AccentBlue else BorderMedium, RoundedCornerShape(12.sdp()))
                        .background(BgCardAlt, RoundedCornerShape(12.sdp()))
                        .padding(horizontal = 12.sdp(), vertical = 10.sdp()),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Column(verticalArrangement = Arrangement.Center) {
                        if (selectedEmployees.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.sdp())
                            ) {
                                selectedEmployees.forEach { emp ->
                                    Row(
                                        modifier = Modifier
                                            .background(AccentBlue.copy(alpha = 0.1f), RoundedCornerShape(8.sdp()))
                                            .border(1.sdp(), AccentBlue.copy(alpha = 0.25f), RoundedCornerShape(8.sdp()))
                                            .padding(horizontal = 10.sdp(), vertical = 6.sdp()),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            emp.name,
                                            color = AccentBlue,
                                            fontSize = 12.ssp(),
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(Modifier.width(6.sdp()))
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = "Remove",
                                            tint = AccentBlue,
                                            modifier = Modifier
                                                .size(16.sdp())
                                                .clip(CircleShape)
                                                .clickable { selectedEmployees = selectedEmployees - emp }
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.sdp()))
                        }

                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it; showDropdown = true },
                            textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 14.ssp()),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { if (it.isFocused) showDropdown = true },
                            cursorBrush = SolidColor(AccentBlue),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty() && selectedEmployees.isEmpty()) {
                                    Text("Search employees...", color = TextMuted, fontSize = 14.ssp())
                                }
                                innerTextField()
                            }
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showDropdown && filteredEmployees.isNotEmpty(),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.sdp())
                            .border(1.sdp(), BorderMedium, RoundedCornerShape(12.sdp()))
                            .clip(RoundedCornerShape(12.sdp()))
                            .background(BgCard)
                    ) {
                        filteredEmployees.take(4).forEach { emp ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedEmployees = selectedEmployees + emp
                                        searchQuery = ""
                                    }
                                    .padding(horizontal = 16.sdp(), vertical = 12.sdp()),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.sdp())
                                        .background(AccentBlue.copy(alpha = 0.1f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        emp.name.take(1).uppercase(),
                                        color = AccentBlue,
                                        fontSize = 13.ssp(),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.width(12.sdp()))
                                Column {
                                    Text(emp.name, color = TextPrimary, fontSize = 14.ssp(), fontWeight = FontWeight.Medium)
                                    Text("${emp.email} • ${emp.department}", color = TextMuted, fontSize = 12.ssp())
                                }
                            }
                            if (emp != filteredEmployees.take(4).last()) {
                                Divider(
                                    color = BorderSubtle,
                                    thickness = 1.sdp(),
                                    modifier = Modifier.padding(horizontal = 16.sdp())
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(32.sdp()))

                val isFormValid = newSender.isNotBlank() && selectedEmployees.isNotEmpty()
                Button(
                    onClick = {
                        if (isFormValid) {
                            scope.launch {
                                val token = authManager.getToken()
                                if (!token.isNullOrEmpty()) {
                                    val emailsList = selectedEmployees.map { it.email }

                                    val request = com.mnivesh.smsforwarder.api.WhitelistRequest(
                                        senderID = newSender.trim(),
                                        applicableEmployees = emailsList
                                    )

                                    try {
                                        val res = RetrofitInstance.api.addWhitelistEntry(
                                            "Bearer $token", request
                                        )
                                        if (res.isSuccessful) {
                                            val newEntry = res.body()
                                            if (newEntry != null) {
                                                whitelist = whitelist.filter {
                                                    it.senderID != newEntry.senderID
                                                } + newEntry

                                                val senderIds = whitelist.map { it.senderID }.toSet()
                                                prefs.edit().putStringSet("whitelist_senders", senderIds).apply()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("HomeScreen", "Error adding whitelist", e)
                                    }
                                }
                                showAddSheet = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.sdp()),
                    enabled = isFormValid,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentBlue,
                        contentColor = Color.White,
                        disabledContainerColor = AccentBlue.copy(alpha = 0.25f),
                        disabledContentColor = Color.White.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(14.sdp())
                ) {
                    Text(
                        text = if (editEntry != null) "Update Sender" else "Save Sender",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.ssp()
                    )
                }

                Spacer(Modifier.height(8.sdp()))
            }
        }
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
                        .background(AccentBlue.copy(alpha = 0.1f), CircleShape)
                        .border(1.sdp(), AccentBlue.copy(alpha = 0.2f), CircleShape),
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
                    InfoPoint("🔒", "Matched messages are securely relayed to the server and automatically deleted after 5 minutes.")
                    InfoPoint("🔍", "Matching is partial — whitelisting \"HDFC\" also captures variations like \"PMHDFC-S\" or \"HDFC-T\".")
                    InfoPoint("➕", "Add partial sender IDs to cast a wider net, or full IDs for precise targeting.")
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

@Composable
fun SectionHeader(title: String, subtitle: String, badge: String? = null) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                color = TextPrimary,
                fontSize = 16.ssp(),
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp
            )
            if (badge != null) {
                Spacer(Modifier.width(8.sdp()))
                Box(
                    modifier = Modifier
                        .background(AccentBlue.copy(alpha = 0.12f), RoundedCornerShape(6.sdp()))
                        .border(1.sdp(), AccentBlue.copy(alpha = 0.2f), RoundedCornerShape(6.sdp()))
                        .padding(horizontal = 7.sdp(), vertical = 2.sdp())
                ) {
                    Text(badge, color = AccentBlue, fontSize = 11.ssp(), fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(subtitle, color = TextMuted, fontSize = 12.ssp(), modifier = Modifier.padding(top = 2.sdp()))
    }
}

@Composable
fun EnterpriseHeader(name: String, email: String, dept: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.sdp()))
            .background(
                Brush.linearGradient(
                    0f to Color(0xFFEFF6FF),
                    0.55f to Color(0xFFDBEAFE),
                    1f to Color(0xFFBFDBFE)
                )
            )
            .border(1.sdp(), BorderMedium, RoundedCornerShape(18.sdp()))
            .padding(20.sdp())
    ) {
        // Subtle dot-grid overlay
        Box(
            modifier = Modifier.matchParentSize().drawBehind {
                val step = 28.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawLine(Color(0x0A1D4ED8), Offset(x, 0f), Offset(x, size.height))
                    x += step
                }
            }
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.sdp())
                    .shadow(4.dp, CircleShape)
                    .background(
                        Brush.linearGradient(listOf(AccentBlue, AccentIndigo)),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    name.take(1).uppercase(),
                    color = Color.White,
                    fontSize = 22.ssp(),
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(16.sdp()))
            Column(modifier = Modifier.weight(1f)) {
                Text("Welcome back", color = AccentCyan, fontSize = 11.ssp(), letterSpacing = 0.5.sp, fontWeight = FontWeight.Medium)
                Text(
                    name,
                    color = TextPrimary,
                    fontSize = 20.ssp(),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Spacer(Modifier.height(10.sdp()))
                Row(horizontalArrangement = Arrangement.spacedBy(12.sdp())) {
                    MetaBadge(
                        icon = { Icon(Icons.Rounded.Email, null, tint = AccentBlue, modifier = Modifier.size(11.sdp())) },
                        text = email
                    )
                    MetaBadge(
                        icon = { Icon(Icons.Rounded.Badge, null, tint = AccentBlue, modifier = Modifier.size(11.sdp())) },
                        text = dept
                    )
                }
            }
        }
    }
}

@Composable
fun MetaBadge(icon: @Composable () -> Unit, text: String) {
    Row(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.65f), RoundedCornerShape(6.sdp()))
            .border(1.sdp(), BorderMedium.copy(alpha = 0.6f), RoundedCornerShape(6.sdp()))
            .padding(horizontal = 8.sdp(), vertical = 4.sdp()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(4.sdp()))
        Text(text, color = TextSecondary, fontSize = 11.ssp(), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

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
                    .background(AccentBlue.copy(alpha = 0.08f), CircleShape)
                    .border(1.sdp(), AccentBlue.copy(alpha = 0.15f), CircleShape),
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

@Composable
fun ActiveLogItem(log: SmsLogResponse, currentTime: Long) {
    val timestampMs  = parseServerTimestamp(log.timestamp)
    val expiryTime   = timestampMs + (5 * 60 * 1000)
    val remainingMs  = (expiryTime - currentTime).coerceAtLeast(0)
    val minutes      = TimeUnit.MILLISECONDS.toMinutes(remainingMs)
    val seconds      = TimeUnit.MILLISECONDS.toSeconds(remainingMs) % 60
    val timeString   = String.format("%02d:%02d", minutes, seconds)

    val remainingFraction = (remainingMs / (5f * 60 * 1000)).coerceIn(0f, 1f)
    val elapsedFraction   = 1f - remainingFraction
    val timerColor = when {
        remainingFraction > 0.5f  -> SuccessGreen
        remainingFraction > 0.25f -> Color(0xFFD97706)  // amber
        else                      -> DangerRed
    }

    Surface(
        color  = BgCard,
        shape  = RoundedCornerShape(16.sdp()),
        border = BorderStroke(1.sdp(), BorderSubtle),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.sdp())) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.sdp())
                            .background(AccentBlue.copy(alpha = 0.1f), RoundedCornerShape(10.sdp()))
                            .border(1.sdp(), AccentBlue.copy(alpha = 0.2f), RoundedCornerShape(10.sdp())),
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
                        Text(log.sender, color = TextPrimary, fontSize = 15.ssp(), fontWeight = FontWeight.SemiBold)
                        Text("SMS Sender", color = TextMuted, fontSize = 11.ssp())
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(timerColor.copy(alpha = 0.1f), RoundedCornerShape(8.sdp()))
                        .border(1.sdp(), timerColor.copy(alpha = 0.25f), RoundedCornerShape(8.sdp()))
                        .padding(horizontal = 10.sdp(), vertical = 6.sdp())
                ) {
                    Icon(Icons.Rounded.Timer, null, tint = timerColor, modifier = Modifier.size(13.sdp()))
                    Spacer(Modifier.width(5.sdp()))
                    Text(
                        timeString,
                        color = timerColor,
                        fontSize = 13.ssp(),
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Spacer(Modifier.height(12.sdp()))
            // Progress bar
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.sdp())
                    .clip(RoundedCornerShape(2.sdp()))
                    .background(BorderSubtle)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(elapsedFraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.sdp()))
                        .background(
                            Brush.horizontalGradient(listOf(timerColor, timerColor.copy(alpha = 0.5f)))
                        )
                )
            }
            Spacer(Modifier.height(12.sdp()))
            // Message body
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.sdp()))
                    .background(BgCardAlt)
                    .border(1.sdp(), BorderSubtle, RoundedCornerShape(10.sdp()))
                    .padding(12.sdp())
            ) {
                Text(
                    log.message,
                    color = TextPrimary.copy(alpha = 0.85f),
                    fontSize = 13.ssp(),
                    lineHeight = 19.ssp(),
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(12.sdp()))
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

@Composable
fun WhitelistItem(
    entry: com.mnivesh.smsforwarder.api.WhitelistResponse,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        color  = BgCard,
        shape  = RoundedCornerShape(12.sdp()),
        border = BorderStroke(1.sdp(), BorderSubtle),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.sdp(), vertical = 13.sdp()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(34.sdp())
                        .background(AccentIndigo.copy(alpha = 0.1f), RoundedCornerShape(8.sdp()))
                        .border(1.sdp(), AccentIndigo.copy(alpha = 0.2f), RoundedCornerShape(8.sdp())),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        entry.senderID.take(2).uppercase(),
                        color = AccentIndigo,
                        fontSize = 12.ssp(),
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(12.sdp()))
                Column(modifier = Modifier.weight(1f).padding(end = 12.sdp())) {
                    Text(
                        entry.senderID,
                        color = TextPrimary,
                        fontSize = 15.ssp(),
                        fontWeight = FontWeight.Medium
                    )
                    val subtext = if (entry.applicableEmployees.isNotEmpty()) {
                        "Visible to: ${entry.applicableEmployees.joinToString(", ")}"
                    } else "No employees assigned"
                    Text(subtext, color = TextMuted, fontSize = 11.ssp(), lineHeight = 16.ssp())
                }
            }

            Box {
                IconButton(onClick = { expanded = true }, modifier = Modifier.size(36.sdp())) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = "More options",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.sdp())
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .background(BgCard)
                        .border(1.sdp(), BorderSubtle, RoundedCornerShape(8.sdp()))
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit", color = TextPrimary, fontSize = 13.ssp()) },
                        onClick = {
                            expanded = false
                            onEdit()
                        },
                        leadingIcon = {
                            Icon(Icons.Rounded.Edit, null, tint = AccentBlue, modifier = Modifier.size(16.sdp()))
                        },
                        contentPadding = PaddingValues(horizontal = 16.sdp(), vertical = 8.sdp())
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = DangerRed, fontSize = 13.ssp()) },
                        onClick = {
                            expanded = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(Icons.Rounded.Delete, null, tint = DangerRed, modifier = Modifier.size(16.sdp()))
                        },
                        contentPadding = PaddingValues(horizontal = 16.sdp(), vertical = 8.sdp())
                    )
                }
            }
        }
    }
}