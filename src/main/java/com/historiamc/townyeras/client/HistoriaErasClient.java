package com.historiamc.townyeras.client;

import com.historiamc.townyeras.HistoriaEras;
import com.historiamc.townyeras.client.data.SyncedDashboardDataProvider;
import com.historiamc.townyeras.client.screen.HistoriaErasScreen;
import com.historiamc.townyeras.network.ClientSnapshotCache;
import com.historiamc.townyeras.network.HistoriaErasNetwork;
import com.historiamc.townyeras.server.SereneSeasonsTimelineBridge;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import com.historiamc.townyeras.network.RequestSnapshotPayload;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod(value = HistoriaEras.MOD_ID, dist = Dist.CLIENT)
public final class HistoriaErasClient {
    private static final boolean VISUAL_TEST = Boolean.getBoolean("townyeras.prototype.visualTest");
    private static final String VISUAL_TEST_PREFIX = System.getProperty(
            "townyeras.prototype.visualTestPrefix",
            "townyeras"
    );
    private static final String VISUAL_TEST_WORLD = "HistoriaEras Prototype Automated Test";
    private static final String VISUAL_TEST_SENTINEL = "townyeras-ui-test.success";
    private static final Set<String> COMPLETED_VISUAL_TEST_SCREENSHOTS = ConcurrentHashMap.newKeySet();
    private static int visualTestStartupTicks;
    private static int visualTestTicks;
    private static boolean visualTestWorldRequested;
    private static boolean visualTestWorldCreationRequested;
    private static boolean visualTestWorldCreationSubmitted;
    private static boolean visualTestStarted;
    private static boolean visualCraftGateTestRequested;
    private static volatile Boolean visualCraftGateTestPassed;
    private static String pendingVisualTestScreenshot;
    private static volatile String visualTestFailure;
    private static UUID connectedPlayer;
    public static final KeyMapping OPEN_DASHBOARD = new KeyMapping(
            "key.townyeras.open_dashboard",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.townyeras"
    );

