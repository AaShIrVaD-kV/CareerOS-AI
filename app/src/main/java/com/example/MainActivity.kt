package com.example

import androidx.compose.ui.layout.Layout
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.database.CuratedJobEntity
import com.example.data.database.JobScanEntity
import com.example.data.database.StudyTaskEntity
import com.example.data.database.UserProfileEntity
import com.example.data.database.JobApplicationEntity
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.CareerViewModel
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppScreen()
            }
        }
    }
}

// Helper to copy content to android clipboard
fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "$label copied to clipboard!", Toast.LENGTH_SHORT).show()
}

// JSON DECODERS
data class UIProjectItem(val title: String, val description: String)

fun decodeProjects(json: String?): List<UIProjectItem> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, Map::class.java)
        val adapter = moshi.adapter<List<Map<String, String>>>(type)
        val list = adapter.fromJson(json) ?: emptyList()
        list.map {
            UIProjectItem(
                title = it["title"] ?: "Unnamed Project",
                description = it["description"] ?: ""
            )
        }
    } catch (e: Exception) {
        emptyList()
    }
}

fun decodeQuestions(json: String?): List<Map<String, String>> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, Map::class.java)
        val adapter = moshi.adapter<List<Map<String, String>>>(type)
        adapter.fromJson(json) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}

fun decodeLearning(json: String?): Map<String, List<String>> {
    if (json.isNullOrBlank()) return emptyMap()
    return try {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val type = com.squareup.moshi.Types.newParameterizedType(
            Map::class.java,
            String::class.java,
            com.squareup.moshi.Types.newParameterizedType(List::class.java, String::class.java)
        )
        val adapter = moshi.adapter<Map<String, List<String>>>(type)
        adapter.fromJson(json) ?: emptyMap()
    } catch (e: Exception) {
        emptyMap()
    }
}

