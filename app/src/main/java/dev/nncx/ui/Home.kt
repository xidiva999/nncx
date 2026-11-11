package dev.nncx.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.nncx.BuildConfig
import dev.nncx.apps.AppEntry
import dev.nncx.apps.AppIndex
import dev.nncx.devtools.DevToggles
import dev.nncx.devtools.Memory
import dev.nncx.folders.AppFacts
import dev.nncx.folders.FolderConfig
import dev.nncx.folders.FolderEngine
import dev.nncx.folders.FolderView
import dev.nncx.folders.Folders
import dev.nncx.index.Kind
import dev.nncx.index.Rank
import dev.nncx.index.Signatures
import dev.nncx.index.Trend
import dev.nncx.index.UsageAccess
import dev.nncx.index.UsageHarvester
import dev.nncx.index.UseLog
import dev.nncx.lock.VitalsService
import dev.nncx.settings.Settings
import dev.nncx.theme.LocalNncx
import dev.nncx.theme.NncxTheme
import kotlinx.coroutines.Dispatchers
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val DragRange = 220.dp
private const val EVERYTHING = "everything"

/** Gesture gutter: the back-swipe strip the rail must not sit in. */
private val EdgeInset = 24.dp

@Composable
fun NncxRoot() {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val rangePx = with(density) { DragRange.toPx() }
    val posture = rememberPosture()

    val x = remember { Animatable(0f) }
    var committed by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    // What the band says instead of the mark, briefly, after a pull-down.
    var bandNote by remember { mutableStateOf<String?>(null) }

    fun animateTo(target: Float) {
        scope.launch {
            x.animateTo(target, spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow))
        }
    }

    /**
     * Swipe up to go in, swipe up again to come out — one gesture, both ways.
     *
     * The drag always travels AWAY from where you are, so while x-ray is on,
     * pulling up scrubs back toward the phone. That also settles what a
     * downward exit never could: there is no room to drag down from a band
     * already sitting on the bottom edge.
     */
    fun progress(netPx: Float) {
        val p = (netPx / rangePx).coerceIn(0f, 1f)
        scope.launch { x.snapTo(if (committed) 1f - p else p) }
    }

    val upFlick = 0.25f

    fun settle(netPx: Float) {
        val p = netPx / rangePx

        if (p > upFlick) {
            committed = !committed
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        animateTo(if (committed) 1f else 0f)
    }

    // The status and nav bar icons are drawn by the system, so they have to be
    // told which way to go. On a paper-white page they must be dark; on
    // near-black, light. Driven by the same float as everything else.
    val view = LocalView.current
    val lightBars = x.value < 0.5f
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        // Android paints its own translucent scrim behind the system bars for
        // contrast. Harmless over a wallpaper; over a flat band it just makes
        // the strip a shade darker than the colour it sits on.
        window.isNavigationBarContrastEnforced = false
        window.isStatusBarContrastEnforced = false
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = lightBars
            // The nav bar sits on the x band, which is plum or black — never
            // paper. So its icons stay light whichever mode we are in.
            isAppearanceLightNavigationBars = false
        }
    }

    // The cover is a 23:9 sliver — a single column is the only thing that fits,
    // and landscape on it is nobody's idea of a home screen. Open, the canvas
    // is square and the three-column layout works either way up, so the lock
    // comes off.
    LaunchedEffect(posture.posture) {
        val activity = view.context as? Activity ?: return@LaunchedEffect
        activity.requestedOrientation = when (posture.posture) {
            Posture.COVER -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    NncxTheme(xray = x.value) {
        Home(
            posture = posture,
            bandNote = bandNote,
            onClearRam = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                scope.launch {
                    val freed = Memory.free(ctx)
                    bandNote = if (freed.gainedMb > 0) "freed ${freed.gainedMb} MB"
                    else "nothing to reclaim"
                    delay(2200)
                    bandNote = null
                }
            },
            onProgress = { net -> progress(net) },
            onDragEnd = { net -> settle(net) },
        )
    }
}

