package dev.modernftb.questui.forge.client;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.LayoutMeasureCache;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.FilterRenderer;
import com.sighs.apricityui.render.FontDrawer;
import com.sighs.apricityui.render.ForegroundRenderNodeProvider;
import com.sighs.apricityui.render.Mask;
import com.sighs.apricityui.render.Operation;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.render.RectFrameCache;
import com.sighs.apricityui.render.RenderNode;
import com.sighs.apricityui.render.TransformFrameCache;
import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.style.Cursor;
import com.sighs.apricityui.style.Filter;
import com.sighs.apricityui.style.Style;
import com.sighs.apricityui.style.StyleFrameCache;
import com.sighs.apricityui.style.Transition;
import dev.ftb.mods.ftblibrary.icon.Drawable;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestButton;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestLinkButton;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestPanel;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestPositionableButton;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.ftb.mods.ftbquests.client.gui.quests.TaskButton;
import dev.ftb.mods.ftbquests.net.ClaimRewardMessage;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.ChapterGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestLink;
import dev.ftb.mods.ftbquests.quest.QuestObject;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.modernftb.questui.common.model.ChapterGroupSnapshot;
import dev.modernftb.questui.common.model.ChapterSnapshot;
import dev.modernftb.questui.common.model.QuestBookSnapshot;
import dev.modernftb.questui.common.model.QuestBookState;
import dev.modernftb.questui.common.model.QuestMapNodeSnapshot;
import dev.modernftb.questui.common.model.QuestSnapshot;
import dev.modernftb.questui.common.model.RewardSnapshot;
import dev.modernftb.questui.common.model.TaskSnapshot;
import dev.modernftb.questui.common.search.QuestBookSearch;
import dev.modernftb.questui.common.search.QuestSearchIndex;
import dev.modernftb.questui.common.search.QuestSearchOptions;
import dev.modernftb.questui.common.search.QuestSearchResult;
import dev.modernftb.questui.forge.QuestUiConfig;
import dev.modernftb.questui.forge.QuestUiMod;
import dev.modernftb.questui.forge.client.QuestBookSnapshotFactory;
import dev.modernftb.questui.forge.client.QuestUiClientEvents;
import dev.modernftb.questui.forge.client.QuestUiResources;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.joml.Matrix4f;