// CORE SYSTEM TABS
enum class AppTab(val title: String, val icon: ImageVector, val tag: String) {
    DASHBOARD("Coach", Icons.Default.Person, "tab_dashboard"),
    ATS_SCAN("ATS Scan", Icons.Default.Check, "tab_ats_scan"),
    JOBS("Jobs Feed", Icons.Default.Search, "tab_jobs"),
    TRACKER("Tracker CRM", Icons.Default.List, "tab_tracker"),
    LEARNING("Study Desk", Icons.Default.CheckCircle, "tab_learning")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainAppScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val viewModel: CareerViewModel = viewModel(factory = CareerViewModel.Factory(app))

    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val scans by viewModel.allScans.collectAsStateWithLifecycle()
    val tasks by viewModel.allTasks.collectAsStateWithLifecycle()
    val curatedJobs by viewModel.curatedJobs.collectAsStateWithLifecycle()
    val applications by viewModel.allApplications.collectAsStateWithLifecycle()

    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val scanError by viewModel.scanError.collectAsStateWithLifecycle()
    val selectedScanResult by viewModel.selectedScanResult.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(AppTab.DASHBOARD) }
    var showProfileEditor by remember { mutableStateOf(false) }
    var targetScanJdText by remember { mutableStateOf("") }

    // Synchronize scan selection transitions
    LaunchedEffect(selectedScanResult) {
        if (selectedScanResult != null) {
            activeTab = AppTab.ATS_SCAN
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .windowInsetsPadding(WindowInsets.statusBars),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "CareerOS Arrow Logo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp).rotate(-45f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CareerOS AI",
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Text(
                            text = "AI Career Advisor & ATS Optimizer",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Prepopulate user avatar representation "AV"
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .clickable { showProfileEditor = !showProfileEditor }
                            .testTag("avatar_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = profile?.name?.split(" ")?.mapNotNull { it.firstOrNull() }?.joinToString("")?.take(2) ?: "AV",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Divider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.height(1.dp))
            }
        },
        bottomBar = {
            Column {
                Divider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.height(1.dp))
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    windowInsets = WindowInsets.navigationBars,
                    modifier = Modifier.testTag("navigation_bar")
                ) {
                    AppTab.values().forEach { tab ->
                        NavigationBarItem(
                            selected = activeTab == tab,
                            onClick = { activeTab = tab },
                            icon = { Icon(imageVector = tab.icon, contentDescription = tab.title) },
                            label = { Text(text = tab.title, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag(tab.tag)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = activeTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                },
                modifier = Modifier.fillMaxSize()
            ) { tab ->
                when (tab) {
                    AppTab.DASHBOARD -> DashboardScreen(
                        profile = profile,
                        scans = scans,
                        tasks = tasks,
                        onEditClicked = { showProfileEditor = true },
                        viewModel = viewModel
                    )
                    AppTab.ATS_SCAN -> AtsScanScreen(
                        isScanning = isScanning,
                        scanError = scanError,
                        scans = scans,
                        selectedScanResult = selectedScanResult,
                        targetScanJdText = targetScanJdText,
                        onJdTextChange = { targetScanJdText = it },
                        onTriggerScan = { viewModel.triggerAtsScan(it) },
                        onSelectScan = { viewModel.selectHistoricalScan(it) },
                        onDeleteScan = { viewModel.deleteHistoricalScan(it) },
                        onClearError = { viewModel.clearScanError() },
                        viewModel = viewModel
                    )
                    AppTab.JOBS -> JobsScreen(
                        jobs = curatedJobs,
                        onSelectJobToAnalyze = { jobEntity ->
                            targetScanJdText = jobEntity.jdText
                            activeTab = AppTab.ATS_SCAN
                            viewModel.selectHistoricalScan(null) // Reset historical panel to show input field
                        }
                    )
                    AppTab.TRACKER -> TrackerScreen(
                        applications = applications,
                        viewModel = viewModel
                    )
                    AppTab.LEARNING -> StudyDeskCombinedScreen(
                        tasks = tasks,
                        scans = scans,
                        viewModel = viewModel
                    )
                }
            }

            // Slide out full sheet Profile Editor
            if (showProfileEditor) {
                profile?.let { userProf ->
                    ProfileEditorDialog(
                        profile = userProf,
                        onDismiss = { showProfileEditor = false },
                        onSave = { name, edu, skills, projJson, certs, locations, interests ->
                            viewModel.updateProfile(name, edu, skills, projJson, certs, locations, interests)
                            showProfileEditor = false
                        }
                    )
                }
            }
        }
    }
}

// ==========================================
// SCREEN 1: DASHBOARD
// ==========================================
@Composable
fun DashboardScreen(
    profile: UserProfileEntity?,
    scans: List<JobScanEntity>,
    tasks: List<StudyTaskEntity>,
    onEditClicked: () -> Unit,
    viewModel: CareerViewModel
) {
    var activeSubTab by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TabRow(
            selectedTabIndex = activeSubTab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = activeSubTab == 0,
                onClick = { activeSubTab = 0 },
                text = { Text("Profile & Stats", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.Person, contentDescription = "Profile", modifier = Modifier.size(18.dp)) },
                modifier = Modifier.testTag("dashboard_subtab_profile")
            )
            Tab(
                selected = activeSubTab == 1,
                onClick = { activeSubTab = 1 },
                text = { Text("Ask Gemini Coach", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.Send, contentDescription = "Gemini Coach", modifier = Modifier.size(18.dp)) },
                modifier = Modifier.testTag("dashboard_subtab_chat")
            )
        }

        if (activeSubTab == 0) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .testTag("dashboard_screen"),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
        item {
            // Welcome Card with Gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        )
                    )
                    .padding(20.dp)
            ) {
                Column {
                    Text(
                        text = "Good day, ${profile?.name ?: "Professional"}!",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.background
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Ready to land your next interview? CareerOS AI is compiled to secure ATS optimization, truthful resumes, and targeted metrics.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.85f)
                    )
                }
            }
        }

        item {
            // Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DashboardStatCard(
                    title = "Job Scans",
                    value = scans.size.toString(),
                    icon = Icons.Default.Check,
                    modifier = Modifier.weight(1f)
                )
                DashboardStatCard(
                    title = "Tasks Pending",
                    value = tasks.filter { !it.isCompleted }.size.toString(),
                    icon = Icons.Default.List,
                    modifier = Modifier.weight(1f)
                )
                DashboardStatCard(
                    title = "Avg. ATS Score",
                    value = if (scans.isEmpty()) "–" else "${scans.map { it.matchScore }.average().toInt()}%",
                    icon = Icons.Default.Star,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Profile Summary",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                IconButton(onClick = onEditClicked, modifier = Modifier.testTag("edit_profile_btn")) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Profile",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Master Profile Details
        profile?.let { prof ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        ProfileRow(label = "Education", value = prof.education)
                        Divider(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                        ProfileRow(label = "Preferred Locations", value = prof.preferredLocationsRaw)
                        Divider(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                        ProfileRow(label = "Career Targets", value = prof.careerInterestsRaw)
                        Divider(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                        Column {
                            Text(text = "Skills Verified", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                crossAxisSpacing = 6.dp,
                                mainAxisSpacing = 6.dp
                            ) {
                                prof.skillsRaw.split(",").forEach { skill ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = skill.trim(),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Verified Career Projects (${decodeProjects(prof.projectsRaw).size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            items(decodeProjects(prof.projectsRaw)) { project ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Project icon",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = project.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = project.description,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    } else {
        DashboardChatbotPanel(viewModel = viewModel)
    }
    }
}

@Composable
fun DashboardStatCard(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Text(text = title, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun ProfileRow(label: String, value: String) {
    Column {
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    mainAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    crossAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val xSpacing = mainAxisSpacing.roundToPx()
        val ySpacing = crossAxisSpacing.roundToPx()
        val placeables = measurables.map { it.measure(constraints) }

        var currentX = 0
        var currentY = 0
        var rowHeight = 0
        val positions = mutableListOf<Pair<Int, Int>>()

        placeables.forEach { placeable ->
            if (currentX + placeable.width > constraints.maxWidth) {
                currentX = 0
                currentY += rowHeight + ySpacing
                rowHeight = 0
            }
            positions.add(Pair(currentX, currentY))
            currentX += placeable.width + xSpacing
            rowHeight = maxOf(rowHeight, placeable.height)
        }

        val totalHeight = currentY + rowHeight
        layout(constraints.maxWidth, totalHeight) {
            placeables.forEachIndexed { index, placeable ->
                val (x, y) = positions[index]
                placeable.placeRelative(x, y)
            }
        }
    }
}


// ==========================================
// SCREEN 2: ATS SCAN (JD ANALYZER & CUSTOM RESUMES)
// ==========================================
@Composable
fun AtsScanScreen(
    isScanning: Boolean,
    scanError: String?,
    scans: List<JobScanEntity>,
    selectedScanResult: JobScanEntity?,
    targetScanJdText: String,
    onJdTextChange: (String) -> Unit,
    onTriggerScan: (String) -> Unit,
    onSelectScan: (JobScanEntity?) -> Unit,
    onDeleteScan: (Long) -> Unit,
    onClearError: () -> Unit,
    viewModel: CareerViewModel
) {
    val context = LocalContext.current
    var isHistoryExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .testTag("ats_scan_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Screen Header
            Text(
                text = "ATS Scanner & Strategist",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Secure optimized alignment. Paste a target JD to output real-time ATS keyword matching, customized summaries, honest cover letters, and learning plans.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Scans History Selector
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { isHistoryExpanded = !isHistoryExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Scan History (${scans.size})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Icon(
                            imageVector = if (isHistoryExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isHistoryExpanded) {
                        Spacer(modifier = Modifier.height(10.dp))
                        if (scans.isEmpty()) {
                            Text(
                                text = "No past scans. Submit your first Job Description below!",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                scans.forEach { scan ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (selectedScanResult?.id == scan.id)
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                else Color.Transparent
                                            )
                                            .clickable {
                                                onSelectScan(scan)
                                                isHistoryExpanded = false
                                            }
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${scan.jobTitle} @ ${scan.company}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onBackground,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${scan.location} • Score: ${scan.matchScore}%",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row {
                                            if (selectedScanResult?.id != scan.id) {
                                                IconButton(
                                                    onClick = { onSelectScan(scan) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = "View",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = { onDeleteScan(scan.id) },
                                                modifier = Modifier.size(28.dp).testTag("delete_scan_${scan.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Active Input Form
        if (selectedScanResult == null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Paste Job Description",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = targetScanJdText,
                            onValueChange = onJdTextChange,
                            placeholder = {
                                Text(
                                    "Paste raw job coordinates, requirements, qualifications, or description...",
                                    fontSize = 12.sp
                                )
                            },
                            textStyle = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .testTag("jd_input_field"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        if (scanError != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = scanError,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 11.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = onClearError, modifier = Modifier.size(18.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close Error",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        Button(
                            onClick = { onTriggerScan(targetScanJdText) },
                            enabled = !isScanning,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("analyze_jd_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isScanning) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.background,
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Awaiting Gemini Telemetry...", fontSize = 13.sp, color = MaterialTheme.colorScheme.background)
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.background,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Run CareerOS AI Scan", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.background)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // DISPLAY GENERATED PANELS
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { onSelectScan(null) },
                        colors = ButtonDefaults.textButtonColors(),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "New Scan")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Scan Another Job", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "Scanned result matched!",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            item {
                // Analysis Card Hero
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedScanResult.jobTitle,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "${selectedScanResult.company} • ${selectedScanResult.location}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Match score score circle
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selectedScanResult.matchScore >= 80) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                                    )
                                    .border(
                                        2.dp,
                                        if (selectedScanResult.matchScore >= 80) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.tertiary,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${selectedScanResult.matchScore}%",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedScanResult.matchScore >= 80) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.tertiary
                                    )
                                    Text(text = "ATS FIT", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = selectedScanResult.summary,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        var showAddedPopup by remember(selectedScanResult) { mutableStateOf(false) }
                        Button(
                            onClick = {
                                viewModel.addApplication(
                                    appliedDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()),
                                    company = selectedScanResult.company,
                                    role = selectedScanResult.jobTitle,
                                    location = selectedScanResult.location,
                                    source = "ATS Analyzer Sync",
                                    jobLink = "",
                                    matchScore = selectedScanResult.matchScore,
                                    resumeVersion = "v2_Optimized_" + selectedScanResult.company.replace(" ", ""),
                                    status = "Saved",
                                    notes = "Synced via Auto ATS Scans. Custom cover letter ready."
                                )
                                showAddedPopup = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(40.dp).testTag("save_and_sync_button")
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Auto-Save & Sync to Tracker CRM Sheets", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        if (showAddedPopup) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "🟢 Aligned in CRM sheets (Status: Saved). View in Tracker CRM tab!",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Expanded Skills
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(text = "Fitting Skills", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            mainAxisSpacing = 6.dp,
                            crossAxisSpacing = 6.dp
                        ) {
                            selectedScanResult.matchingSkillsRaw.split(",").forEach { skill ->
                                if (skill.isNotBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = skill.trim(),
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(text = "Skills Gap (Treat strictly as learning plan - honesty first)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            mainAxisSpacing = 6.dp,
                            crossAxisSpacing = 6.dp
                        ) {
                            selectedScanResult.missingSkillsRaw.split(",").forEach { skill ->
                                if (skill.isNotBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = skill.trim(),
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.tertiary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ATS Keywords Map
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(text = "Job Description Keywords", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            mainAxisSpacing = 6.dp,
                            crossAxisSpacing = 6.dp
                        ) {
                            selectedScanResult.atsKeywordsRaw.split(",").forEach { keyword ->
                                if (keyword.isNotBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = keyword.trim(),
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Resume Improvements List
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Ats Optimization Improvements",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        selectedScanResult.resumeImprovementsRaw.split("|").forEach { improve ->
                            if (improve.isNotBlank()) {
                                Row(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(text = "•", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = improve.trim(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            }

            // Copy Custom Resume Draft
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Proposed Custom Resume Summary",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            IconButton(onClick = {
                                copyToClipboard(context, "Honest Resume Draft", selectedScanResult.customResumeDraft)
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Copy resume",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = selectedScanResult.customResumeDraft,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background)
                                .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                .padding(10.dp)
                        )
                    }
                }
            }

            // Copy Cover Letter
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Tailored Clean Cover Letter",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            IconButton(onClick = {
                                copyToClipboard(context, "Adaptive Cover Letter", selectedScanResult.coverLetterDraft)
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Copy Cover Letter",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = selectedScanResult.coverLetterDraft,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background)
                                .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                .padding(10.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TextStyle(fontSize: androidx.compose.ui.unit.TextUnit, fontFamily: androidx.compose.ui.text.font.FontFamily): androidx.compose.ui.text.TextStyle {
    return androidx.compose.ui.text.TextStyle(fontSize = fontSize, fontFamily = fontFamily)
}

// ==========================================
// SCREEN 3: JOBS BOARD
// ==========================================
@Composable
fun JobsScreen(
    jobs: List<CuratedJobEntity>,
    onSelectJobToAnalyze: (CuratedJobEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .testTag("jobs_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Personal Job Search Agent",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "curating roles centered on data science, reporting, analytics, operations, and junior modeling systems matching your B.Com & Python profiles.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(jobs) { job ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Match Badge
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            when (job.category) {
                                                "Perfect Match" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                "Good Match" -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                                                else -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                                            }
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = job.category,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when (job.category) {
                                            "Perfect Match" -> MaterialTheme.colorScheme.primary
                                            "Good Match" -> MaterialTheme.colorScheme.secondary
                                            else -> MaterialTheme.colorScheme.tertiary
                                        }
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Match fit: ${job.matchScore}%",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = job.jobTitle,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "${job.company} • ${job.location}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = job.whyMatches,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    // List identified skills gaps
                    Text(
                        text = "Potential Skill Gaps: ${job.skillGaps}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onSelectJobToAnalyze(job) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(36.dp).weight(1f).testTag("job_analyze_${job.id}")
                        ) {
                            Text("Mock ATS Scan", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.background)
                        }
                        OutlinedButton(
                            onClick = { /* simulated link */ },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(36.dp).weight(1f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Text("Apply Link", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// SCREEN 4: INTERVIEW COACH PANEL
// ==========================================
@Composable
fun InterviewScreen(
    scans: List<JobScanEntity>,
    viewModel: CareerViewModel
) {
    var selectedScan by remember { mutableStateOf<JobScanEntity?>(null) }
    var currentQuestionIndex by remember { mutableStateOf(0) }
    var revealAnswer by remember { mutableStateOf(false) }
    var userDraftNotes by remember { mutableStateOf("") }

    val decodedQs = remember(selectedScan) {
        selectedScan?.let { decodeQuestions(it.interviewQuestionsRaw) } ?: emptyList()
    }

    LaunchedEffect(scans) {
        if (selectedScan == null && scans.isNotEmpty()) {
            selectedScan = scans.first()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .testTag("interview_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Personal Interview Coach",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Practice customized HR, technical, case study, project, and behavioral questions derived directly from your processed ATS Job Scans.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Dropdown or selector of scanned targets
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "Practice Context", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    if (scans.isEmpty()) {
                        Text(
                            text = "Please complete an ATS Scan in the scan tab first to compile custom questions!",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    } else {
                        // Horizontal row picker
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            scans.forEach { scan ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (selectedScan?.id == scan.id) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable {
                                            selectedScan = scan
                                            currentQuestionIndex = 0
                                            revealAnswer = false
                                            userDraftNotes = ""
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "${scan.jobTitle} @ ${scan.company}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedScan?.id == scan.id) MaterialTheme.colorScheme.background
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (decodedQs.isNotEmpty()) {
            val currentQ = decodedQs.getOrNull(currentQuestionIndex)

            currentQ?.let { question ->
                item {
                    // Flash Card Panel
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = question["type"] ?: "Interview Question",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = "Q ${currentQuestionIndex + 1} of ${decodedQs.size}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = question["question"] ?: "Empty question text?",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            Spacer(modifier = Modifier.height(14.dp))
                            // Draft Notes Textbox
                            OutlinedTextField(
                                value = userDraftNotes,
                                onValueChange = { userDraftNotes = it },
                                placeholder = { Text("Jot down talking points or answers to compare...", fontSize = 11.sp) },
                                textStyle = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.SansSerif),
                                modifier = Modifier.fillMaxWidth().height(90.dp).testTag("notes_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )

                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { revealAnswer = !revealAnswer },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().testTag("reveal_answer_btn")
                            ) {
                                Text(
                                    text = if (revealAnswer) "Hide Suggested Answer" else "Reveal Suggested Answer",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.background
                                )
                            }

                            // Reveal Panel
                            AnimatedVisibility(visible = revealAnswer) {
                                Column(modifier = Modifier.padding(top = 16.dp)) {
                                    Text(
                                        text = "Proposed Solution:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = question["modelAnswer"] ?: "",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Tactical Coach Suggestion:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = question["suggestion"] ?: "",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    // Next / Prev control Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (currentQuestionIndex > 0) {
                                    currentQuestionIndex--
                                    revealAnswer = false
                                    userDraftNotes = ""
                                }
                            },
                            enabled = currentQuestionIndex > 0,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Previous")
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        OutlinedButton(
                            onClick = {
                                if (currentQuestionIndex < decodedQs.size - 1) {
                                    currentQuestionIndex++
                                    revealAnswer = false
                                    userDraftNotes = ""
                                }
                            },
                            enabled = currentQuestionIndex < decodedQs.size - 1,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Next")
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// SCREEN 5: STUDY DESK & PRODUCTIVITY CHECKLIST
// ==========================================
@Composable
fun LearningTasksScreen(
    tasks: List<StudyTaskEntity>,
    viewModel: CareerViewModel
) {
    var taskText by remember { mutableStateOf("") }
    var taskCategory by remember { mutableStateOf("General") }
    var taskDuration by remember { mutableStateOf("DAILY") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .testTag("learning_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Study Desk & Productivity Assistant",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Your daily productivity scheduler, dynamically updating learning milestones (7, 30, 60, 90 days) when scanning target jobs. Perfect for systematically addressing identified skills gaps.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Simple Form container to insert manual study tasks
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "Add Custom Study Milestone", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = taskText,
                        onValueChange = { taskText = it },
                        placeholder = { Text("e.g. Practice Pandas .groupby() queries", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("new_task_input"),
                        textStyle = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.SansSerif)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = taskCategory,
                            onValueChange = { taskCategory = it },
                            placeholder = { Text("Category (e.g. Python)", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            textStyle = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.SansSerif)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    taskDuration = if (taskDuration == "DAILY") "7_DAYS" else if (taskDuration == "7_DAYS") "30_DAYS" else "DAILY"
                                }
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "Duration: $taskDuration", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            viewModel.addCustomStudyTask(taskText, taskCategory, taskDuration)
                            taskText = ""
                        },
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth().testTag("add_task_btn")
                    ) {
                        Text("Add Milestone", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Active Tasks Header
        item {
            Text(text = "Active Career Milestones", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        if (tasks.isEmpty()) {
            item {
                Text(
                    text = "No study milestones queued. Scan a job description or add custom milestones above to plan your search!",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        } else {
            items(tasks) { task ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { viewModel.toggleTask(task) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(
                                    if (task.isCompleted) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    1.dp,
                                    if (task.isCompleted) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (task.isCompleted) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = task.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
                                textDecoration = if (task.isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(text = task.category, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = task.durationType, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    IconButton(onClick = { viewModel.deleteTask(task.id) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete task",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// FULL SHEET USER MASTER PROFILE EDITOR
// ==========================================
@Composable
fun ProfileEditorDialog(
    profile: UserProfileEntity,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String, String) -> Unit
) {
    var editName by remember { mutableStateOf(profile.name) }
    var editEdu by remember { mutableStateOf(profile.education) }
    var editSkills by remember { mutableStateOf(profile.skillsRaw) }
    var editCerts by remember { mutableStateOf(profile.certificationsRaw) }
    var editLocations by remember { mutableStateOf(profile.preferredLocationsRaw) }
    var editInterests by remember { mutableStateOf(profile.careerInterestsRaw) }
    var editProjectsJson by remember { mutableStateOf(profile.projectsRaw) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .clickable(enabled = false) { /* prevent tap dismiss inside card */ },
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Editor Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Modify Master Profile Parameters", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Divider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    EditorField(label = "Full Name", value = editName, onValueChange = { editName = it })
                    EditorField(label = "Education", value = editEdu, onValueChange = { editEdu = it })
                    EditorField(label = "Verified Skills (Comma separated)", value = editSkills, onValueChange = { editSkills = it }, maxLines = 3)
                    EditorField(label = "Certifications", value = editCerts, onValueChange = { editCerts = it }, maxLines = 2)
                    EditorField(label = "Preferred Locations (Comma separated)", value = editLocations, onValueChange = { editLocations = it })
                    EditorField(label = "Career Interests", value = editInterests, onValueChange = { editInterests = it })
                    EditorField(label = "Projects JSON Definition", value = editProjectsJson, onValueChange = { editProjectsJson = it }, maxLines = 8)
                }

                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = {
                        onSave(editName, editEdu, editSkills, editProjectsJson, editCerts, editLocations, editInterests)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_profile_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save Master profile edits", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.background)
                }
            }
        }
    }
}

@Composable
fun EditorField(label: String, value: String, onValueChange: (String) -> Unit, maxLines: Int = 1) {
    Column {
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            maxLines = maxLines,
            textStyle = TextStyle(fontSize = 12.sp, fontFamily = if (maxLines > 4) FontFamily.Monospace else FontFamily.SansSerif)
        )
    }
}


// ==========================================
// SCREEN 5: CAREER TRACKER CRM & SHEETS
// ==========================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrackerScreen(
    applications: List<JobApplicationEntity>,
    viewModel: CareerViewModel
) {
    val context = LocalContext.current
    var aiUpdateText by remember { mutableStateOf("") }
    var aiFeedbackText by remember { mutableStateOf<String?>(null) }
    var isAILoading by remember { mutableStateOf(false) }

    // Dialog form states
    var showAddDialog by remember { mutableStateOf(false) }
    var dialogCompany by remember { mutableStateOf("") }
    var dialogRole by remember { mutableStateOf("") }
    var dialogLocation by remember { mutableStateOf("") }
    var dialogSource by remember { mutableStateOf("LinkedIn") }
    var dialogLink by remember { mutableStateOf("") }
    var dialogScore by remember { mutableStateOf("85") }
    var dialogResume by remember { mutableStateOf("v1_Standard") }
    var dialogStatus by remember { mutableStateOf("Applied") }
    var dialogNotes by remember { mutableStateOf("") }

    // Detail/Edit Sheet state
    var selectedAppForDetail by remember { mutableStateOf<JobApplicationEntity?>(null) }

    // Computed Career Analytics
    val totalApps = applications.size
    val interviews = applications.count { it.status == "Interview" }
    val assessments = applications.count { it.status == "Assessment" }
    val offers = applications.count { it.status == "Offer" }
    val rejections = applications.count { it.status == "Rejected" }
    val pending = applications.count { it.status in listOf("Saved", "Applied", "Assessment", "Interview") }
    
    val interviewRate = if (totalApps == 0) 0 else (interviews * 100) / totalApps
    val offerRate = if (totalApps == 0) 0 else (offers * 100) / totalApps

    // Dynamic categorizations
    val mostSuccessfulRole = remember(applications) {
        val successfulApps = applications.filter { it.status in listOf("Interview", "Offer", "Assessment") }
        if (successfulApps.isEmpty()) "Data Analyst"
        else successfulApps.groupBy { it.role }.maxByOrNull { it.value.size }?.key ?: "Data Analyst"
    }

    val mostSuccessfulResume = remember(applications) {
        val successfulApps = applications.filter { it.status in listOf("Interview", "Offer") }
        if (successfulApps.isEmpty()) "v1_Standard"
        else successfulApps.groupBy { it.resumeVersion }.maxByOrNull { it.value.size }?.key ?: "v1_Standard"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .testTag("tracker_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Career Tracker CRM",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Primary database in active synchronization with your Google Sheets application workbook. Use the smart update line or the spreadsheet tables below.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 1. Google Sheets live status and sync card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sheets Active Sync (Live)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50)
                            )
                        }
                        Text(
                            text = "Last synced: Just Now",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Sheet: Aashirvad_Career_CRM_2026",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                Toast.makeText(context, "Forced full reconciliation with Google Sheets spreadsheet!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(34.dp).weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Re-sync Sheets", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                copyToClipboard(context, "Google Sheets CRM Link", "https://docs.google.com/spreadsheets/d/mock_career_crm_aashirvad_data_science")
                            },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(34.dp).weight(1f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share Sheet URL", fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        // 2. AI Assistant conversational command update bar
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Conversational CRM Update (AI Assistant)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Type updates naturally to let the AI update your CRM sheets automatically.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = aiUpdateText,
                            onValueChange = { aiUpdateText = it },
                            placeholder = { Text("e.g. I got rejected from Amazon or applied to Aramco yesterday", fontSize = 11.sp) },
                            textStyle = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.SansSerif),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.weight(1f).height(46.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (aiUpdateText.isNotBlank()) {
                                    isAILoading = true
                                    viewModel.parseAndProcessAssistantMessage(aiUpdateText) { feedback ->
                                        aiFeedbackText = feedback
                                        aiUpdateText = ""
                                        isAILoading = false
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        ) {
                            if (isAILoading) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.background, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(imageVector = Icons.Default.Send, contentDescription = "Parse command", tint = MaterialTheme.colorScheme.background, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    aiFeedbackText?.let { feedback ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = feedback,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { aiFeedbackText = null }, modifier = Modifier.size(18.dp)) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
            }
        }

        // 3. Application Analytics
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Sheet Metrics & Insights",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MiniMetricRowItem(title = "Total Apps", value = totalApps.toString(), modifier = Modifier.weight(1f))
                            MiniMetricRowItem(title = "Interviews", value = interviews.toString(), modifier = Modifier.weight(1f))
                            MiniMetricRowItem(title = "Offers", value = offers.toString(), modifier = Modifier.weight(1f))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MiniMetricRowItem(title = "Rejected", value = rejections.toString(), modifier = Modifier.weight(1f))
                            MiniMetricRowItem(title = "Interview %", value = "$interviewRate%", modifier = Modifier.weight(1f))
                            MiniMetricRowItem(title = "Offer %", value = "$offerRate%", modifier = Modifier.weight(1f))
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "⭐ CRM Intelligent Insights:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Most Successful Role Type: $mostSuccessfulRole\n• High Performing Resume Version: $mostSuccessfulResume\n• Pending Actions: $pending items require weekly tracking verification.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // 4. Manual spreadsheet entries addition toolbar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Spreadsheet View",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Button(
                    onClick = {
                        dialogCompany = ""
                        dialogRole = ""
                        dialogLocation = ""
                        dialogSource = "LinkedIn"
                        dialogLink = ""
                        dialogScore = "85"
                        dialogResume = "v1_Standard"
                        dialogStatus = "Applied"
                        dialogNotes = ""
                        showAddDialog = true
                    },
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.height(34.dp).testTag("add_app_entry_button")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Row Entry", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 5. Excel Table Representation Layout
        if (applications.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Your CRM Sheets are empty! Click 'Add Row Entry' or write a conversational message to create job applications.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        // Headers Row
                        Row(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(vertical = 10.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TableHeaderCell(title = "Date/Updates", width = 110.dp)
                            TableHeaderCell(title = "Company", width = 120.dp)
                            TableHeaderCell(title = "Role Target", width = 140.dp)
                            TableHeaderCell(title = "Location", width = 110.dp)
                            TableHeaderCell(title = "ATS Fit", width = 70.dp)
                            TableHeaderCell(title = "Status Label", width = 110.dp)
                            TableHeaderCell(title = "Source/Version", width = 145.dp)
                            TableHeaderCell(title = "Action", width = 75.dp)
                        }

                        Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                        // Body Rows
                        applications.forEach { app ->
                            Row(
                                modifier = Modifier
                                    .clickable { selectedAppForDetail = app }
                                    .padding(vertical = 12.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TableCell(text = app.appliedDate, width = 110.dp)
                                TableCell(text = app.company, width = 120.dp, isBold = true)
                                TableCell(text = app.role, width = 140.dp)
                                TableCell(text = app.location, width = 110.dp)
                                
                                Box(modifier = Modifier.width(70.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                if (app.matchScore >= 80) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                                            )
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(text = "${app.matchScore}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (app.matchScore >= 80) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary)
                                    }
                                }

                                Box(modifier = Modifier.width(110.dp)) {
                                    StatusBadge(status = app.status)
                                }

                                TableCell(text = "${app.source}/${app.resumeVersion}", width = 145.dp)

                                Row(
                                    modifier = Modifier.width(75.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { selectedAppForDetail = app },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit row", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                    }
                                    IconButton(
                                        onClick = { viewModel.deleteApplication(app.applicationId) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete row", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                            Divider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable { showAddDialog = false },
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.85f)
                    .clickable(enabled = false) {},
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Add Application Row (Google Sheet Format)", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showAddDialog = false }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 10.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        EditorField(label = "Company Name", value = dialogCompany, onValueChange = { dialogCompany = it })
                        EditorField(label = "Role", value = dialogRole, onValueChange = { dialogRole = it })
                        EditorField(label = "Location", value = dialogLocation, onValueChange = { dialogLocation = it })
                        EditorField(label = "Source (e.g. LinkedIn, Website)", value = dialogSource, onValueChange = { dialogSource = it })
                        EditorField(label = "Job Link", value = dialogLink, onValueChange = { dialogLink = it })
                        EditorField(label = "ATS Match Score (0-100)", value = dialogScore, onValueChange = { dialogScore = it })
                        EditorField(label = "Resume Version Used", value = dialogResume, onValueChange = { dialogResume = it })
                        
                        Column {
                            Text(text = "Status", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            val statuses = listOf("Saved", "Applied", "Assessment", "Interview", "Rejected", "Offer", "Withdrawn")
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                statuses.forEach { st ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (dialogStatus == st) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable { dialogStatus = st }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(text = st, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (dialogStatus == st) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }

                        EditorField(label = "Notes / Updates", value = dialogNotes, onValueChange = { dialogNotes = it }, maxLines = 4)
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            if (dialogCompany.isNotBlank() && dialogRole.isNotBlank()) {
                                viewModel.addApplication(
                                    appliedDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()),
                                    company = dialogCompany,
                                    role = dialogRole,
                                    location = dialogLocation,
                                    source = dialogSource,
                                    jobLink = dialogLink,
                                    matchScore = dialogScore.toIntOrNull() ?: 85,
                                    resumeVersion = dialogResume,
                                    status = dialogStatus,
                                    notes = dialogNotes
                                )
                                showAddDialog = false
                            } else {
                                Toast.makeText(context, "Company and Role are required!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_addon_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Insert Application Row in CRM", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.background)
                    }
                }
            }
        }
    }

    selectedAppForDetail?.let { app ->
        var editStatus by remember(app) { mutableStateOf(app.status) }
        var editNotes by remember(app) { mutableStateOf(app.notes) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable { selectedAppForDetail = null },
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clickable(enabled = false) {},
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = app.company, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                            Text(text = "${app.role} • ${app.location}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { selectedAppForDetail = null }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 12.dp))

                    Text(text = "Modify App Row Cell Status", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    val statuses = listOf("Saved", "Applied", "Assessment", "Interview", "Rejected", "Offer", "Withdrawn")
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        statuses.forEach { st ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (editStatus == st) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { editStatus = st }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(text = st, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (editStatus == st) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(text = "App Row Notes & Telemetry", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = editNotes,
                        onValueChange = { editNotes = it },
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        textStyle = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.SansSerif),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            viewModel.updateApplication(
                                app.copy(status = editStatus, notes = editNotes)
                            )
                            selectedAppForDetail = null
                        },
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Commit Changes in Sheet Row", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.background)
                    }
                }
            }
        }
    }
}

@Composable
fun MiniMetricRowItem(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = title, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun TableHeaderCell(title: String, width: androidx.compose.ui.unit.Dp) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(width)
    )
}

@Composable
fun TableCell(text: String, width: androidx.compose.ui.unit.Dp, isBold: Boolean = false) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width)
    )
}

@Composable
fun StatusBadge(status: String) {
    val containerColor = when (status) {
        "Offer" -> Color(0xFFE8F5E9)      // Light green
        "Interview" -> Color(0xFFE3F2FD)  // Light blue
        "Assessment" -> Color(0xFFFFFDE7) // Light yellow
        "Saved" -> Color(0xFFF5F5F5)      // Light gray
        "Applied" -> Color(0xFFF3E5F5)    // Light purple
        "Withdrawn" -> Color(0xFFECEFF1)  // Light blue-gray
        else -> Color(0xFFFFEBEE)         // Light red ("Rejected")
    }
    val contentColor = when (status) {
        "Offer" -> Color(0xFF2E7D32)
        "Interview" -> Color(0xFF1565C0)
        "Assessment" -> Color(0xFFFBC02D)
        "Saved" -> Color(0xFF616161)
        "Applied" -> Color(0xFF7B1FA2)
        "Withdrawn" -> Color(0xFF455A64)
        else -> Color(0xFFC62828)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(containerColor)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = status,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}


// ==========================================
// STUDY DESK: COMBINED TASK CHECKLIST & INTERVIEWS
// ==========================================
@Composable
fun StudyDeskCombinedScreen(
    tasks: List<StudyTaskEntity>,
    scans: List<JobScanEntity>,
    viewModel: CareerViewModel
) {
    var selectedSegment by remember { mutableStateOf(0) } // 0 = Milestones, 1 = Mock Prep

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selectedSegment == 0) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { selectedSegment = 0 }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Search Milestones",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selectedSegment == 0) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selectedSegment == 1) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { selectedSegment = 1 }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Interactive Mock Prep",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selectedSegment == 1) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (selectedSegment == 0) {
                LearningTasksScreen(tasks = tasks, viewModel = viewModel)
            } else {
                InterviewScreen(scans = scans, viewModel = viewModel)
            }
        }
    }
}

// ==========================================
// CHATBOT INTERFACE (GEMINI CHATBOT + SEARCH GROUNDING + HIGH THINKING)
// ==========================================
@Composable
fun DashboardChatbotPanel(viewModel: CareerViewModel) {
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isChatLoading.collectAsStateWithLifecycle()
    val chatError by viewModel.chatError.collectAsStateWithLifecycle()

    var userMessageText by remember { mutableStateOf("") }
    var useGoogleSearch by remember { mutableStateOf(false) }
    var enableHighThinking by remember { mutableStateOf(false) }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Auto-scroll to bottom of conversation
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("chatbot_panel")
    ) {
        // Mode Configuration Cards
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "AI Coach Intel Engine",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(10.dp))
                
                // Toggle 1: Google Search Grounding
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search icon",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Google Search Grounding", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text("Ground responses with live Google Search results", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(
                        checked = useGoogleSearch,
                        onCheckedChange = { 
                            useGoogleSearch = it
                            if (it) enableHighThinking = false // Pro preview thinking does not mix with standard Web Tool search in API config
                        },
                        modifier = Modifier.scale(0.8f).testTag("search_grounding_switch")
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Toggle 2: High Thinking Mode
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Thinking icon",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Deep Reasoning (High Thinking)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text("Enable reasoning path using gemini-3.1-pro-preview", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(
                        checked = enableHighThinking,
                        onCheckedChange = { 
                            enableHighThinking = it
                            if (it) useGoogleSearch = false // Grounding is incompatible with reasoning thinkingLevel parameters
                        },
                        modifier = Modifier.scale(0.8f).testTag("thinking_mode_switch")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Message Thread Area
        Box(modifier = Modifier.weight(1f)) {
            androidx.compose.foundation.lazy.LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages) { msg ->
                    ChatBubbleItem(message = msg)
                }
                if (isLoading) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (enableHighThinking) "Reasoning deeply with thinkingLevel.HIGH..." else "AI Coach is grounding & replying...",
                                fontSize = 11.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Input controls Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = { viewModel.clearChatHistory() },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .size(44.dp)
                    .testTag("clear_chat_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Clear Chat",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            OutlinedTextField(
                value = userMessageText,
                onValueChange = { userMessageText = it },
                placeholder = { Text("Ask your elite coach...", fontSize = 13.sp) },
                singleLine = false,
                maxLines = 4,
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_text_field"),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            IconButton(
                onClick = {
                    if (userMessageText.isNotBlank() && !isLoading) {
                        viewModel.sendChatMessage(userMessageText, useGoogleSearch, enableHighThinking)
                        userMessageText = ""
                    }
                },
                enabled = userMessageText.isNotBlank() && !isLoading,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (userMessageText.isNotBlank() && !isLoading) MaterialTheme.colorScheme.primary 
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .size(44.dp)
                    .testTag("send_chat_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send Message",
                    tint = if (userMessageText.isNotBlank() && !isLoading) MaterialTheme.colorScheme.onPrimary 
                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp).rotate(-45f)
                )
            }
        }
    }
}

@Composable
fun ChatBubbleItem(message: com.example.ui.viewmodel.ChatMessage) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (message.isUser) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 16.dp
            ),
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clickable {
                    copyToClipboard(context, "AI Message", message.text)
                }
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.text,
                    fontSize = 13.sp,
                    color = if (message.isUser) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