@Composable
private fun Home(
    posture: PostureInfo,
    bandNote: String?,
    onClearRam: () -> Unit,
    onProgress: (Float) -> Unit,
    onDragEnd: (Float) -> Unit,
) {
    val t = LocalNncx.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var query by remember { mutableStateOf("") }
    var order by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var facts by remember { mutableStateOf<List<AppFacts>>(emptyList()) }
    var folderViews by remember { mutableStateOf<List<FolderView>>(emptyList()) }
    var granted by remember { mutableStateOf(true) }
    var rows by remember { mutableStateOf(0) }
    var trendReady by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    var loaded by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(setOf("daily")) }
    var showSettings by remember { mutableStateOf(false) }
    var showDialer by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf("daily") }
    val settings = Settings.get(context)
    val secureGranted = remember(reloadKey) { DevToggles.granted(context) }

    // Keyed rows anchor the viewport, so the first complete draw gets pinned to
    // the top explicitly. One shot — after this the scroll belongs to you.
    val listState = rememberLazyListState()
    LaunchedEffect(loaded) { if (loaded) listState.scrollToItem(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                reloadKey++
                // No boot receiver needed: the launcher is resumed constantly,
                // and starting a foreground service from it is always allowed.
                VitalsService.sync(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(reloadKey) {
        val harvest = UsageHarvester.harvest(context)
        val entries = AppIndex.load(context)
        val log = UseLog.get(context)
        val fresh = withContext(Dispatchers.IO) { log.ranks(Kind.APP, harvest.today) }
        val oldest = withContext(Dispatchers.IO) { log.oldestDay(Kind.APP) }

        rows = withContext(Dispatchers.IO) { log.totalRows() }
        granted = UsageAccess.granted(context)

        val span = if (oldest == null) 0L else harvest.today - oldest + 1
        trendReady = span >= UseLog.WINDOW_DAYS

        val seated = if (order.isEmpty()) {
            entries.sortedWith(
                compareByDescending<AppEntry> { fresh[it.packageName]?.total14 ?: 0 }
                    .thenBy { it.sortKey }
            )
        } else {
            val seat = order.withIndex().associate { (i, e) -> e.packageName to i }
            entries.sortedWith(
                compareBy<AppEntry> { seat[it.packageName] ?: Int.MAX_VALUE }.thenBy { it.sortKey }
            )
        }
        order = seated

        val now = System.currentTimeMillis()
        val lastUsed = UsageHarvester.lastUsed(context)
        val built = withContext(Dispatchers.IO) {
            seated.map { e ->
                AppFacts(
                    entry = e,
                    rank = fresh[e.packageName] ?: Rank.NONE,
                    signedLikeMe = Signatures.signedLikeMe(context, e.packageName),
                    installedDaysAgo = if (e.firstInstall <= 0) -1
                    else (now - e.firstInstall) / 86_400_000L,
                    logSpanDays = span,
                    lastUsedMs = lastUsed[e.packageName] ?: 0L,
                )
            }
        }
        facts = built

        val cfg = FolderConfig(
            workPrefixes = settings.workPrefixes,
            logSpanDays = span,
            nowMs = now,
        )
        folderViews = withContext(Dispatchers.IO) {
            FolderEngine.evaluate(log, Folders.all(cfg), built)
        }
        loaded = true
    }

    val hits = remember(facts, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) emptyList()
        else facts.filter { it.entry.sortKey.contains(q) || it.packageName.contains(q) }
    }

    // The dialer occupies the first slot, so the dock never overflows.
    val dock = dockItems(facts, settings.pins, settings.dockSize - 1)
    // How many leading slots are yours by intent rather than by ranking.
    val pinnedCount = dock.count { it.packageName in settings.pins }
    val launch: (AppFacts) -> Unit = { AppIndex.launch(context, it.entry) }

    Box(Modifier.fillMaxSize().background(t.scrim)) {
        BlueprintGrid()

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = t.gutter)
        ) {
            Spacer(Modifier.height(t.gutter))
            SearchField(query = query, onQuery = { query = it })

            if (!granted) {
                Spacer(Modifier.height(8.dp))
                UsageBanner { runCatching { context.startActivity(UsageAccess.settingsIntent(context)) } }
            } else if (t.xray > 0.05f) {
                Spacer(Modifier.height(8.dp))
                IndexReadout(rows, facts.count { it.rank.total14 > 0 }, posture)
            }

            Spacer(Modifier.height(t.gutter))

            when {
                !loaded -> Unit

                query.isNotBlank() -> SearchResults(
                    hits, trendReady,
                    endPad = if (posture.isWide || settings.coverRail) 96.dp else 0.dp,
                    onLaunch = launch,
                )

                // A square canvas is the one shape that finally earns two panes,
                // and folder-first is exactly the content for them.
                posture.isWide -> WidePanes(
                    posture = posture,
                    views = folderViews,
                    all = facts,
                    selected = selected,
                    trendReady = trendReady,
                    showTools = t.isXray,
                    toolsGranted = secureGranted,
                    onSelect = { selected = it },
                    onOpenSettings = { showSettings = true },
                    onLaunch = launch,
                )

                else -> NarrowColumn(
                    // With the rail out, the list clears the side instead of
                    // the bottom.
                    contentPadding = if (settings.coverRail)
                        PaddingValues(end = 88.dp, bottom = 96.dp)
                    else PaddingValues(bottom = 156.dp),
                    listState = listState,
                    views = folderViews,
                    all = facts,
                    expanded = expanded,
                    trendReady = trendReady,
                    showTools = t.isXray,
                    toolsGranted = secureGranted,
                    onOpenSettings = { showSettings = true },
                    onToggleFolder = { id ->
                        expanded = if (id in expanded) expanded - id else expanded + id
                    },
                    onLaunch = launch,
                )
            }
        }

        // The dock follows your grip: bottom when the phone is closed and your
        // thumb is low, the side edge when it is open and your thumbs rest
        // mid-bezel. Nobody's thumb is at the bottom of an open fold.
        //
        // X-ray is pulled up from the bottom rather than pressed. The whole
        // strip below the list is the grab zone, so the gesture never fights
        // the list's own scrolling, and the handle is the one accent-coloured
        // thing on an otherwise plain screen.
        // Net travel for the whole gesture, so a short flick down still reads
        // as "put it back" even with no room left to drag.
        val net = remember { mutableFloatStateOf(0f) }
        // A gesture can deliver BOTH onDragEnd and onDragCancel. That was
        // harmless while settling was decided by position, but a toggle called
        // twice cancels itself — so each gesture gets to settle exactly once.
        val settled = remember { mutableStateOf(false) }
        val finish = {
            if (!settled.value) {
                settled.value = true
                onDragEnd(net.floatValue)
            }
        }
        val scrub = Modifier.pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragStart = { net.floatValue = 0f; settled.value = false },
                onDragEnd = { finish() },
                onDragCancel = { finish() },
            ) { _, dy ->
                net.floatValue += -dy       // up = toward the other mode
                onProgress(net.floatValue)
            }
        }

        // Bottom furniture, per the sketch: the dock sits above a band of its
        // own, and the x lives centred in that band. When the fold opens, the
        // dock leaves for the side rail but the band stays — the swipe still
        // comes up from the bottom edge either way.
        // Open, the dock is always a rail — nobody's thumb is at the bottom of
        // an open fold. Folded, it is your choice: the bottom is where the
        // thumb naturally sits, but the rail reads better.
        val rail = posture.isWide || settings.coverRail

        if (rail) {
            Dock(
                items = dock,
                vertical = true,
                pinned = pinnedCount,
                onDialer = { showDialer = true },
                modifier = Modifier
                    .align(if (settings.dockOnRight) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = EdgeInset),
                onLaunch = launch,
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // Solid, not a fade. The hairline is what separates it.
                .background(t.scrim)
                .then(scrub),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!rail) {
                Spacer(Modifier.height(24.dp))
                Dock(
                    items = dock, vertical = false, pinned = pinnedCount,
                    onLaunch = launch, onDialer = { showDialer = true },
                )
                Spacer(Modifier.height(14.dp))
            }
            // Only needed once the band goes black; the purple separates itself.
            Box(
                Modifier.fillMaxWidth().height(1.dp)
                    .background(lerp(Color.Transparent, t.hairline, t.xray))
            )
            XBand(bandNote, onClearRam)
        }

        if (showDialer) {
            BackHandler { showDialer = false }
            DialerScreen { showDialer = false }
        }

        if (showSettings) {
            BackHandler { showSettings = false }
            SettingsScreen(all = facts) { showSettings = false }
        } else if (!showDialer) {
            // A home screen has nowhere to go back to. Without this, back drops
            // you into whatever launcher NNCx replaced.
            BackHandler(enabled = true) { }
        }
    }
}