final class QuestUiScreenController {
    private static final String PAGE_PATH = "questui/questbook-v8.html";
    private static final int DOCUMENT_WAIT_TICKS = 40;
    private static final int QUEST_REFRESH_TICKS = 10;
    private static final double FTB_MAP_PADDING_X = 40.0;
    private static final double FTB_MAP_PADDING_Y = 30.0;
    private static final boolean DETAILED_MAP_DIAGNOSTICS = Boolean.getBoolean("questui.debug.map");
    private static final double MAP_DEPENDENCY_LINE_Z = 100.0;
    private static final double MAP_QUEST_NODE_Z = 200.0;
    private static final double MAP_NODE_LABEL_Z = 600.0;
    private static final double SCREEN_FADE_LAYER_Z = 1000.0;
    private static Screen vanillaScreen;
    private static Screen apricityScreen;
    private static QuestScreen sourceQuestScreen;
    private static QuestBookState state;
    private static Document document;
    private static final Set<Long> collapsedGroups;
    private static boolean waitingForDocument;
    private static boolean usingVanillaEditor;
    private static boolean staticEventsBound;
    private static boolean searchSettingsOpen;
    private static boolean chapterSidebarCollapsed;
    /** Flush the chapter toggle hit-test cache after the next committed screen render. */
    private static boolean chapterHitTestRefreshPending;
    /** Re-evaluate the system cursor after chapter geometry changes, even if the mouse is stationary. */
    private static boolean chapterCursorRefreshPending;
    private static boolean chapterToggleCursorOverrideActive;
    private static Document chapterHitTestDebugListenerDocument;
    private static long chapterHitTestDebugSequence;
    private static long activeChapterHitTestDebugSequence;
    private static boolean chapterToggleMousePressCaptured;
    private static Document chapterToggleMousePressDocument;
    private static boolean searchUpdatePending;
    private static String pendingSearchQuery = "";
    private static String lastObservedSearchValue = "";
    private static long pendingSearchUpdatedNanos;
    private static final long SEARCH_DEBOUNCE_NANOS = 180_000_000L;
    private static final long SEARCH_TIME_BUDGET_NANOS = 1_000_000_000L;
    private static final ExecutorService SEARCH_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "QuestUI search");
        thread.setDaemon(true);
        return thread;
    });
    private static Future<?> activeSearchTask;
    private static long searchRequestGeneration;
    private static volatile QuestSearchIndex searchIndex;
    /**
     * True while the page geometry is still settling, so {@link #tick()} keeps retrying the map
     * layout instead of waiting for an input event to force a re-layout.
     */
    private static boolean mapLayoutUnsettled;
    private static boolean mapUnsettledWarningLogged;
    private static int mapUnsettledRetries;
    private static String chapterListSignature;
    private static int waitTicks;
    private static int refreshTicks;
    private static long renderedGeneration;
    private static long screenFadeStartNanos;
    private static boolean screenFadePending;
    private static boolean openTimingEnabled;
    private static long openStartedNanos;
    private static long nativeButtonsChapterId;
    private static long lastCenteredChapterId = Long.MIN_VALUE;
    private static long mapCenterStageChapterId = Long.MIN_VALUE;
    private static double mapCenterStageX = Double.NaN;
    private static double mapCenterStageY = Double.NaN;
    private static int mapCenterStableSamples;
    private static final Map<Long, QuestButton> nativeQuestButtons;
    private static final Set<Long> mapDiagnosticsLogged;
    private static final Set<String> mapDiagnosticWarningsLogged;
    private static final double DEFAULT_MAP_ZOOM = 1.0;
    private static final double MIN_MAP_ZOOM = 0.5;
    private static final double MAX_MAP_ZOOM = 2.0;
    private static final double MAP_ZOOM_STEP = 1.1;
    private static final double MAP_HOVER_LABEL_WIDTH_CSS = 260.0;
    private static final double MAP_HOVER_LABEL_HEIGHT_CSS = 48.0;
    private static final double MAP_HOVER_LABEL_GAP_CSS = 8.0;
    private static double mapZoom = DEFAULT_MAP_ZOOM;
    private static MapLayout mapLayout;
    private static long hoveredMapNodeLabelId = Long.MIN_VALUE;
    private static MapNodeLabelPhase mapNodeLabelPhase = MapNodeLabelPhase.HIDDEN;
    private static MapNodeLabelFrame mapNodeLabelFrame;
    private static boolean mapNodeLabelReplayActive;
    private static boolean mapNodeLabelShowPending;
    private static MapViewportInputSnapshot mapViewportInputSnapshot;
    private static boolean pendingMapCenter;
    private static boolean snapMapCenterWithoutCache;
    private static boolean pendingMapScrollRestore;
    private static boolean pendingMapZoomAnchorRestore;
    private static PendingMapZoomAnchor pendingMapZoomAnchor;
    private static boolean pendingMapZoomAnchorAppliedImmediately;
    private static double pendingMapScrollLeft;
    private static double pendingMapScrollTop;
    private static final long MAP_SCROLL_SAVE_QUIET_NANOS = 250_000_000L;
    private static long observedMapScrollChapterId = Long.MIN_VALUE;
    private static double observedMapScrollLeft = Double.NaN;
    private static double observedMapScrollTop = Double.NaN;
    private static double observedMapTargetScrollLeft = Double.NaN;
    private static double observedMapTargetScrollTop = Double.NaN;
    private static long lastMapScrollMovementNanos;
    private static boolean pendingMapCenterSave;
    private static MapZoomTrace pendingMapZoomTrace;
    private static boolean pendingMapZoomTraceWaitForFreshFrame;
    private static final List<PendingMapZoomInput> pendingMapZoomInputs = new ArrayList<>();
    private static String lastLoggedDomWheelTrace;
    private static final Properties MAP_CENTER_CACHE = new Properties();
    private static boolean mapCenterCacheLoaded;
    private static boolean mapCenterCacheWarningLogged;
    private static Field apricityScrollModelField;
    private static Method apricityScrollImmediateMethod;
    private static boolean apricityScrollAccessInitialized;
    private static boolean apricityScrollAccessWarningLogged;
    private static boolean initialFtbChapterRefresh;
    private static boolean ftbPersistWarningLogged;

    private QuestUiScreenController() {
    }

    static void open(QuestScreen source, Screen sourceScreen) {
        Minecraft minecraft = Minecraft.m_91087_();
        openTimingEnabled = Boolean.getBoolean("questui.debug.open");
        openStartedNanos = System.nanoTime();
        QuestUiTheme.loadIfNeeded(minecraft.f_91069_.toPath());
        if (!ClientQuestFile.exists() || ClientQuestFile.INSTANCE.selfTeamData == null) {
            QuestUiScreenController.openVanilla(source, sourceScreen);
            return;
        }
        long resourcesStarted = System.nanoTime();
        if (!QuestUiResources.installDefaults()) {
            QuestUiScreenController.openVanilla(source, sourceScreen);
            return;
        }
        long resourcesNanos = System.nanoTime() - resourcesStarted;
        long templateStarted = System.nanoTime();
        if (!QuestUiResources.reloadIfChanged(PAGE_PATH, "questui/questbook-v8.css")) {
            QuestUiMod.LOGGER.error("ApricityUI could not load the quest book page '{}'", (Object)PAGE_PATH);
            QuestUiScreenController.openVanilla(source, sourceScreen);
            return;
        }
        long templateNanos = System.nanoTime() - templateStarted;
        vanillaScreen = sourceScreen;
        sourceQuestScreen = source;
        ftbPersistWarningLogged = false;
        long persistedChapterId = QuestUiScreenController.readPersistedFtbChapterId(source);
        long snapshotStarted = System.nanoTime();
        var snapshot = QuestBookSnapshotFactory.capture(ClientQuestFile.INSTANCE);
        long snapshotNanos = System.nanoTime() - snapshotStarted;
        int questCount = snapshot.chapters().stream().mapToInt(chapter -> chapter.quests().size()).sum();
        QuestUiMod.LOGGER.info("[QuestUI SnapshotCheck] groups={} chapters={} quests={} canEdit={}",
                snapshot.groups().size(), snapshot.chapters().size(), questCount, ClientQuestFile.INSTANCE.canEdit());
        QuestUiScreenController.cancelSearchRequest();
        state = new QuestBookState(snapshot);
        if (persistedChapterId != 0L) {
            state.selectChapter(persistedChapterId);
        }
        initialFtbChapterRefresh = true;
        collapsedGroups.clear();
        mapDiagnosticsLogged.clear();
        mapDiagnosticWarningsLogged.clear();
        state.snapshot().groups().stream().filter(group -> !group.defaultGroup() && group.collapsed()).map(ChapterGroupSnapshot::id).forEach(collapsedGroups::add);
        document = null;
        waitingForDocument = true;
        usingVanillaEditor = false;
        staticEventsBound = false;
        searchSettingsOpen = false;
        chapterSidebarCollapsed = false;
        chapterHitTestRefreshPending = false;
        chapterCursorRefreshPending = true;
        chapterToggleCursorOverrideActive = false;
        chapterHitTestDebugListenerDocument = null;
        chapterHitTestDebugSequence = 0L;
        activeChapterHitTestDebugSequence = 0L;
        chapterToggleMousePressCaptured = false;
        chapterToggleMousePressDocument = null;
        searchUpdatePending = false;
        pendingSearchQuery = "";
        pendingSearchUpdatedNanos = 0L;
        lastObservedSearchValue = "";
        waitTicks = 0;
        refreshTicks = 0;
        renderedGeneration = -1L;
        screenFadeStartNanos = 0L;
        screenFadePending = true;
        nativeButtonsChapterId = Long.MIN_VALUE;
        lastCenteredChapterId = Long.MIN_VALUE;
        mapCenterStageChapterId = Long.MIN_VALUE;
        mapCenterStageX = Double.NaN;
        mapCenterStageY = Double.NaN;
        mapCenterStableSamples = 0;
        nativeQuestButtons.clear();
        mapLayout = null;
        resetMapNodeLabelHover();
        mapViewportInputSnapshot = null;
        mapZoom = DEFAULT_MAP_ZOOM;
        pendingMapCenter = false;
        snapMapCenterWithoutCache = false;
        pendingMapScrollRestore = false;
        pendingMapZoomAnchorRestore = false;
        pendingMapZoomAnchor = null;
        pendingMapZoomAnchorAppliedImmediately = false;
        pendingMapZoomTrace = null;
        pendingMapZoomTraceWaitForFreshFrame = false;
        pendingMapZoomInputs.clear();
        lastLoggedDomWheelTrace = null;
        pendingMapScrollLeft = 0.0;
        pendingMapScrollTop = 0.0;
        QuestUiScreenController.resetMapScrollPersistenceTracking();
        try {
            Screen previousScreen = minecraft.f_91080_;
            long screenStarted = System.nanoTime();
            ApricityUI.screen((String)PAGE_PATH);
            apricityScreen = minecraft.f_91080_;
            long screenNanos = System.nanoTime() - screenStarted;
            if (apricityScreen == previousScreen) {
                QuestUiMod.LOGGER.error("ApricityUI did not open the quest book page '{}'; restoring FTB Quests", (Object)PAGE_PATH);
                QuestUiScreenController.openVanilla(source, sourceScreen);
            } else if (openTimingEnabled) {
                QuestUiMod.LOGGER.info("[QuestUI OpenTiming] assets={}ms template={}ms snapshot={}ms screenCreate={}ms",
                        resourcesNanos / 1_000_000L, templateNanos / 1_000_000L, snapshotNanos / 1_000_000L,
                        screenNanos / 1_000_000L);
            }
        }
        catch (RuntimeException exception) {
            QuestUiMod.LOGGER.error("Could not open the Apricity Quests page", (Throwable)exception);
            QuestUiScreenController.openVanilla(source, sourceScreen);
        }
    }

    static boolean ownsScreen(Screen screen) {
        return state != null && screen != null && screen == apricityScreen;
    }

    static void renderFtbIcons(GuiGraphics graphics) {
        if (!QuestUiScreenController.ownsScreen(Minecraft.m_91087_().f_91080_)) {
            return;
        }
        if (screenFadePending) {
            QuestUiScreenController.renderScreenFade(graphics);
            return;
        }
        if (document == null || document.isDisposed() || !ClientQuestFile.exists() || state == null) {
            return;
        }
        // Recheck the client config during rendering so enabling diagnostics while the page is open
        // binds the capture listener on the next frame; disabling it silences the listeners immediately.
        QuestUiScreenController.bindChapterHitTestDebugListener();
        Element chapterList = QuestUiScreenController.element("chapter-list");
        if (chapterList != null && QuestUiScreenController.enableElementScissor(graphics, chapterList)) {
            try {
                for (ChapterGroupSnapshot group : state.snapshot().groups()) {
                    String groupKey = QuestUiScreenController.id(group.id());
                    if (!group.defaultGroup()) {
                        QuestUiScreenController.drawObjectIcon(graphics, "group-icon-" + groupKey,
                                (QuestObjectBase)ClientQuestFile.INSTANCE.get(group.id()));
                        Element groupChapters = QuestUiScreenController.element("chapter-group-chapters-" + groupKey);
                        if (groupChapters == null || !QuestUiScreenController.enableElementScissor(graphics, groupChapters)) {
                            continue;
                        }
                        try {
                            for (ChapterSnapshot chapter : group.chapters()) {
                                QuestUiScreenController.drawObjectIcon(graphics,
                                        "chapter-icon-" + QuestUiScreenController.id(chapter.id()),
                                        (QuestObjectBase)ClientQuestFile.INSTANCE.get(chapter.id()));
                            }
                            QuestUiScreenController.flushAll(graphics);
                        } finally {
                            graphics.m_280618_();
                        }
                    } else {
                        for (ChapterSnapshot chapter : group.chapters()) {
                            QuestUiScreenController.drawObjectIcon(graphics,
                                    "chapter-icon-" + QuestUiScreenController.id(chapter.id()),
                                    (QuestObjectBase)ClientQuestFile.INSTANCE.get(chapter.id()));
                        }
                    }
                }
                QuestUiScreenController.flushAll(graphics);
            } finally {
                graphics.m_280618_();
            }
        }
        QuestSnapshot quest = state.selectedQuest().orElse(null);
        Element detailPanel = QuestUiScreenController.element("quest-detail");
        if (quest != null && detailPanel != null && QuestUiScreenController.enableElementScissor(graphics, detailPanel)) {
            try {
                for (TaskSnapshot task : quest.tasks()) {
                    Task actualTask = ClientQuestFile.INSTANCE.getTask(task.id());
                    QuestUiScreenController.drawTaskIcon(graphics, QuestUiScreenController.taskIconId(task.id()), actualTask);
                }
                for (RewardSnapshot reward : quest.rewards()) {
                    Reward actualReward = ClientQuestFile.INSTANCE.getReward(reward.id());
                    QuestUiScreenController.drawObjectIcon(graphics, QuestUiScreenController.rewardIconId(reward.id()), (QuestObjectBase)actualReward);
                }
                QuestUiScreenController.flushAll(graphics);
            } finally {
                graphics.m_280618_();
            }
        }
        Element mapViewport = QuestUiScreenController.element("map-viewport");
        int[] mapScissorBounds = mapViewport == null ? null : QuestUiScreenController.mapViewportScissorBounds(mapViewport);
        if (QuestUiScreenController.renderNativeQuestMap(graphics, mapScissorBounds)) {
            QuestUiScreenController.flushAll(graphics);
            QuestUiScreenController.renderAuiMapNodeHoverLabelOverlay(graphics, mapScissorBounds);
            QuestUiScreenController.updateMapNodeHoverLabel();
        } else {
            mapNodeLabelFrame = null;
            QuestUiScreenController.resetMapNodeLabelHover();
        }
        QuestUiScreenController.renderScreenFade(graphics);
        if (document != null && document.isActive()) {
            boolean refreshHitTest = chapterHitTestRefreshPending;
            if (refreshHitTest) {
                // renderFtbIcons runs from ScreenEvent.Render.Post, after Apricity has committed and
                // painted this frame's page geometry. The next hit-test rebuild now sees the same rects
                // the player can see, instead of caching the previous layout during the input/style gap.
                document.markHitTestDirtyAll();
                chapterHitTestRefreshPending = false;
            }
            boolean forceCursorRefresh = refreshHitTest || chapterCursorRefreshPending;
            // Also check the live cursor position every screen frame: a mousemove may have been
            // coalesced while AUI still had a stale target, so do not rely on its hover event alone.
            QuestUiScreenController.refreshChapterToggleCursor(forceCursorRefresh, refreshHitTest);
            chapterCursorRefreshPending = false;
        }
    }

    static void tick() {
        if (state == null) {
            return;
        }
        Minecraft minecraft = Minecraft.m_91087_();
        if (minecraft.f_91080_ != apricityScreen) {
            if (usingVanillaEditor && minecraft.f_91080_ != null) {
                return;
            }
            QuestUiScreenController.clearSession();
            return;
        }
        if (waitingForDocument) {
            QuestUiScreenController.bindDocument();
            if (waitingForDocument && ++waitTicks >= 40) {
                QuestUiMod.LOGGER.warn("ApricityUI did not create quest book page '{}'; restoring the FTB Quests screen", (Object)PAGE_PATH);
                QuestUiScreenController.openVanilla(sourceQuestScreen, vanillaScreen);
            }
            return;
        }
        if (document == null || document.isDisposed()) {
            QuestUiScreenController.bindDocument();
            return;
        }
        long generation = document.getRefreshGeneration();
        if (generation != renderedGeneration) {
            staticEventsBound = false;
            QuestUiScreenController.renderPage();
        }
        QuestUiScreenController.pollSearchInput();
        QuestUiScreenController.flushPendingSearch();
        if (mapLayoutUnsettled) {
            // The page geometry was still settling when the map was last laid out. Retry every tick
            // so opening the book never waits on a mouse movement to recover.
            QuestUiScreenController.renderMapIfChanged();
        }
        if (++refreshTicks >= 10) {
            refreshTicks = 0;
            QuestUiScreenController.refreshSelectedQuest();
        }
    }

    private static void bindDocument() {
        ArrayList documents = ApricityUI.getDocument((String)PAGE_PATH);
        if (documents.isEmpty()) {
            return;
        }
        document = (Document)documents.get(documents.size() - 1);
        waitingForDocument = false;
        staticEventsBound = false;
        chapterCursorRefreshPending = true;
        chapterToggleCursorOverrideActive = false;
        chapterHitTestDebugListenerDocument = null;
        activeChapterHitTestDebugSequence = 0L;
        chapterToggleMousePressCaptured = false;
        chapterToggleMousePressDocument = null;
        renderedGeneration = -1L;
        // A fresh document means a fresh DOM tree: every cached "already rendered" marker is void.
        chapterListSignature = null;
        lastObservedSearchValue = "";
    }


    private static void renderScreenFade(GuiGraphics graphics) {
        int alpha;
        if (screenFadePending) {
            alpha = 255;
        } else {
            if (screenFadeStartNanos == 0L) {
                return;
            }
            double progress = Math.min(1.0, (System.nanoTime() - screenFadeStartNanos) / 240_000_000.0);
            if (progress >= 1.0) {
                screenFadeStartNanos = 0L;
                return;
            }
            alpha = (int)Math.round((1.0 - progress) * 255.0);
        }
        if (alpha > 0) {
            Window window = Minecraft.m_91087_().m_91268_();
            QuestUiScreenController.flushAll(graphics);
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(0.0d, 0.0d, SCREEN_FADE_LAYER_Z);
                graphics.fill(0, 0, window.m_85445_(), window.m_85446_(), alpha << 24);
                graphics.flush();
            } finally {
                graphics.pose().popPose();
            }
        }
    }

    private static void bindChapterHitTestDebugListener() {
        if (!QuestUiScreenController.isChapterHitTestDebugEnabled() || document == null || !document.isActive()
                || chapterHitTestDebugListenerDocument == document) {
            return;
        }
        Element body = QuestUiScreenController.element("questbook-body");
        if (body == null) {
            return;
        }
        for (String type : List.of("mousedown", "mouseup", "click")) {
            body.addEventListener(type, QuestUiScreenController::logChapterHitTestEvent, true);
        }
        chapterHitTestDebugListenerDocument = document;
    }

    private static void logChapterHitTestEvent(Event event) {
        if (!QuestUiScreenController.isChapterHitTestDebugEnabled() || event == null || activeChapterHitTestDebugSequence == 0L
                || !(event instanceof MouseEvent mouse)) {
            return;
        }
        Element target = event.target instanceof Element element ? element : null;
        Element toggle = QuestUiScreenController.element("chapter-collapse-toggle");
        Element panel = QuestUiScreenController.element("chapter-panel");
        QuestUiMod.LOGGER.info("[QuestUI ChapterHitTest] seq={} phase=dom type={} doc=({}, {}) target={} targetHitRect={} toggleHitRect={} panelHitRect={} collapsed={} viewportScale=({}, {})",
                activeChapterHitTestDebugSequence, event.type, mouse.clientX, mouse.clientY,
                QuestUiScreenController.debugElement(target), QuestUiScreenController.committedHitTestRect(target),
                QuestUiScreenController.committedHitTestRect(toggle), QuestUiScreenController.committedHitTestRect(panel),
                chapterSidebarCollapsed, document.getViewportScaleX(), document.getViewportScaleY());
        if ("click".equals(event.type)) {
            activeChapterHitTestDebugSequence = 0L;
        }
    }


    static boolean routeChapterToggleMouseInput(boolean press, boolean release) {
        if (!press && !release) {
            return false;
        }
        if (document == null || !document.isActive() || state == null) {
            chapterToggleMousePressCaptured = false;
            chapterToggleMousePressDocument = null;
            return false;
        }
        Position guiPosition = Operation.getMousePositionDirectly();
        Position documentPosition = guiPosition == null ? null : document.screenToDocumentPosition(guiPosition);
        HitTestRect toggleRect = QuestUiScreenController.committedHitTestRect(
                QuestUiScreenController.element("chapter-collapse-toggle"));
        boolean insideToggle = toggleRect != null && toggleRect.contains(documentPosition);
        if (press) {
            chapterToggleMousePressCaptured = insideToggle;
            chapterToggleMousePressDocument = insideToggle ? document : null;
            return chapterToggleMousePressCaptured;
        }
        if (!chapterToggleMousePressCaptured) {
            return false;
        }
        boolean shouldToggle = chapterToggleMousePressDocument == document && insideToggle;
        chapterToggleMousePressCaptured = false;
        chapterToggleMousePressDocument = null;
        if (shouldToggle) {
            QuestUiScreenController.toggleChapterSidebar();
        }
        // A captured press must also capture its release, even when the pointer was dragged away.
        return true;
    }

    static boolean isChapterHitTestDebugEnabled() {
        return QuestUiConfig.CHAPTER_HIT_TEST_DEBUG.get();
    }

    static void logChapterHitTestInput(int button, int action, boolean canceledAtProbe, boolean directHandled) {
        if (!QuestUiScreenController.isChapterHitTestDebugEnabled() || document == null || !document.isActive()) {
            activeChapterHitTestDebugSequence = 0L;
            return;
        }
        Position guiPosition = Operation.getMousePositionDirectly();
        if (guiPosition == null) {
            activeChapterHitTestDebugSequence = 0L;
            return;
        }
        Position documentPosition = document.screenToDocumentPosition(guiPosition);
        Element toggle = QuestUiScreenController.element("chapter-collapse-toggle");
        Element panel = QuestUiScreenController.element("chapter-panel");
        HitTestRect toggleRect = QuestUiScreenController.committedHitTestRect(toggle);
        HitTestRect panelRect = QuestUiScreenController.committedHitTestRect(panel);
        if (!QuestUiScreenController.isNearChapterHitTestRegion(documentPosition, panelRect, toggleRect)) {
            activeChapterHitTestDebugSequence = 0L;
            return;
        }
        activeChapterHitTestDebugSequence = ++chapterHitTestDebugSequence;
        boolean insideToggleRect = toggleRect != null && toggleRect.contains(documentPosition);
        String route = directHandled ? (insideToggleRect ? "direct" : "direct-cancel") : "aui";
        QuestUiMod.LOGGER.info("[QuestUI ChapterHitTest] seq={} phase=input action={} button={} canceledAtProbe={} route={} insideToggleRect={} gui=({}, {}) doc=({}, {}) toggleHitRect={} panelHitRect={} panelClass={} pendingRender={} collapsed={} viewportScale=({}, {})",
                activeChapterHitTestDebugSequence, action, button, canceledAtProbe, route, insideToggleRect,
                guiPosition.x, guiPosition.y, documentPosition.x, documentPosition.y, toggleRect, panelRect,
                panel == null ? "<missing>" : panel.getAttribute("class"), document.hasPendingRenderState(),
                chapterSidebarCollapsed, document.getViewportScaleX(), document.getViewportScaleY());
    }

    private static boolean isNearChapterHitTestRegion(Position point, HitTestRect panel, HitTestRect toggle) {
        if (point == null || panel == null || toggle == null) {
            return true;
        }
        double left = panel.x() - 64.0d;
        double right = panel.x() + Math.max(panel.width(), 280.0d) + 64.0d;
        double top = panel.y() - 64.0d;
        double bottom = Math.max(panel.y() + 200.0d, toggle.y() + toggle.height() + 96.0d);
        return point.x >= left && point.x <= right && point.y >= top && point.y <= bottom;
    }

    private static HitTestRect committedHitTestRect(Element element) {
        if (element == null) {
            return null;
        }
        Rect rect = element.getRenderer().getCommittedRect();
        if (rect == null) {
            return null;
        }
        var size = rect.getElementSize();
        return new HitTestRect(rect.position.x + rect.box.getMarginLeft(),
                rect.position.y + rect.box.getMarginTop(), size.width(), size.height());
    }

    private static void refreshChapterToggleCursor(boolean forceRefresh, boolean hitTestAlreadyDirty) {
        if (document == null || !document.isActive()) {
            chapterToggleCursorOverrideActive = false;
            return;
        }
        Position guiPosition = forceRefresh ? Operation.getMousePositionDirectly() : Operation.getMousePosition();
        if (guiPosition == null) {
            guiPosition = Operation.getMousePositionDirectly();
        }
        if (guiPosition == null) {
            if (forceRefresh || chapterToggleCursorOverrideActive) {
                Cursor.refreshFromDocuments(null);
            }
            chapterToggleCursorOverrideActive = false;
            return;
        }
        Position documentPosition = document.screenToDocumentPosition(guiPosition);
        HitTestRect toggleRect = QuestUiScreenController.committedHitTestRect(
                QuestUiScreenController.element("chapter-collapse-toggle"));
        if (toggleRect != null && toggleRect.contains(documentPosition)) {
            // Hit-test caches can lag a layout transition even though this button's committed
            // rectangle is current. Keep the hand cursor synchronized with the visible control.
            Cursor.applyCssCursor(document.getPath(), "pointer");
            chapterToggleCursorOverrideActive = true;
            return;
        }
        if (forceRefresh || chapterToggleCursorOverrideActive) {
            if (!hitTestAlreadyDirty) {
                document.markHitTestDirtyAll();
            }
            Cursor.refreshFromDocuments(guiPosition);
            chapterToggleCursorOverrideActive = false;
        }
    }

    private static String debugElement(Element element) {
        if (element == null) {
            return "<none>";
        }
        return element.tagName + "#" + element.getAttribute("id") + "." + element.getAttribute("class");
    }

    private record HitTestRect(double x, double y, double width, double height) {
        private boolean contains(Position point) {
            return point != null && point.x >= x && point.x <= x + width
                    && point.y >= y && point.y <= y + height;
        }
    }

    private static void renderPage() {
        if (document == null || !document.isActive() || state == null) {
            return;
        }
        QuestUiScreenController.bindChapterHitTestDebugListener();
        QuestUiScreenController.setText("book-title", state.snapshot().title());
        Element search = QuestUiScreenController.element("quest-search");
        if (!staticEventsBound) {
            // A page reload rebuilds the whole DOM, so every "already rendered" marker is void.
            chapterListSignature = null;
            Element detailClose;
            Element editor;
            Element close;
            if (search != null) {
                // Never overwrite text the player is still typing while the debounce is running.
                if (!searchUpdatePending) {
                    search.setValue(state.searchQuery());
                }
                lastObservedSearchValue = search.getValue();
                search.addEventListener("input", event -> QuestUiScreenController.queueSearchUpdate(search.getValue()));
            }
            Element settingsToggle = QuestUiScreenController.element("search-settings-toggle");
            if (settingsToggle != null) {
                settingsToggle.addEventListener("click", event -> {
                    searchSettingsOpen = !searchSettingsOpen;
                    QuestUiScreenController.applyUiPreferencesToPage();
                });
            }
            Element chapterToggle = QuestUiScreenController.element("chapter-collapse-toggle");
            if (chapterToggle != null) {
                chapterToggle.addEventListener("click", event -> {
                    // Physical mouse clicks are routed against the current committed rectangle in
                    // the Forge input hook. Ignore a stale AUI MouseEvent that lands on this button
                    // outside its visible hit box; non-mouse clicks remain available to keyboard use.
                    if (event instanceof MouseEvent mouse && mouse.button >= 0) {
                        HitTestRect toggleRect = QuestUiScreenController.committedHitTestRect(chapterToggle);
                        if (toggleRect == null || !toggleRect.contains(new Position(mouse.clientX, mouse.clientY))) {
                            return;
                        }
                    }
                    QuestUiScreenController.toggleChapterSidebar();
                });
            }
            QuestUiScreenController.bindSearchSettings();
            if ((close = QuestUiScreenController.element("close-button")) != null) {
                close.addEventListener("click", event -> {
                    QuestUiScreenController.persistCurrentMapCenter();
                    Minecraft.m_91087_().m_91152_(null);
                });
            }
            if ((editor = QuestUiScreenController.element("vanilla-editor-button")) != null) {
                if (ClientQuestFile.exists() && ClientQuestFile.INSTANCE.canEdit()) {
                    editor.setAttribute("class", "button button-secondary");
                    editor.addEventListener("click", event -> QuestUiScreenController.openVanillaEditor());
                } else {
                    editor.setAttribute("class", "hidden");
                }
            }
            if ((detailClose = QuestUiScreenController.element("detail-close-button")) != null) {
                detailClose.addEventListener("click", event -> {
                    state.clearSelectedQuest();
                    QuestUiScreenController.syncMapNodesInPlace(false);
                    QuestUiScreenController.renderQuestDetails();
                });
            }
            staticEventsBound = true;
        }
        if (initialFtbChapterRefresh) {
            ChapterSnapshot initialChapter = state.selectedChapter().orElse(null);
            if (initialChapter != null) {
                QuestUiScreenController.syncFtbChapter(initialChapter);
            }
        }
        QuestUiScreenController.applyUiPreferencesToPage();
        QuestUiScreenController.renderChapterList();
        QuestUiScreenController.renderMapIfChanged();
        QuestUiScreenController.renderQuestDetails();
        if (screenFadePending) {
            screenFadePending = false;
            Element body = QuestUiScreenController.element("questbook-body");
            boolean reducedMotion = body != null && "true".equals(body.getAttribute("data-reduced-motion"));
            screenFadeStartNanos = reducedMotion ? 0L : System.nanoTime();
            if (openTimingEnabled) {
                QuestUiMod.LOGGER.info("[QuestUI OpenTiming] firstFrameReady={}ms",
                        (System.nanoTime() - openStartedNanos) / 1_000_000L);
            }
            openTimingEnabled = false;
            openStartedNanos = 0L;
        }
        renderedGeneration = document.getRefreshGeneration();
    }

    /**
     * ApricityUI never dispatches an {@code input} event for Backspace -- AbstractText.deleteBackward
     * calls sliceText with dispatchInputEvent=false -- so the listener alone can leave a stale search
     * on screen. Reading the field each tick covers Backspace, Delete, paste, the type=search clear
     * button, undo and IME commits.
     */
    private static void pollSearchInput() {
        if (document == null || state == null) {
            return;
        }
        Element search = QuestUiScreenController.element("quest-search");
        if (search == null) {
            return;
        }
        String value = search.getValue();
        if (value == null) {
            value = "";
        }
        if (!value.equals(lastObservedSearchValue)) {
            lastObservedSearchValue = value;
            QuestUiScreenController.queueSearchUpdate(value);
        }
    }

    private static void queueSearchUpdate(String query) {
        QuestUiScreenController.cancelSearchRequest();
        if (state != null) {
            state.setSearchPending(false);
        }
        pendingSearchQuery = query == null ? "" : query;
        pendingSearchUpdatedNanos = System.nanoTime();
        searchUpdatePending = true;
        // Deliberately no DOM work here. This runs inside an ApricityUI event dispatch, and rewriting
        // the chapter list while the document is mid-dispatch is what stalled the client thread. The
        // pending placeholder is rendered from tick() instead.
    }

    private static void cancelSearchRequest() {
        searchRequestGeneration++;
        Future<?> task = activeSearchTask;
        activeSearchTask = null;
        if (task != null) {
            task.cancel(true);
        }
    }

    private static void startSearchRequest() {
        QuestUiScreenController.cancelSearchRequest();
        if (state == null) {
            return;
        }
        QuestBookState targetState = state;
        String query = targetState.searchQuery();
        if (query.isBlank()) {
            targetState.setSearchPending(false);
            targetState.setSearchResults(List.of());
            return;
        }
        Document targetDocument = document;
        if (targetDocument == null || !targetDocument.isActive()) {
            targetState.setSearchPending(false);
            return;
        }
        QuestBookSnapshot snapshot = targetState.snapshot();
        long chapterId = targetState.selectedChapterId();
        QuestSearchOptions options = targetState.searchOptions();
        long requestGeneration = searchRequestGeneration;
        targetState.setSearchPending(true);
        activeSearchTask = SEARCH_EXECUTOR.submit(() -> {
            QuestBookSearch.Outcome outcome;
            try {
                outcome = QuestBookSearch.search(QuestUiScreenController.searchIndexFor(snapshot),
                        chapterId, query, options, System.nanoTime() + SEARCH_TIME_BUDGET_NANOS);
            } catch (RuntimeException exception) {
                QuestUiMod.LOGGER.error("Could not search the quest book", exception);
                outcome = new QuestBookSearch.Outcome(List.of(), false);
            }
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            if (outcome.truncated()) {
                QuestUiMod.LOGGER.warn("QuestUI search for \"{}\" hit its {} ms budget after {} results; narrow the query or the search scope",
                        query, SEARCH_TIME_BUDGET_NANOS / 1_000_000L, outcome.results().size());
            }
            List<QuestSearchResult> completedResults = outcome.results();
            Minecraft.getInstance().execute(() -> {
                if (requestGeneration != searchRequestGeneration || state != targetState
                        || document != targetDocument || !targetDocument.isActive()) {
                    return;
                }
                if (targetState.snapshot() != snapshot || targetState.selectedChapterId() != chapterId
                        || !targetState.searchQuery().equals(query) || !targetState.searchOptions().equals(options)) {
                    return;
                }
                activeSearchTask = null;
                targetState.setSearchResults(completedResults);
                QuestUiScreenController.renderChapterList();
                QuestUiScreenController.syncMapNodesInPlace(false);
            });
        });
    }

    /**
     * Reuses the normalized search text for a snapshot instead of re-lowercasing the whole quest
     * book on every keystroke. Snapshots are immutable records and every mutation replaces the
     * instance, so an identity check is enough to know the index is current.
     */
    private static QuestSearchIndex searchIndexFor(QuestBookSnapshot snapshot) {
        QuestSearchIndex index = searchIndex;
        if (index != null && index.snapshot() == snapshot) {
            return index;
        }
        QuestSearchIndex built = QuestSearchIndex.build(snapshot);
        searchIndex = built;
        return built;
    }

    private static void commitPendingSearchQuery() {
        if (!searchUpdatePending || state == null) {
            return;
        }
        state.setSearchQuery(pendingSearchQuery);
        searchUpdatePending = false;
        pendingSearchQuery = "";
        pendingSearchUpdatedNanos = 0L;
    }

    private static void flushPendingSearch() {
        if (!searchUpdatePending || state == null
                || System.nanoTime() - pendingSearchUpdatedNanos < SEARCH_DEBOUNCE_NANOS) {
            return;
        }
        QuestUiScreenController.commitPendingSearchQuery();
        if (!state.searchQuery().isBlank()) {
            chapterSidebarCollapsed = false;
        }
        QuestUiScreenController.startSearchRequest();
        QuestUiScreenController.applyUiPreferencesToPage();
        QuestUiScreenController.renderChapterList();
        QuestUiScreenController.syncMapNodesInPlace(false);
    }

    private static void bindSearchSettings() {
        Element chapterFilter = QuestUiScreenController.element("filter-chapter");
        Element questFilter = QuestUiScreenController.element("filter-quest");
        Element taskRewardFilter = QuestUiScreenController.element("filter-task-reward");
        if (chapterFilter != null) {
            chapterFilter.addEventListener("click", event -> QuestUiScreenController.toggleSearchFilter("chapterNames"));
        }
        if (questFilter != null) {
            questFilter.addEventListener("click", event -> QuestUiScreenController.toggleSearchFilter("questNames"));
        }
        if (taskRewardFilter != null) {
            taskRewardFilter.addEventListener("click", event -> QuestUiScreenController.toggleSearchFilter("tasksAndRewards"));
        }
        Element currentScope = QuestUiScreenController.element("search-scope-current");
        Element allScope = QuestUiScreenController.element("search-scope-all");
        if (currentScope != null) {
            currentScope.addEventListener("click", event -> QuestUiScreenController.setSearchScope(false));
        }
        if (allScope != null) {
            allScope.addEventListener("click", event -> QuestUiScreenController.setSearchScope(true));
        }
        for (String theme : List.of("green", "yellow", "blue", "cyan", "purple", "pink")) {
            Element themeButton = QuestUiScreenController.element("theme-" + theme);
            if (themeButton != null) {
                themeButton.addEventListener("click", event -> {
                    QuestUiTheme.select(theme, Minecraft.m_91087_().f_91069_.toPath());
                    QuestUiScreenController.applyUiPreferencesToPage();
                });
            }
        }
    }

    private static void toggleSearchFilter(String filter) {
        QuestSearchOptions options = state.searchOptions();
        QuestSearchOptions next = switch (filter) {
            case "chapterNames" -> new QuestSearchOptions(!options.chapterNames(), options.questNames(), options.tasksAndRewards(), options.crossChapter());
            case "questNames" -> new QuestSearchOptions(options.chapterNames(), !options.questNames(), options.tasksAndRewards(), options.crossChapter());
            case "tasksAndRewards" -> new QuestSearchOptions(options.chapterNames(), options.questNames(), !options.tasksAndRewards(), options.crossChapter());
            default -> options;
        };
        state.setSearchOptions(next);
        QuestUiScreenController.refreshSearchView();
    }

    private static void setSearchScope(boolean crossChapter) {
        QuestSearchOptions options = state.searchOptions();
        state.setSearchOptions(new QuestSearchOptions(options.chapterNames(), options.questNames(),
                options.tasksAndRewards(), crossChapter));
        QuestUiScreenController.refreshSearchView();
    }

    private static void refreshSearchView() {
        QuestUiScreenController.commitPendingSearchQuery();
        QuestUiScreenController.startSearchRequest();
        QuestUiScreenController.applyUiPreferencesToPage();
        QuestUiScreenController.renderChapterList();
        QuestUiScreenController.syncMapNodesInPlace(false);
    }

    private static void toggleChapterSidebar() {
        chapterSidebarCollapsed = !chapterSidebarCollapsed;
        QuestUiScreenController.applyUiPreferencesToPage();
    }

    private static void applyUiPreferencesToPage() {
        boolean refreshThemeStyles = false;
        Element body = QuestUiScreenController.element("questbook-body");
        if (body != null) {
            String currentTheme = QuestUiTheme.currentId();
            String previousTheme = body.getAttribute("data-theme");
            boolean themeChanged = !currentTheme.equals(previousTheme);
            refreshThemeStyles = themeChanged
                    && (!"green".equals(currentTheme) || (previousTheme != null && !previousTheme.isBlank()));
            body.setAttribute("data-theme", currentTheme);
        }
        Element settings = QuestUiScreenController.element("search-settings-panel");
        if (settings != null) {
            settings.setAttribute("class", searchSettingsOpen ? "search-settings" : "search-settings hidden");
        }
        Element settingsToggle = QuestUiScreenController.element("search-settings-toggle");
        if (settingsToggle != null) {
            settingsToggle.setAttribute("aria-expanded", Boolean.toString(searchSettingsOpen));
        }
        Element chapterPanel = QuestUiScreenController.element("chapter-panel");
        if (chapterPanel != null) {
            String chapterPanelClass = chapterSidebarCollapsed ? "chapter-panel collapsed" : "chapter-panel";
            if (!chapterPanelClass.equals(chapterPanel.getAttribute("class"))) {
                chapterPanel.setAttribute("class", chapterPanelClass);
                // setAttribute queues AUI style recalculation; dirtying the hit cache here can make
                // the next input rebuild it from the previous committed rect. Defer invalidation
                // until the screen's next post-render hook, after the new geometry is committed.
                chapterHitTestRefreshPending = true;
                chapterCursorRefreshPending = true;
            }
        }
        Element mapToolbar = QuestUiScreenController.element("map-toolbar");
        if (mapToolbar != null) {
            mapToolbar.setAttribute("class", chapterSidebarCollapsed ? "map-toolbar sidebar-collapsed" : "map-toolbar");
        }
        Element chapterToggle = QuestUiScreenController.element("chapter-collapse-toggle");
        if (chapterToggle != null) {
            chapterToggle.setTextContent(chapterSidebarCollapsed ? "›" : "‹");
            chapterToggle.setAttribute("aria-label", chapterSidebarCollapsed ? "展开章节栏" : "收起章节栏");
            chapterToggle.setAttribute("title", chapterSidebarCollapsed ? "展开章节栏" : "收起章节栏");
        }
        QuestSearchOptions options = state == null ? QuestSearchOptions.defaults() : state.searchOptions();
        QuestUiScreenController.setButtonActive("search-scope-current", "scope-button", !options.crossChapter());
        QuestUiScreenController.setButtonActive("search-scope-all", "scope-button", options.crossChapter());
        QuestUiScreenController.setButtonActive("filter-chapter", "filter-chip", options.chapterNames());
        QuestUiScreenController.setButtonActive("filter-quest", "filter-chip", options.questNames());
        QuestUiScreenController.setButtonActive("filter-task-reward", "filter-chip", options.tasksAndRewards());
        for (String theme : List.of("green", "yellow", "blue", "cyan", "purple", "pink")) {
            Element themeButton = QuestUiScreenController.element("theme-" + theme);
            if (themeButton != null) {
                themeButton.setAttribute("class", "theme-swatch theme-" + theme
                        + (theme.equals(QuestUiTheme.currentId()) ? " active" : ""));
                themeButton.setAttribute("aria-pressed", Boolean.toString(theme.equals(QuestUiTheme.currentId())));
            }
        }
        if (refreshThemeStyles && document != null && document.isActive()) {
            document.refreshStyles();
        }
    }

    private static void setButtonActive(String id, String baseClass, boolean active) {
        Element button = QuestUiScreenController.element(id);
        if (button != null) {
            button.setAttribute("class", baseClass + (active ? " active" : ""));
            button.setAttribute("aria-pressed", Boolean.toString(active));
        }
    }

    private static void selectSearchResult(QuestSearchResult result) {
        QuestUiScreenController.persistCurrentMapCenter();
        QuestUiScreenController.cancelSearchRequest();
        searchUpdatePending = false;
        pendingSearchQuery = "";
        pendingSearchUpdatedNanos = 0L;
        state.selectChapter(result.chapterId());
        if (result.questId() != 0L) {
            state.selectQuest(result.questId());
        } else {
            state.setSearchQuery("");
            Element search = QuestUiScreenController.element("quest-search");
            if (search != null) {
                search.setValue("");
            }
        }
        searchSettingsOpen = false;
        QuestUiScreenController.startSearchRequest();
        QuestUiScreenController.applyUiPreferencesToPage();
        QuestUiScreenController.renderChapterList();
        QuestUiScreenController.renderMap();
        if (result.questId() != 0L) {
            QuestUiScreenController.focusQuestOnMap(result.questId());
        }
        QuestUiScreenController.renderQuestDetails();
    }

    private static void focusQuestOnMap(long questId) {
        ChapterSnapshot chapter = state.selectedChapter().orElse(null);
        Element viewport = QuestUiScreenController.element("map-viewport");
        if (chapter == null || viewport == null || mapLayout == null || mapLayout.chapterId() != chapter.id()) {
            return;
        }
        QuestMapNodeSnapshot node = chapter.mapNodes().stream()
                .filter(candidate -> candidate.questId() == questId && !candidate.link() && candidate.visible())
                .findFirst().orElseGet(() -> chapter.mapNodes().stream()
                        .filter(candidate -> candidate.questId() == questId && candidate.visible())
                        .findFirst().orElse(null));
        MapNodePlacement placement = node == null ? null : mapLayout.placements().get(node.id());
        Element.DOMRect bounds = viewport.getBoundingClientRect();
        if (placement == null || bounds == null || bounds.width <= 0.0 || bounds.height <= 0.0) {
            return;
        }
        double scaleX = Math.max(0.01, mapLayout.guiScaleX());
        double scaleY = Math.max(0.01, mapLayout.guiScaleY());
        double maxLeft = Math.max(0.0, mapLayout.contentWidth() / scaleX - bounds.width);
        double maxTop = Math.max(0.0, mapLayout.contentHeight() / scaleY - bounds.height);
        double left = (placement.x() + placement.width() / 2.0) / scaleX - bounds.width / 2.0;
        double top = (placement.y() + placement.height() / 2.0) / scaleY - bounds.height / 2.0;
        viewport.scrollTo(Math.max(0.0, Math.min(left, maxLeft)), Math.max(0.0, Math.min(top, maxTop)));
        pendingMapCenter = false;
        snapMapCenterWithoutCache = false;
        pendingMapScrollRestore = false;
        pendingMapZoomAnchorRestore = false;
        pendingMapZoomAnchor = null;
        lastCenteredChapterId = chapter.id();
    }

    private static void renderSearchResults(Element list) {
        if (state.searchPending()) {
            Element pending = document.createElement("div");
            pending.setAttribute("class", "search-pending-message");
            pending.setTextContent("正在搜索…");
            list.appendChild(pending);
            return;
        }
        List<QuestSearchResult> results = state.searchResults();
        if (results.isEmpty()) {
            Element empty = document.createElement("div");
            empty.setAttribute("class", "search-empty");
            empty.setTextContent("没有找到匹配的内容");
            list.appendChild(empty);
            return;
        }
        for (QuestSearchResult result : results) {
            Element button = document.createElement("button");
            button.setAttribute("class", "search-result "
                    + (result.kind() == QuestSearchResult.MatchKind.CHAPTER ? "chapter-match" : "quest-match"));
            Element kind = document.createElement("span");
            kind.setAttribute("class", "search-result-kind");
            kind.setTextContent(switch (result.kind()) {
                case CHAPTER -> "章节";
                case QUEST -> "任务";
                case TASK -> "目标";
                case REWARD -> "奖励";
            });
            Element copy = document.createElement("span");
            copy.setAttribute("class", "search-result-copy");
            Element title = document.createElement("span");
            title.setAttribute("class", "search-result-title");
            title.setTextContent(result.title());
            Element detail = document.createElement("span");
            detail.setAttribute("class", "search-result-detail");
            detail.setTextContent(result.matchDetail());
            copy.appendChild(title);
            copy.appendChild(detail);
            button.appendChild(kind);
            button.appendChild(copy);
            button.addEventListener("click", event -> QuestUiScreenController.selectSearchResult(result));
            list.appendChild(button);
        }
        if (results.size() >= QuestBookSearch.MAX_RESULTS) {
            Element limit = document.createElement("div");
            limit.setAttribute("class", "search-limit-note");
            limit.setTextContent("仅显示前 " + QuestBookSearch.MAX_RESULTS + " 条，请继续输入缩小范围");
            list.appendChild(limit);
        }
    }

    private static void renderChapterList() {
        Element list = QuestUiScreenController.element("chapter-list");
        if (list == null) {
            return;
        }
        if (!"chapter-list".equals(list.getAttribute("class"))) {
            list.setAttribute("class", "chapter-list");
        }
        // Rebuilding up to 120 result rows (about 600 nodes) on every refresh is what made typing
        // feel like a hang, so only rebuild when the content actually changed.
        String signature = QuestUiScreenController.chapterListSignature();
        if (signature.equals(chapterListSignature)) {
            return;
        }
        chapterListSignature = signature;
        list.setTextContent("");
        if (!state.searchQuery().isBlank()) {
            QuestUiScreenController.renderSearchResults(list);
            return;
        }
        for (ChapterGroupSnapshot group : state.snapshot().groups()) {
            if (group.defaultGroup()) {
                for (ChapterSnapshot chapter : group.chapters()) {
                    list.appendChild(QuestUiScreenController.createChapterButton(chapter));
                }
                continue;
            }
            String groupKey = QuestUiScreenController.id(group.id());
            Element groupElement = document.createElement("section");
            groupElement.setAttribute("id", "chapter-group-" + groupKey);
            groupElement.setAttribute("class", "chapter-group-block");
            Element heading = document.createElement("button");
            heading.setAttribute("class", "chapter-group-heading");
            Element icon = document.createElement("span");
            icon.setAttribute("class", "ftb-icon group-icon");
            icon.setAttribute("id", "group-icon-" + groupKey);
            heading.appendChild(icon);
            Element title = document.createElement("span");
            title.setAttribute("class", "chapter-group-title");
            title.setTextContent(group.title());
            heading.appendChild(title);
            Element caret = document.createElement("span");
            caret.setAttribute("id", "chapter-group-caret-" + groupKey);
            caret.setAttribute("class", "group-caret");
            caret.setTextContent(collapsedGroups.contains(group.id()) ? "›" : "⌄");
            heading.appendChild(caret);
            heading.addEventListener("click", event -> QuestUiScreenController.toggleGroup(group.id()));
            groupElement.appendChild(heading);
            Element chapters = document.createElement("div");
            chapters.setAttribute("id", "chapter-group-chapters-" + groupKey);
            chapters.setAttribute("class", collapsedGroups.contains(group.id())
                    ? "chapter-group-chapters collapsed" : "chapter-group-chapters");
            Element inner = document.createElement("div");
            inner.setAttribute("class", "chapter-group-inner");
            for (ChapterSnapshot chapter : group.chapters()) {
                inner.appendChild(QuestUiScreenController.createChapterButton(chapter));
            }
            chapters.appendChild(inner);
            groupElement.appendChild(chapters);
            list.appendChild(groupElement);
        }
    }

    /** Everything renderChapterList() would draw, flattened into one comparable string. */
    private static String chapterListSignature() {
        StringBuilder signature = new StringBuilder(256);
        signature.append(state.searchQuery()).append('\u0000')
                .append(state.searchPending()).append('\u0000')
                .append(state.selectedChapterId()).append('\u0000');
        for (QuestSearchResult result : state.searchResults()) {
            signature.append(result.chapterId()).append(':').append(result.questId())
                    .append(':').append(result.kind()).append(':').append(result.title()).append(',');
        }
        signature.append('\u0000');
        for (ChapterGroupSnapshot group : state.snapshot().groups()) {
            signature.append(group.id()).append(':').append(collapsedGroups.contains(group.id())).append(';');
            for (ChapterSnapshot chapter : group.chapters()) {
                signature.append(chapter.id()).append('=').append(chapter.quests().size()).append(',');
            }
            signature.append('|');
        }
        return signature.toString();
    }

    private static Element createChapterButton(ChapterSnapshot chapter) {
        Element button = document.createElement("button");
        button.setAttribute("class", chapter.id() == state.selectedChapterId() ? "chapter-button selected" : "chapter-button");
        Element chapterIcon = document.createElement("span");
        chapterIcon.setAttribute("class", "ftb-icon chapter-icon");
        chapterIcon.setAttribute("id", "chapter-icon-" + QuestUiScreenController.id(chapter.id()));
        button.appendChild(chapterIcon);
        Element chapterTitle = document.createElement("span");
        chapterTitle.setAttribute("class", "chapter-title");
        chapterTitle.setTextContent(chapter.title());
        button.appendChild(chapterTitle);
        Element count = document.createElement("span");
        count.setAttribute("class", "chapter-count");
        count.setTextContent(Integer.toString(chapter.quests().size()));
        button.appendChild(count);
        button.addEventListener("click", event -> {
            QuestUiScreenController.persistCurrentMapCenter();
            QuestUiScreenController.cancelSearchRequest();
            searchUpdatePending = false;
            pendingSearchQuery = "";
            pendingSearchUpdatedNanos = 0L;
            state.selectChapter(chapter.id());
            QuestUiScreenController.startSearchRequest();
            QuestUiScreenController.renderChapterList();
            QuestUiScreenController.renderMap();
            QuestUiScreenController.renderQuestDetails();
        });
        return button;
    }

    private static void toggleGroup(long groupId) {
        ChapterGroup group = ClientQuestFile.exists() ? ClientQuestFile.INSTANCE.getChapterGroup(groupId) : null;
        if (group != null && group.isDefaultGroup()) {
            return;
        }
        if (group != null) {
            group.toggleCollapsed();
            if (group.isGuiCollapsed()) {
                collapsedGroups.add(groupId);
            } else {
                collapsedGroups.remove(groupId);
            }
        } else if (!collapsedGroups.add(groupId)) {
            collapsedGroups.remove(groupId);
        }
        boolean collapsed = collapsedGroups.contains(groupId);
        String groupKey = QuestUiScreenController.id(groupId);
        Element chapters = QuestUiScreenController.element("chapter-group-chapters-" + groupKey);
        if (chapters != null) {
            chapters.setAttribute("class", collapsed ? "chapter-group-chapters collapsed" : "chapter-group-chapters");
        }
        Element caret = QuestUiScreenController.element("chapter-group-caret-" + groupKey);
        if (caret != null) {
            caret.setTextContent(collapsed ? "›" : "⌄");
        }
    }

    /**
     * guiScale() derives the CSS->GUI scale from the body box, so before the first settled layout
     * pass the body (and #map-viewport with it) can measure taller than the whole window -- observed
     * 19682 CSS px on a 1080p window, giving scaleX=1.0 against scaleY=0.013. Laying the map out
     * from that puts every node outside the scissor and nothing recomputes until an input event
     * forces a re-layout, which is the "opens blank until you move the mouse" symptom. A settled page
     * maps both axes with the same factor, so cross-axis agreement is the cheap, reliable detector.
     */
    private static boolean mapGeometrySettled(Element.DOMRect viewportBounds, GuiScale scale) {
        if (viewportBounds == null || viewportBounds.width <= 0.0 || viewportBounds.height <= 0.0) {
            return false;
        }
        if (scale.x() <= 0.0 || scale.y() <= 0.0) {
            return false;
        }
        double smaller = Math.min(scale.x(), scale.y());
        double larger = Math.max(scale.x(), scale.y());
        return larger - smaller <= Math.max(0.05d, 0.25d * smaller);
    }

    /**
     * Re-renders the map only when the layout is actually stale. The chapter rail overlays the map,
     * so collapsing it must not rebuild the map DOM or re-center the view.
     */
    private static void renderMapIfChanged() {
        ChapterSnapshot chapter = state.selectedChapter().orElse(null);
        QuestUiScreenController.setText("chapter-title", chapter == null ? "没有可显示的章节" : chapter.title());
        QuestUiScreenController.setText("chapter-subtitle", chapter == null ? "" : chapter.subtitle());
        Element stage = QuestUiScreenController.element("map-stage");
        Element viewport = QuestUiScreenController.element("map-viewport");
        if (stage == null || viewport == null) {
            return;
        }
        Element.DOMRect bounds = viewport.getBoundingClientRect();
        GuiScale scale = QuestUiScreenController.guiScale();
        if (chapter != null && bounds != null && bounds.width > 0.0 && bounds.height > 0.0
                && mapLayout != null && mapLayout.matches(chapter.id(), bounds, scale, mapZoom)) {
            // Keep mirroring the chapter into FTB's own screen state, exactly as renderMap() did.
            QuestUiScreenController.syncFtbChapter(chapter);
            QuestUiScreenController.syncMapNodesInPlace(false);
            return;
        }
        QuestUiScreenController.renderMap();
    }

    private static void renderMap() {
        ChapterSnapshot chapter = state.selectedChapter().orElse(null);
        QuestUiScreenController.setText("chapter-title", chapter == null ? "\u6ca1\u6709\u53ef\u663e\u793a\u7684\u7ae0\u8282" : chapter.title());
        QuestUiScreenController.setText("chapter-subtitle", chapter == null ? "" : chapter.subtitle());
        Element stage = QuestUiScreenController.element("map-stage");
        Element viewport = QuestUiScreenController.element("map-viewport");
        if (stage == null || viewport == null) {
            return;
        }
        MapLayout previousLayout = mapLayout;
        double previousScrollLeft = viewport.getTargetScrollLeft();
        double previousScrollTop = viewport.getTargetScrollTop();
        Element.DOMRect bounds = viewport.getBoundingClientRect();
        GuiScale guiScale = QuestUiScreenController.guiScale();
        if (!QuestUiScreenController.mapGeometrySettled(bounds, guiScale) && mapUnsettledRetries < 40) {
            // Keep the previous layout on screen and let tick() retry; wiping the stage here would
            // leave an empty map until the player happens to move the mouse.
            mapLayoutUnsettled = true;
            mapUnsettledRetries++;
            if (!mapUnsettledWarningLogged) {
                mapUnsettledWarningLogged = true;
                QuestUiMod.LOGGER.warn("[QuestUI MapCheck] status=page-geometry-unsettled viewport={},{},{},{} scale={},{} -- deferring the map layout until it settles",
                        bounds == null ? 0.0 : bounds.x, bounds == null ? 0.0 : bounds.y,
                        bounds == null ? 0.0 : bounds.width, bounds == null ? 0.0 : bounds.height,
                        guiScale.x(), guiScale.y());
            }
            return;
        }
        // Bounded retry: if the page never agrees on a uniform scale we still have to draw something,
        // so fall back to laying out from whatever geometry we have rather than staying blank.
        mapLayoutUnsettled = false;
        mapUnsettledRetries = 0;
        stage.setTextContent("");
        stage.appendChild(QuestUiScreenController.createMapHoverLabel());
        QuestUiScreenController.resetMapNodeLabelHover();
        mapLayout = null;
        mapViewportInputSnapshot = null;
        if (chapter == null) {
            pendingMapCenter = false;
            snapMapCenterWithoutCache = false;
            pendingMapScrollRestore = false;
            pendingMapZoomAnchorRestore = false;
            pendingMapZoomAnchor = null;
            pendingMapZoomAnchorAppliedImmediately = false;
            pendingMapZoomTrace = null;
            pendingMapZoomTraceWaitForFreshFrame = false;
            pendingMapZoomInputs.clear();
            return;
        }
        QuestUiScreenController.syncFtbChapter(chapter);
        boolean chapterChanged = previousLayout == null || previousLayout.chapterId() != chapter.id();
        if (chapterChanged) {
            pendingMapZoomAnchorRestore = false;
            pendingMapZoomAnchor = null;
            pendingMapZoomAnchorAppliedImmediately = false;
            pendingMapZoomTrace = null;
            pendingMapZoomTraceWaitForFreshFrame = false;
            MapCenter cachedCenter = QuestUiScreenController.readCachedMapCenter(chapter);
            mapZoom = cachedCenter == null ? DEFAULT_MAP_ZOOM : cachedCenter.zoom();
        }
        boolean centerChapter = chapterChanged || !previousLayout.matchesViewport(chapter.id(), bounds, guiScale);
        mapLayout = QuestUiScreenController.createMapLayout(chapter, bounds, guiScale, mapZoom);
        stage.setAttribute("style", "width: " + Math.ceil(mapLayout.contentWidth() / mapLayout.guiScaleX()) + "px; height: " + Math.ceil(mapLayout.contentHeight() / mapLayout.guiScaleY()) + "px;");
        if (centerChapter && pendingMapZoomAnchorRestore) {
            pendingMapCenter = false;
            snapMapCenterWithoutCache = false;
            pendingMapScrollRestore = true;
        } else if (centerChapter) {
            viewport.scrollTo(viewport.getScrollLeft(), viewport.getScrollTop());
            if (!QuestUiScreenController.restoreCachedMapCenter(chapter, bounds)) {
                pendingMapCenter = true;
                snapMapCenterWithoutCache = true;
                pendingMapScrollRestore = false;
            }
        } else if (!pendingMapCenter && !pendingMapScrollRestore) {
            pendingMapScrollLeft = previousScrollLeft;
            pendingMapScrollTop = previousScrollTop;
            pendingMapScrollRestore = true;
        }
        Set visibleQuestIds = state.visibleQuests().stream().map(QuestSnapshot::id).collect(Collectors.toSet());
        List<QuestMapNodeSnapshot> visibleNodes = chapter.mapNodes().stream().filter(QuestMapNodeSnapshot::visible).filter(node -> visibleQuestIds.contains(node.questId())).toList();
        Element resultCount = QuestUiScreenController.element("result-count");
        if (resultCount != null) {
            resultCount.setTextContent(state.searchQuery().isBlank() ? visibleNodes.size() + " \u4e2a\u5730\u56fe\u8282\u70b9" : visibleQuestIds.size() + " \u4e2a\u5339\u914d\u4efb\u52a1");
        }
        if (visibleNodes.isEmpty()) {
            Element empty = document.createElement("div");
            empty.setAttribute("id", "map-empty");
            empty.setAttribute("class", "map-empty");
            empty.setTextContent(state.searchPending() ? "正在搜索…"
                    : (state.searchQuery().isBlank() ? "\\u8fd9\\u4e2a\\u7ae0\\u8282\\u8fd8\\u6ca1\\u6709\\u53ef\\u663e\\u793a\\u7684\\u4efb\\u52a1\\u3002" : "\\u6ca1\\u6709\\u627e\\u5230\\u5339\\u914d\\u7684\\u4efb\\u52a1\\u3002"));
            stage.appendChild(empty);
            return;
        }
        Map<Long, QuestSnapshot> quests = chapter.quests().stream().collect(Collectors.toMap(QuestSnapshot::id, quest -> quest, (first, ignored) -> first));
        for (QuestMapNodeSnapshot node : visibleNodes) {
            MapNodePlacement placement = mapLayout.placements().get(node.id());
            QuestSnapshot quest = quests.get(node.questId());
            if (quest == null || placement == null) {
                continue;
            }
            double left = placement.x() / mapLayout.guiScaleX();
            double top = placement.y() / mapLayout.guiScaleY();
            double width = placement.width() / mapLayout.guiScaleX();
            double height = placement.height() / mapLayout.guiScaleY();
            Element button = document.createElement("button");
            String classes = QuestUiScreenController.mapNodeClasses(node, quest, true);
            button.setAttribute("class", classes);
            button.setAttribute("id", QuestUiScreenController.mapNodeId(node.id()));
            button.setAttribute("style", "left: " + Math.round(left) + "px; top: " + Math.round(top) + "px; width: " + Math.max(1L, Math.round(width)) + "px; height: " + Math.max(1L, Math.round(height)) + "px;");
            button.addEventListener("click", event -> QuestUiScreenController.selectQuestForNode(node.id()));
            stage.appendChild(button);
        }
    }

    private static void selectQuestForNode(long nodeId) {
        ChapterSnapshot chapter = state == null ? null : state.selectedChapter().orElse(null);
        if (chapter == null) {
            return;
        }
        QuestMapNodeSnapshot node = chapter.mapNodes().stream()
                .filter(candidate -> candidate.id() == nodeId)
                .findFirst()
                .orElse(null);
        if (node == null) {
            return;
        }
        state.selectQuest(node.questId());
        QuestUiScreenController.syncMapNodesInPlace(false);
        QuestUiScreenController.renderQuestDetails();
    }

    private static String mapNodeClasses(QuestMapNodeSnapshot node, QuestSnapshot quest, boolean visible) {
        return "map-node" + (node.link() ? " link-node" : "")
                + (quest.completed() ? " completed" : (quest.started() ? " started" : (quest.canStart() ? " not-started" : " locked")))
                + (quest.id() == state.selectedQuestId() ? " selected" : "")
                + (visible ? "" : " hidden");
    }

    private static void syncMapNodesInPlace(boolean refreshLayout) {
        if (document == null || state == null) {
            return;
        }
        ChapterSnapshot chapter = state.selectedChapter().orElse(null);
        Element stage = QuestUiScreenController.element("map-stage");
        Element viewport = QuestUiScreenController.element("map-viewport");
        if (chapter == null || stage == null || viewport == null) {
            return;
        }
        if (mapLayout == null || mapLayout.chapterId() != chapter.id()
                || (!refreshLayout && Math.abs(mapLayout.zoom() - mapZoom) > 1.0e-4)) {
            QuestUiScreenController.renderMap();
            return;
        }
        Element.DOMRect bounds = viewport.getBoundingClientRect();
        if (bounds == null || bounds.width <= 0.0 || bounds.height <= 0.0) {
            return;
        }
        if (refreshLayout) {
            double previousScrollLeft = viewport.getTargetScrollLeft();
            double previousScrollTop = viewport.getTargetScrollTop();
            GuiScale scale = QuestUiScreenController.guiScale();
            mapLayout = QuestUiScreenController.createMapLayout(chapter, bounds, scale, mapZoom);
            String stageStyle = "width: " + Math.ceil(mapLayout.contentWidth() / mapLayout.guiScaleX())
                    + "px; height: " + Math.ceil(mapLayout.contentHeight() / mapLayout.guiScaleY()) + "px;";
            if (!stageStyle.equals(stage.getAttribute("style"))) {
                stage.setAttribute("style", stageStyle);
            }
            double maxScrollLeft = Math.max(0.0, Math.ceil(mapLayout.contentWidth() / mapLayout.guiScaleX()) - bounds.width);
            double maxScrollTop = Math.max(0.0, Math.ceil(mapLayout.contentHeight() / mapLayout.guiScaleY()) - bounds.height);
            double targetLeft = Math.max(0.0, Math.min(previousScrollLeft, maxScrollLeft));
            double targetTop = Math.max(0.0, Math.min(previousScrollTop, maxScrollTop));
            if (!pendingMapZoomAnchorRestore
                    && (Math.abs(targetLeft - previousScrollLeft) > 0.5 || Math.abs(targetTop - previousScrollTop) > 0.5)) {
                viewport.scrollTo(targetLeft, targetTop);
            }
        }

        Set<Long> visibleQuestIds = state.visibleQuests().stream().map(QuestSnapshot::id).collect(Collectors.toSet());
        Map<Long, QuestSnapshot> quests = chapter.quests().stream()
                .collect(Collectors.toMap(QuestSnapshot::id, quest -> quest, (first, ignored) -> first));
        int visibleNodeCount = 0;
        for (QuestMapNodeSnapshot node : chapter.mapNodes()) {
            QuestSnapshot quest = quests.get(node.questId());
            MapNodePlacement placement = mapLayout.placements().get(node.id());
            Element button = QuestUiScreenController.element(QuestUiScreenController.mapNodeId(node.id()));
            if (quest == null || placement == null) {
                if (button != null && !"map-node hidden".equals(button.getAttribute("class"))) {
                    button.setAttribute("class", "map-node hidden");
                }
                continue;
            }
            boolean visible = node.visible() && visibleQuestIds.contains(node.questId());
            if (!visible) {
                String classes = QuestUiScreenController.mapNodeClasses(node, quest, false);
                if (button != null && !classes.equals(button.getAttribute("class"))) {
                    button.setAttribute("class", classes);
                }
                continue;
            }
            visibleNodeCount++;
            if (button == null) {
                button = document.createElement("button");
                button.setAttribute("id", QuestUiScreenController.mapNodeId(node.id()));
                long nodeId = node.id();
                button.addEventListener("click", event -> QuestUiScreenController.selectQuestForNode(nodeId));
                stage.appendChild(button);
            }
            String classes = QuestUiScreenController.mapNodeClasses(node, quest, true);
            if (!classes.equals(button.getAttribute("class"))) {
                button.setAttribute("class", classes);
            }
            double left = placement.x() / mapLayout.guiScaleX();
            double top = placement.y() / mapLayout.guiScaleY();
            double width = placement.width() / mapLayout.guiScaleX();
            double height = placement.height() / mapLayout.guiScaleY();
            String style = "left: " + Math.round(left) + "px; top: " + Math.round(top) + "px; width: "
                    + Math.max(1L, Math.round(width)) + "px; height: " + Math.max(1L, Math.round(height)) + "px;";
            if (!style.equals(button.getAttribute("style"))) {
                button.setAttribute("style", style);
            }
        }
        Element resultCount = QuestUiScreenController.element("result-count");
        if (resultCount != null) {
            String count = state.searchQuery().isBlank()
                    ? visibleNodeCount + " 个地图节点"
                    : visibleQuestIds.size() + " 个匹配任务";
            if (!count.equals(resultCount.getTextContent())) {
                resultCount.setTextContent(count);
            }
        }
        Element empty = QuestUiScreenController.element("map-empty");
        if (visibleNodeCount == 0) {
            String message = state.searchPending() ? "正在搜索…"
                    : (state.searchQuery().isBlank()
                    ? "\\u8fd9\\u4e2a\\u7ae0\\u8282\\u8fd8\\u6ca1\\u6709\\u53ef\\u663e\\u793a\\u7684\\u4efb\\u52a1\\u3002"
                    : "\\u6ca1\\u6709\\u627e\\u5230\\u5339\\u914d\\u7684\\u4efb\\u52a1\\u3002");
            if (empty == null) {
                empty = document.createElement("div");
                empty.setAttribute("id", "map-empty");
                empty.setAttribute("class", "map-empty");
                empty.setTextContent(message);
                stage.appendChild(empty);
            } else {
                if (!"map-empty".equals(empty.getAttribute("class"))) {
                    empty.setAttribute("class", "map-empty");
                }
                if (!message.equals(empty.getTextContent())) {
                    empty.setTextContent(message);
                }
            }
        } else if (empty != null && !"hidden".equals(empty.getAttribute("class"))) {
            empty.setAttribute("class", "hidden");
        }
    }

    private static void drawDependencyLines(GuiGraphics graphics, ChapterSnapshot chapter, Set<Long> visibleQuestIds, MapLayout layout, Element.DOMRect stageBounds, GuiScale guiScale) {
        Map<Long, QuestSnapshot> quests = chapter.quests().stream().collect(Collectors.toMap(QuestSnapshot::id, quest -> quest, (first, ignored) -> first));
        Map<Long, QuestMapNodeSnapshot> primaryNodes = chapter.mapNodes().stream().filter(node -> !node.link() && node.visible()).collect(Collectors.toMap(QuestMapNodeSnapshot::questId, node -> node, (first, ignored) -> first));
        int lineThickness = Math.max(1, (int)Math.round(3.0 * guiScale.y() * layout.zoom()));
        int lineTop = -lineThickness / 2;
        int lineBottom = lineTop + lineThickness;
        boolean drewLines = false;
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(0.0d, 0.0d, MAP_DEPENDENCY_LINE_Z);
            for (QuestSnapshot quest : chapter.quests()) {
                QuestMapNodeSnapshot to;
                if (!visibleQuestIds.contains(quest.id()) || quest.hideDependencyLines() || (to = primaryNodes.get(quest.id())) == null) {
                    continue;
                }
                for (long dependencyId : quest.dependencies()) {
                    QuestSnapshot dependency = quests.get(dependencyId);
                    QuestMapNodeSnapshot from = primaryNodes.get(dependencyId);
                    if (dependency == null || from == null || !visibleQuestIds.contains(dependencyId) || dependency.hideDependentLines() || !from.visible()) {
                        continue;
                    }
                    MapNodePlacement fromPlacement = layout.placements().get(from.id());
                    MapNodePlacement toPlacement = layout.placements().get(to.id());
                    if (fromPlacement == null || toPlacement == null) {
                        continue;
                    }
                    double x1 = stageBounds.x * guiScale.x() + fromPlacement.x() + fromPlacement.width() / 2.0;
                    double y1 = stageBounds.y * guiScale.y() + fromPlacement.y() + fromPlacement.height() / 2.0;
                    double x2 = stageBounds.x * guiScale.x() + toPlacement.x() + toPlacement.width() / 2.0;
                    double y2 = stageBounds.y * guiScale.y() + toPlacement.y() + toPlacement.height() / 2.0;
                    double dx = x2 - x1;
                    double dy = y2 - y1;
                    double length = Math.sqrt(dx * dx + dy * dy);
                    if (length <= 0.0) {
                        continue;
                    }
                    int lineLength = Math.max(1, (int)Math.round(length));
                    int color = dependency.completed() ? QuestUiTheme.completedDependencyArgb() : 0xFF52617A;
                    graphics.pose().pushPose();
                    try {
                        graphics.pose().translate(x1, y1, 0.0d);
                        graphics.pose().mulPose(Axis.ZP.rotationDegrees((float)Math.toDegrees(Math.atan2(dy, dx))));
                        graphics.fill(0, lineTop, lineLength, lineBottom, color);
                    } finally {
                        graphics.pose().popPose();
                    }
                    drewLines = true;
                }
            }
            if (drewLines) {
                QuestUiScreenController.flushAll(graphics);
            }
        } finally {
            graphics.pose().popPose();
        }
    }

    private static boolean applyPendingMapZoomInputs(ChapterSnapshot chapter, Element viewport, Element.DOMRect bounds) {
        if (pendingMapZoomInputs.isEmpty() || chapter == null || viewport == null || bounds == null
                || mapLayout == null || mapLayout.chapterId() != chapter.id()) {
            return false;
        }
        String viewportClasses = viewport.getAttribute("class");
        if (viewportClasses != null && viewportClasses.contains("dragging")) {
            pendingMapZoomInputs.clear();
            return false;
        }
        ArrayList<PendingMapZoomInput> batch = new ArrayList<>(pendingMapZoomInputs);
        pendingMapZoomInputs.clear();

        MapLayout oldLayout = mapLayout;
        double oldZoom = mapZoom;
        double oldScrollLeft = viewport.getScrollLeft();
        double oldScrollTop = viewport.getScrollTop();
        double oldTargetLeft = viewport.getTargetScrollLeft();
        double oldTargetTop = viewport.getTargetScrollTop();
        double virtualZoom = mapZoom;
        double virtualScrollLeft = oldScrollLeft;
        double virtualScrollTop = oldScrollTop;
        MapLayout virtualLayout = oldLayout;
        if (pendingMapZoomAnchorRestore && pendingMapZoomAnchor != null
                && pendingMapZoomAnchor.chapterId() == chapter.id()) {
            double priorAnchorX = pendingMapZoomAnchor.mouseX() / Math.max(0.01, virtualLayout.guiScaleX()) - bounds.x;
            double priorAnchorY = pendingMapZoomAnchor.mouseY() / Math.max(0.01, virtualLayout.guiScaleY()) - bounds.y;
            double priorContentWidth = Math.max(bounds.width, Math.ceil(virtualLayout.contentWidth() / virtualLayout.guiScaleX()));
            double priorContentHeight = Math.max(bounds.height, Math.ceil(virtualLayout.contentHeight() / virtualLayout.guiScaleY()));
            double priorMaxLeft = Math.max(0.0, priorContentWidth - bounds.width);
            double priorMaxTop = Math.max(0.0, priorContentHeight - bounds.height);
            virtualScrollLeft = Math.max(0.0, Math.min(
                    (pendingMapZoomAnchor.mapPointX() * virtualZoom + virtualLayout.offsetX()) / virtualLayout.guiScaleX() - priorAnchorX,
                    priorMaxLeft));
            virtualScrollTop = Math.max(0.0, Math.min(
                    (pendingMapZoomAnchor.mapPointY() * virtualZoom + virtualLayout.offsetY()) / virtualLayout.guiScaleY() - priorAnchorY,
                    priorMaxTop));
        }
        double baseVirtualScrollLeft = virtualScrollLeft;
        double baseVirtualScrollTop = virtualScrollTop;

        PendingMapZoomInput lastApplied = null;
        double finalMapPointX = 0.0;
        double finalMapPointY = 0.0;
        double totalWheelDelta = 0.0;
        int appliedEventCount = 0;
        for (PendingMapZoomInput input : batch) {
            if (input.chapterId() != chapter.id()) {
                continue;
            }
            totalWheelDelta += input.scrollDelta();
            boolean capturedGeometryMatches = Math.abs(input.eventScaleX() - virtualLayout.guiScaleX()) < 1.0e-4
                    && Math.abs(input.eventScaleY() - virtualLayout.guiScaleY()) < 1.0e-4
                    && Math.abs(input.viewportCssX() - bounds.x) < 0.5
                    && Math.abs(input.viewportCssY() - bounds.y) < 0.5;
            double anchorX = capturedGeometryMatches ? input.anchorX()
                    : input.mouseX() / Math.max(0.01, virtualLayout.guiScaleX()) - bounds.x;
            double anchorY = capturedGeometryMatches ? input.anchorY()
                    : input.mouseY() / Math.max(0.01, virtualLayout.guiScaleY()) - bounds.y;
            if (anchorX < 0.0 || anchorY < 0.0 || anchorX > bounds.width || anchorY > bounds.height) {
                continue;
            }
            double wheelSteps = Math.max(-4.0, Math.min(4.0, input.scrollDelta()));
            double nextZoom = QuestUiScreenController.clampMapZoom(virtualZoom * Math.pow(MAP_ZOOM_STEP, wheelSteps));
            if (Math.abs(nextZoom - virtualZoom) < 1.0e-6) {
                continue;
            }
            double mapPointX = ((virtualScrollLeft + anchorX) * virtualLayout.guiScaleX() - virtualLayout.offsetX()) / virtualZoom;
            double mapPointY = ((virtualScrollTop + anchorY) * virtualLayout.guiScaleY() - virtualLayout.offsetY()) / virtualZoom;
            GuiScale virtualScale = new GuiScale(virtualLayout.guiScaleX(), virtualLayout.guiScaleY());
            MapLayout nextLayout = QuestUiScreenController.createMapLayout(chapter, bounds, virtualScale, nextZoom);
            double contentWidthCss = Math.max(bounds.width, Math.ceil(nextLayout.contentWidth() / nextLayout.guiScaleX()));
            double contentHeightCss = Math.max(bounds.height, Math.ceil(nextLayout.contentHeight() / nextLayout.guiScaleY()));
            double maxScrollLeft = Math.max(0.0, contentWidthCss - bounds.width);
            double maxScrollTop = Math.max(0.0, contentHeightCss - bounds.height);
            virtualScrollLeft = Math.max(0.0, Math.min(
                    (mapPointX * nextZoom + nextLayout.offsetX()) / nextLayout.guiScaleX() - anchorX,
                    maxScrollLeft));
            virtualScrollTop = Math.max(0.0, Math.min(
                    (mapPointY * nextZoom + nextLayout.offsetY()) / nextLayout.guiScaleY() - anchorY,
                    maxScrollTop));
            virtualZoom = nextZoom;
            virtualLayout = nextLayout;
            finalMapPointX = mapPointX;
            finalMapPointY = mapPointY;
            lastApplied = input;
            appliedEventCount++;
        }
        if (appliedEventCount == 0 || lastApplied == null
                || Math.abs(virtualZoom - oldZoom) < 1.0e-6
                && Math.abs(virtualScrollLeft - baseVirtualScrollLeft) <= 0.5
                && Math.abs(virtualScrollTop - baseVirtualScrollTop) <= 0.5) {
            return false;
        }

        mapZoom = virtualZoom;
        pendingMapCenter = false;
        snapMapCenterWithoutCache = false;
        pendingMapScrollRestore = true;
        pendingMapZoomAnchorRestore = true;
        pendingMapZoomAnchorAppliedImmediately = false;
        pendingMapZoomAnchor = new PendingMapZoomAnchor(chapter.id(), lastApplied.mouseX(), lastApplied.mouseY(), finalMapPointX, finalMapPointY);
        pendingMapScrollLeft = virtualScrollLeft;
        pendingMapScrollTop = virtualScrollTop;
        lastCenteredChapterId = chapter.id();
        QuestUiScreenController.syncMapNodesInPlace(true);
        if (mapLayout == null || mapLayout.chapterId() != chapter.id()
                || Math.abs(mapLayout.zoom() - mapZoom) > 1.0e-4) {
            mapZoom = oldZoom;
            pendingMapScrollRestore = false;
            pendingMapZoomAnchorRestore = false;
            pendingMapZoomAnchor = null;
            return false;
        }

        double cursorCssX = lastApplied.mouseX() / Math.max(0.01, mapLayout.guiScaleX());
        double cursorCssY = lastApplied.mouseY() / Math.max(0.01, mapLayout.guiScaleY());
        double anchorX = cursorCssX - bounds.x;
        double anchorY = cursorCssY - bounds.y;
        double contentWidthCss = Math.max(bounds.width, Math.ceil(mapLayout.contentWidth() / mapLayout.guiScaleX()));
        double contentHeightCss = Math.max(bounds.height, Math.ceil(mapLayout.contentHeight() / mapLayout.guiScaleY()));
        viewport.scrollWidth = contentWidthCss;
        viewport.scrollHeight = contentHeightCss;
        double maxScrollLeft = Math.max(0.0, contentWidthCss - bounds.width);
        double maxScrollTop = Math.max(0.0, contentHeightCss - bounds.height);
        pendingMapScrollLeft = Math.max(0.0, Math.min(
                (finalMapPointX * mapLayout.zoom() + mapLayout.offsetX()) / mapLayout.guiScaleX() - anchorX,
                maxScrollLeft));
        pendingMapScrollTop = Math.max(0.0, Math.min(
                (finalMapPointY * mapLayout.zoom() + mapLayout.offsetY()) / mapLayout.guiScaleY() - anchorY,
                maxScrollTop));
        observedMapScrollChapterId = chapter.id();
        observedMapScrollLeft = viewport.getScrollLeft();
        observedMapScrollTop = viewport.getScrollTop();
        observedMapTargetScrollLeft = viewport.getTargetScrollLeft();
        observedMapTargetScrollTop = viewport.getTargetScrollTop();
        pendingMapCenterSave = true;
        lastMapScrollMovementNanos = System.nanoTime();
        pendingMapZoomTrace = new MapZoomTrace(chapter.id(), totalWheelDelta, appliedEventCount,
                lastApplied.mouseX(), lastApplied.mouseY(), cursorCssX, cursorCssY, anchorX, anchorY,
                lastApplied.eventScaleX(), lastApplied.eventScaleY(), oldLayout.guiScaleX(), oldLayout.guiScaleY(),
                mapLayout.guiScaleX(), mapLayout.guiScaleY(), oldZoom, mapZoom, oldScrollLeft, oldScrollTop,
                oldTargetLeft, oldTargetTop, finalMapPointX, finalMapPointY, oldLayout.offsetX(), oldLayout.offsetY(),
                oldLayout.contentWidth(), oldLayout.contentHeight(), pendingMapScrollLeft, pendingMapScrollTop, false);
        QuestUiMod.LOGGER.info("[QuestUI MapZoomTrace] phase=apply {}", pendingMapZoomTrace);
        pendingMapZoomTraceWaitForFreshFrame = true;
        return true;
    }
    private static void logMapWheelDomTrace(long chapterId, Element viewport) {
        if (viewport == null) {
            return;
        }
        String trace = viewport.getAttribute("data-wheel-trace");
        if (trace == null || trace.isEmpty() || trace.equals(lastLoggedDomWheelTrace)) {
            return;
        }
        lastLoggedDomWheelTrace = trace;
        QuestUiMod.LOGGER.info("[QuestUI WheelTrace] phase=dom chapter={} scroll={},{} target={},{} {}",
            QuestUiScreenController.id(chapterId), viewport.getScrollLeft(), viewport.getScrollTop(),
            viewport.getTargetScrollLeft(), viewport.getTargetScrollTop(), trace);
    }

    private static void renderAuiMapNodeHoverLabelOverlay(GuiGraphics graphics, int[] mapScissorBounds) {
        MapNodeLabelFrame frame = mapNodeLabelFrame;
        mapNodeLabelFrame = null;
        if (document == null || !document.isActive() || frame == null
                || frame.label().document != document || !frame.label().isConnected()) {
            return;
        }
        Element viewportElement = QuestUiScreenController.element("map-viewport");
        var auiViewport = document.getViewport();
        int[] clip = QuestUiScreenController.hasUsableMapScissorBounds(mapScissorBounds)
                ? mapScissorBounds : viewportElement == null ? null : QuestUiScreenController.mapViewportScissorBounds(viewportElement);
        if (auiViewport == null || !QuestUiScreenController.hasUsableMapScissorBounds(clip)) {
            return;
        }
        double renderScale = Math.max(0.01, auiViewport.renderScale());
        float clipX = (float)(clip[0] / renderScale);
        float clipY = (float)(clip[1] / renderScale);
        float clipWidth = (float)((clip[2] - clip[0]) / renderScale);
        float clipHeight = (float)((clip[3] - clip[1]) / renderScale);
        float[] clipRadii = new float[4];
        QuestUiScreenController.flushAll(graphics);
        AuiRenderService.RenderStateScope renderState = AuiServices.render().pushFilterRenderState();
        try (Document.ContextScope ignored = Document.withContext(document);
             AuiRenderService.RenderStateScope ignoredRenderState = renderState == null
                     ? AuiRenderService.RenderStateScope.NOOP : renderState) {
            RectFrameCache.begin();
            TransformFrameCache.begin();
            LayoutMeasureCache.begin();
            StyleFrameCache.begin();
            Base.pushDepthTest(false);
            Base.pushDepthMode(false);
            FontDrawer.pushDocumentPixelScale(auiViewport.scissorScale());
            FilterRenderer.beginFrame();
            graphics.pose().pushPose();
            boolean maskScalePushed = false;
            boolean viewportMaskPushed = false;
            try {
                frame.paintStates().forEach((target, paintState) -> {
                    StyleFrameCache.put(target, paintState.style());
                    RectFrameCache.put(target, paintState.rect());
                    TransformFrameCache.put(target, paintState.transform());
                });
                Base.beginRendering();
                Mask.resetDepth();
                graphics.pose().scale(auiViewport.renderScale(), auiViewport.renderScale(), 1.0f);
                Mask.pushScissorScale(auiViewport.scissorScale(), graphics.pose());
                maskScalePushed = true;
                // A real outer mask survives the title's own overflow masks and filter composite.
                Mask.pushMask(graphics.pose(), clipX, clipY, clipWidth, clipHeight, clipRadii);
                viewportMaskPushed = true;
                graphics.pose().translate(0.0, 0.0, MAP_NODE_LABEL_Z);
                // Replay only the title's already-sampled AUI nodes. A second document draw
                // would advance the map's motion/scroll and rebuild hit testing a second time.
                boolean wasReplayActive = mapNodeLabelReplayActive;
                mapNodeLabelReplayActive = true;
                try {
                    for (RenderNode node : frame.nodes()) {
                        graphics.pose().pushPose();
                        try {
                            Base.resolveOffset(graphics.pose());
                            node.render(graphics.pose());
                        } finally {
                            graphics.pose().popPose();
                        }
                    }
                } finally {
                    mapNodeLabelReplayActive = wasReplayActive;
                }
            } finally {
                try {
                    // Flush while the overlay depth/blend/clip scope is still in effect.
                    Base.commitDraws();
                    QuestUiScreenController.flushAll(graphics);
                } finally {
                    try {
                        if (viewportMaskPushed) {
                            Mask.popMask(graphics.pose(), clipX, clipY, clipWidth, clipHeight, clipRadii);
                        }
                    } finally {
                        try {
                            FilterRenderer.endFrame();
                        } finally {
                            Mask.resetDepth();
                            if (maskScalePushed) {
                                Mask.popScissorScale();
                            }
                            graphics.pose().popPose();
                            FontDrawer.popDocumentPixelScale();
                            Base.popDepthMode();
                            Base.popDepthTest();
                            StyleFrameCache.end();
                            LayoutMeasureCache.end();
                            TransformFrameCache.end();
                            RectFrameCache.end();
                        }
                    }
                }
            }
        }
    }

    private static void captureMapNodeLabelFrame(MapHoverLabelElement label) {
        if (label.document != document || !QuestUiScreenController.ownsScreen(Minecraft.m_91087_().f_91080_)) {
            return;
        }
        // The frame cache contains interpolated styles only inside AUI's normal paint pass.
        // Capture here, rather than re-sampling transitions or reading their raw target styles.
        float opacity = Filter.getOpacity(label);
        if ("true".equals(label.getAttribute("aria-hidden"))
                && !Transition.isActive(label) && opacity <= 0.0f) {
            mapNodeLabelPhase = MapNodeLabelPhase.HIDDEN;
        }
        if (opacity <= 0.0f) {
            mapNodeLabelFrame = null;
            return;
        }
        List<RenderNode> nodes = new ArrayList<>();
        Map<Element, MapNodeLabelPaintState> paintStates = new IdentityHashMap<>();
        for (RenderNode node : document.getPaintList()) {
            Element target = RenderNode.getRenderNodeTarget(node);
            if (node != label.captureNode && RenderNode.isSameOrDescendant(target, label)) {
                nodes.add(node);
                QuestUiScreenController.captureMapNodeLabelPaintState(target, paintStates);
            }
        }
        for (Element ancestor = label.parentElement; ancestor != null; ancestor = ancestor.parentElement) {
            QuestUiScreenController.captureMapNodeLabelPaintState(ancestor, paintStates);
        }
        mapNodeLabelFrame = new MapNodeLabelFrame(label, List.copyOf(nodes), paintStates);
    }

    private static void captureMapNodeLabelPaintState(Element target, Map<Element, MapNodeLabelPaintState> paintStates) {
        if (!paintStates.containsKey(target)) {
            paintStates.put(target, new MapNodeLabelPaintState(target.getComputedStyle().clone(), Rect.of(target),
                    new Matrix4f(Base.prepareWorldTransform(target))));
        }
    }

    private static boolean renderNativeQuestMap(GuiGraphics graphics, int[] mapScissorBounds) {
        if (sourceQuestScreen == null || state == null || !ClientQuestFile.exists() || ClientQuestFile.INSTANCE.selfTeamData == null) {
            return false;
        }
        ChapterSnapshot chapter = state.selectedChapter().orElse(null);
        if (chapter == null) {
            return false;
        }
        Set<Long> visibleQuestIds = state.visibleQuests().stream().map(QuestSnapshot::id).collect(Collectors.toSet());
        int expectedVisibleNodes = 0;
        for (QuestMapNodeSnapshot node : chapter.mapNodes()) {
            if (node.visible() && visibleQuestIds.contains(node.questId())) {
                expectedVisibleNodes++;
            }
        }
        Element viewport = QuestUiScreenController.element("map-viewport");
        Element stage = QuestUiScreenController.element("map-stage");
        if (viewport == null || stage == null) {
            QuestUiScreenController.logMapWarningOnce(chapter.id(), "missing-map-elements", "visibleNodes=" + expectedVisibleNodes + " viewport=" + (viewport != null) + " stage=" + (stage != null));
            return false;
        }
        Element.DOMRect bounds = viewport.getBoundingClientRect();
        if (bounds == null || bounds.width <= 0.0 || bounds.height <= 0.0) {
            QuestUiScreenController.logMapWarningOnce(chapter.id(), "empty-viewport", "visibleNodes=" + expectedVisibleNodes + " viewport=" + QuestUiScreenController.formatMapRect(bounds));
            return false;
        }
        GuiScale guiScale = QuestUiScreenController.guiScale();
        if (mapLayout == null || !mapLayout.matches(chapter.id(), bounds, guiScale, mapZoom)) {
            QuestUiScreenController.renderMap();
            bounds = viewport.getBoundingClientRect();
            guiScale = QuestUiScreenController.guiScale();
        }
        if (mapLayout == null || mapLayout.chapterId() != chapter.id()) {
            QuestUiScreenController.logMapWarningOnce(chapter.id(), "layout-not-ready", "visibleNodes=" + expectedVisibleNodes + " viewport=" + QuestUiScreenController.formatMapRect(bounds) + " scale=" + guiScale.x() + "x" + guiScale.y());
            return false;
        }
        if (QuestUiScreenController.applyPendingMapZoomInputs(chapter, viewport, bounds)) {
            bounds = viewport.getBoundingClientRect();
            guiScale = new GuiScale(mapLayout.guiScaleX(), mapLayout.guiScaleY());
        }
        QuestUiScreenController.updateMapViewportInputSnapshot(chapter, viewport, bounds,
                new GuiScale(mapLayout.guiScaleX(), mapLayout.guiScaleY()));
        QuestUiScreenController.logMapWheelDomTrace(chapter.id(), viewport);
        Element.DOMRect stageBounds = stage.getBoundingClientRect();
        double measuredScrollWidth = viewport.scrollWidth;
        double measuredScrollHeight = viewport.scrollHeight;
        double expectedStageWidth = Math.ceil(mapLayout.contentWidth() / mapLayout.guiScaleX());
        double expectedStageHeight = Math.ceil(mapLayout.contentHeight() / mapLayout.guiScaleY());
        if (stageBounds.width > 0.0 && stageBounds.height > 0.0) {
            viewport.scrollWidth = Math.max(bounds.width, stageBounds.width);
            viewport.scrollHeight = Math.max(bounds.height, stageBounds.height);
        }
        double maxScrollLeft = Math.max(0.0, viewport.scrollWidth - bounds.width);
        double maxScrollTop = Math.max(0.0, viewport.scrollHeight - bounds.height);
        boolean stageLayoutReady = stageBounds.width > 0.0 && stageBounds.height > 0.0
                && Math.abs(stageBounds.width - expectedStageWidth) <= 1.0
                && Math.abs(stageBounds.height - expectedStageHeight) <= 1.0;
        String viewportClasses = viewport.getAttribute("class");
        boolean mapDragging = viewportClasses != null && viewportClasses.contains("dragging");
        if (mapDragging) {
            pendingMapCenter = false;
            snapMapCenterWithoutCache = false;
            pendingMapScrollRestore = false;
            pendingMapZoomAnchorRestore = false;
            pendingMapZoomAnchor = null;
            pendingMapZoomAnchorAppliedImmediately = false;
            pendingMapZoomTrace = null;
            pendingMapZoomTraceWaitForFreshFrame = false;
            pendingMapZoomInputs.clear();
            pendingMapScrollLeft = viewport.getTargetScrollLeft();
            pendingMapScrollTop = viewport.getTargetScrollTop();
            lastCenteredChapterId = chapter.id();
        }
        if (pendingMapZoomAnchorRestore && (pendingMapZoomAnchor == null || pendingMapZoomAnchor.chapterId() != chapter.id())) {
            pendingMapZoomAnchorRestore = false;
            pendingMapZoomAnchor = null;
            pendingMapZoomAnchorAppliedImmediately = false;
        }
        boolean restoringMapScroll = pendingMapScrollRestore;
        boolean restoringZoomAnchor = pendingMapZoomAnchorRestore;
        boolean centerMap = !restoringZoomAnchor && (pendingMapCenter || lastCenteredChapterId != chapter.id());
        boolean programmaticMapMovement = centerMap || restoringMapScroll;
        double requestedScrollLeft = viewport.getTargetScrollLeft();
        double requestedScrollTop = viewport.getTargetScrollTop();
        if (centerMap) {
            pendingMapCenter = true;
            pendingMapScrollRestore = false;
        }
        boolean scrollSettled = Math.abs(viewport.getScrollLeft() - viewport.getTargetScrollLeft()) <= 0.5
                && Math.abs(viewport.getScrollTop() - viewport.getTargetScrollTop()) <= 0.5;
        boolean stagePositionStable = !centerMap || stageLayoutReady && QuestUiScreenController.mapStagePositionStable(chapter.id(), stageBounds);
        if (centerMap && stageLayoutReady && !stagePositionStable) {
            QuestUiScreenController.logMapWarningOnce(chapter.id(), "stage-position-unstable", "stage=" + QuestUiScreenController.formatMapRect(stageBounds)
                    + " scrollMetrics=" + measuredScrollWidth + "x" + measuredScrollHeight
                    + " expectedStage=" + expectedStageWidth + "x" + expectedStageHeight);
        }
        if ((centerMap || pendingMapScrollRestore) && stageLayoutReady
                && (scrollSettled || restoringZoomAnchor) && (!centerMap || stagePositionStable)) {
            double targetLeft;
            double targetTop;
            boolean canApplyTarget = true;
            if (restoringZoomAnchor) {
                double cursorCssX = pendingMapZoomAnchor.mouseX() / Math.max(0.01, mapLayout.guiScaleX());
                double cursorCssY = pendingMapZoomAnchor.mouseY() / Math.max(0.01, mapLayout.guiScaleY());
                double anchorX = cursorCssX - bounds.x;
                double anchorY = cursorCssY - bounds.y;
                double desiredScrollLeft = (pendingMapZoomAnchor.mapPointX() * mapLayout.zoom() + mapLayout.offsetX())
                        / mapLayout.guiScaleX() - anchorX;
                double desiredScrollTop = (pendingMapZoomAnchor.mapPointY() * mapLayout.zoom() + mapLayout.offsetY())
                        / mapLayout.guiScaleY() - anchorY;
                targetLeft = Math.max(0.0, Math.min(desiredScrollLeft, maxScrollLeft));
                targetTop = Math.max(0.0, Math.min(desiredScrollTop, maxScrollTop));
            } else if (centerMap && expectedVisibleNodes > 0) {
                int[] currentNodeBounds = QuestUiScreenController.mapLayoutVisibleNodeBounds(chapter, visibleQuestIds, mapLayout, stageBounds, guiScale);
                int[] centerScissor = QuestUiScreenController.mapViewportScissorBounds(viewport);
                if (currentNodeBounds == null || centerScissor[2] <= centerScissor[0] || centerScissor[3] <= centerScissor[1]) {
                    QuestUiScreenController.logMapWarningOnce(chapter.id(), "center-geometry-unavailable", "visibleNodes=" + expectedVisibleNodes + " nodes=" + QuestUiScreenController.formatMapScissor(currentNodeBounds) + " scissor=" + QuestUiScreenController.formatMapScissor(centerScissor));
                    targetLeft = viewport.getTargetScrollLeft();
                    targetTop = viewport.getTargetScrollTop();
                    canApplyTarget = false;
                } else {
                    double nodeCenterX = (currentNodeBounds[0] + currentNodeBounds[2]) / (2.0 * guiScale.x());
                    double nodeCenterY = (currentNodeBounds[1] + currentNodeBounds[3]) / (2.0 * guiScale.y());
                    double viewportCenterX = (centerScissor[0] + centerScissor[2]) / (2.0 * guiScale.x());
                    double viewportCenterY = (centerScissor[1] + centerScissor[3]) / (2.0 * guiScale.y());
                    double desiredScrollLeft = viewport.getTargetScrollLeft() + nodeCenterX - viewportCenterX;
                    double desiredScrollTop = viewport.getTargetScrollTop() + nodeCenterY - viewportCenterY;
                    targetLeft = Math.max(0.0, Math.min(desiredScrollLeft, maxScrollLeft));
                    targetTop = Math.max(0.0, Math.min(desiredScrollTop, maxScrollTop));
                }
            } else {
                targetLeft = centerMap ? maxScrollLeft / 2.0 : Math.max(0.0, Math.min(pendingMapScrollLeft, maxScrollLeft));
                targetTop = centerMap ? maxScrollTop / 2.0 : Math.max(0.0, Math.min(pendingMapScrollTop, maxScrollTop));
            }
            if (canApplyTarget) {
                requestedScrollLeft = targetLeft;
                requestedScrollTop = targetTop;
                boolean scrollTargetChanged = Math.abs(targetLeft - viewport.getTargetScrollLeft()) > 0.5
                        || Math.abs(targetTop - viewport.getTargetScrollTop()) > 0.5;
                if (scrollTargetChanged) {
                    Element.DOMRect stageBoundsBeforeScroll = stageBounds;
                    boolean snappedDefaultCenter = centerMap && snapMapCenterWithoutCache
                            && QuestUiScreenController.scrollMapViewportImmediately(viewport, targetLeft, targetTop);
                    boolean snappedZoomAnchor = restoringZoomAnchor
                            && QuestUiScreenController.scrollMapViewportImmediately(viewport, targetLeft, targetTop);
                    if (!snappedDefaultCenter && !snappedZoomAnchor) {
                        viewport.scrollTo(targetLeft, targetTop);
                    }
                    if (snappedDefaultCenter || snappedZoomAnchor) {
                        Element.DOMRect refreshedStageBounds = stage.getBoundingClientRect();
                        if (refreshedStageBounds != null) {
                            stageBounds = refreshedStageBounds;
                        }
                        int[] nativeBoundsBefore = QuestUiScreenController.mapLayoutVisibleNodeBounds(
                                chapter, visibleQuestIds, mapLayout, stageBoundsBeforeScroll, guiScale);
                        int[] nativeBoundsAfter = QuestUiScreenController.mapLayoutVisibleNodeBounds(
                                chapter, visibleQuestIds, mapLayout, stageBounds, guiScale);
                        QuestUiMod.LOGGER.info("[QuestUI NativeStageTrace] mode={} stageBefore={} stageAfter={} nativeBoundsBefore={} nativeBoundsAfter={} scissor={} scroll={},{} target={},{}",
                                snappedZoomAnchor ? "zoom-anchor" : "center",
                                QuestUiScreenController.formatMapRect(stageBoundsBeforeScroll),
                                QuestUiScreenController.formatMapRect(stageBounds),
                                QuestUiScreenController.formatMapScissor(nativeBoundsBefore),
                                QuestUiScreenController.formatMapScissor(nativeBoundsAfter),
                                QuestUiScreenController.formatMapScissor(QuestUiScreenController.mapViewportScissorBounds(viewport)),
                                viewport.getScrollLeft(), viewport.getScrollTop(),
                                viewport.getTargetScrollLeft(), viewport.getTargetScrollTop());
                    }
                    if (centerMap) {
                        pendingMapCenter = true;
                        pendingMapScrollRestore = false;
                        scrollSettled = snappedDefaultCenter;
                    } else {
                        pendingMapScrollLeft = targetLeft;
                        pendingMapScrollTop = targetTop;
                        pendingMapScrollRestore = !snappedZoomAnchor;
                        if (restoringZoomAnchor) {
                            pendingMapZoomAnchorAppliedImmediately = snappedZoomAnchor;
                        }
                        if (snappedZoomAnchor) {
                            pendingMapZoomAnchorRestore = false;
                            pendingMapZoomAnchor = null;
                            scrollSettled = true;
                        } else {
                            scrollSettled = false;
                        }
                    }
                } else if (!centerMap) {
                    if (restoringZoomAnchor && !scrollSettled) {
                        pendingMapScrollRestore = true;
                    } else {
                        pendingMapScrollRestore = false;
                        pendingMapZoomAnchorRestore = false;
                        pendingMapZoomAnchor = null;
                    }
                }
            }
        }
        QuestUiScreenController.logPendingMapZoomTrace(chapter.id(), viewport, bounds, stageBounds, stageLayoutReady, scrollSettled, pendingMapScrollRestore);
        QuestUiScreenController.trackMapScrollForPersistence(chapter.id(), viewport, mapDragging, programmaticMapMovement, scrollSettled);
        int[] scissorBounds = QuestUiScreenController.hasUsableMapScissorBounds(mapScissorBounds)
                ? mapScissorBounds : QuestUiScreenController.mapViewportScissorBounds(viewport);
        if (bounds.width <= 0.0 || bounds.height <= 0.0 || !QuestUiScreenController.hasUsableMapScissorBounds(scissorBounds)) {
            Element detailPanel = QuestUiScreenController.element("quest-detail");
            Element.DOMRect detailBounds = detailPanel == null ? null : detailPanel.getBoundingClientRect();
            String panelState = detailPanel == null ? "absent" : String.valueOf(detailPanel.getAttribute("class")) + ":" + QuestUiScreenController.formatMapRect(detailBounds);
            QuestUiScreenController.logMapWarningOnce(chapter.id(), "scissor-rejected", "visibleNodes=" + expectedVisibleNodes + " viewport=" + QuestUiScreenController.formatMapRect(bounds) + " scissor=" + QuestUiScreenController.formatMapScissor(scissorBounds) + " panel=" + panelState + " scale=" + guiScale.x() + "x" + guiScale.y());
            return false;
        }
        int[] visibleNodeBounds = QuestUiScreenController.mapLayoutVisibleNodeBounds(chapter, visibleQuestIds, mapLayout, stageBounds, guiScale);
        double centerOffsetX = visibleNodeBounds == null ? Double.POSITIVE_INFINITY
                : (visibleNodeBounds[0] + visibleNodeBounds[2] - scissorBounds[0] - scissorBounds[2]) / 2.0;
        double centerOffsetY = visibleNodeBounds == null ? Double.POSITIVE_INFINITY
                : (visibleNodeBounds[1] + visibleNodeBounds[3] - scissorBounds[1] - scissorBounds[3]) / 2.0;
        boolean visibleNodeCentered = Math.abs(centerOffsetX) <= 2.0 && Math.abs(centerOffsetY) <= 2.0;
        boolean centerComplete = centerMap && scrollSettled && stagePositionStable
                && (expectedVisibleNodes == 0 || visibleNodeCentered);
        if (centerMap && expectedVisibleNodes > 0 && !visibleNodeCentered) {
            pendingMapCenter = true;
            QuestUiScreenController.logMapWarningOnce(chapter.id(), "center-not-settled", "nodes=" + QuestUiScreenController.formatMapScissor(visibleNodeBounds)
                    + " scissor=" + QuestUiScreenController.formatMapScissor(scissorBounds)
                    + " centerOffset=" + centerOffsetX + "," + centerOffsetY
                    + " requestedScroll=" + requestedScrollLeft + "," + requestedScrollTop
                    + " currentScroll=" + viewport.getScrollLeft() + "," + viewport.getScrollTop()
                    + " targetScroll=" + viewport.getTargetScrollLeft() + "," + viewport.getTargetScrollTop()
                    + " scrollMetrics=" + measuredScrollWidth + "x" + measuredScrollHeight + "->" + viewport.scrollWidth + "x" + viewport.scrollHeight
                    + " viewport=" + bounds.width + "x" + bounds.height + " stage=" + QuestUiScreenController.formatMapRect(stageBounds)
                    + " geometry=" + QuestUiScreenController.mapNodeGeometrySummary(chapter, visibleQuestIds, mapLayout, stageBounds, guiScale));
        }
        if (centerComplete) {
            lastCenteredChapterId = chapter.id();
            pendingMapCenter = false;
            if (snapMapCenterWithoutCache && expectedVisibleNodes > 0) {
                QuestUiScreenController.persistCurrentMapCenter();
            }
            snapMapCenterWithoutCache = false;
        } else if (centerMap) {
            pendingMapCenter = true;
        }
        graphics.enableScissor(scissorBounds[0], scissorBounds[1], scissorBounds[2], scissorBounds[3]);
        try {
            QuestUiScreenController.drawDependencyLines(graphics, chapter, visibleQuestIds, mapLayout, stageBounds, guiScale);
            QuestUiScreenController.ensureNativeQuestButtons(chapter);
            Theme theme = sourceQuestScreen.getTheme();
            int nativeButtonCount = 0;
            int missingButtonCount = 0;
            int layoutRectCount = 0;
            int missingGeometryCount = 0;
            int drawCallCount = 0;
            int scissorIntersectingDrawCalls = 0;
            int minDrawX = Integer.MAX_VALUE;
            int minDrawY = Integer.MAX_VALUE;
            int maxDrawX = Integer.MIN_VALUE;
            int maxDrawY = Integer.MIN_VALUE;
            int detailedNodeCount = 0;
            StringBuilder nodeDetails = new StringBuilder();
            for (QuestMapNodeSnapshot node : chapter.mapNodes()) {
                if (!node.visible() || !visibleQuestIds.contains(node.questId())) {
                    continue;
                }
                QuestButton button = nativeQuestButtons.get(node.id());
                if (button == null) {
                    missingButtonCount++;
                    continue;
                }
                nativeButtonCount++;
                MapNodePlacement placement = mapLayout.placements().get(node.id());
                if (placement == null) {
                    missingGeometryCount++;
                    continue;
                }
                double preciseX = stageBounds.x * guiScale.x() + placement.x();
                double preciseY = stageBounds.y * guiScale.y() + placement.y();
                int x = (int)Math.round(preciseX);
                int y = (int)Math.round(preciseY);
                int width = Math.max(1, (int)Math.round(placement.width()));
                int height = Math.max(1, (int)Math.round(placement.height()));
                button.setPosAndSize(x, y, width, height);
                layoutRectCount++;
                if (preciseX < scissorBounds[2] && preciseX + width > scissorBounds[0]
                        && preciseY < scissorBounds[3] && preciseY + height > scissorBounds[1]) {
                    scissorIntersectingDrawCalls++;
                }
                if (DETAILED_MAP_DIAGNOSTICS && detailedNodeCount < 4) {
                    if (nodeDetails.length() > 0) {
                        nodeDetails.append("; ");
                    }
                    nodeDetails.append("node=").append(QuestUiScreenController.id(node.id()))
                            .append(" screen=[").append(x).append(',').append(y).append(',').append(width).append(',').append(height).append(']')
                            .append(" placement=[").append(placement.x()).append(',').append(placement.y()).append(',').append(placement.width()).append(',').append(placement.height()).append(']');
                    detailedNodeCount++;
                }
                graphics.pose().pushPose();
                try {
                    graphics.pose().translate(preciseX - x, preciseY - y, MAP_QUEST_NODE_Z);
                    button.draw(graphics, theme, x, y, width, height);
                } finally {
                    graphics.pose().popPose();
                }
                drawCallCount++;
                minDrawX = Math.min(minDrawX, x);
                minDrawY = Math.min(minDrawY, y);
                maxDrawX = Math.max(maxDrawX, x + width);
                maxDrawY = Math.max(maxDrawY, y + height);
            }
            QuestUiScreenController.flushAll(graphics);
            String drawBounds = drawCallCount == 0 ? "none" : "[" + minDrawX + "," + minDrawY + "-" + maxDrawX + "," + maxDrawY + "]";
            String scissor = QuestUiScreenController.formatMapScissor(scissorBounds);
            String drawResult = expectedVisibleNodes == 0 ? "NO_VISIBLE_NODES"
                    : drawCallCount != expectedVisibleNodes || missingButtonCount != 0 || missingGeometryCount != 0 ? "DRAW_CALLS_INCOMPLETE"
                    : scissorIntersectingDrawCalls == 0 ? "DRAW_CALLS_OFFSCREEN"
                    : scissorIntersectingDrawCalls < drawCallCount ? "DRAW_CALLS_PARTLY_CLIPPED"
                    : "DRAW_CALLS_COMPLETE";
            if ((!centerMap || centerComplete) && mapDiagnosticsLogged.add(chapter.id())) {
                Element detailPanel = QuestUiScreenController.element("quest-detail");
                Element.DOMRect detailBounds = detailPanel == null ? null : detailPanel.getBoundingClientRect();
                String panelState = detailPanel == null ? "absent" : String.valueOf(detailPanel.getAttribute("class")) + ":" + QuestUiScreenController.formatMapRect(detailBounds);
                QuestUiMod.LOGGER.info("[QuestUI MapCheck] chapter={} result={} visibleNodes={} nativeButtons={} missingButtons={} drawCalls={} scissorIntersecting={} layoutRects={} missingGeometry={} drawBounds={} scissor={} viewport={} stage={} stageReady={} centerRequested={} chapterCentered={} requestedScroll={},{} scale={}x{} scroll={},{} targetScroll={},{} panel={}",
                        QuestUiScreenController.id(chapter.id()), drawResult, expectedVisibleNodes, nativeButtonCount, missingButtonCount, drawCallCount, scissorIntersectingDrawCalls,
                        layoutRectCount, missingGeometryCount, drawBounds, scissor, QuestUiScreenController.formatMapRect(bounds),
                        QuestUiScreenController.formatMapRect(stageBounds), stageLayoutReady, centerMap, lastCenteredChapterId == chapter.id(), requestedScrollLeft, requestedScrollTop, guiScale.x(), guiScale.y(),
                        viewport.scrollLeft, viewport.scrollTop, viewport.getTargetScrollLeft(), viewport.getTargetScrollTop(), panelState);
                if (DETAILED_MAP_DIAGNOSTICS && nodeDetails.length() > 0) {
                    QuestUiMod.LOGGER.info("[QuestUI MapCheckNodes] chapter={} {}", QuestUiScreenController.id(chapter.id()), nodeDetails.toString());
                }
            }
            if (expectedVisibleNodes > 0 && drawCallCount < expectedVisibleNodes) {
                QuestUiScreenController.logMapWarningOnce(chapter.id(), "incomplete-draws", "visibleNodes=" + expectedVisibleNodes + " nativeButtons=" + nativeButtonCount + " missingButtons=" + missingButtonCount + " drawCalls=" + drawCallCount + " missingGeometry=" + missingGeometryCount);
            } else if (drawCallCount > 0 && scissorIntersectingDrawCalls == 0) {
                QuestUiScreenController.logMapWarningOnce(chapter.id(), "draws-outside-scissor", "drawCalls=" + drawCallCount + " drawBounds=" + drawBounds + " scissor=" + scissor);
            } else if (scissorIntersectingDrawCalls < drawCallCount) {
                QuestUiScreenController.logMapWarningOnce(chapter.id(), "draws-partly-outside-scissor", "drawCalls=" + drawCallCount + " scissorIntersecting=" + scissorIntersectingDrawCalls + " scissor=" + scissor);
            }
            return true;
        }
        finally {
            graphics.m_280618_();
        }
    }

    private static boolean enableElementScissor(GuiGraphics graphics, Element element) {
        Element.DOMRect bounds = element.getBoundingClientRect();
        GuiScale scale = QuestUiScreenController.guiScale();
        Window window = Minecraft.m_91087_().m_91268_();
        int left = Math.max(0, (int)Math.floor(bounds.x * scale.x()));
        int top = Math.max(0, (int)Math.floor(bounds.y * scale.y()));
        int right = Math.min(window.m_85445_(), (int)Math.ceil((bounds.x + bounds.width) * scale.x()));
        int bottom = Math.min(window.m_85446_(), (int)Math.ceil((bounds.y + bounds.height) * scale.y()));
        if (right <= left || bottom <= top) {
            return false;
        }
        graphics.m_280588_(left, top, right, bottom);
        return true;
    }

    private static void logMapWarningOnce(long chapterId, String reason, String details) {
        String key = QuestUiScreenController.id(chapterId) + ":" + reason;
        if (mapDiagnosticWarningsLogged.add(key)) {
            QuestUiMod.LOGGER.warn("[QuestUI MapCheck] chapter={} status={} {}", QuestUiScreenController.id(chapterId), reason, details);
        }
    }

    private static String formatMapRect(Element.DOMRect bounds) {
        return bounds == null ? "none" : "[" + bounds.x + "," + bounds.y + "," + bounds.width + "," + bounds.height + "]";
    }


    private static boolean scrollMapViewportImmediately(Element viewport, double left, double top) {
        if (viewport == null) {
            return false;
        }
        try {
            if (!apricityScrollAccessInitialized) {
                apricityScrollAccessInitialized = true;
                Field scrollModelField = Element.class.getDeclaredField("scroll");
                scrollModelField.setAccessible(true);
                Object scrollModel = scrollModelField.get(viewport);
                if (scrollModel == null) {
                    throw new IllegalStateException("ApricityUI Element has no scroll model");
                }
                Method setScrollImmediate = scrollModel.getClass().getDeclaredMethod("setScrollImmediate", boolean.class, double.class);
                setScrollImmediate.setAccessible(true);
                apricityScrollModelField = scrollModelField;
                apricityScrollImmediateMethod = setScrollImmediate;
            }
            if (apricityScrollModelField == null || apricityScrollImmediateMethod == null) {
                return false;
            }
            Object scrollModel = apricityScrollModelField.get(viewport);
            if (scrollModel == null) {
                return false;
            }
            // ApricityUI 1.2.5 has no public instant scroll API; use immediate positioning for fresh centering and cursor-anchored zoom.
            apricityScrollImmediateMethod.invoke(scrollModel, false, left);
            apricityScrollImmediateMethod.invoke(scrollModel, true, top);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (!apricityScrollAccessWarningLogged) {
                apricityScrollAccessWarningLogged = true;
                QuestUiMod.LOGGER.warn("Could not access ApricityUI immediate scroll; falling back to animated positioning", exception);
            }
            return false;
        }
    }

    private static void trackMapScrollForPersistence(long chapterId, Element viewport, boolean dragging, boolean programmaticMovement, boolean scrollSettled) {
        if (viewport == null) {
            return;
        }
        double scrollLeft = viewport.getScrollLeft();
        double scrollTop = viewport.getScrollTop();
        double targetScrollLeft = viewport.getTargetScrollLeft();
        double targetScrollTop = viewport.getTargetScrollTop();
        if (!Double.isFinite(scrollLeft) || !Double.isFinite(scrollTop)
                || !Double.isFinite(targetScrollLeft) || !Double.isFinite(targetScrollTop)) {
            return;
        }
        long nowNanos = System.nanoTime();
        if (observedMapScrollChapterId != chapterId) {
            observedMapScrollChapterId = chapterId;
            observedMapScrollLeft = scrollLeft;
            observedMapScrollTop = scrollTop;
            observedMapTargetScrollLeft = targetScrollLeft;
            observedMapTargetScrollTop = targetScrollTop;
            lastMapScrollMovementNanos = 0L;
            pendingMapCenterSave = false;
            return;
        }
        boolean scrollChanged = Math.abs(scrollLeft - observedMapScrollLeft) > 0.1
                || Math.abs(scrollTop - observedMapScrollTop) > 0.1
                || Math.abs(targetScrollLeft - observedMapTargetScrollLeft) > 0.1
                || Math.abs(targetScrollTop - observedMapTargetScrollTop) > 0.1;
        observedMapScrollLeft = scrollLeft;
        observedMapScrollTop = scrollTop;
        observedMapTargetScrollLeft = targetScrollLeft;
        observedMapTargetScrollTop = targetScrollTop;
        if (scrollChanged) {
            if (!programmaticMovement) {
                pendingMapCenterSave = true;
            }
            if (pendingMapCenterSave) {
                lastMapScrollMovementNanos = nowNanos;
            }
        }
        if (!pendingMapCenterSave || programmaticMovement || dragging || !scrollSettled
                || nowNanos - lastMapScrollMovementNanos < MAP_SCROLL_SAVE_QUIET_NANOS) {
            return;
        }
        QuestUiScreenController.persistCurrentMapCenter();
        pendingMapCenterSave = false;
    }

    private static void resetMapScrollPersistenceTracking() {
        observedMapScrollChapterId = Long.MIN_VALUE;
        observedMapScrollLeft = Double.NaN;
        observedMapScrollTop = Double.NaN;
        observedMapTargetScrollLeft = Double.NaN;
        observedMapTargetScrollTop = Double.NaN;
        lastMapScrollMovementNanos = 0L;
        pendingMapCenterSave = false;
    }

    private static boolean mapStagePositionStable(long chapterId, Element.DOMRect stageBounds) {
            if (mapCenterStageChapterId != chapterId || !Double.isFinite(mapCenterStageX) || !Double.isFinite(mapCenterStageY)) {
                mapCenterStageChapterId = chapterId;
                mapCenterStageX = stageBounds.x;
                mapCenterStageY = stageBounds.y;
                mapCenterStableSamples = 0;
                return false;
            }
            if (Math.abs(stageBounds.x - mapCenterStageX) > 1.0 || Math.abs(stageBounds.y - mapCenterStageY) > 1.0) {
                mapCenterStageX = stageBounds.x;
                mapCenterStageY = stageBounds.y;
                mapCenterStableSamples = 0;
                return false;
            }
            return ++mapCenterStableSamples >= 1;
        }

    private static String mapNodeGeometrySummary(ChapterSnapshot chapter, Set<Long> visibleQuestIds, MapLayout layout, Element.DOMRect stageBounds, GuiScale scale) {
            StringBuilder geometry = new StringBuilder();
            int count = 0;
            for (QuestMapNodeSnapshot node : chapter.mapNodes()) {
                if (!node.visible() || !visibleQuestIds.contains(node.questId())) {
                    continue;
                }
                MapNodePlacement placement = layout.placements().get(node.id());
                if (placement == null) {
                    continue;
                }
                Element mapNode = QuestUiScreenController.element(QuestUiScreenController.mapNodeId(node.id()));
                Element.DOMRect nodeBounds = mapNode == null ? null : mapNode.getBoundingClientRect();
                int x = (int)Math.round(stageBounds.x * scale.x() + placement.x());
                int y = (int)Math.round(stageBounds.y * scale.y() + placement.y());
                int width = Math.max(1, (int)Math.round(placement.width()));
                int height = Math.max(1, (int)Math.round(placement.height()));
                if (geometry.length() > 0) {
                    geometry.append("; ");
                }
                geometry.append("node=").append(QuestUiScreenController.id(node.id()))
                        .append(" predictedGUI=[").append(x).append(',').append(y).append(',').append(width).append(',').append(height).append(']')
                        .append(" domCSS=").append(QuestUiScreenController.formatMapRect(nodeBounds));
                if (nodeBounds != null) {
                    geometry.append(" domGUI=[").append(Math.round(nodeBounds.x * scale.x())).append(',').append(Math.round(nodeBounds.y * scale.y()))
                            .append(',').append(Math.ceil(nodeBounds.width * scale.x())).append(',').append(Math.ceil(nodeBounds.height * scale.y())).append(']');
                }
                if (++count >= 3) {
                    break;
                }
            }
            return geometry.length() == 0 ? "none" : geometry.toString();
        }

    private static int[] mapLayoutVisibleNodeBounds(ChapterSnapshot chapter, Set<Long> visibleQuestIds, MapLayout layout, Element.DOMRect stageBounds, GuiScale scale) {
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            for (QuestMapNodeSnapshot node : chapter.mapNodes()) {
                if (!node.visible() || !visibleQuestIds.contains(node.questId())) {
                    continue;
                }
                MapNodePlacement placement = layout.placements().get(node.id());
                if (placement == null) {
                    continue;
                }
                int x = (int)Math.round(stageBounds.x * scale.x() + placement.x());
                int y = (int)Math.round(stageBounds.y * scale.y() + placement.y());
                int width = Math.max(1, (int)Math.round(placement.width()));
                int height = Math.max(1, (int)Math.round(placement.height()));
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x + width);
                maxY = Math.max(maxY, y + height);
            }
            return minX == Integer.MAX_VALUE ? null : new int[]{minX, minY, maxX, maxY};
        }

    private static int[] mapViewportScissorBounds(Element viewport) {
        Element.DOMRect bounds = viewport.getBoundingClientRect();
        if (bounds == null) {
            return new int[]{0, 0, 0, 0};
        }
        GuiScale scale = QuestUiScreenController.guiScale();
        Window window = Minecraft.getInstance().getWindow();
        double leftCss = bounds.x;
        double rightCss = bounds.x + bounds.width;
        Element chapterPanel = QuestUiScreenController.element("chapter-panel");
        if (chapterPanel != null) {
            Element.DOMRect chapterBounds = chapterPanel.getBoundingClientRect();
            if (chapterBounds != null) {
                boolean overlapsViewport = bounds.x < chapterBounds.x + chapterBounds.width
                        && bounds.x + bounds.width > chapterBounds.x
                        && bounds.y < chapterBounds.y + chapterBounds.height
                        && bounds.y + bounds.height > chapterBounds.y;
                if (chapterBounds.width > 0.0 && chapterBounds.height > 0.0 && overlapsViewport) {
                    leftCss = Math.max(leftCss, chapterBounds.x + chapterBounds.width);
                }
            }
        }
        Element panel = QuestUiScreenController.element("quest-detail");
        if (panel != null && !"detail-panel hidden".equals(panel.getAttribute("class"))) {
            Element.DOMRect panelBounds = panel.getBoundingClientRect();
            boolean overlapsViewport = bounds.x < panelBounds.x + panelBounds.width
                    && bounds.x + bounds.width > panelBounds.x
                    && bounds.y < panelBounds.y + panelBounds.height
                    && bounds.y + bounds.height > panelBounds.y;
            if (panelBounds.width > 0.0 && panelBounds.height > 0.0 && overlapsViewport) {
                rightCss = Math.min(rightCss, Math.max(bounds.x, panelBounds.x));
            }
        }
        int left = Math.max(0, (int)Math.floor(leftCss * scale.x()));
        int top = Math.max(0, (int)Math.floor(bounds.y * scale.y()));
        int right = Math.min(window.getGuiScaledWidth(), (int)Math.ceil(rightCss * scale.x()));
        int bottom = Math.min(window.getGuiScaledHeight(), (int)Math.ceil((bounds.y + bounds.height) * scale.y()));
        return new int[]{left, top, right, bottom};
    }

    private static String formatMapScissor(int[] bounds) {
        return bounds == null || bounds.length < 4 ? "none" : "[" + bounds[0] + "," + bounds[1] + "," + bounds[2] + "," + bounds[3] + "]";
    }

    private static boolean hasUsableMapScissorBounds(int[] bounds) {
        return bounds != null && bounds.length >= 4 && bounds[2] > bounds[0] && bounds[3] > bounds[1];
    }

    private static Element createMapHoverLabel() {
        mapNodeLabelPhase = MapNodeLabelPhase.HIDDEN;
        mapNodeLabelFrame = null;
        mapNodeLabelShowPending = false;
        Element label = new MapHoverLabelElement(document, "div", true);
        label.setAttribute("id", "map-hover-label");
        label.setAttribute("class", "map-hover-label");
        label.setAttribute("aria-hidden", "true");
        Element accent = new MapHoverLabelElement(document, "span", false);
        accent.setAttribute("class", "map-hover-label-accent");
        label.appendChild(accent);
        Element text = new MapHoverLabelElement(document, "span", false);
        text.setAttribute("id", "map-hover-label-text");
        text.setAttribute("class", "map-hover-label-text");
        label.appendChild(text);
        return label;
    }

    private static void resetMapNodeLabelHover() {
        hoveredMapNodeLabelId = Long.MIN_VALUE;
        mapNodeLabelShowPending = false;
        Element label = QuestUiScreenController.element("map-hover-label");
        if (label == null) {
            mapNodeLabelPhase = MapNodeLabelPhase.HIDDEN;
            mapNodeLabelFrame = null;
            return;
        }
        if ("map-hover-label visible".equals(label.getAttribute("class"))) {
            mapNodeLabelPhase = MapNodeLabelPhase.FADING_OUT;
        }
        if (!"map-hover-label".equals(label.getAttribute("class"))) {
            label.setAttribute("class", "map-hover-label");
        }
        if (!"true".equals(label.getAttribute("aria-hidden"))) {
            label.setAttribute("aria-hidden", "true");
        }
    }

    private static void updateMapNodeHoverLabel() {
        ChapterSnapshot chapter = state == null ? null : state.selectedChapter().orElse(null);
        Element viewport = QuestUiScreenController.element("map-viewport");
        Element stage = QuestUiScreenController.element("map-stage");
        Element label = QuestUiScreenController.element("map-hover-label");
        Element labelText = QuestUiScreenController.element("map-hover-label-text");
        if (chapter == null || viewport == null || stage == null || label == null || labelText == null || mapLayout == null) {
            QuestUiScreenController.resetMapNodeLabelHover();
            return;
        }
        Element.DOMRect stageBounds = stage.getBoundingClientRect();
        Element.DOMRect viewportBounds = viewport.getBoundingClientRect();
        if (stageBounds == null || viewportBounds == null || stageBounds.width <= 0.0 || stageBounds.height <= 0.0
                || viewportBounds.width <= 0.0 || viewportBounds.height <= 0.0) {
            QuestUiScreenController.resetMapNodeLabelHover();
            return;
        }
        var auiViewport = document.getViewport();
        double renderScale = auiViewport == null ? mapLayout.guiScaleX() : auiViewport.renderScale();
        renderScale = Math.max(0.01, renderScale);
        double visibleLeftCss = viewportBounds.x;
        double visibleTopCss = viewportBounds.y;
        double visibleRightCss = viewportBounds.x + viewportBounds.width;
        double visibleBottomCss = viewportBounds.y + viewportBounds.height;
        int[] scissorBounds = QuestUiScreenController.mapViewportScissorBounds(viewport);
        if (QuestUiScreenController.hasUsableMapScissorBounds(scissorBounds)) {
            visibleLeftCss = Math.max(visibleLeftCss, scissorBounds[0] / renderScale);
            visibleTopCss = Math.max(visibleTopCss, scissorBounds[1] / renderScale);
            visibleRightCss = Math.min(visibleRightCss, scissorBounds[2] / renderScale);
            visibleBottomCss = Math.min(visibleBottomCss, scissorBounds[3] / renderScale);
        }
        double clipLeft = visibleLeftCss - stageBounds.x;
        double clipTop = visibleTopCss - stageBounds.y;
        double clipRight = visibleRightCss - stageBounds.x;
        double clipBottom = visibleBottomCss - stageBounds.y;
        double labelWidth = Math.max(1.0, Math.min(MAP_HOVER_LABEL_WIDTH_CSS, clipRight - clipLeft - 8.0));
        Set<Long> visibleQuestIds = state.visibleQuests().stream().map(QuestSnapshot::id).collect(Collectors.toSet());
        Map<Long, QuestSnapshot> quests = chapter.quests().stream()
                .collect(Collectors.toMap(QuestSnapshot::id, quest -> quest, (first, ignored) -> first));
        for (QuestMapNodeSnapshot node : chapter.mapNodes()) {
            if (!node.visible() || !visibleQuestIds.contains(node.questId())) {
                continue;
            }
            Element mapNode = QuestUiScreenController.element(QuestUiScreenController.mapNodeId(node.id()));
            if (mapNode == null || !mapNode.isHover) {
                continue;
            }
            QuestSnapshot quest = quests.get(node.questId());
            if (quest == null || quest.title() == null || quest.title().isBlank()) {
                continue;
            }
            MapNodePlacement placement = mapLayout.placements().get(node.id());
            if (placement == null || placement.width() <= 0.0 || placement.height() <= 0.0) {
                continue;
            }
            double nativeNodeLeft = stageBounds.x * mapLayout.guiScaleX() + placement.x();
            double nativeNodeTop = stageBounds.y * mapLayout.guiScaleY() + placement.y();
            double nodeCenter = (nativeNodeLeft + placement.width() / 2.0) / renderScale - stageBounds.x;
            double nodeTop = nativeNodeTop / renderScale - stageBounds.y;
            double nodeBottom = (nativeNodeTop + placement.height()) / renderScale - stageBounds.y;
            double left = nodeCenter - labelWidth / 2.0;
            left = Math.max(clipLeft + 4.0, Math.min(left, clipRight - labelWidth - 4.0));
            double gap = MAP_HOVER_LABEL_GAP_CSS;
            double top = nodeTop - MAP_HOVER_LABEL_HEIGHT_CSS - gap;
            if (top < clipTop + 4.0) {
                top = nodeBottom + gap;
            }
            top = Math.max(clipTop + 4.0, Math.min(top, clipBottom - MAP_HOVER_LABEL_HEIGHT_CSS - 4.0));
            double roundedLeft = Math.round(left * 100.0) / 100.0;
            double roundedTop = Math.round(top * 100.0) / 100.0;
            String labelStyle = "left: " + roundedLeft + "px; top: " + roundedTop + "px; width: " + Math.round(labelWidth * 100.0) / 100.0 + "px;";
            if (!labelStyle.equals(label.getAttribute("style"))) {
                label.setAttribute("style", labelStyle);
            }
            if (!quest.title().equals(labelText.getTextContent())) {
                labelText.setTextContent(quest.title());
            }

            if (hoveredMapNodeLabelId != node.id()) {
                boolean labelAlreadyVisible = "map-hover-label visible".equals(label.getAttribute("class"));
                boolean reversingFadeOut = mapNodeLabelPhase == MapNodeLabelPhase.FADING_OUT;
                hoveredMapNodeLabelId = node.id();
                if (labelAlreadyVisible || reversingFadeOut) {
                    mapNodeLabelShowPending = false;
                    mapNodeLabelPhase = MapNodeLabelPhase.VISIBLE;
                    if (!labelAlreadyVisible) {
                        label.setAttribute("class", "map-hover-label visible");
                    }
                    label.setAttribute("aria-hidden", "false");
                } else {
                    mapNodeLabelShowPending = true;
                    mapNodeLabelPhase = MapNodeLabelPhase.HIDDEN;
                    if (!"map-hover-label".equals(label.getAttribute("class"))) {
                        label.setAttribute("class", "map-hover-label");
                    }
                    label.setAttribute("aria-hidden", "true");
                }
                return;
            }
            if (mapNodeLabelShowPending || !"map-hover-label visible".equals(label.getAttribute("class"))) {
                label.setAttribute("class", "map-hover-label visible");
                label.setAttribute("aria-hidden", "false");
                mapNodeLabelShowPending = false;
                mapNodeLabelPhase = MapNodeLabelPhase.VISIBLE;
            }
            return;
        }
        QuestUiScreenController.resetMapNodeLabelHover();
    }

    private static void flushAll(GuiGraphics graphics) {
        graphics.flush();
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
    }

    private static void ensureNativeQuestButtons(ChapterSnapshot chapter) {
        if (nativeButtonsChapterId != chapter.id()) {
            nativeQuestButtons.clear();
            nativeButtonsChapterId = chapter.id();
        }
        QuestPanel parent = QuestUiScreenController.sourceQuestScreen.questPanel;
        for (QuestMapNodeSnapshot node : chapter.mapNodes()) {
            QuestButton button;
            Quest quest;
            if (nativeQuestButtons.containsKey(node.id()) || (quest = ClientQuestFile.INSTANCE.getQuest(node.questId())) == null) continue;
            if (node.link()) {
                QuestObject object = ClientQuestFile.INSTANCE.get(node.id());
                if (!(object instanceof QuestLink)) continue;
                QuestLink link = (QuestLink)object;
                button = new QuestLinkButton(parent, link, quest);
            } else {
                button = new QuestButton((Panel)parent, quest);
            }
            nativeQuestButtons.put(node.id(), button);
        }
    }

    private static void syncFtbChapter(ChapterSnapshot chapter) {
        if (sourceQuestScreen == null || !ClientQuestFile.exists()) {
            return;
        }
        Chapter actualChapter = ClientQuestFile.INSTANCE.getChapter(chapter.id());
        if (actualChapter != null) {
            if (initialFtbChapterRefresh) {
                sourceQuestScreen.selectChapter(null);
                initialFtbChapterRefresh = false;
            }
            sourceQuestScreen.selectChapter(actualChapter);
            QuestUiScreenController.persistFtbChapter(chapter.id());
        }
    }

    private static long readPersistedFtbChapterId(QuestScreen screen) {
        if (screen == null) {
            return 0L;
        }
        try {
            QuestScreen.PersistedData data = screen.getPersistedScreenData();
            return QuestUiScreenController.persistedChapterField(data).getLong(data);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            QuestUiScreenController.logFtbPersistWarning("read", exception);
            return 0L;
        }
    }

    private static void persistFtbChapter(long chapterId) {
        if (sourceQuestScreen == null || !ClientQuestFile.exists()) {
            return;
        }
        try {
            QuestScreen.PersistedData data = sourceQuestScreen.getPersistedScreenData();
            QuestUiScreenController.persistedChapterField(data).setLong(data, chapterId);
            ClientQuestFile.INSTANCE.setPersistedScreenInfo(data);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            QuestUiScreenController.logFtbPersistWarning("write", exception);
        }
    }

    private static Field persistedChapterField(QuestScreen.PersistedData data) throws NoSuchFieldException {
        Field field = data.getClass().getDeclaredField("selectedChapter");
        field.setAccessible(true);
        return field;
    }

    private static void logFtbPersistWarning(String operation, Exception exception) {
        if (!ftbPersistWarningLogged) {
            ftbPersistWarningLogged = true;
            QuestUiMod.LOGGER.warn("Could not {} FTB Quests persisted chapter; falling back to the default chapter", operation, exception);
        }
    }

    private static Path mapCenterCachePath() {
        return Minecraft.m_91087_().f_91069_.toPath().resolve("config").resolve("questui-map-centers.properties");
    }

    private static String mapCenterCachePrefix(ChapterSnapshot chapter) {
        String bookTitle = state == null ? "" : state.snapshot().title();
        return "book." + Integer.toHexString(bookTitle.hashCode()) + ".chapter." + QuestUiScreenController.id(chapter.id());
    }

    private static void loadMapCenterCache() {
        if (mapCenterCacheLoaded) {
            return;
        }
        mapCenterCacheLoaded = true;
        try {
            Path path = QuestUiScreenController.mapCenterCachePath();
            if (!Files.isRegularFile(path)) {
                return;
            }
            try (InputStream input = Files.newInputStream(path)) {
                MAP_CENTER_CACHE.load(input);
            }
        } catch (IOException | RuntimeException exception) {
            MAP_CENTER_CACHE.clear();
            QuestUiScreenController.logMapCenterCacheWarning("read", exception);
        }
    }

    private static void saveMapCenterCache() {
        try {
            Path path = QuestUiScreenController.mapCenterCachePath();
            Files.createDirectories(path.getParent());
            try (OutputStream output = Files.newOutputStream(path)) {
                MAP_CENTER_CACHE.store(output, "QuestUI per-chapter map center cache");
            }
        } catch (IOException | RuntimeException exception) {
            QuestUiScreenController.logMapCenterCacheWarning("write", exception);
        }
    }

    private static MapCenter readCachedMapCenter(ChapterSnapshot chapter) {
        QuestUiScreenController.loadMapCenterCache();
        String prefix = QuestUiScreenController.mapCenterCachePrefix(chapter);
        String xValue = MAP_CENTER_CACHE.getProperty(prefix + ".x");
        String yValue = MAP_CENTER_CACHE.getProperty(prefix + ".y");
        String zoomValue = MAP_CENTER_CACHE.getProperty(prefix + ".zoom");
        if (xValue == null || yValue == null) {
            return null;
        }
        try {
            double xRatio = Double.parseDouble(xValue);
            double yRatio = Double.parseDouble(yValue);
            if (!Double.isFinite(xRatio) || !Double.isFinite(yRatio)) {
                return null;
            }
            double cachedZoom = DEFAULT_MAP_ZOOM;
            if (zoomValue != null) {
                try {
                    double parsedZoom = Double.parseDouble(zoomValue);
                    if (Double.isFinite(parsedZoom)) {
                        cachedZoom = QuestUiScreenController.clampMapZoom(parsedZoom);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            return new MapCenter(Math.max(0.0, Math.min(1.0, xRatio)), Math.max(0.0, Math.min(1.0, yRatio)), cachedZoom);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static boolean restoreCachedMapCenter(ChapterSnapshot chapter, Element.DOMRect bounds) {
        if (chapter == null || bounds == null || mapLayout == null || mapLayout.chapterId() != chapter.id()
                || bounds.width <= 0.0 || bounds.height <= 0.0) {
            return false;
        }
        MapCenter center = QuestUiScreenController.readCachedMapCenter(chapter);
        if (center == null) {
            return false;
        }
        double contentWidth = Math.max(bounds.width, mapLayout.contentWidth() / mapLayout.guiScaleX());
        double contentHeight = Math.max(bounds.height, mapLayout.contentHeight() / mapLayout.guiScaleY());
        double maxScrollLeft = Math.max(0.0, contentWidth - bounds.width);
        double maxScrollTop = Math.max(0.0, contentHeight - bounds.height);
        pendingMapScrollLeft = Math.max(0.0, Math.min(center.xRatio() * contentWidth - bounds.width / 2.0, maxScrollLeft));
        pendingMapScrollTop = Math.max(0.0, Math.min(center.yRatio() * contentHeight - bounds.height / 2.0, maxScrollTop));
        lastCenteredChapterId = chapter.id();
        pendingMapCenter = false;
        snapMapCenterWithoutCache = false;
        pendingMapScrollRestore = true;
        return true;
    }

    private static double clampMapZoom(double zoom) {
        if (!Double.isFinite(zoom)) {
            return DEFAULT_MAP_ZOOM;
        }
        return Math.max(MIN_MAP_ZOOM, Math.min(MAX_MAP_ZOOM, zoom));
    }

    private static void updateMapViewportInputSnapshot(ChapterSnapshot chapter, Element viewport, Element.DOMRect bounds, GuiScale scale) {
        if (chapter == null || viewport == null || bounds == null || bounds.width <= 0.0 || bounds.height <= 0.0 || scale == null) {
            mapViewportInputSnapshot = null;
            return;
        }
        Element detailPanel = QuestUiScreenController.element("quest-detail");
        String detailClasses = detailPanel == null ? "hidden" : detailPanel.getAttribute("class");
        boolean detailVisible = detailPanel != null && (detailClasses == null || !detailClasses.contains("hidden"));
        Element.DOMRect detailBounds = detailVisible ? detailPanel.getBoundingClientRect() : null;
        detailVisible = detailBounds != null && detailBounds.width > 0.0 && detailBounds.height > 0.0;
        mapViewportInputSnapshot = new MapViewportInputSnapshot(chapter.id(), bounds.x, bounds.y, bounds.width, bounds.height,
                scale.x(), scale.y(), detailVisible,
                detailVisible ? detailBounds.x : 0.0, detailVisible ? detailBounds.y : 0.0,
                detailVisible ? detailBounds.width : 0.0, detailVisible ? detailBounds.height : 0.0);
    }

    static boolean zoomMapAt(double mouseX, double mouseY, double scrollDelta) {
        if (!Double.isFinite(scrollDelta) || Math.abs(scrollDelta) < 1.0e-6
                || state == null || document == null || document.isDisposed()) {
            return false;
        }
        ChapterSnapshot chapter = state.selectedChapter().orElse(null);
        if (chapter == null) {
            return false;
        }
        MapViewportInputSnapshot input = mapViewportInputSnapshot;
        if (input == null || input.chapterId() != chapter.id()) {
            Element viewport = QuestUiScreenController.element("map-viewport");
            if (viewport == null) {
                return false;
            }
            Element.DOMRect bounds = viewport.getBoundingClientRect();
            if (bounds == null || bounds.width <= 0.0 || bounds.height <= 0.0) {
                return true;
            }
            GuiScale scale = mapLayout != null && mapLayout.chapterId() == chapter.id()
                    ? new GuiScale(mapLayout.guiScaleX(), mapLayout.guiScaleY())
                    : QuestUiScreenController.guiScale();
            QuestUiScreenController.updateMapViewportInputSnapshot(chapter, viewport, bounds, scale);
            input = mapViewportInputSnapshot;
            if (input == null) {
                return true;
            }
        }
        double cursorCssX = mouseX / Math.max(0.01, input.guiScaleX());
        double cursorCssY = mouseY / Math.max(0.01, input.guiScaleY());
        double anchorX = cursorCssX - input.viewportCssX();
        double anchorY = cursorCssY - input.viewportCssY();
        boolean insideViewport = anchorX >= 0.0 && anchorY >= 0.0
                && anchorX <= input.viewportCssWidth() && anchorY <= input.viewportCssHeight();
        if (!insideViewport) {
            Element viewport = QuestUiScreenController.element("map-viewport");
            if (viewport == null) {
                return false;
            }
            Element.DOMRect bounds = viewport.getBoundingClientRect();
            if (bounds == null || bounds.width <= 0.0 || bounds.height <= 0.0) {
                return true;
            }
            GuiScale liveScale = QuestUiScreenController.guiScale();
            QuestUiScreenController.updateMapViewportInputSnapshot(chapter, viewport, bounds, liveScale);
            input = mapViewportInputSnapshot;
            if (input == null) {
                return true;
            }
            cursorCssX = mouseX / Math.max(0.01, input.guiScaleX());
            cursorCssY = mouseY / Math.max(0.01, input.guiScaleY());
            anchorX = cursorCssX - input.viewportCssX();
            anchorY = cursorCssY - input.viewportCssY();
            insideViewport = anchorX >= 0.0 && anchorY >= 0.0
                    && anchorX <= input.viewportCssWidth() && anchorY <= input.viewportCssHeight();
            if (!insideViewport) {
                return false;
            }
        }
        Element detailPanel = QuestUiScreenController.element("quest-detail");
        String detailClasses = detailPanel == null ? "hidden" : detailPanel.getAttribute("class");
        boolean detailVisibleNow = detailPanel != null && (detailClasses == null || !detailClasses.contains("hidden"));
        if (detailVisibleNow) {
            double detailX = input.detailCssX();
            double detailY = input.detailCssY();
            double detailWidth = input.detailCssWidth();
            double detailHeight = input.detailCssHeight();
            if (!input.detailVisible()) {
                Element.DOMRect detailBounds = detailPanel.getBoundingClientRect();
                if (detailBounds != null && detailBounds.width > 0.0 && detailBounds.height > 0.0) {
                    detailX = detailBounds.x;
                    detailY = detailBounds.y;
                    detailWidth = detailBounds.width;
                    detailHeight = detailBounds.height;
                }
            }
            if (detailWidth > 0.0 && detailHeight > 0.0
                    && cursorCssX >= detailX && cursorCssX <= detailX + detailWidth
                    && cursorCssY >= detailY && cursorCssY <= detailY + detailHeight) {
                return false;
            }
        }
        pendingMapZoomInputs.add(new PendingMapZoomInput(chapter.id(), mouseX, mouseY, scrollDelta,
                input.viewportCssX(), input.viewportCssY(), anchorX, anchorY, input.guiScaleX(), input.guiScaleY()));
        return true;
    }


    private static void logPendingMapZoomTrace(long chapterId, Element viewport, Element.DOMRect viewportBounds, Element.DOMRect stageBounds, boolean stageLayoutReady, boolean scrollSettled, boolean scrollRestorePending) {
        MapZoomTrace trace = pendingMapZoomTrace;
        if (trace == null) {
            return;
        }
        if (trace.chapterId() != chapterId) {
            pendingMapZoomTrace = null;
            pendingMapZoomTraceWaitForFreshFrame = false;
            return;
        }
        if (!stageLayoutReady || !scrollSettled || scrollRestorePending
                || viewport == null || viewportBounds == null || stageBounds == null || mapLayout == null) {
            return;
        }
        if (pendingMapZoomTraceWaitForFreshFrame) {
            pendingMapZoomTraceWaitForFreshFrame = false;
            return;
        }
        double finalScaleX = Math.max(0.01, mapLayout.guiScaleX());
        double finalScaleY = Math.max(0.01, mapLayout.guiScaleY());
        double cursorCssX = trace.mouseX() / finalScaleX;
        double cursorCssY = trace.mouseY() / finalScaleY;
        double anchorX = cursorCssX - viewportBounds.x;
        double anchorY = cursorCssY - viewportBounds.y;
        double domMapX = ((cursorCssX - stageBounds.x) * mapLayout.guiScaleX() - mapLayout.offsetX()) / mapLayout.zoom();
        double domMapY = ((cursorCssY - stageBounds.y) * mapLayout.guiScaleY() - mapLayout.offsetY()) / mapLayout.zoom();
        double scrollMapX = ((viewport.getScrollLeft() + anchorX) * mapLayout.guiScaleX() - mapLayout.offsetX()) / mapLayout.zoom();
        double scrollMapY = ((viewport.getScrollTop() + anchorY) * mapLayout.guiScaleY() - mapLayout.offsetY()) / mapLayout.zoom();
        QuestUiMod.LOGGER.info("[QuestUI MapZoomTrace] phase=layout chapter={} batchEvents={} wheelTotal={} mouseGui={},{} scales[event={},{} old={},{} apply={},{} final={},{}] cursorCss={},{} anchorCss={},{} zoom={}->{} expectedMap={},{} domMap={},{} domError={},{} scrollMap={},{}",
                QuestUiScreenController.id(chapterId), trace.batchSize(), trace.scrollDelta(), trace.mouseX(), trace.mouseY(),
                trace.eventScaleX(), trace.eventScaleY(), trace.oldLayoutScaleX(), trace.oldLayoutScaleY(),
                trace.applyLayoutScaleX(), trace.applyLayoutScaleY(), mapLayout.guiScaleX(), mapLayout.guiScaleY(),
                cursorCssX, cursorCssY, anchorX, anchorY, trace.oldZoom(), trace.newZoom(),
                trace.mapPointX(), trace.mapPointY(), domMapX, domMapY,
                domMapX - trace.mapPointX(), domMapY - trace.mapPointY(), scrollMapX, scrollMapY);
        QuestUiMod.LOGGER.info("[QuestUI MapZoomTrace] chapter={} viewport={} stage={} beforeScroll={},{} beforeTarget={},{} requested={},{} actualScroll={},{} actualTarget={},{} oldOffset={},{} oldContent={}x{} newOffset={},{} newContent={}x{} immediate={}",
                QuestUiScreenController.id(chapterId), QuestUiScreenController.formatMapRect(viewportBounds),
                QuestUiScreenController.formatMapRect(stageBounds), trace.oldScrollLeft(), trace.oldScrollTop(),
                trace.oldTargetLeft(), trace.oldTargetTop(), pendingMapScrollLeft, pendingMapScrollTop,
                viewport.getScrollLeft(), viewport.getScrollTop(), viewport.getTargetScrollLeft(), viewport.getTargetScrollTop(),
                trace.oldOffsetX(), trace.oldOffsetY(), trace.oldContentWidth(), trace.oldContentHeight(),
                mapLayout.offsetX(), mapLayout.offsetY(), mapLayout.contentWidth(), mapLayout.contentHeight(), pendingMapZoomAnchorAppliedImmediately);
        pendingMapZoomTrace = null;
        pendingMapZoomTraceWaitForFreshFrame = false;
    }

    static void persistCurrentMapCenter() {
        if (state == null || document == null || document.isDisposed() || mapLayout == null) {
            return;
        }
        ChapterSnapshot chapter = state.selectedChapter().orElse(null);
        Element viewport = QuestUiScreenController.element("map-viewport");
        if (chapter == null || viewport == null || mapLayout.chapterId() != chapter.id()) {
            return;
        }
        Element.DOMRect bounds = viewport.getBoundingClientRect();
        if (bounds == null || bounds.width <= 0.0 || bounds.height <= 0.0) {
            return;
        }
        double contentWidth = Math.max(bounds.width, mapLayout.contentWidth() / mapLayout.guiScaleX());
        double contentHeight = Math.max(bounds.height, mapLayout.contentHeight() / mapLayout.guiScaleY());
        double scrollLeft = pendingMapScrollRestore ? pendingMapScrollLeft : viewport.getScrollLeft();
        double scrollTop = pendingMapScrollRestore ? pendingMapScrollTop : viewport.getScrollTop();
        double xRatio = Math.max(0.0, Math.min(1.0, (scrollLeft + bounds.width / 2.0) / contentWidth));
        double yRatio = Math.max(0.0, Math.min(1.0, (scrollTop + bounds.height / 2.0) / contentHeight));
        QuestUiScreenController.loadMapCenterCache();
        String prefix = QuestUiScreenController.mapCenterCachePrefix(chapter);
        MAP_CENTER_CACHE.setProperty(prefix + ".x", Double.toString(xRatio));
        MAP_CENTER_CACHE.setProperty(prefix + ".y", Double.toString(yRatio));
        MAP_CENTER_CACHE.setProperty(prefix + ".zoom", Double.toString(mapZoom));
        QuestUiScreenController.saveMapCenterCache();
    }

    private static void logMapCenterCacheWarning(String operation, Exception exception) {
        if (!mapCenterCacheWarningLogged) {
            mapCenterCacheWarningLogged = true;
            QuestUiMod.LOGGER.warn("Could not {} the local QuestUI map center cache", operation, exception);
        }
    }

    private static MapLayout createMapLayout(ChapterSnapshot chapter, Element.DOMRect bounds, GuiScale guiScale, double zoom) {
        double effectiveZoom = QuestUiScreenController.clampMapZoom(zoom);
        double viewportWidth = Math.max(1.0, bounds.width * guiScale.x());
        double viewportHeight = Math.max(1.0, bounds.height * guiScale.y());
        List<QuestMapNodeSnapshot> nodes = chapter.mapNodes();
        if (nodes.isEmpty()) {
            return new MapLayout(chapter.id(), bounds.x, bounds.y, bounds.width, bounds.height, guiScale.x(), guiScale.y(),
                    effectiveZoom, viewportWidth, viewportHeight, 0.0, 0.0, Map.of());
        }
        double baseButtonSize = Math.max(1.0, sourceQuestScreen == null ? 24.0 : sourceQuestScreen.getQuestButtonSize());
        double baseSpacing = Math.max(0.0, sourceQuestScreen == null ? 8.0 : sourceQuestScreen.getQuestButtonSpacing());
        double buttonSize = baseButtonSize * effectiveZoom;
        double spacing = baseSpacing * effectiveZoom;
        double[] boundsInQuestSpace = QuestUiScreenController.ftbMapBounds(nodes);
        double minX = boundsInQuestSpace[0];
        double minY = boundsInQuestSpace[1];
        double maxX = boundsInQuestSpace[2];
        double maxY = boundsInQuestSpace[3];
        double step = buttonSize + spacing;
        double virtualWidth = Math.max(1.0, (maxX - minX) * step);
        double virtualHeight = Math.max(1.0, (maxY - minY) * step);
        double contentWidth = Math.max(viewportWidth, virtualWidth);
        double contentHeight = Math.max(viewportHeight, virtualHeight);
        double offsetX = (contentWidth - virtualWidth) / 2.0;
        double offsetY = (contentHeight - virtualHeight) / 2.0;
        HashMap<Long, MapNodePlacement> placements = new HashMap<>();
        for (QuestMapNodeSnapshot node : nodes) {
            double x = (node.x() - minX - node.width() / 2.0) * step + spacing / 2.0 + spacing * (node.width() - 1.0) / 2.0;
            double y = (node.y() - minY - node.height() / 2.0) * step + spacing / 2.0 + spacing * (node.height() - 1.0) / 2.0;
            placements.put(node.id(), new MapNodePlacement(offsetX + x, offsetY + y, buttonSize * node.width(), buttonSize * node.height()));
        }
        return new MapLayout(chapter.id(), bounds.x, bounds.y, bounds.width, bounds.height, guiScale.x(), guiScale.y(),
                effectiveZoom, contentWidth, contentHeight, offsetX, offsetY, Map.copyOf(placements));
    }

    private static double[] ftbMapBounds(List<QuestMapNodeSnapshot> nodes) {
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            if (sourceQuestScreen != null && sourceQuestScreen.questPanel != null) {
                for (Widget widget : sourceQuestScreen.questPanel.getWidgets()) {
                    if (!(widget instanceof QuestPositionableButton)) {
                        continue;
                    }
                    QuestPositionableButton.Position position = ((QuestPositionableButton)widget).getPosition();
                    minX = Math.min(minX, position.x() - position.w() / 2.0);
                    minY = Math.min(minY, position.y() - position.h() / 2.0);
                    maxX = Math.max(maxX, position.x() + position.w() / 2.0);
                    maxY = Math.max(maxY, position.y() + position.h() / 2.0);
                }
            }
            if (minX == Double.POSITIVE_INFINITY) {
                for (QuestMapNodeSnapshot node : nodes) {
                    if (!node.visible()) {
                        continue;
                    }
                    minX = Math.min(minX, node.x() - node.width() / 2.0);
                    minY = Math.min(minY, node.y() - node.height() / 2.0);
                    maxX = Math.max(maxX, node.x() + node.width() / 2.0);
                    maxY = Math.max(maxY, node.y() + node.height() / 2.0);
                }
            }
            if (minX == Double.POSITIVE_INFINITY) {
                minX = 0.0;
                minY = 0.0;
                maxX = 0.0;
                maxY = 0.0;
            }
            return new double[]{minX - FTB_MAP_PADDING_X, minY - FTB_MAP_PADDING_Y, maxX + FTB_MAP_PADDING_X, maxY + FTB_MAP_PADDING_Y};
        }

    private static GuiScale guiScale() {
        Minecraft minecraft = Minecraft.m_91087_();
        Element body = QuestUiScreenController.element("questbook-body");
        if (body != null) {
            Element.DOMRect bounds = body.getBoundingClientRect();
            if (bounds.width > 0.0 && bounds.height > 0.0) {
                return new GuiScale((double)minecraft.m_91268_().m_85445_() / bounds.width, (double)minecraft.m_91268_().m_85446_() / bounds.height);
            }
        }
        return new GuiScale((double)minecraft.m_91268_().m_85445_() / (double)Math.max(1, minecraft.m_91268_().m_85443_()), (double)minecraft.m_91268_().m_85446_() / (double)Math.max(1, minecraft.m_91268_().m_85444_()));
    }

    private static void renderQuestDetails() {
        Element panel = QuestUiScreenController.element("quest-detail");
        Element empty = QuestUiScreenController.element("quest-detail-empty");
        if (panel == null) {
            return;
        }
        QuestSnapshot quest = state.selectedQuest().orElse(null);
        if (quest == null) {
            QuestUiScreenController.setAttributeIfChanged(panel, "class", "detail-panel hidden");
            if (empty != null) {
                QuestUiScreenController.setAttributeIfChanged(empty, "class", "empty-state detail-empty");
                QuestUiScreenController.setAttributeIfChanged(empty, "style", "");
            }
            return;
        }
        QuestUiScreenController.setAttributeIfChanged(panel, "class", "detail-panel");
        if (empty != null) {
            QuestUiScreenController.setAttributeIfChanged(empty, "style", "display: none;");
            QuestUiScreenController.setAttributeIfChanged(empty, "class", "hidden");
        }
        ChapterSnapshot selectedChapter = state.selectedChapter().orElse(null);
        String panelStyle = selectedChapter != null && selectedChapter.defaultMinWidth() > 0
                ? "width: " + Math.max(350, selectedChapter.defaultMinWidth()) + "px; max-width: 43%;" : "";
        QuestUiScreenController.setAttributeIfChanged(panel, "style", panelStyle);
        QuestUiScreenController.setText("quest-title", quest.title());
        QuestUiScreenController.setText("quest-subtitle", quest.subtitle());
        QuestUiScreenController.setText("quest-description", quest.description());
        QuestUiScreenController.setText("quest-progress-label", quest.completed() ? "已完成" : "进度 " + quest.progressPercent() + "%");
        Element statusBadge = QuestUiScreenController.element("quest-state");
        if (statusBadge != null) {
            String statusText = quest.completed() ? "已完成"
                    : quest.started() ? "进行中"
                    : quest.canStart() ? "未开始" : "未解锁";
            String statusClass = quest.completed() ? "completed"
                    : quest.started() ? "started"
                    : quest.canStart() ? "not-started" : "locked";
            QuestUiScreenController.setText("quest-state", statusText);
            QuestUiScreenController.setAttributeIfChanged(statusBadge, "class", "status-badge " + statusClass);
            QuestUiScreenController.setAttributeIfChanged(statusBadge, "title", statusText);
        }
        Element content = QuestUiScreenController.element("quest-detail-content");
        Element restricted = QuestUiScreenController.element("quest-details-locked");
        if (content != null) {
            QuestUiScreenController.setAttributeIfChanged(content, "class", quest.detailsHidden() ? "hidden" : "detail-body");
        }
        if (restricted != null) {
            QuestUiScreenController.setAttributeIfChanged(restricted, "class", quest.detailsHidden() ? "detail-restricted" : "hidden");
        }
        Element progress = QuestUiScreenController.element("quest-progress-bar");
        if (progress != null) {
            QuestUiScreenController.setAttributeIfChanged(progress, "style", "width: " + quest.progressPercent() + "%");
        }
        Element tasks = QuestUiScreenController.element("task-list");
        if (tasks != null) {
            QuestUiScreenController.syncTaskRows(tasks, quest.tasks());
        }
        Element rewards = QuestUiScreenController.element("reward-list");
        if (rewards != null) {
            QuestUiScreenController.syncRewardRows(rewards, quest.rewards());
        }
    }


    private static void syncTaskRows(Element container, List<TaskSnapshot> taskSnapshots) {
        Map<String, Element> existingRows = new HashMap<>();
        for (Element row : List.copyOf(container.getChildren())) {
            String rowId = row.getAttribute("id");
            if (rowId != null && rowId.startsWith("quest-task-row-")) {
                existingRows.put(rowId, row);
            }
        }
        List<Element> orderedRows = new ArrayList<>(taskSnapshots.size());
        for (TaskSnapshot task : taskSnapshots) {
            String rowId = "quest-task-row-" + task.id();
            String layout = QuestUiScreenController.taskRowLayout(task);
            Element row = existingRows.remove(rowId);
            if (row != null && !layout.equals(row.getAttribute("data-layout"))) {
                row.remove();
                row = null;
            }
            if (row == null) {
                row = QuestUiScreenController.createTaskRow(task, rowId, layout);
            } else {
                QuestUiScreenController.updateTaskRow(row, task);
            }
            orderedRows.add(row);
        }
        for (Element staleRow : existingRows.values()) {
            staleRow.remove();
        }
        if (!container.getChildren().equals(orderedRows)) {
            container.setTextContent("");
            for (Element row : orderedRows) {
                container.appendChild(row);
            }
        }
    }

    private static String taskRowLayout(TaskSnapshot task) {
        return (task.maxProgress() > 1L ? "amount" : "plain")
                + (task.completed() ? "-completed" : "-active")
                + (task.canClick() ? "-clickable" : "-disabled");
    }

    private static Element createTaskRow(TaskSnapshot task, String rowId, String layout) {
        Element row = document.createElement("li");
        row.setAttribute("id", rowId);
        row.setAttribute("data-layout", layout);
        row.setAttribute("class", task.completed() ? "task-row completed" : "task-row");
        Element icon = document.createElement("span");
        icon.setAttribute("class", "ftb-icon task-icon");
        icon.setAttribute("id", QuestUiScreenController.taskIconId(task.id()));
        row.appendChild(icon);
        Element label = document.createElement("span");
        label.setAttribute("class", "task-label");
        label.setAttribute("id", "quest-task-label-" + task.id());
        label.setTextContent(task.title());
        row.appendChild(label);
        if (task.maxProgress() > 1L) {
            Element amount = document.createElement("span");
            amount.setAttribute("class", "task-progress");
            amount.setAttribute("id", "quest-task-progress-" + task.id());
            amount.setTextContent(task.progress() + " / " + task.maxProgress());
            row.appendChild(amount);
        }
        if (task.completed()) {
            Element status = document.createElement("span");
            status.setAttribute("class", "task-status");
            status.setAttribute("id", "quest-task-status-" + task.id());
            status.setTextContent("✓");
            row.appendChild(status);
        } else {
            Element action = document.createElement("button");
            action.setAttribute("class", task.canClick() ? "button button-small task-action" : "button button-small task-action disabled");
            action.setAttribute("id", "quest-task-action-" + task.id());
            action.setAttribute("title", task.typeName());
            action.setTextContent(QuestUiScreenController.taskActionLabel(task));
            if (task.canClick()) {
                action.addEventListener("click", event -> QuestUiScreenController.clickTask(task.id()));
            }
            row.appendChild(action);
        }
        return row;
    }

    private static void updateTaskRow(Element row, TaskSnapshot task) {
        QuestUiScreenController.setAttributeIfChanged(row, "class", task.completed() ? "task-row completed" : "task-row");
        QuestUiScreenController.setText("quest-task-label-" + task.id(), task.title());
        if (task.maxProgress() > 1L) {
            QuestUiScreenController.setText("quest-task-progress-" + task.id(), task.progress() + " / " + task.maxProgress());
        }
        if (task.completed()) {
            QuestUiScreenController.setText("quest-task-status-" + task.id(), "✓");
        } else {
            Element action = QuestUiScreenController.element("quest-task-action-" + task.id());
            QuestUiScreenController.setAttributeIfChanged(action,
                    "class", task.canClick() ? "button button-small task-action" : "button button-small task-action disabled");
            QuestUiScreenController.setAttributeIfChanged(action, "title", task.typeName());
            QuestUiScreenController.setText("quest-task-action-" + task.id(), QuestUiScreenController.taskActionLabel(task));
        }
    }

    private static void syncRewardRows(Element container, List<RewardSnapshot> rewardSnapshots) {
        Map<String, Element> existingRows = new HashMap<>();
        for (Element row : List.copyOf(container.getChildren())) {
            String rowId = row.getAttribute("id");
            if (rowId != null && rowId.startsWith("quest-reward-row-")) {
                existingRows.put(rowId, row);
            }
        }
        List<Element> orderedRows = new ArrayList<>(rewardSnapshots.size());
        for (RewardSnapshot reward : rewardSnapshots) {
            String rowId = "quest-reward-row-" + reward.id();
            String layout = reward.claimable() ? "claimable" : "status";
            Element row = existingRows.remove(rowId);
            if (row != null && !layout.equals(row.getAttribute("data-layout"))) {
                row.remove();
                row = null;
            }
            if (row == null) {
                row = QuestUiScreenController.createRewardRow(reward, rowId, layout);
            } else {
                QuestUiScreenController.updateRewardRow(row, reward);
            }
            orderedRows.add(row);
        }
        for (Element staleRow : existingRows.values()) {
            staleRow.remove();
        }
        if (!container.getChildren().equals(orderedRows)) {
            container.setTextContent("");
            for (Element row : orderedRows) {
                container.appendChild(row);
            }
        }
    }

    private static Element createRewardRow(RewardSnapshot reward, String rowId, String layout) {
        Element row = document.createElement("li");
        row.setAttribute("id", rowId);
        row.setAttribute("data-layout", layout);
        row.setAttribute("class", reward.claimed() ? "reward-row completed" : "reward-row");
        Element icon = document.createElement("span");
        icon.setAttribute("class", "ftb-icon reward-icon");
        icon.setAttribute("id", QuestUiScreenController.rewardIconId(reward.id()));
        row.appendChild(icon);
        Element label = document.createElement("span");
        label.setAttribute("class", "reward-label");
        label.setAttribute("id", "quest-reward-label-" + reward.id());
        label.setTextContent(reward.title());
        row.appendChild(label);
        if (reward.claimable()) {
            Element claim = document.createElement("button");
            claim.setAttribute("class", "button button-small button-accent");
            claim.setAttribute("id", "quest-reward-claim-" + reward.id());
            claim.setTextContent("领取");
            claim.addEventListener("click", event -> QuestUiScreenController.claimReward(reward.id()));
            row.appendChild(claim);
        } else {
            Element status = document.createElement("span");
            status.setAttribute("class", "reward-status");
            status.setAttribute("id", "quest-reward-status-" + reward.id());
            status.setTextContent(reward.claimed() ? "已领取" : "待解锁");
            row.appendChild(status);
        }
        return row;
    }

    private static void updateRewardRow(Element row, RewardSnapshot reward) {
        QuestUiScreenController.setAttributeIfChanged(row, "class", reward.claimed() ? "reward-row completed" : "reward-row");
        QuestUiScreenController.setText("quest-reward-label-" + reward.id(), reward.title());
        if (reward.claimable()) {
            QuestUiScreenController.setText("quest-reward-claim-" + reward.id(), "领取");
        } else {
            QuestUiScreenController.setText("quest-reward-status-" + reward.id(), reward.claimed() ? "已领取" : "待解锁");
        }
    }

    private static void setAttributeIfChanged(Element element, String name, String value) {
        if (element != null && !value.equals(element.getAttribute(name))) {
            element.setAttribute(name, value);
        }
    }

    private static void refreshSelectedQuest() {
        if (!ClientQuestFile.exists() || state == null) {
            return;
        }
        QuestSnapshot selected = state.selectedQuest().orElse(null);
        if (selected == null) {
            return;
        }
        Quest current = ClientQuestFile.INSTANCE.getQuest(selected.id());
        if (current == null) {
            return;
        }
        QuestSnapshot updated = QuestBookSnapshotFactory.captureQuest(ClientQuestFile.INSTANCE, current);
        if (updated == null || updated.equals(selected)) {
            return;
        }

        ChapterSnapshot previousChapter = state.selectedChapter().orElse(null);
        String previousSearchQuery = state.searchQuery();
        QuestSearchOptions previousSearchOptions = state.searchOptions();
        long previousSearchChapterId = state.selectedChapterId();
        QuestBookSnapshot previousSnapshot = state.snapshot();
        var latestSnapshot = QuestBookSnapshotFactory.capture(ClientQuestFile.INSTANCE);
        boolean snapshotChanged = previousSnapshot != latestSnapshot;
        if (snapshotChanged) {
            QuestUiScreenController.cancelSearchRequest();
        }
        state.replaceSnapshot(latestSnapshot);
        boolean searchInputsStillActive = !previousSearchQuery.isBlank()
                && previousSearchQuery.equals(state.searchQuery())
                && previousSearchOptions.equals(state.searchOptions());
        boolean searchContextChanged = snapshotChanged || previousSearchChapterId != state.selectedChapterId();
        if (searchInputsStillActive && searchContextChanged) {
            QuestUiScreenController.startSearchRequest();
            QuestUiScreenController.renderChapterList();
        }
        ChapterSnapshot currentChapter = state.selectedChapter().orElse(null);
        if (previousChapter == null || currentChapter == null || previousChapter.id() != currentChapter.id()) {
            QuestUiScreenController.renderMap();
        } else {
            boolean refreshLayout = !previousChapter.mapNodes().equals(currentChapter.mapNodes());
            QuestUiScreenController.syncMapNodesInPlace(refreshLayout);
        }
        QuestUiScreenController.renderQuestDetails();
    }

    private static void clickTask(long taskId) {
        if (!ClientQuestFile.exists() || sourceQuestScreen == null) {
            return;
        }
        Task task = ClientQuestFile.INSTANCE.getTask(taskId);
        if (task == null) {
            return;
        }
        new TaskButton((Panel)QuestUiScreenController.sourceQuestScreen.viewQuestPanel, task).onClicked(MouseButton.LEFT);
        refreshTicks = 9;
    }

    private static void claimReward(long rewardId) {
        boolean canClaim;
        if (!ClientQuestFile.exists() || Minecraft.m_91087_().f_91074_ == null) {
            return;
        }
        Reward reward = ClientQuestFile.INSTANCE.getReward(rewardId);
        if (reward == null) {
            return;
        }
        TeamData teamData = ClientQuestFile.INSTANCE.selfTeamData;
        boolean bl = canClaim = teamData.getClaimType(Minecraft.m_91087_().f_91074_.m_20148_(), reward).canClaim() && !teamData.isRewardBlocked(reward);
        if (canClaim) {
            new ClaimRewardMessage(rewardId, true).sendToServer();
            refreshTicks = 9;
        }
    }

    private static void drawObjectIcon(GuiGraphics graphics, String elementId, QuestObjectBase object) {
        if (object != null) {
            QuestUiScreenController.drawIcon(graphics, elementId, (Drawable)object.getIcon());
        }
    }

    private static void drawTaskIcon(GuiGraphics graphics, String elementId, Task task) {
        Element target = QuestUiScreenController.element(elementId);
        if (target == null || task == null || ClientQuestFile.INSTANCE.selfTeamData == null) {
            return;
        }
        Element.DOMRect rect = target.getBoundingClientRect();
        if (rect.width > 0.0 && rect.height > 0.0) {
            GuiScale scale = QuestUiScreenController.guiScale();
            task.drawGUI(ClientQuestFile.INSTANCE.selfTeamData, graphics, (int)Math.round(rect.x * scale.x()), (int)Math.round(rect.y * scale.y()), (int)Math.ceil(rect.width * scale.x()), (int)Math.ceil(rect.height * scale.y()));
        }
    }

    private static void drawIcon(GuiGraphics graphics, String elementId, Drawable drawable) {
        Element target = QuestUiScreenController.element(elementId);
        if (target == null || drawable == null) {
            return;
        }
        Element.DOMRect rect = target.getBoundingClientRect();
        if (rect.width <= 0.0 || rect.height <= 0.0) {
            return;
        }
        GuiScale scale = QuestUiScreenController.guiScale();
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(rect.x * scale.x(), rect.y * scale.y(), 0.0d);
            drawable.draw(graphics, 0, 0, (int)Math.ceil(rect.width * scale.x()),
                    (int)Math.ceil(rect.height * scale.y()));
        } finally {
            graphics.pose().popPose();
        }
    }

    private static void openVanillaEditor() {
        if (vanillaScreen == null) {
            return;
        }
        QuestUiScreenController.persistCurrentMapCenter();
        usingVanillaEditor = true;
        QuestUiClientEvents.openVanillaScreen(vanillaScreen);
    }

    private static void openVanilla(QuestScreen questScreen, Screen screen) {
        QuestUiScreenController.clearSession();
        if (screen != null) {
            QuestUiClientEvents.openVanillaScreen(screen);
        } else if (questScreen != null) {
            ClientQuestFile.openGui();
        }
    }

    private static void clearSession() {
        QuestUiScreenController.persistCurrentMapCenter();
        QuestUiScreenController.cancelSearchRequest();
        vanillaScreen = null;
        apricityScreen = null;
        sourceQuestScreen = null;
        state = null;
        document = null;
        collapsedGroups.clear();
        mapDiagnosticsLogged.clear();
        mapDiagnosticWarningsLogged.clear();
        mapLayoutUnsettled = false;
        mapUnsettledWarningLogged = false;
        mapUnsettledRetries = 0;
        waitingForDocument = false;
        usingVanillaEditor = false;
        staticEventsBound = false;
        chapterHitTestRefreshPending = false;
        chapterCursorRefreshPending = false;
        chapterToggleCursorOverrideActive = false;
        chapterHitTestDebugListenerDocument = null;
        chapterHitTestDebugSequence = 0L;
        activeChapterHitTestDebugSequence = 0L;
        chapterToggleMousePressCaptured = false;
        chapterToggleMousePressDocument = null;
        searchUpdatePending = false;
        pendingSearchQuery = "";
        pendingSearchUpdatedNanos = 0L;
        lastObservedSearchValue = state == null ? "" : state.searchQuery();
        renderedGeneration = -1L;
        screenFadeStartNanos = 0L;
        screenFadePending = false;
        openTimingEnabled = false;
        openStartedNanos = 0L;
        nativeButtonsChapterId = Long.MIN_VALUE;
        lastCenteredChapterId = Long.MIN_VALUE;
        mapCenterStageChapterId = Long.MIN_VALUE;
        mapCenterStageX = Double.NaN;
        mapCenterStageY = Double.NaN;
        mapCenterStableSamples = 0;
        nativeQuestButtons.clear();
        mapLayout = null;
        resetMapNodeLabelHover();
        mapViewportInputSnapshot = null;
        mapZoom = DEFAULT_MAP_ZOOM;
        initialFtbChapterRefresh = false;
        pendingMapCenter = false;
        snapMapCenterWithoutCache = false;
        pendingMapScrollRestore = false;
        pendingMapZoomAnchorRestore = false;
        pendingMapZoomAnchor = null;
        pendingMapZoomAnchorAppliedImmediately = false;
        pendingMapZoomTrace = null;
        pendingMapZoomTraceWaitForFreshFrame = false;
        pendingMapZoomInputs.clear();
        lastLoggedDomWheelTrace = null;
        pendingMapScrollLeft = 0.0;
        pendingMapScrollTop = 0.0;
        QuestUiScreenController.resetMapScrollPersistenceTracking();
    }

    private static String taskActionLabel(TaskSnapshot task) {
        String id = task.typeId().toLowerCase();
        if (id.contains("item") || id.contains("consume") || id.contains("submit")) {
            return "\u63d0\u4ea4\u7269\u54c1";
        }
        String name = task.typeName().toLowerCase();
        if (name.contains("check") || name.contains("checkbox") || name.contains("\u52fe\u9009")) {
            return "\u5b8c\u6210";
        }
        return "\u6267\u884c";
    }

    private static String id(long value) {
        return Long.toUnsignedString(value, 16);
    }

    private static String mapNodeId(long nodeId) {
        return "quest-node-" + QuestUiScreenController.id(nodeId);
    }


    private static String taskIconId(long taskId) {
        return "task-icon-" + QuestUiScreenController.id(taskId);
    }

    private static String rewardIconId(long rewardId) {
        return "reward-icon-" + QuestUiScreenController.id(rewardId);
    }

    private static Element element(String id) {
        return document == null ? null : document.getElementById(id);
    }

    private static void setText(String id, String text) {
        Element element = QuestUiScreenController.element(id);
        if (element == null) {
            return;
        }
        String next = text == null ? "" : text;
        // Skip no-op writes: they still dirty the text node and force AUI to re-measure it.
        if (!next.equals(element.getTextContent())) {
            element.setTextContent(next);
        }
    }

    static {
        collapsedGroups = new HashSet<Long>();
        renderedGeneration = -1L;
        nativeButtonsChapterId = Long.MIN_VALUE;
        lastCenteredChapterId = Long.MIN_VALUE;
        nativeQuestButtons = new HashMap<Long, QuestButton>();
        mapDiagnosticsLogged = new HashSet<Long>();
        mapDiagnosticWarningsLogged = new HashSet<String>();
    }

    private record MapCenter(double xRatio, double yRatio, double zoom) {
    }

    private record MapViewportInputSnapshot(
        long chapterId,
        double viewportCssX,
        double viewportCssY,
        double viewportCssWidth,
        double viewportCssHeight,
        double guiScaleX,
        double guiScaleY,
        boolean detailVisible,
        double detailCssX,
        double detailCssY,
        double detailCssWidth,
        double detailCssHeight
    ) {
    }
    private record PendingMapZoomInput(
        long chapterId,
        double mouseX,
        double mouseY,
        double scrollDelta,
        double viewportCssX,
        double viewportCssY,
        double anchorX,
        double anchorY,
        double eventScaleX,
        double eventScaleY
    ) {
    }
    private record PendingMapZoomAnchor(long chapterId, double mouseX, double mouseY, double mapPointX, double mapPointY) {
    }
    private record MapZoomTrace(
        long chapterId,
        double scrollDelta,
        int batchSize,
        double mouseX,
        double mouseY,
        double cursorCssX,
        double cursorCssY,
        double anchorX,
        double anchorY,
        double eventScaleX,
        double eventScaleY,
        double oldLayoutScaleX,
        double oldLayoutScaleY,
        double applyLayoutScaleX,
        double applyLayoutScaleY,
        double oldZoom,
        double newZoom,
        double oldScrollLeft,
        double oldScrollTop,
        double oldTargetLeft,
        double oldTargetTop,
        double mapPointX,
        double mapPointY,
        double oldOffsetX,
        double oldOffsetY,
        double oldContentWidth,
        double oldContentHeight,
        double requestedLeft,
        double requestedTop,
        boolean snappedImmediately
    ) {
    }


    private enum MapNodeLabelPhase {
        HIDDEN, VISIBLE, FADING_OUT
    }

    private record MapNodeLabelPaintState(Style style, Rect rect, Matrix4f transform) {
    }

    private record MapNodeLabelFrame(Element label, List<RenderNode> nodes,
                                     Map<Element, MapNodeLabelPaintState> paintStates) {
    }

    /** Captures normal AUI samples but emits title pixels only during the topmost replay. */
    private static final class MapHoverLabelElement extends Element implements ForegroundRenderNodeProvider {
        private final RenderNode captureNode;

        private MapHoverLabelElement(Document ownerDocument, String tagName, boolean captureFrame) {
            super(ownerDocument, tagName);
            captureNode = captureFrame ? new RenderNode.ElementForegroundNode(this,
                    ignored -> QuestUiScreenController.captureMapNodeLabelFrame(this)) : null;
        }

        @Override
        public void drawPhase(PoseStack poseStack, Base.RenderPhase phase) {
            if (mapNodeLabelReplayActive) {
                super.drawPhase(poseStack, phase);
            }
        }

        @Override
        public void drawBackgroundOnly(PoseStack poseStack) {
            if (mapNodeLabelReplayActive) {
                super.drawBackgroundOnly(poseStack);
            }
        }

        @Override
        public void drawContentOnly(PoseStack poseStack) {
            if (mapNodeLabelReplayActive) {
                super.drawContentOnly(poseStack);
            }
        }

        @Override
        public List<RenderNode> createForegroundRenderNodes() {
            return captureNode == null ? List.of() : List.of(captureNode);
        }
    }

    private record MapLayout(long chapterId, double viewportCssX, double viewportCssY, double viewportCssWidth, double viewportCssHeight, double guiScaleX, double guiScaleY, double zoom, double contentWidth, double contentHeight, double offsetX, double offsetY, Map<Long, MapNodePlacement> placements) {
        private boolean matchesViewport(long selectedChapterId, Element.DOMRect bounds, GuiScale scale) {
            return this.chapterId == selectedChapterId
                    && MapLayout.close(this.viewportCssX, bounds.x)
                    && MapLayout.close(this.viewportCssY, bounds.y)
                    && MapLayout.close(this.viewportCssWidth, bounds.width)
                    && MapLayout.close(this.viewportCssHeight, bounds.height)
                    && MapLayout.close(this.guiScaleX, scale.x())
                    && MapLayout.close(this.guiScaleY, scale.y());
        }

        private boolean matches(long selectedChapterId, Element.DOMRect bounds, GuiScale scale, double selectedZoom) {
            return this.matchesViewport(selectedChapterId, bounds, scale) && Math.abs(this.zoom - selectedZoom) < 1.0e-4;
        }

        private static boolean close(double first, double second) {
            return Math.abs(first - second) < 0.5;
        }
    }

    private record GuiScale(double x, double y) {
    }

    private record MapNodePlacement(double x, double y, double width, double height) {
    }
}

