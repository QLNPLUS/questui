package dev.modernftb.questui.forge.client;

import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.ScreenWrapper;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.modernftb.questui.forge.QuestUiMod;
import dev.modernftb.questui.forge.client.QuestUiScreenController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid="questui", bus=Mod.EventBusSubscriber.Bus.FORGE, value={Dist.CLIENT})
public final class QuestUiClientEvents {
    private static Screen nextVanillaScreen;
    private static Screen pendingQuestScreenWrapper;
    private static QuestScreen pendingQuestScreen;
    private static boolean debugQuestBookOpenRequested;

    private QuestUiClientEvents() {
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        ScreenWrapper wrapper;
        BaseScreen baseScreen;
        Screen opening = event.getNewScreen();
        Screen current = Minecraft.getInstance().screen;
        if (current != opening && QuestUiScreenController.ownsScreen(current)) {
            QuestUiScreenController.persistCurrentMapCenter();
        }
        if (opening == nextVanillaScreen) {
            nextVanillaScreen = null;
            return;
        }
        if (opening instanceof ScreenWrapper && (baseScreen = (wrapper = (ScreenWrapper)opening).getGui()) instanceof QuestScreen) {
            pendingQuestScreenWrapper = opening;
            pendingQuestScreen = (QuestScreen)baseScreen;
        }
    }

    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        if (QuestUiScreenController.ownsScreen(event.getScreen())) {
            QuestUiScreenController.persistCurrentMapCenter();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onMapShiftWheel(ScreenEvent.MouseScrolled.Pre event) {
        if (!QuestUiScreenController.ownsScreen(event.getScreen())) {
            return;
        }
        boolean shiftDown = Screen.hasShiftDown();
        boolean canceledBefore = event.isCanceled();
        int eventId = System.identityHashCode(event);
        boolean zoomHandled = shiftDown
                && QuestUiScreenController.zoomMapAt(event.getMouseX(), event.getMouseY(), event.getScrollDelta());
        if (zoomHandled) {
            event.setCanceled(true);
        }
        QuestUiMod.LOGGER.info("[QuestUI WheelTrace] phase=forge-pre id={} shiftDown={} delta={} mouse={},{} canceledBefore={} zoomHandled={} canceledAfter={}",
                eventId, shiftDown, event.getScrollDelta(), event.getMouseX(), event.getMouseY(),
                canceledBefore, zoomHandled, event.isCanceled());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onMapWheelFinal(ScreenEvent.MouseScrolled.Pre event) {
        if (!QuestUiScreenController.ownsScreen(event.getScreen())) {
            return;
        }
        QuestUiMod.LOGGER.info("[QuestUI WheelTrace] phase=forge-final id={} shiftDown={} delta={} mouse={},{} canceled={}",
                System.identityHashCode(event), Screen.hasShiftDown(), event.getScrollDelta(),
                event.getMouseX(), event.getMouseY(), event.isCanceled());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onChapterHitTestInput(InputEvent.MouseButton.Pre event) {
        int action = event.getAction();
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_1
                || (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_RELEASE)
                || !QuestUiScreenController.ownsScreen(Minecraft.getInstance().screen)) {
            return;
        }
        boolean canceledAtProbe = event.isCanceled();
        if (canceledAtProbe) {
            if (QuestUiScreenController.isChapterHitTestDebugEnabled()) {
                QuestUiScreenController.logChapterHitTestInput(event.getButton(), action, true, false);
            }
            return;
        }
        boolean directHandled = QuestUiScreenController.routeChapterToggleMouseInput(
                action == GLFW.GLFW_PRESS, action == GLFW.GLFW_RELEASE);
        if (QuestUiScreenController.isChapterHitTestDebugEnabled()) {
            QuestUiScreenController.logChapterHitTestInput(event.getButton(), action, canceledAtProbe, directHandled);
        }
        if (directHandled) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(receiveCanceled = true)
    public static void onQuestUiEscape(ScreenEvent.KeyPressed.Pre event) {
        if (event.getKeyCode() != GLFW.GLFW_KEY_ESCAPE || !QuestUiScreenController.ownsScreen(event.getScreen())) {
            return;
        }
        event.setCanceled(true);
        Minecraft.getInstance().setScreen(null);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Screen pendingWrapper = pendingQuestScreenWrapper;
        QuestScreen pendingQuest = pendingQuestScreen;
        pendingQuestScreenWrapper = null;
        pendingQuestScreen = null;
        if (pendingWrapper != null && pendingQuest != null && minecraft.screen == pendingWrapper) {
            QuestUiScreenController.open(pendingQuest, pendingWrapper);
        }
        QuestUiScreenController.tick();
        if (!Boolean.getBoolean("questui.debug.map")) {
            debugQuestBookOpenRequested = false;
            return;
        }
        if (minecraft.level == null) {
            debugQuestBookOpenRequested = false;
            return;
        }
        if (!debugQuestBookOpenRequested && ClientQuestFile.exists() && ClientQuestFile.INSTANCE.selfTeamData != null) {
            debugQuestBookOpenRequested = true;
            QuestUiMod.LOGGER.info("[QuestUI MapCheck] runClient auto-opening quest book after FTB data sync");
            ClientQuestFile.openGui();
        }
    }

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        QuestUiScreenController.renderFtbIcons(event.getGuiGraphics());
    }

    static void openVanillaScreen(Screen screen) {
        nextVanillaScreen = screen;
        Minecraft.m_91087_().m_91152_(screen);
    }
}