/**
 * The x, centred in a band of its own along the bottom edge.
 *
 * Not a button: the whole band is the swipe surface and the mark only says
 * where. It is also the single accent-coloured thing in normal mode — a hint
 * of what is underneath, in the exact colour about to flood in.
 */
@Composable
private fun XBand(note: String?, onClearRam: () -> Unit) {
    val t = LocalNncx.current
    // Inverted across the fold: white on the builder's purple while it is a
    // phone, that purple on black once it is an instrument.
    val ground = lerp(t.accentDeep, t.scrim, t.xray)
    val mark = lerp(Color.White, t.accentFull, t.xray)

    // Sideways on the mark clears memory. Horizontal, so it never argues with
    // the vertical drag that owns x-ray — and the mark travels under your
    // thumb, so the gesture says what it is doing before it does it.
    val scope = rememberCoroutineScope()
    val dx = remember { Animatable(0f) }
    val trigger = with(LocalDensity.current) { 42.dp.toPx() }
    val limit = with(LocalDensity.current) { 88.dp.toPx() }

    Box(
        Modifier
            .fillMaxWidth()
            .background(ground)
            .navigationBarsPadding()
            .height(52.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (note != null) {
            Text(note, mark, 14.sp, mono = true, weight = FontWeight.Medium)
        } else {
            Box(
                Modifier
                    .width(140.dp)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val fired = abs(dx.value) > trigger
                                scope.launch { dx.animateTo(0f, spring(dampingRatio = 0.6f)) }
                                if (fired) onClearRam()
                            },
                            onDragCancel = { scope.launch { dx.animateTo(0f, spring()) } },
                        ) { _, amount ->
                            scope.launch { dx.snapTo((dx.value + amount).coerceIn(-limit, limit)) }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                XMark(
                    mark,
                    modifier = Modifier.offset { IntOffset(dx.value.roundToInt(), 0) },
                )
            }
        }
    }
}