    public HistoriaErasClient(IEventBus modEventBus) {
        HistoriaErasNetwork.installReminderHandler(show -> {
            Minecraft client = Minecraft.getInstance();
            if (!show || client.player == null || !ClientSnapshotCache.isSynchronizedWithServer()) return;
            client.player.sendSystemMessage(gettingStartedReminderMessage());
        });
        modEventBus.addListener(HistoriaErasClient::registerKeyMappings);
        modEventBus.addListener(HistoriaErasHud::register);
        NeoForge.EVENT_BUS.addListener(HistoriaErasClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(HistoriaErasClient::onRenderFramePost);
    }

    private static Component gettingStartedReminderMessage() {
        if (OPEN_DASHBOARD.isUnbound()) return Component.literal(
                "HistoriaEras: Assign the Open HistoriaEras menu key under Options > Controls > Key Binds > HistoriaEras, then open Technology to get started.")
                .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE);
        return Component.literal("HistoriaEras: Press ").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE)
                .append(OPEN_DASHBOARD.getTranslatedKeyMessage().copy().withStyle(net.minecraft.ChatFormatting.YELLOW))
                .append(Component.literal(" to open your menu, then choose Technology to view your tech tree. Getting Started will guide you."));
    }

    private static void verifyReminderKeybindForVisualTest() {
        var original = OPEN_DASHBOARD.getKey();
        try {
            OPEN_DASHBOARD.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_J));
            if (!gettingStartedReminderMessage().getString().contains("Press J to open"))
                throw new IllegalStateException("Reminder did not use the rebound menu key");
            OPEN_DASHBOARD.setKey(InputConstants.UNKNOWN);
            if (!gettingStartedReminderMessage().getString().contains("Options > Controls > Key Binds"))
                throw new IllegalStateException("Unbound-key reminder did not explain how to assign a key");
        } finally {
            OPEN_DASHBOARD.setKey(original);
        }
        HistoriaEras.LOGGER.info("Visual test passed rebound and unbound menu-key reminders");
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_DASHBOARD);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            if (connectedPlayer != null) {
                connectedPlayer = null;
                ClientSnapshotCache.clear();
                HistoriaErasScreen.clearRememberedView();
            }
        } else if (!minecraft.player.getUUID().equals(connectedPlayer)) {
            ClientSnapshotCache.clear();
            HistoriaErasScreen.clearRememberedView();
            connectedPlayer = minecraft.player.getUUID();
            if (serverSupportsHistoriaEras(minecraft)) {
                HistoriaErasNetwork.requestSnapshot();
            }
        }
        while (OPEN_DASHBOARD.consumeClick()) {
            if (minecraft.player != null
                    && minecraft.screen == null
                    && ClientSnapshotCache.isSynchronizedWithServer()) {
                HistoriaErasNetwork.requestSnapshot();
                minecraft.setScreen(new HistoriaErasScreen(
                        new SyncedDashboardDataProvider().snapshot(minecraft.player)
                ));
            }
        }

        if (minecraft.screen instanceof HistoriaErasScreen
                && !ClientSnapshotCache.isSynchronizedWithServer()) {
            minecraft.setScreen(null);
        }

        if (VISUAL_TEST) {
            runVisualTest(minecraft);
        }
    }

    private static boolean serverSupportsHistoriaEras(Minecraft minecraft) {
        return minecraft.getConnection() != null
                && NetworkRegistry.hasChannel(
                        minecraft.getConnection(),
                        RequestSnapshotPayload.TYPE.id()
                );
    }

    private static void runVisualTest(Minecraft minecraft) {
        if (visualTestFailure != null) {
            throw new IllegalStateException(visualTestFailure);
        }

        if (minecraft.player == null || minecraft.level == null) {
            visualTestStartupTicks++;
            if (!visualTestWorldRequested && visualTestStartupTicks >= 100 && minecraft.screen != null) {
                visualTestWorldRequested = true;
                Path levelData = minecraft.gameDirectory.toPath()
                        .resolve("saves")
                        .resolve(VISUAL_TEST_WORLD)
                        .resolve("level.dat");
                if (Files.isRegularFile(levelData)) {
                    HistoriaEras.LOGGER.info("Visual test opening isolated world");
                    minecraft.createWorldOpenFlows().openWorld(
                            VISUAL_TEST_WORLD,
                            () -> visualTestFailure = "Visual test could not open its isolated world"
                    );
                } else {
                    HistoriaEras.LOGGER.info("Visual test creating isolated world");
                    visualTestWorldCreationRequested = true;
                    CreateWorldScreen.openFresh(minecraft, null);
                }
            }

            if (visualTestWorldCreationRequested
                    && !visualTestWorldCreationSubmitted
                    && minecraft.screen instanceof CreateWorldScreen createWorldScreen) {
                visualTestWorldCreationSubmitted = true;
                createWorldScreen.getUiState().setName(VISUAL_TEST_WORLD);
                if (!createWorldScreen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0)) {
                    throw new IllegalStateException("Visual test could not submit isolated world creation");
                }
            }

            if (visualTestStartupTicks >= 2400) {
                throw new IllegalStateException("Visual test timed out while preparing its isolated world");
            }
            return;
        }

        // Keep the reusable automated world ticking even when the test window
        // briefly loses focus while screenshots are written.
        minecraft.options.pauseOnLostFocus = false;
        // Focus can be lost during world loading, before the setting above runs.
        // Only the isolated test may dismiss this startup pause screen.
        if (!visualTestStarted && minecraft.screen instanceof net.minecraft.client.gui.screens.PauseScreen)
            minecraft.setScreen(null);

        if (!visualTestStarted) {
            // A reused world can leave the receiving-level screen visible for a
            // few ticks after the player exists. Start the test clock only once
            // normal gameplay is ready so the dashboard key is not discarded.
            if (minecraft.screen != null) {
                return;
            }
            if (!visualCraftGateTestRequested) {
                visualCraftGateTestRequested = true;
                UUID playerId = minecraft.player.getUUID();
                minecraft.getSingleplayerServer().execute(() -> {
                    ServerPlayer serverPlayer = minecraft.getSingleplayerServer()
                            .getPlayerList().getPlayer(playerId);
                    boolean sereneSeasonsOkay = true;
                    if (ModList.get().isLoaded("sereneseasons")) {
                        SereneSeasonsTimelineBridge.sync(minecraft.getSingleplayerServer());
                        sereneSeasonsOkay = SereneSeasonsTimelineBridge.synchronizedNow(
                                minecraft.getSingleplayerServer()
                        );
                    }
                    visualCraftGateTestPassed = serverPlayer != null
                            && !mayTakeCraftResult(serverPlayer, new ItemStack(Items.STONE_PICKAXE))
                            && mayTakeCraftResult(serverPlayer, new ItemStack(Items.APPLE))
                            && com.historiamc.townyeras.server.ReviewedNodeSmokeTest.run(serverPlayer)
                            && sereneSeasonsOkay;
                });
                return;
            }
            if (visualCraftGateTestPassed == null) {
                return;
            }
            if (!visualCraftGateTestPassed) {
                throw new IllegalStateException(
                        "Server compatibility check failed for recipe gating or Serene Seasons synchronization"
                );
            }
            visualTestTicks++;
            if (visualTestTicks >= 20) {
                visualTestStarted = true;
                HistoriaEras.LOGGER.info("Visual test passed locked-recipe output enforcement");
                KeyMapping.click(OPEN_DASHBOARD.getKey());
            }
            return;
        }

        visualTestTicks++;
        if (visualTestTicks == 50) {
            requireDashboard(minecraft, "Overview screenshot");
            pendingVisualTestScreenshot = VISUAL_TEST_PREFIX + "-overview.png";
        } else if (visualTestTicks == 60) {
            HistoriaErasScreen screen = requireDashboard(minecraft, "Operator progress reset");
            screen.runAdminResetSmokeTestForVisualTest();
            HistoriaEras.LOGGER.info("Visual test passed operator node-management controls");
        } else if (visualTestTicks == 70) {
            HistoriaErasScreen screen = requireDashboard(minecraft, "Technology navigation");
            if (!screen.keyPressed(GLFW.GLFW_KEY_2, 0, 0)) {
                throw new IllegalStateException("Visual test could not open the Technology page");
            }
        } else if (visualTestTicks == 75) {
            HistoriaErasScreen screen = requireDashboard(minecraft, "Technology interactions");
            screen.runInteractionSmokeTestForVisualTest();
            HistoriaEras.LOGGER.info("Visual test passed node selection, drag, zoom, and recenter interactions");
        } else if (visualTestTicks == 80) {
            requireDashboard(minecraft, "Expanded detail list").prepareDetailsScrollForVisualTest();
        } else if (visualTestTicks == 85) {
            HistoriaErasScreen screen = requireDashboard(minecraft, "Technology detail scrolling");
            screen.runDetailsScrollSmokeTestForVisualTest();
            HistoriaEras.LOGGER.info("Visual test passed technology detail scrolling");
        } else if (visualTestTicks == 90) {
            requireDashboard(minecraft, "Technology redesign").runTechnologyRedesignSmokeTestForVisualTest();
            HistoriaEras.LOGGER.info("Visual test passed responsive technology layout, concealed search and tracked navigation");
        } else if (visualTestTicks == 94) {
            requireDashboard(minecraft, "Unlock group scrolling").runUnlockGroupScrollForVisualTest();
        } else if (visualTestTicks == 96) {
            requireDashboard(minecraft, "Unlock group expansion").runUnlockGroupClickForVisualTest();
            HistoriaEras.LOGGER.info("Visual test passed expandable exact unlock groups");
        } else if (visualTestTicks == 98) {
            requireDashboard(minecraft, "Era visibility").runEraVisibilitySmokeTestForVisualTest();
            HistoriaEras.LOGGER.info("Visual test passed current/past browsing, future detail blocking, remembered-view validation and admin era access");
        } else if (visualTestTicks == 100) {
            requireDashboard(minecraft, "Technology screenshot");
            pendingVisualTestScreenshot = VISUAL_TEST_PREFIX + "-technology.png";
        } else if (visualTestTicks == 102) {
            requireDashboard(minecraft, "Layered branch").showLayeredBranchForVisualTest(true);
        } else if (visualTestTicks == 104) {
            pendingVisualTestScreenshot = VISUAL_TEST_PREFIX + "-technology-branches.png";
        } else if (visualTestTicks == 106) {
            requireDashboard(minecraft, "Restore technology branch").showLayeredBranchForVisualTest(false);
        } else if (visualTestTicks == 110) {
            HistoriaErasScreen screen = requireDashboard(minecraft, "Classes navigation");
            if (!screen.keyPressed(GLFW.GLFW_KEY_3, 0, 0)) {
                throw new IllegalStateException("Visual test could not open the Classes page");
            }
        } else if (visualTestTicks == 115) {
            HistoriaErasScreen screen = requireDashboard(minecraft, "Classes interactions");
            screen.runClassesSmokeTestForVisualTest();
            HistoriaEras.LOGGER.info("Visual test passed class selection interactions");
        } else if (visualTestTicks == 125) {
            requireDashboard(minecraft, "Classes screenshot");
            pendingVisualTestScreenshot = VISUAL_TEST_PREFIX + "-classes.png";
        } else if (visualTestTicks == 135) {
            HistoriaErasScreen screen = requireDashboard(minecraft, "Timeline navigation");
            if (!screen.keyPressed(GLFW.GLFW_KEY_4, 0, 0)) {
                throw new IllegalStateException("Visual test could not open the Timeline page");
            }
        } else if (visualTestTicks == 140) {
            HistoriaErasScreen screen = requireDashboard(minecraft, "Timeline calendar-bar toggle");
            screen.runTimelineHudToggleSmokeTestForVisualTest();
            HistoriaEras.LOGGER.info("Visual test passed persistent calendar-bar visibility toggle");
        } else if (visualTestTicks == 150) {
            requireDashboard(minecraft, "Timeline screenshot");
            pendingVisualTestScreenshot = VISUAL_TEST_PREFIX + "-timeline.png";
        } else if (visualTestTicks == 160) {
            HistoriaErasScreen screen = requireDashboard(minecraft, "K-key close");
            if (!screen.keyPressed(GLFW.GLFW_KEY_K, 0, 0) || minecraft.screen != null) {
                throw new IllegalStateException("Dashboard K-key close did not work");
            }
            HistoriaEras.LOGGER.info("Visual test passed K-key close interaction");
        } else if (visualTestTicks == 170) {
            pendingVisualTestScreenshot = VISUAL_TEST_PREFIX + "-hud.png";
        } else if (visualTestTicks == 180) {
            KeyMapping.click(OPEN_DASHBOARD.getKey());
        } else if (visualTestTicks == 185) {
            verifyReminderKeybindForVisualTest();
            requireDashboard(minecraft, "Tutorial start").runGettingStartedStartForVisualTest();
        } else if (visualTestTicks == 190) {
            pendingVisualTestScreenshot = VISUAL_TEST_PREFIX + "-tutorial-welcome.png";
        } else if (visualTestTicks == 195) {
            var screen = requireDashboard(minecraft, "Tutorial overview/classes");
            screen.runGettingStartedNextForVisualTest(1);
            screen.runGettingStartedNextForVisualTest(2);
        } else if (visualTestTicks == 200) {
            pendingVisualTestScreenshot = VISUAL_TEST_PREFIX + "-tutorial-classes.png";
        } else if (visualTestTicks == 205) {
            requireDashboard(minecraft, "Tutorial technology").runGettingStartedNextForVisualTest(3);
        } else if (visualTestTicks == 210) {
            pendingVisualTestScreenshot = VISUAL_TEST_PREFIX + "-tutorial-technology.png";
        } else if (visualTestTicks == 215) {
            requireDashboard(minecraft, "Tutorial details").runGettingStartedNextForVisualTest(4);
        } else if (visualTestTicks == 220) {
            pendingVisualTestScreenshot = VISUAL_TEST_PREFIX + "-tutorial-details.png";
        } else if (visualTestTicks == 225) {
            requireDashboard(minecraft, "Tutorial tracking").runGettingStartedNextForVisualTest(5);
        } else if (visualTestTicks == 230) {
            pendingVisualTestScreenshot = VISUAL_TEST_PREFIX + "-tutorial-tracking.png";
        } else if (visualTestTicks == 235) {
            requireDashboard(minecraft, "Tutorial timeline").runGettingStartedNextForVisualTest(6);
        } else if (visualTestTicks == 240) {
            pendingVisualTestScreenshot = VISUAL_TEST_PREFIX + "-tutorial-timeline.png";
        } else if (visualTestTicks == 245) {
            requireDashboard(minecraft, "Tutorial completion/replay/skip").runGettingStartedFinishForVisualTest();
            HistoriaEras.LOGGER.info("Visual test passed Getting Started steps, expanded page layouts, Back, Done, replay and Skip");
        } else if (visualTestTicks >= 248 && visualTestTicks <= 270 && (visualTestTicks - 248) % 4 == 0) {
            String branch = new String[]{"farmer", "chef", "mariner", "carpenter", "mason", "blacksmith"}[(visualTestTicks - 248) / 4];
            requireDashboard(minecraft, "Class branch shape").showClassBranchForVisualTest(branch);
        } else if (visualTestTicks >= 250 && visualTestTicks <= 270 && (visualTestTicks - 250) % 4 == 0) {
            String branch = new String[]{"farmer", "chef", "mariner", "carpenter", "mason", "blacksmith"}[(visualTestTicks - 250) / 4];
            pendingVisualTestScreenshot = VISUAL_TEST_PREFIX + "-branch-" + branch + ".png";
        } else if (visualTestTicks >= 272 && visualTestTicks <= 316 && (visualTestTicks - 272) % 4 == 0) {
            int stage = (visualTestTicks - 272) / 4;
            String branch = new String[]{"farmer", "chef", "mariner", "carpenter", "mason", "blacksmith"}[stage / 2];
            requireDashboard(minecraft, "Revealed graph geometry").showRevealedClassForVisualTest(branch, stage % 2 == 1);
        } else if (visualTestTicks >= 274 && visualTestTicks <= 318 && (visualTestTicks - 274) % 4 == 0) {
            int stage = (visualTestTicks - 274) / 4;
            String branch = new String[]{"farmer", "chef", "mariner", "carpenter", "mason", "blacksmith"}[stage / 2];
            pendingVisualTestScreenshot = VISUAL_TEST_PREFIX + "-revealed-" + branch
                    + (stage % 2 == 1 ? "-labels" : "-fit") + ".png";
        } else if (visualTestTicks == 322) {
            requireDashboard(minecraft, "Extended map zoom").runExtendedZoomForVisualTest();
        } else if (visualTestTicks == 326) {
            pendingVisualTestScreenshot = VISUAL_TEST_PREFIX + "-zoom-8-percent.png";
        } else if (visualTestTicks == 332) {
            UUID playerId = minecraft.player.getUUID();
            minecraft.getSingleplayerServer().execute(() -> {
                ServerPlayer target = minecraft.getSingleplayerServer().getPlayerList().getPlayer(playerId);
                com.historiamc.townyeras.server.ProgressService.resetEverything(target);
                com.historiamc.townyeras.server.ProgressService.adminGrantNode(target, "early_agriculture");
                com.historiamc.townyeras.server.ServerSync.sync(target);
            });
        } else if (visualTestTicks == 342) {
            requireDashboard(minecraft, "Real synchronized grant visibility").runSyncedGrantVisibilityForVisualTest();
        } else if (visualTestTicks == 348) {
            pendingVisualTestScreenshot = VISUAL_TEST_PREFIX + "-granted-successor.png";
        } else if (visualTestTicks == 352) {
            UUID playerId = minecraft.player.getUUID();
            minecraft.getSingleplayerServer().execute(() -> {
                ServerPlayer target = minecraft.getSingleplayerServer().getPlayerList().getPlayer(playerId);
                com.historiamc.townyeras.server.ProgressService.resetEverything(target);
                com.historiamc.townyeras.server.ServerSync.sync(target);
            });
        } else if (visualTestTicks >= 360 && visualTestTicks <= 384 && (visualTestTicks - 360) % 8 == 0) {
            requireDashboard(minecraft, "Remember last menu view")
                    .verifyReopenForVisualTest((visualTestTicks - 360) / 8);
        } else if (visualTestTicks == 388) {
            requireDashboard(minecraft, "Technology feedback in gameplay").onClose();
            UUID playerId = minecraft.player.getUUID();
            minecraft.getSingleplayerServer().execute(() -> {
                ServerPlayer target = minecraft.getSingleplayerServer().getPlayerList().getPlayer(playerId);
                com.historiamc.townyeras.server.GateService.denyStation("farmersdelight:cutting_board", target, true);
            });
        } else if (visualTestTicks == 398) {
            try {
                var field = net.minecraft.client.gui.Gui.class.getDeclaredField("overlayMessageString");
                field.setAccessible(true);
                Component message = (Component) field.get(minecraft.gui);
                if (message == null || !message.getString().equals("HistoriaEras: Unlock Butchery first."))
                    throw new IllegalStateException("Technology denial did not reach the gameplay action bar: " + message);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Could not inspect test action bar", failure);
            }
            pendingVisualTestScreenshot = VISUAL_TEST_PREFIX + "-locked-technology-feedback.png";
            HistoriaEras.LOGGER.info("Technology denial delivered to client action bar");
        } else if (visualTestTicks >= 410) {
            if (!COMPLETED_VISUAL_TEST_SCREENSHOTS.contains(VISUAL_TEST_PREFIX + "-locked-technology-feedback.png"))
                throw new IllegalStateException("Missing gameplay denial capture");
            if (!COMPLETED_VISUAL_TEST_SCREENSHOTS.contains(VISUAL_TEST_PREFIX + "-granted-successor.png")
                    || !COMPLETED_VISUAL_TEST_SCREENSHOTS.contains(VISUAL_TEST_PREFIX + "-zoom-8-percent.png"))
                throw new IllegalStateException("Missing grant or extended-zoom capture");
            for (String branch : new String[]{"farmer", "chef", "mariner", "carpenter", "mason", "blacksmith"}) {
                if (!COMPLETED_VISUAL_TEST_SCREENSHOTS.contains(VISUAL_TEST_PREFIX + "-revealed-" + branch + "-fit.png")
                        || !COMPLETED_VISUAL_TEST_SCREENSHOTS.contains(VISUAL_TEST_PREFIX + "-revealed-" + branch + "-labels.png"))
                    throw new IllegalStateException("Missing geometry screenshot for " + branch);
            }
            if (COMPLETED_VISUAL_TEST_SCREENSHOTS.size() < 11 && visualTestTicks < 360) {
                return;
            }
            if (!COMPLETED_VISUAL_TEST_SCREENSHOTS.contains(VISUAL_TEST_PREFIX + "-overview.png")
                    || !COMPLETED_VISUAL_TEST_SCREENSHOTS.contains(VISUAL_TEST_PREFIX + "-technology.png")
                    || !COMPLETED_VISUAL_TEST_SCREENSHOTS.contains(VISUAL_TEST_PREFIX + "-classes.png")
                    || !COMPLETED_VISUAL_TEST_SCREENSHOTS.contains(VISUAL_TEST_PREFIX + "-timeline.png")
                    || !COMPLETED_VISUAL_TEST_SCREENSHOTS.contains(VISUAL_TEST_PREFIX + "-hud.png")
                    || !COMPLETED_VISUAL_TEST_SCREENSHOTS.contains(VISUAL_TEST_PREFIX + "-tutorial-welcome.png")
                    || !COMPLETED_VISUAL_TEST_SCREENSHOTS.contains(VISUAL_TEST_PREFIX + "-tutorial-tracking.png")
                    || COMPLETED_VISUAL_TEST_SCREENSHOTS.size() < 11) {
                throw new IllegalStateException("Visual test screenshots did not finish successfully");
            }
            try {
                Files.writeString(
                        minecraft.gameDirectory.toPath().resolve(VISUAL_TEST_SENTINEL),
                        "HistoriaEras UI smoke test passed\n"
                );
            } catch (IOException exception) {
                throw new IllegalStateException("Visual test could not write its success marker", exception);
            }
            HistoriaEras.LOGGER.info("Visual test completed successfully");
            minecraft.stop();
        }
    }

    private static boolean mayTakeCraftResult(ServerPlayer player, ItemStack result) {
        ResultContainer output = new ResultContainer();
        output.setItem(0, result);
        return new ResultSlot(player, null, output, 0, 0, 0).mayPickup(player);
    }

    private static HistoriaErasScreen requireDashboard(Minecraft minecraft, String milestone) {
        if (minecraft.screen instanceof HistoriaErasScreen screen) {
            return screen;
        }
        throw new IllegalStateException("Visual test lost the dashboard before: " + milestone);
    }

    private static void onRenderFramePost(RenderFrameEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!VISUAL_TEST
                || pendingVisualTestScreenshot == null) {
            return;
        }

        boolean hudScreenshot = pendingVisualTestScreenshot.endsWith("-hud.png")
                || pendingVisualTestScreenshot.endsWith("-locked-technology-feedback.png");
        if ((!hudScreenshot && !(minecraft.screen instanceof HistoriaErasScreen))
                || (hudScreenshot && minecraft.screen != null)) {
            return;
        }

        String fileName = pendingVisualTestScreenshot;
        pendingVisualTestScreenshot = null;
        capture(minecraft, fileName);
    }

    private static void capture(Minecraft minecraft, String fileName) {
        int framebufferWidth = minecraft.getMainRenderTarget().width;
        int framebufferHeight = minecraft.getMainRenderTarget().height;
        if (framebufferWidth <= 1 || framebufferHeight <= 1) {
            throw new IllegalStateException(
                    "Visual test framebuffer is not ready: " + framebufferWidth + "x" + framebufferHeight
            );
        }

        Path output = minecraft.gameDirectory.toPath().resolve("screenshots").resolve(fileName);
        try {
            Files.deleteIfExists(output);
        } catch (IOException exception) {
            throw new IllegalStateException("Visual test could not replace " + fileName, exception);
        }

        HistoriaEras.LOGGER.info(
                "Visual test capturing {} at {}x{}",
                fileName,
                framebufferWidth,
                framebufferHeight
        );
        Screenshot.grab(
                minecraft.gameDirectory,
                fileName,
                minecraft.getMainRenderTarget(),
                message -> {
                    HistoriaEras.LOGGER.info("Visual test screenshot: {}", message.getString());
                    if (message.getContents() instanceof TranslatableContents contents
                            && "screenshot.success".equals(contents.getKey())) {
                        try {
                            if (Files.size(output) <= 70L) {
                                visualTestFailure = "Visual test wrote an invalid screenshot: " + fileName;
                            } else {
                                COMPLETED_VISUAL_TEST_SCREENSHOTS.add(fileName);
                            }
                        } catch (IOException exception) {
                            visualTestFailure = "Visual test could not verify screenshot: " + fileName;
                        }
                    } else {
                        visualTestFailure = "Visual test screenshot failed: " + message.getString();
                    }
                }
        );
    }
}