/**
 * The NNCx mark.
 *
 * Not a typed "✕" and not a plain cross: one stroke runs the full diagonal,
 * the other breaks at the crossing and passes behind it. The break is what
 * gives it a hand — it reads as a drawn mark at icon size instead of a close
 * button, and it suits a launcher whose whole idea is seeing through things.
 */
@Composable
private fun XMark(
    color: Color,
    size: Dp = 20.dp,
    stroke: Dp = 2.5.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(size)) {
        val w = stroke.toPx()
        val i = w / 2f
        val e = this.size.width - i
        val c = this.size.width / 2f
        val d = w * 1.25f          // half the break, along the diagonal

        // over
        drawLine(color, Offset(i, i), Offset(e, e), strokeWidth = w, cap = StrokeCap.Round)
        // under, broken at the crossing
        drawLine(color, Offset(e, i), Offset(c + d, c - d), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(color, Offset(c - d, c + d), Offset(i, e), strokeWidth = w, cap = StrokeCap.Round)
    }
}

@Composable
private fun NarrowColumn(
    listState: LazyListState,
    views: List<FolderView>,
    all: List<AppFacts>,
    expanded: Set<String>,
    trendReady: Boolean,
    showTools: Boolean,
    toolsGranted: Boolean,
    onOpenSettings: () -> Unit,
    onToggleFolder: (String) -> Unit,
    onLaunch: (AppFacts) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    contentPadding: PaddingValues = PaddingValues(bottom = 156.dp),
) {
    LazyColumn(
        modifier,
        state = listState,
        verticalArrangement = Arrangement.spacedBy(0.dp),
        contentPadding = contentPadding,
    ) {
        views.forEach { view ->
            val id = view.folder.id
            item(key = "h-$id") {
                FolderHeader(view.folder.name, view.folder.rule, view.size, id in expanded) {
                    onToggleFolder(id)
                }
            }
            if (id in expanded) {
                items(view.members, key = { "$id-" + it.packageName }) {
                    AppRow(it, trendReady, indent = true) { onLaunch(it) }
                }
            }
        }
        item(key = "h-$EVERYTHING") {
            FolderHeader(
                "Everything", "no rule · all launchable targets",
                all.size, EVERYTHING in expanded,
            ) { onToggleFolder(EVERYTHING) }
        }
        if (EVERYTHING in expanded) {
            items(all, key = { "all-" + it.packageName }) {
                AppRow(it, trendReady, indent = true) { onLaunch(it) }
            }
        }

        // The instruments sit at the end, under everything they operate on,
        // and only exist once the skin is off.
        if (showTools) {
            item(key = "h-tools") {
                ToolsHeader(
                    count = DevToggles.all.size + 2,
                    open = "tools" in expanded,
                    granted = toolsGranted,
                ) { onToggleFolder("tools") }
            }
            if ("tools" in expanded) {
                if (!toolsGranted) item(key = "t-grant") { GrantRow() }
                items(DevToggles.all, key = { "t-" + it.id }) { ToggleRow(it, toolsGranted) }
                item(key = "t-memory") { MemoryRow() }
                item(key = "t-deeplink") { DeepLinkRow() }
            }
            item(key = "h-settings") { SettingsRow(onOpenSettings) }
        }
    }
}

/** Settings live under the tools, not behind a gesture nobody can see. */
@Composable
private fun SettingsRow(onOpen: () -> Unit) {
    val t = LocalNncx.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = t.rowHeight)
            .clip(RoundedCornerShape(t.corner))
            .pointerInput(Unit) { detectTapGestures { onOpen() } }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("\u203A", t.onSurfaceDim, 15.sp, mono = true)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Settings", t.onSurface, 17.sp, mono = false, weight = FontWeight.Medium)
            Text("accent · dock · pins · work", t.onSurfaceDim, 11.sp, mono = true)
        }
    }
}

@Composable
private fun WidePanes(
    posture: PostureInfo,
    views: List<FolderView>,
    all: List<AppFacts>,
    selected: String,
    trendReady: Boolean,
    showTools: Boolean,
    toolsGranted: Boolean,
    onSelect: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onLaunch: (AppFacts) -> Unit,
) {
    val t = LocalNncx.current

    // Three columns, split on the physical screen: the crease divides the
    // groups from the apps they contain, and the clock takes the outer half of
    // the left side — the part a phone never had room for.
    val leftHalf = posture.hingeStartDp ?: (posture.widthDp * 0.52f).dp
    val clockWidth = leftHalf * 0.52f
    val groupsWidth = leftHalf - clockWidth
    val crease = if (posture.hingeSizeDp > 0.dp) posture.hingeSizeDp else 1.dp

    val members = remember(views, all, selected) {
        if (selected == EVERYTHING) all
        else views.firstOrNull { it.folder.id == selected }?.members ?: emptyList()
    }

    Row(Modifier.fillMaxSize()) {
        AmbientPanel(Modifier.width(clockWidth))

        // centre: the groups
        LazyColumn(
            Modifier.width(groupsWidth),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 104.dp),
        ) {
            items(views, key = { "g-" + it.folder.id }) { view ->
                FolderHeader(
                    name = view.folder.name,
                    rule = view.folder.rule,
                    count = view.size,
                    open = false,
                    asSelector = true,
                    selected = view.folder.id == selected,
                ) { onSelect(view.folder.id) }
            }
            item(key = "g-$EVERYTHING") {
                FolderHeader(
                    "Everything", "no rule · all launchable targets",
                    all.size, open = false, asSelector = true,
                    selected = selected == EVERYTHING,
                ) { onSelect(EVERYTHING) }
            }
            if (showTools) {
                item(key = "g-tools") {
                    ToolsHeader(
                        count = DevToggles.all.size + 2,
                        open = false,
                        granted = toolsGranted,
                    ) { onSelect("tools") }
                }
                item(key = "g-settings") { SettingsRow(onOpenSettings) }
            }
        }

        Box(Modifier.width(crease).fillMaxHeight().background(t.hairline))

        // right: whatever the centre column is pointing at
        LazyColumn(
            Modifier.weight(1f),
            // Clear the side rail and the x band.
            contentPadding = PaddingValues(start = 12.dp, end = 96.dp, bottom = 104.dp),
        ) {
            if (selected == "tools") {
                if (!toolsGranted) item(key = "w-grant") { GrantRow() }
                items(DevToggles.all, key = { "w-" + it.id }) { ToggleRow(it, toolsGranted) }
                item(key = "w-memory") { MemoryRow() }
                item(key = "w-deeplink") { DeepLinkRow() }
            } else {
                items(members, key = { "m-" + it.packageName }) {
                    AppRow(it, trendReady, indent = false) { onLaunch(it) }
                }
            }
        }
    }
}

@Composable
private fun SearchResults(
    hits: List<AppFacts>,
    trendReady: Boolean,
    endPad: Dp,
    onLaunch: (AppFacts) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 156.dp, end = endPad),
    ) {
        items(hits, key = { "hit-" + it.packageName }) {
            AppRow(it, trendReady, indent = false) { onLaunch(it) }
        }
    }
}

// ---- pieces ---------------------------------------------------------------

@Composable
private fun FolderHeader(
    name: String,
    rule: String,
    count: Int,
    open: Boolean,
    asSelector: Boolean = false,
    selected: Boolean = false,
    onToggle: () -> Unit,
) {
    val t = LocalNncx.current
    val dead = count == 0

    // As a selector the row is a label in a column of labels, so it wants to
    // sit close to its neighbours. As a section header in the single-column
    // list it is separating content, and needs the air.
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (asSelector) 44.dp else t.rowHeight)
            .clip(RoundedCornerShape(t.corner))
            .pointerInput(name) { detectTapGestures { if (!dead) onToggle() } }
            .padding(horizontal = 12.dp, vertical = if (asSelector) 6.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A mark rather than a shape: a filled bar on the leading edge and the
        // label in the accent. Nothing to fill, nothing to round, and it holds
        // up on both poles because it is the colour doing the work.
        if (asSelector) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (selected) t.accentFull else Color.Transparent)
            )
            Spacer(Modifier.width(14.dp))
        }
        if (!asSelector) {
            Text(
                if (open) "⌄" else "›",
                when {
                    dead -> t.onSurfaceDim.copy(alpha = 0.4f)
                    open -> t.accentFull        // accent marks what is open
                    else -> t.onSurfaceDim
                },
                15.sp, mono = true,
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                name,
                when {
                    dead -> t.onSurfaceDim
                    selected -> t.accentFull
                    else -> t.onSurface
                },
                17.sp, mono = false, weight = FontWeight.Medium,
            )
            if (t.xray > 0.05f) {
                Text(rule, t.onSurfaceDim.copy(alpha = t.xray * 0.9f), 11.sp, mono = true)
            }
        }
        Text(
            "$count",
            if ((open || selected) && !dead) t.accentFull else t.onSurfaceDim,
            13.sp, mono = true,
        )
    }
}

@Composable
private fun BlueprintGrid() {
    val t = LocalNncx.current
    if (t.gridAlpha <= 0.002f) return
    val step = with(LocalDensity.current) { t.gridStep.toPx() }

    Canvas(Modifier.fillMaxSize()) {
        val c = t.onSurface.copy(alpha = t.gridAlpha)
        var px = 0f
        while (px < size.width) {
            drawLine(c, Offset(px, 0f), Offset(px, size.height), strokeWidth = 1f); px += step
        }
        var py = 0f
        while (py < size.height) {
            drawLine(c, Offset(0f, py), Offset(size.width, py), strokeWidth = 1f); py += step
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    val t = LocalNncx.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(t.corner))
            .background(t.surface)
            .padding(start = 16.dp, end = 6.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(
                    color = t.onSurface,
                    fontSize = 16.sp,
                    fontFamily = if (t.isXray) t.dataFamily else t.uiFamily,
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(t.accentFull),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(if (t.isXray) "grep" else "Search", t.onSurfaceDim, 16.sp, t.isXray)
                    }
                    inner()
                }
            )
        }

        // Nothing to clear is nothing to show — the button only exists once
        // there is something behind it.
        if (query.isNotEmpty()) {
            Box(
                Modifier
                    .size(34.dp)
                    .pointerInput("clear") { detectTapGestures { onQuery("") } },
                contentAlignment = Alignment.Center,
            ) { Text("\u2715", t.onSurfaceDim, 14.sp, mono = true) }
        }
    }
}

@Composable
private fun IndexReadout(rows: Int, ranked: Int, posture: PostureInfo) {
    val t = LocalNncx.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Text(
            "use_log · $rows rows · $ranked ranked · " +
                "${posture.posture.name.lowercase()} ${posture.widthDp}×${posture.heightDp}",
            t.onSurfaceDim.copy(alpha = t.xray), 11.sp, mono = true,
        )
    }
}

@Composable
private fun UsageBanner(onClick: () -> Unit) {
    val t = LocalNncx.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(t.corner))
            .background(t.surface)
            .pointerInput(Unit) { detectTapGestures { onClick() } }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text("Usage access is off — order is alphabetical", t.onSurface, 14.sp, mono = false)
        Spacer(Modifier.height(4.dp))
        Text(
            if (t.isXray) UsageAccess.ADB_GRANT else "Tap to turn it on",
            t.onSurfaceDim, 11.sp, mono = t.isXray,
        )
    }
}

@Composable
private fun AppRow(
    facts: AppFacts,
    trendReady: Boolean,
    indent: Boolean,
    onClick: () -> Unit,
) {
    val t = LocalNncx.current
    val entry = facts.entry

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = t.rowHeight)
            .clip(RoundedCornerShape(t.corner))
            .pointerInput(entry.packageName) { detectTapGestures { onClick() } }
            .padding(
                start = if (indent) 28.dp else 10.dp,
                end = 12.dp, top = 8.dp, bottom = 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconView(entry, 34.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.label, t.onSurface, 17.sp, mono = false)
            if (t.xray > 0.05f) {
                Text(entry.packageName, t.onSurfaceDim.copy(alpha = t.xray), 11.sp, mono = true)
            }
        }
        if (t.xray > 0.05f) {
            if (facts.signedLikeMe) {
                Text("MINE", t.accent, 10.sp, mono = true)
                Spacer(Modifier.width(10.dp))
            }
            Text(
                facts.rank.readout(trendReady),
                t.onSurfaceDim.copy(alpha = t.xray), 11.sp, mono = true,
            )
        }
    }
}

private fun Rank.readout(trendReady: Boolean): String {
    if (total14 == 0) return "—"
    val arrow = if (!trendReady) "" else when (trend) {
        Trend.RISING -> " ↑"
        Trend.FALLING -> " ↓"
        Trend.FLAT -> ""
    }
    return "$total14/${UseLog.WINDOW_DAYS}d$arrow"
}

@Composable
private fun Text(
    value: String,
    color: Color,
    size: androidx.compose.ui.unit.TextUnit,
    mono: Boolean,
    weight: FontWeight = FontWeight.Normal,
) {
    val t = LocalNncx.current
    androidx.compose.foundation.text.BasicText(
        text = value,
        style = TextStyle(
            color = color,
            fontSize = size,
            fontFamily = if (mono) t.dataFamily else t.uiFamily,
            fontWeight = weight,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
