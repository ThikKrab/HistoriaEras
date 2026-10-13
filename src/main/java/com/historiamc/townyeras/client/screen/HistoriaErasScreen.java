package com.historiamc.townyeras.client.screen;

import com.historiamc.townyeras.client.HistoriaErasClient;
import com.historiamc.townyeras.client.HistoriaErasClientPreferences;
import com.historiamc.townyeras.client.data.DashboardSnapshot;
import com.historiamc.townyeras.client.data.NodeDefinition;
import com.historiamc.townyeras.client.data.NodeProgress;
import com.historiamc.townyeras.client.data.NodeStatus;
import com.historiamc.townyeras.client.data.RequirementProgress;
import com.historiamc.townyeras.client.data.SyncedDashboardDataProvider;
import com.historiamc.townyeras.client.data.TimelineView;
import com.historiamc.townyeras.client.data.TechnologyBranchVisibility;
import com.historiamc.townyeras.client.data.TechnologyEraVisibility;
import com.historiamc.townyeras.client.data.TechnologyPresentation;
import com.historiamc.townyeras.client.data.TechnologyTreeLayout;
import com.historiamc.townyeras.client.data.TechnologyNodeVisibility;
import com.historiamc.townyeras.client.data.TechnologyEdgeRouter;
import com.historiamc.townyeras.network.ClientSnapshotCache;
import com.historiamc.townyeras.network.HistoriaErasNetwork;
import com.historiamc.townyeras.tech.TechClass;
import com.historiamc.townyeras.tech.TechEra;
import com.historiamc.townyeras.tech.TechRegistry;
import com.historiamc.townyeras.tech.TechnologyManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class HistoriaErasScreen extends Screen {
    private static RememberedView rememberedView;

    private record RememberedView(java.util.UUID playerId, Page page, String era, String branch,
                                  ResourceLocation node, double panX, double panY, double zoom,
                                  int scroll, int maxScroll, String search, List<String> groups,
                                  String focusedClass) {}

    public static void clearRememberedView() {
        rememberedView = null;
    }

    private static final int OUTER = 0xFF17191B;
    private static final int EDGE_LIGHT = 0xFF777B7E;
    private static final int EDGE_DARK = 0xFF292C2E;
    private static final int PANEL = 0xFF4B4E50;
    private static final int PANEL_DARK = 0xFF303336;
    private static final int FIELD = 0xFF202326;
    private static final int TEXT = 0xFFF2EEE4;
    private static final int MUTED = 0xFFC5C0B5;
    private static final int GOLD = 0xFFD5A94D;
    private static final int GOLD_DARK = 0xFF7B5B23;
    private static final int RED = 0xFFB13C37;

    private DashboardSnapshot snapshot;
    private Page activePage = Page.OVERVIEW;
    private ResourceLocation selectedNodeId;
    private ResourceLocation trackedNodeId;
    private String selectedEraId;
    private String selectedBranchId;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int navigationWidth;
    private int headerX;
    private int headerY;
    private int headerWidth;
    private int contentX;
    private int contentY;
    private int contentWidth;
    private int contentHeight;

    private double treePanX;
    private double treePanY;
    private double treeZoom = 0.78D;
    private boolean treePositionInitialized;
    private boolean draggingTree;
    private boolean draggingDetailsScrollbar;
    private double detailsScrollbarGrabOffset;
    private Rect detailsScrollbarTrack;
    private Rect detailsScrollbarThumb;
    private int technologyDetailsScroll;
    private int technologyDetailsMaxScroll;
    private EditBox technologySearch;
    private String searchQuery = "";
    private final LinkedHashSet<String> expandedUnlockGroups = new LinkedHashSet<>();
    private final List<GroupHit> unlockGroupHits = new ArrayList<>();
    private final List<PrerequisiteHit> prerequisiteHits = new ArrayList<>();
    private final List<ObjectiveHint> objectiveHints = new ArrayList<>();
    private List<NodeDefinition> layoutNodes = List.of();
    private java.util.Map<String, TechnologyTreeLayout.Position> treeLayout = java.util.Map.of();
    private NodeDefinition hoveredNode;
    private boolean visualTestRevealNodes;
    private Page hoveredNavigation;
    private boolean recenterHovered;
    private long snapshotRevision;
    private final LinkedHashSet<String> pendingInitialClasses = new LinkedHashSet<>();
    private String swapFromClassId;
    private String swapToClassId;
    private String focusedClassId;
    private String classFeedback = "";
    private String armedTimelineAction = "";
    private String adminResetTarget;
    private ResourceLocation adminGrantNodeId;
    private boolean armedProgressReset;
    private int tutorialStep = -1;
    private int tutorialTextScroll;
    private int tutorialTextMaxScroll;
    private boolean treeViewedAcknowledged;
    private static final int LAVENDER = 0xFFD9ACFF;
    private static final String[] QUESTION_PIXELS = {"01110", "11011", "00011", "00110", "00100", "00000", "00100"};

    public HistoriaErasScreen(DashboardSnapshot snapshot) {
        super(Component.translatable("screen.townyeras.dashboard"));
        this.snapshot = snapshot;
        treeViewedAcknowledged = ClientSnapshotCache.latest().getBoolean("techTreeViewed");
        if (!ClientSnapshotCache.latest().getBoolean("tutorialDismissed")
                && !Boolean.getBoolean("townyeras.prototype.visualTest")) tutorialStep = 0;
        this.snapshotRevision = ClientSnapshotCache.revision();
        this.selectedNodeId = snapshot.trackedNodeId();
        this.trackedNodeId = snapshot.trackedNodeId();
        if (snapshot.selectedClassIds().size() < 2) {
            this.pendingInitialClasses.addAll(snapshot.selectedClassIds());
        }
        this.focusedClassId = snapshot.selectedClassIds().isEmpty()
                ? selectableClasses().getFirst().id()
                : snapshot.selectedClassIds().getFirst();
        this.adminResetTarget = snapshot.onlinePlayerNames().isEmpty()
                ? snapshot.displayName()
                : snapshot.onlinePlayerNames().getFirst();
        this.adminGrantNodeId = snapshot.nodes().isEmpty() ? null : snapshot.nodes().getFirst().id();
        NodeDefinition initiallySelected = snapshot.node(this.selectedNodeId);
        this.selectedEraId = initiallySelected == null ? snapshot.eraId() : initiallySelected.eraId();
        this.selectedBranchId = initiallySelected == null ? "universal" : initiallySelected.classId();
        if (initiallySelected != null && (!prerequisitesRevealed(initiallySelected)
                || !technologyEraAllowed(initiallySelected.eraId()))) {
            this.selectedNodeId = null;
            this.selectedEraId = snapshot.eraId();
            this.selectedBranchId = "universal";
        }
        if (!technologyBranchAllowed(this.selectedBranchId)) {
            this.selectedNodeId = null;
            this.selectedBranchId = "universal";
        }
        restoreRememberedView();
    }

    private void restoreRememberedView() {
        RememberedView view = rememberedView;
        if (view == null || !view.playerId().equals(snapshot.playerId())) return;
        activePage = view.page();
        focusedClassId = selectableClasses().stream().anyMatch(role -> role.id().equals(view.focusedClass()))
                ? view.focusedClass() : focusedClassId;
        boolean validEra = TechnologyManager.current().eras().values().stream()
                .anyMatch(era -> era.id().equals(view.era()));
        if (!validEra || !technologyEraAllowed(view.era()) || !technologyBranchAllowed(view.branch())) return;
        selectedEraId = view.era();
        selectedBranchId = view.branch();
        NodeDefinition node = snapshot.node(view.node());
        selectedNodeId = node != null && node.eraId().equals(selectedEraId)
                && node.classId().equals(selectedBranchId) ? node.id() : null;
        treePanX = view.panX();
        treePanY = view.panY();
        treeZoom = Math.max(0.08D, Math.min(1.45D, view.zoom()));
        treePositionInitialized = true;
        technologyDetailsScroll = view.scroll();
        technologyDetailsMaxScroll = view.maxScroll();
        searchQuery = view.search();
        expandedUnlockGroups.addAll(view.groups());
    }

    public void verifyReopenForVisualTest(int pageIndex) {
        if (!Boolean.getBoolean("townyeras.prototype.visualTest")) return;
        selectPage(Page.values()[pageIndex]);
        selectedEraId = "ancient";
        selectedBranchId = "universal";
        selectedNodeId = ResourceLocation.parse("townyeras:craftsmanship");
        treePanX = 37.5D;
        treePanY = -14.25D;
        treeZoom = 0.61D;
        treePositionInitialized = true;
        technologyDetailsScroll = 24;
        technologyDetailsMaxScroll = 100;
        searchQuery = "craft";
        if (technologySearch != null) technologySearch.setValue(searchQuery);
        expandedUnlockGroups.add("Tables and storage");
        onClose();
        HistoriaErasScreen reopened = new HistoriaErasScreen(snapshot);
        Minecraft.getInstance().setScreen(reopened);
        if (reopened.activePage != activePage || !selectedNodeId.equals(reopened.selectedNodeId)
                || !selectedBranchId.equals(reopened.selectedBranchId)
                || !selectedEraId.equals(reopened.selectedEraId)
                || reopened.treePanX != treePanX || reopened.treePanY != treePanY
                || reopened.treeZoom != treeZoom || reopened.technologyDetailsScroll != 24
                || !searchQuery.equals(reopened.searchQuery)
                || !reopened.expandedUnlockGroups.contains("Tables and storage"))
            throw new IllegalStateException("Menu did not restore view on " + activePage);
        com.historiamc.townyeras.HistoriaEras.LOGGER.info("Close/reopen preserved {} tab and technology view", activePage);
    }

    @Override
    public void removed() {
        // Keep navigation only, never armed admin actions or pending class changes.
        if (ClientSnapshotCache.isSynchronizedWithServer()) {
            rememberedView = new RememberedView(snapshot.playerId(), activePage, selectedEraId,
                    selectedBranchId, selectedNodeId, treePanX, treePanY, treeZoom,
                    technologyDetailsScroll, technologyDetailsMaxScroll, searchQuery,
                    List.copyOf(expandedUnlockGroups), focusedClassId);
        }
        super.removed();
    }

    @Override
    public void tick() {
        super.tick();
        long currentRevision = ClientSnapshotCache.revision();
        Minecraft minecraft = Minecraft.getInstance();
        if (currentRevision == snapshotRevision || minecraft.player == null
                || !ClientSnapshotCache.isSynchronizedWithServer()) {
            return;
        }

        List<String> previousClasses = snapshot.selectedClassIds();
        snapshot = new SyncedDashboardDataProvider().snapshot(minecraft.player);
        snapshotRevision = currentRevision;
        trackedNodeId = snapshot.trackedNodeId();
        boolean branchChanged = false;
        if (!technologyEraAllowed(selectedEraId)) {
            selectedEraId = snapshot.eraId();
            selectedNodeId = null;
            technologyDetailsScroll = 0;
            treePositionInitialized = false;
            branchChanged = true;
        }
        List<String> availableBranches = availableBranchIds();
        if (!availableBranches.contains(selectedBranchId)) {
            selectedBranchId = availableBranches.contains("universal")
                    ? "universal"
                    : availableBranches.stream().findFirst().orElse("universal");
            branchChanged = true;
        }
        if (selectedNodeId != null && snapshot.node(selectedNodeId) == null) {
            selectedNodeId = trackedNodeId;
        }
        if (branchChanged || (selectedNodeId != null
                && visibleNodes().stream().noneMatch(node -> node.id().equals(selectedNodeId)))) {
            selectFirstVisibleNode();
        }
        if (!previousClasses.equals(snapshot.selectedClassIds())) {
            pendingInitialClasses.clear();
            if (snapshot.selectedClassIds().size() < 2) {
                pendingInitialClasses.addAll(snapshot.selectedClassIds());
            }
            swapFromClassId = null;
            swapToClassId = null;
            classFeedback = "Class selection updated.";
        }
        if (!snapshot.onlinePlayerNames().isEmpty()
                && !snapshot.onlinePlayerNames().contains(adminResetTarget)) {
            adminResetTarget = snapshot.onlinePlayerNames().getFirst();
            armedProgressReset = false;
        }
    }

    @Override
    protected void init() {
        panelWidth = Math.min(600, Math.max(240, width - 12));
        panelHeight = Math.min(330, Math.max(150, height - 12));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;

        navigationWidth = panelWidth < 360 ? 32 : 70;
        headerX = panelX + navigationWidth + 5;
        headerY = panelY + 5;
        headerWidth = panelWidth - navigationWidth - 10;

        contentX = headerX;
        contentWidth = headerWidth;
        updateContentLayout();
        Rect search = technologySearchRect();
        technologySearch = new EditBox(font, search.x, search.y, search.width, search.height,
                Component.literal("Search revealed technology"));
        technologySearch.setMaxLength(64);
        technologySearch.setHint(Component.literal("Search revealed..."));
        technologySearch.setValue(searchQuery);
        technologySearch.setResponder(value -> searchQuery = value);
        addWidget(technologySearch);

        if (!treePositionInitialized) {
            fitTreeToViewport(technologyViewport());
            treePositionInitialized = true;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The base screen draws the blurred/dimmed world before the dashboard.
        super.render(graphics, mouseX, mouseY, partialTick);
        technologySearch.visible = activePage == Page.TECHNOLOGY && tutorialStep < 0;
        technologySearch.active = technologySearch.visible;
        hoveredNode = null;
        hoveredNavigation = null;
        recenterHovered = false;

        drawBeveledPanel(graphics, panelX, panelY, panelWidth, panelHeight, PANEL_DARK, EDGE_LIGHT);
        renderNavigation(graphics, mouseX, mouseY);
        if (activePage == Page.OVERVIEW) renderHeader(graphics);

        switch (activePage) {
            case OVERVIEW -> renderOverview(graphics, mouseX, mouseY);
            case TECHNOLOGY -> renderTechnology(graphics, mouseX, mouseY);
            case CLASSES -> renderClasses(graphics, mouseX, mouseY);
            case TIMELINE -> renderTimeline(graphics, mouseX, mouseY);
        }

        if (tutorialStep >= 0) {
            renderTutorial(graphics, mouseX, mouseY);
            return;
        }
        if (hoveredNode != null) {
            NodeProgress progress = snapshot.progress(hoveredNode.id());
            String tooltip = isConcealed(hoveredNode)
                    ? "Unknown technology — complete its prerequisites to reveal it"
                    : hoveredNode.title() + " — " + progress.status().label();
            graphics.renderTooltip(
                    font,
                    Component.literal(tooltip),
                    mouseX,
                    mouseY
            );
        } else if (hoveredNavigation != null) {
            String suffix = hoveredNavigation.enabled ? "" : " (coming soon)";
            graphics.renderTooltip(
                    font,
                    Component.literal(hoveredNavigation.label + suffix),
                    mouseX,
                    mouseY
            );
        } else if (recenterHovered) {
            graphics.renderTooltip(font, Component.translatable("screen.townyeras.recenter"), mouseX, mouseY);
        }
        if (activePage == Page.TECHNOLOGY) for (ObjectiveHint hint : objectiveHints) {
            Rect bounds = hint.bounds();
            if (contains(mouseX, mouseY, bounds.x, bounds.y, bounds.width, bounds.height)) {
                graphics.renderTooltip(font, font.split(Component.literal(hint.text()), Math.min(220, width - 20)), mouseX, mouseY);
                break;
            }
        }
    }

    private void renderNavigation(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = panelX + 5;
        int y = panelY + 5;
        int width = navigationWidth - 10;
        int titleHeight = 32;

        drawBeveledPanel(graphics, x, y, width, titleHeight, FIELD, EDGE_LIGHT);
        String logo = navigationWidth < 50 ? "HE" : "HISTORIA\nERAS";
        if (logo.contains("\n")) {
            drawFittedCenteredString(graphics, "HISTORIA", x + width / 2, y + 7, width - 6, GOLD);
            graphics.drawCenteredString(font, "ERAS", x + width / 2, y + 18, TEXT);
        } else {
            graphics.drawCenteredString(font, logo, x + width / 2, y + 12, GOLD);
        }

        int buttonY = y + titleHeight + 5;
        for (Page page : visiblePages()) {
            int buttonHeight = 27;
            boolean hovered = contains(mouseX, mouseY, x, buttonY, width, buttonHeight);
            if (hovered) {
                hoveredNavigation = page;
            }

            int fill = page == activePage ? 0xFF5E5138 : hovered ? 0xFF45494C : FIELD;
            int border = page == activePage ? GOLD : page.enabled ? EDGE_LIGHT : 0xFF4A4D50;
            drawBeveledPanel(graphics, x, buttonY, width, buttonHeight, fill, border);

            String label = navigationWidth < 50 ? page.shortLabel : page.label;
            int color = page.enabled ? TEXT : 0xFF848484;
            graphics.drawCenteredString(font, label, x + width / 2, buttonY + 9, color);
            buttonY += buttonHeight + 4;
        }

        int footerY = panelY + panelHeight - 22;
        Rect help = tutorialHelpRect();
        drawBeveledPanel(graphics, help.x, help.y, help.width, help.height, FIELD,
                contains(mouseX, mouseY, help.x, help.y, help.width, help.height) ? LAVENDER : EDGE_LIGHT);
        graphics.drawCenteredString(font, navigationWidth < 50 ? "?" : "Tutorial", help.x + help.width / 2,
                help.y + 5, LAVENDER);
        String version = net.neoforged.fml.ModList.get().getModContainerById("townyeras")
                .map(container -> container.getModInfo().getVersion().toString().replace("-alpha", ""))
                .orElse("?");
        graphics.drawCenteredString(font, "v" + version, x + width / 2, footerY, 0xFFE7C879);
    }

    private void drawFittedCenteredString(GuiGraphics graphics, String text, int centerX, int y,
                                         int maxWidth, int color) {
        float scale = Math.min(1.0F, Math.max(1, maxWidth) / (float) Math.max(1, font.width(text)));
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, y, 0);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawCenteredString(font, text, 0, 0, color);
        graphics.pose().popPose();
    }

    private void renderHeader(GuiGraphics graphics) {
        drawBeveledPanel(graphics, headerX, headerY, headerWidth, 41, PANEL, EDGE_LIGHT);

        Minecraft minecraft = Minecraft.getInstance();
        int headX = headerX + 6;
        int headY = headerY + 6;
        if (minecraft.player != null) {
            PlayerFaceRenderer.draw(graphics, minecraft.player.getSkin().texture(), headX, headY, 29);
        } else {
            graphics.fill(headX, headY, headX + 29, headY + 29, FIELD);
        }

        int textX = headX + 35;
        int textWidth = Math.max(35, headerX + headerWidth - textX - 7);
        graphics.drawString(font, ellipsize(snapshot.displayName(), textWidth), textX, headerY + 7, TEXT, false);
        graphics.drawString(font, ellipsize(snapshot.eraName(), textWidth), textX, headerY + 19, GOLD, false);
        if (headerWidth >= 235) {
            graphics.drawString(font, ellipsize(snapshot.calendarDate(), textWidth), textX, headerY + 30, MUTED, false);
        }
    }

    private void renderOverview(GuiGraphics graphics, int mouseX, int mouseY) {
        int progressHeight = contentHeight >= 120 ? 35 : 27;
        int upperHeight = contentHeight - progressHeight - 4;
        int characterWidth = Math.max(76, Math.min(150, (int) (contentWidth * 0.43F)));
        int statisticsX = contentX + characterWidth + 4;
        int statisticsWidth = contentWidth - characterWidth - 4;

        drawBeveledPanel(graphics, contentX, contentY, characterWidth, upperHeight, FIELD, EDGE_LIGHT);
        graphics.drawCenteredString(font, Component.translatable("screen.townyeras.character"), contentX + characterWidth / 2, contentY + 6, MUTED);

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && upperHeight >= 60) {
            int entityTop = contentY + 17;
            int entityBottom = contentY + upperHeight - 4;
            int scale = Math.max(22, Math.min(48, (entityBottom - entityTop) / 2));
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    graphics,
                    contentX + 4,
                    entityTop,
                    contentX + characterWidth - 4,
                    entityBottom,
                    scale,
                    0.0625F,
                    mouseX,
                    mouseY,
                    minecraft.player
            );
        }

        drawBeveledPanel(graphics, statisticsX, contentY, statisticsWidth, upperHeight, PANEL, EDGE_LIGHT);
        int textX = statisticsX + 7;
        int textY = contentY + 7;
        graphics.drawString(font, Component.translatable("screen.townyeras.summary"), textX, textY, GOLD, false);
        textY += 14;

        List<String> lines = new ArrayList<>();
        lines.add("Nation: " + snapshot.nationName());
        lines.add("Classes: " + String.join(", ", snapshot.classes()));
        lines.add("Job tokens: " + snapshot.jobTokens());
        lines.add("Nodes complete: " + snapshot.completedNodeCount());
        lines.add("Era: " + snapshot.eraName());

        int availableLines = Math.max(1, (upperHeight - 26) / 12);
        for (int i = 0; i < Math.min(lines.size(), availableLines); i++) {
            graphics.drawString(font, ellipsize(lines.get(i), statisticsWidth - 14), textX, textY, TEXT, false);
            textY += 12;
        }

        if (snapshot.timelineAdmin() && upperHeight >= 137 && !snapshot.onlinePlayerNames().isEmpty()) {
            Rect target = overviewAdminTargetRect();
            Rect node = overviewAdminNodeRect();
            Rect grant = overviewAdminGrantRect();
            Rect reset = overviewAdminResetRect();
            boolean targetHovered = contains(mouseX, mouseY, target.x, target.y, target.width, target.height);
            boolean nodeHovered = contains(mouseX, mouseY, node.x, node.y, node.width, node.height);
            boolean grantHovered = contains(mouseX, mouseY, grant.x, grant.y, grant.width, grant.height);
            boolean resetHovered = contains(mouseX, mouseY, reset.x, reset.y, reset.width, reset.height);
            drawBeveledPanel(graphics, target.x, target.y, target.width, target.height,
                    targetHovered ? PANEL_DARK : FIELD, targetHovered ? GOLD : EDGE_LIGHT);
            graphics.drawCenteredString(font, ellipsize("Player: " + adminResetTarget + " ›", target.width - 8),
                    target.x + target.width / 2, target.y + 4, TEXT);
            NodeDefinition grantNode = snapshot.node(adminGrantNodeId);
            String grantNodeName = grantNode == null ? "No node" : grantNode.title();
            drawBeveledPanel(graphics, node.x, node.y, node.width, node.height,
                    nodeHovered ? PANEL_DARK : FIELD, nodeHovered ? GOLD : EDGE_LIGHT);
            graphics.drawCenteredString(font, ellipsize("Node: " + grantNodeName + " ›", node.width - 8),
                    node.x + node.width / 2, node.y + 4, TEXT);
            drawBeveledPanel(graphics, grant.x, grant.y, grant.width, grant.height,
                    grantHovered ? 0xFF574A30 : PANEL_DARK, GOLD);
            graphics.drawCenteredString(font, "Grant node",
                    grant.x + grant.width / 2, grant.y + 4, TEXT);
            drawBeveledPanel(graphics, reset.x, reset.y, reset.width, reset.height,
                    resetHovered ? 0xFF5D3935 : PANEL_DARK, 0xFFC36B5C);
            graphics.drawCenteredString(font,
                    armedProgressReset ? "Confirm reset" : "Reset progress",
                    reset.x + reset.width / 2, reset.y + 4, TEXT);
        }

        int progressY = contentY + upperHeight + 4;
        drawBeveledPanel(graphics, contentX, progressY, contentWidth, progressHeight, PANEL, EDGE_LIGHT);
        NodeDefinition tracked = snapshot.node(trackedNodeId);
        if (tracked != null) {
            NodeProgress trackedProgress = snapshot.progress(tracked.id());
            RequirementProgress next = trackedProgress.nextRequirement();
            String objective = next == null
                    ? "Tracked: " + tracked.title() + "  •  " + trackedProgress.status().label()
                    : "Tracked: " + tracked.title() + "  •  " + next.display() + "  " + next.formattedProgress();
            graphics.drawString(font, ellipsize(objective, contentWidth - 14), contentX + 7, progressY + 6, TEXT, false);
            int barY = progressY + progressHeight - 10;
            renderProgressBar(graphics, contentX + 7, barY, contentWidth - 14, 5, trackedProgress.fraction(), trackedProgress.status().color());
        }
    }

    private void renderTechnology(GuiGraphics graphics, int mouseX, int mouseY) {
        int detailsWidth = technologyDetailsWidth();
        int gap = 4;
        int viewWidth = contentWidth - detailsWidth - gap;
        Rect treePanel = new Rect(contentX, contentY, viewWidth, contentHeight);
        Rect viewport = technologyViewport();
        Rect details = new Rect(contentX + viewWidth + gap, contentY, detailsWidth, contentHeight);

        drawBeveledPanel(graphics, treePanel.x, treePanel.y, treePanel.width, treePanel.height, FIELD, EDGE_LIGHT);
        drawBeveledPanel(graphics, details.x, details.y, details.width, details.height, PANEL, EDGE_LIGHT);
        renderTreeFilters(graphics, treePanel, mouseX, mouseY);

        graphics.enableScissor(viewport.x + 2, viewport.y + 2, viewport.right() - 2, viewport.bottom() - 2);
        renderTreeGrid(graphics, viewport);
        renderTreeConnections(graphics, viewport);
        renderTreeNodes(graphics, viewport, mouseX, mouseY);

        graphics.disableScissor();
        for (int i = 0; i < 3; i++) {
            Rect button = treeNavigationRect(i);
            boolean hover = contains(mouseX, mouseY, button.x, button.y, button.width, button.height);
            drawBeveledPanel(graphics, button.x, button.y, button.width, button.height,
                    hover ? PANEL : PANEL_DARK, hover ? GOLD : EDGE_LIGHT);
            String label = i == 0 ? "Fit branch" : i == 1 ? "Center selected" : "Tracked node";
            if (button.width < 75) label = i == 0 ? "Fit" : i == 1 ? "Selected" : "Tracked";
            if (button.width < 48) label = i == 0 ? "Fit" : i == 1 ? "Sel." : "Goal";
            graphics.drawCenteredString(font, label, button.x + button.width / 2, button.y + 4, TEXT);
        }
        if (technologySearch.visible) technologySearch.render(graphics, mouseX, mouseY, 0);
        graphics.drawString(font, Math.round(treeZoom * 100) + "%", viewport.x + 5, viewport.y + 5, MUTED, false);
        if (!searchQuery.isBlank() && tutorialStep < 0) {
            String feedback = searchMatches().isEmpty() ? "No revealed matches" : "Enter: next match";
            graphics.drawString(font, feedback, viewport.right() - font.width(feedback) - 5, viewport.y + 17, LAVENDER, false);
        }
        String legend = viewport.width >= 220 ? "Complete  Available  Locked" : "Drag • Scroll to zoom";
        graphics.drawString(font, legend, treePanel.x + 5, treePanel.bottom() - 12, MUTED, false);
        renderNodeDetails(graphics, details);
    }

    private void renderTreeFilters(GuiGraphics graphics, Rect panel, int mouseX, int mouseY) {
        int x = panel.x + 4;
        int y = panel.y + 3;
        int available = Math.max(60, panel.width - 8);
        int firstWidth = Math.max(52, Math.min(76, available / 2));
        int secondWidth = Math.max(52, available - firstWidth - 3);
        String era = displayId(selectedEraId);
        String branch = displayId(selectedBranchId);
        boolean eraHovered = contains(mouseX, mouseY, x, y, firstWidth, 15);
        boolean branchHovered = contains(mouseX, mouseY, x + firstWidth + 3, y, secondWidth, 15);
        drawBeveledPanel(graphics, x, y, firstWidth, 15, eraHovered ? PANEL : PANEL_DARK, eraHovered ? GOLD : EDGE_LIGHT);
        drawBeveledPanel(graphics, x + firstWidth + 3, y, secondWidth, 15, branchHovered ? PANEL : PANEL_DARK, branchHovered ? GOLD : EDGE_LIGHT);
        graphics.drawCenteredString(font, ellipsize(era + " ›", firstWidth - 6), x + firstWidth / 2, y + 4, TEXT);
        graphics.drawCenteredString(font, ellipsize(branch + " ›", secondWidth - 6), x + firstWidth + 3 + secondWidth / 2, y + 4, TEXT);
    }

    private void renderTreeGrid(GuiGraphics graphics, Rect viewport) {
        int spacing = Math.max(18, (int) Math.round(32.0D * treeZoom));
        int offsetX = Math.floorMod((int) Math.round(treePanX), spacing);
        int offsetY = Math.floorMod((int) Math.round(treePanY), spacing);
        int color = 0xFF292D30;
        for (int x = viewport.x + offsetX; x < viewport.right(); x += spacing) {
            graphics.fill(x, viewport.y + 2, x + 1, viewport.bottom() - 2, color);
        }
        for (int y = viewport.y + offsetY; y < viewport.bottom(); y += spacing) {
            graphics.fill(viewport.x + 2, y, viewport.right() - 2, y + 1, color);
        }
    }

    private void renderTreeConnections(GuiGraphics graphics, Rect viewport) {
        List<NodeDefinition> branchNodes = visibleNodes();
        java.util.Set<ResourceLocation> branchNodeIds = branchNodes.stream()
                .map(NodeDefinition::id)
                .collect(java.util.stream.Collectors.toSet());
        for (NodeDefinition node : branchNodes) {
            Point target = treePoint(viewport, node);
            for (ResourceLocation prerequisiteId : node.prerequisites()) {
                // Other branches use their own coordinates. Drawing their edges here
                // creates dangling lines with no visible source node.
                if (!branchNodeIds.contains(prerequisiteId)) {
                    continue;
                }
                NodeDefinition prerequisite = snapshot.node(prerequisiteId);
                if (prerequisite == null) {
                    continue;
                }
                Point source = treePoint(viewport, prerequisite);
                int color = isConcealed(node)
                        ? 0x90686B6E
                        : withAlpha(snapshot.progress(node.id()).status().color(), 0xC0);
                int half = treeNodeSize() / 2;
                var from = graphPosition(prerequisite);
                var to = graphPosition(node);
                List<TechnologyEdgeRouter.Box> obstacles = branchNodes.stream()
                        .filter(n -> !n.id().equals(node.id()) && !n.id().equals(prerequisite.id()))
                        .map(n -> {
                            var p = graphPosition(n);
                            int radius = Math.max(20, (int) Math.ceil(half / treeZoom) + 4);
                            return new TechnologyEdgeRouter.Box(p.x() - 64, p.y() - radius,
                                    p.x() + 64, p.y() + (treeZoom >= 0.60D ? 80 : radius));
                        }).toList();
                var path = TechnologyEdgeRouter.route(new TechnologyEdgeRouter.Point(from.x(), from.y()),
                        new TechnologyEdgeRouter.Point(to.x(), to.y()), obstacles);
                for (int i = 1; i < path.size(); i++) {
                    var a = path.get(i - 1);
                    var b = path.get(i);
                    int ax = source.x + (int) Math.round((a.x() - from.x()) * treeZoom);
                    int ay = source.y + (int) Math.round((a.y() - from.y()) * treeZoom);
                    int bx = source.x + (int) Math.round((b.x() - from.x()) * treeZoom);
                    int by = source.y + (int) Math.round((b.y() - from.y()) * treeZoom);
                    if (i == 1) ax += half;
                    if (i == path.size() - 1) bx -= half;
                    drawLine(graphics, ax, ay, bx, by, color, 2);
                }
            }
        }
    }

    private void renderTreeNodes(GuiGraphics graphics, Rect viewport, int mouseX, int mouseY) {
        int nodeSize = treeNodeSize();
        for (NodeDefinition node : visibleNodes()) {
            Point center = treePoint(viewport, node);
            int x = center.x - nodeSize / 2;
            int y = center.y - nodeSize / 2;
            NodeProgress progress = snapshot.progress(node.id());
            boolean concealed = isConcealed(node);
            boolean selected = node.id().equals(selectedNodeId);
            boolean hovered = contains(mouseX, mouseY, x, y, nodeSize, nodeSize)
                    && contains(mouseX, mouseY, viewport.x + 2, viewport.y + 2, viewport.width - 4, viewport.height - 4);
            if (hovered) {
                hoveredNode = node;
            }

            boolean searchMatch = !searchQuery.isBlank() && !concealed
                    && matchesSearch(node);
            int border = selected ? GOLD : hovered ? 0xFFFFFFFF : searchMatch ? LAVENDER
                    : concealed ? 0xFF686B6E : progress.status().color();
            int fill = progress.status() == NodeStatus.LOCKED ? 0xFF292B2D : 0xFF3B3F42;
            drawBeveledPanel(graphics, x, y, nodeSize, nodeSize, fill, border);

            if (concealed) renderQuestionMark(graphics, center.x, center.y, nodeSize);
            else {
                float scale = Math.min(1.0F, Math.max(0.25F, (nodeSize - 4) / 16.0F));
                graphics.pose().pushPose();
                graphics.pose().translate(center.x - 8 * scale, center.y - 8 * scale, 0);
                graphics.pose().scale(scale, scale, 1);
                graphics.renderItem(iconFor(node.icon()), 0, 0);
                graphics.pose().popPose();
            }

            if (!concealed && progress.status() == NodeStatus.IN_PROGRESS) {
                renderProgressBar(graphics, x + 3, y + nodeSize - 5, nodeSize - 6, 2, progress.fraction(), progress.status().color());
            }

            // At overview zoom, fixed-size text cannot fit between fixed-minimum-size icons.
            // The selected name remains in the detail panel; hovering shows the full tooltip.
            if (!concealed && treeZoom >= 0.60D) {
                drawTreeNodeLabel(graphics, node.title(), center.x, y, nodeSize, false);
            }
        }
    }

    private void drawTreeNodeLabel(
            GuiGraphics graphics,
            String title,
            int centerX,
            int nodeY,
            int nodeSize,
            boolean above
    ) {
        int wrapWidth = Math.min(112, (int) Math.round(130.0D * treeZoom));
        List<FormattedCharSequence> lines = font.split(Component.literal(title), wrapWidth);
        int labelWidth = lines.stream().mapToInt(font::width).max().orElse(0);
        int labelHeight = Math.max(10, lines.size() * 10);
        int labelY = above ? nodeY - labelHeight - 3 : nodeY + nodeSize + 3;
        int labelX = centerX - labelWidth / 2;
        graphics.fill(labelX - 3, labelY - 2, labelX + labelWidth + 3, labelY + labelHeight,
                0xD91D2022);
        for (int index = 0; index < lines.size(); index++) {
            graphics.drawString(font, lines.get(index), centerX - font.width(lines.get(index)) / 2,
                    labelY + index * 10, TEXT, false);
        }
    }

    private void renderNodeDetails(GuiGraphics graphics, Rect details) {
        detailsScrollbarTrack = null;
        detailsScrollbarThumb = null;
        unlockGroupHits.clear();
        prerequisiteHits.clear();
        objectiveHints.clear();
        NodeDefinition node = snapshot.node(selectedNodeId);
        int x = details.x + 7;
        int width = details.width - 19;
        if (node == null) {
            graphics.drawString(font, Component.translatable("screen.townyeras.select_node"), x, details.y + 7, MUTED, false);
            return;
        }

        if (isConcealed(node)) {
            technologyDetailsScroll = 0;
            technologyDetailsMaxScroll = 0;
            int lockedY = drawWrappedAll(graphics, "Unknown technology", x, details.y + 8, width, LAVENDER);
            graphics.drawString(font, "Locked", x, lockedY + 4, 0xFF9B9D9F, false);
            drawWrappedAll(
                    graphics,
                    "Complete the prerequisite technologies to reveal this node, its objectives, and its unlocks.",
                    x,
                    lockedY + 20,
                    width,
                    MUTED
            );
            return;
        }

        NodeProgress progress = snapshot.progress(node.id());
        graphics.renderItem(iconFor(node.icon()), x, details.y + 7);
        int titleBottom = drawWrappedAll(graphics, node.title(), x + 21, details.y + 7, width - 21, GOLD);
        int statusY = Math.max(details.y + 26, titleBottom + 3);
        graphics.drawString(font, progress.status().label(), x, statusY, progress.status().color(), false);
        int viewportTop = statusY + 14;
        int viewportBottom = details.bottom() - 21;
        int viewportHeight = Math.max(1, viewportBottom - viewportTop);
        technologyDetailsScroll = Math.clamp(technologyDetailsScroll, 0, technologyDetailsMaxScroll);
        int contentStartY = viewportTop + 3;
        int y = contentStartY - technologyDetailsScroll;
        graphics.enableScissor(details.x + 3, viewportTop, details.right() - 3, viewportBottom);

        // Only actionable restrictions belong here; the status already conveys normal progress.
        if ((progress.status() == NodeStatus.CLASS_LOCKED
                || (progress.status() == NodeStatus.COMPLETED && !progress.accessible())) && !progress.reason().isBlank()) {
            y = drawWrappedAll(graphics, progress.reason(), x, y, width, MUTED) + 2;
        }

        graphics.drawString(font, "Prerequisites", x, y, GOLD, false);
        y += 11;
        if (node.prerequisites().isEmpty()) {
            graphics.drawString(font, "• None", x, y, TEXT, false);
            y += 11;
        } else {
            for (ResourceLocation prerequisiteId : node.prerequisites()) {
                NodeDefinition prerequisite = snapshot.node(prerequisiteId);
                String label = prerequisite == null ? prerequisiteId.getPath() : prerequisite.title();
                NodeStatus prerequisiteStatus = snapshot.progress(prerequisiteId).status();
                boolean completed = snapshot.progress(prerequisiteId).accessible();
                int top = y;
                y = drawWrappedAll(graphics, (completed ? "✓ " : "• ") + label, x, y, width,
                        completed ? 0xFF9ED697 : TEXT);
                if (prerequisite != null && !isConcealed(prerequisite) && technologyBranchAllowed(prerequisite.classId())
                        && technologyEraAllowed(prerequisite.eraId())) {
                    prerequisiteHits.add(new PrerequisiteHit(prerequisiteId,
                            new Rect(x, Math.max(top, viewportTop), width,
                                    Math.max(0, Math.min(y, viewportBottom) - Math.max(top, viewportTop)))));
                }
                y += 4;
            }
        }

        graphics.drawString(font, "Requirements", x, y + 2, GOLD, false);
        y += 14;
        if (progress.requirements().isEmpty()) {
            y = drawList(graphics, node.requirements(), x, y, width, Integer.MAX_VALUE);
        } else for (RequirementProgress requirement : progress.requirements()) {
            int rowTop = y;
            y = drawWrappedAll(graphics, (requirement.complete() ? "✓ " : "• ") + requirement.display(),
                    x, y, width, requirement.complete() ? 0xFF9ED697 : TEXT);
            graphics.drawString(font, requirement.formattedProgress(), x, y, MUTED, false);
            y += 11;
            renderProgressBar(graphics, x, y, width, 4, (float) requirement.fraction(),
                    requirement.complete() ? 0xFF9ED697 : GOLD);
            y += 8;
            String help = requirementSourceHelp(requirement.action());
            if (!help.isEmpty()) objectiveHints.add(new ObjectiveHint(help,
                    new Rect(x, Math.max(rowTop, viewportTop), width,
                            Math.max(0, Math.min(y, viewportBottom) - Math.max(rowTop, viewportTop)))));
            y += 4;
        }

        graphics.drawString(font, "Unlocks", x, y + 2, GOLD, false);
        y += 14;
        for (TechnologyPresentation.Group group : TechnologyPresentation.groups(node.unlocks())) {
            boolean expanded = expandedUnlockGroups.contains(node.id() + "|" + group.name());
            int top = y;
            List<FormattedCharSequence> label = font.split(Component.literal((expanded ? "v " : "> ") + group.name()), width - 8);
            int groupHeight = label.size() * 10 + 8;
            drawBeveledPanel(graphics, x, y, width, groupHeight, FIELD, EDGE_LIGHT);
            int labelY = y + 4;
            for (var line : label) { graphics.drawString(font, line, x + 4, labelY, TEXT, false); labelY += 10; }
            y += groupHeight;
            unlockGroupHits.add(new GroupHit(node.id() + "|" + group.name(),
                    new Rect(x, Math.max(top, viewportTop), width,
                            Math.max(0, Math.min(y, viewportBottom) - Math.max(top, viewportTop)))));
            if (expanded) for (TechnologyPresentation.Unlock unlock : group.entries()) {
                ItemStack result = unlockResult(unlock);
                ItemStack displayIcon = unlockIcon(unlock, result, node);
                int nameX = x + 22;
                int nameTop = y + 4;
                graphics.renderItem(displayIcon, x + 3, nameTop);
                y = drawWrappedAll(graphics, result.isEmpty() ? unlock.name() : result.getHoverName().getString(),
                        nameX, nameTop, width - (nameX - x), TEXT);
                y = Math.max(y, nameTop + 18);
                // Recipe IDs and process IDs are never represented as item/output IDs.
                if (!unlock.id().equals("campfire_cooking_whitelist") && !unlock.id().equals("wild_seed_yield_5")
                        && !unlock.id().equals("farming_cart_operation"))
                    y = drawWrappedAll(graphics, unlock.kind() + ": " + unlock.id(), x + 3, y, width - 3, MUTED) + 4;
            }
            y += 4;
        }
        if (node.unlocks().isEmpty()) y = drawWrappedAll(graphics, "None listed", x, y, width, MUTED);
        graphics.disableScissor();

        int contentHeight = y + technologyDetailsScroll - contentStartY + 4;
        technologyDetailsMaxScroll = Math.max(0, contentHeight - viewportHeight);
        technologyDetailsScroll = Math.clamp(technologyDetailsScroll, 0, technologyDetailsMaxScroll);
        detailsScrollbarTrack = null;
        detailsScrollbarThumb = null;
        if (technologyDetailsMaxScroll > 0) {
            int trackHeight = viewportHeight - 4;
            int thumbHeight = Math.max(14, trackHeight * viewportHeight / contentHeight);
            int thumbTravel = Math.max(0, trackHeight - thumbHeight);
            int thumbY = viewportTop + 2 + thumbTravel * technologyDetailsScroll / technologyDetailsMaxScroll;
            detailsScrollbarTrack = new Rect(details.right() - 9, viewportTop + 2, 8, trackHeight);
            detailsScrollbarThumb = new Rect(details.right() - 9, thumbY, 8, thumbHeight);
            graphics.fill(details.right() - 5, viewportTop + 2, details.right() - 3, viewportBottom - 2, EDGE_DARK);
            graphics.fill(details.right() - 5, thumbY, details.right() - 3, thumbY + thumbHeight, GOLD);
            graphics.drawString(font, "Scroll ↕", details.x + 7, details.bottom() - 14, MUTED, false);
        }

        int trackWidth = Math.min(62, width);
        int trackX = details.right() - trackWidth - 7;
        int trackY = details.bottom() - 17;
        boolean tracked = node.id().equals(trackedNodeId);
        drawBeveledPanel(graphics, trackX, trackY, trackWidth, 13, tracked ? 0xFF4E6B48 : PANEL_DARK, tracked ? 0xFF88C47B : GOLD);
        graphics.drawCenteredString(font, tracked ? "Untrack" : "Track", trackX + trackWidth / 2, trackY + 3, TEXT);
    }

    private static String requirementSourceHelp(String action) {
        return switch (action) {
            case "source_mine_block" -> "Mine natural blocks yourself and collect their drops. Placed blocks and player drops do not count.";
            case "source_obtain_item" -> "Collect eligible source-tracked drops yourself. Player-dropped items do not count.";
            case "harvest_obtain_item" -> "Collect your mature-crop or wild-crop harvest. Trades do not count.";
            case "animal_obtain_item" -> "Collect eligible animal-source drops or naturally laid eggs. Player drops do not count.";
            case "travel_water_personal" -> "Swim yourself; boat passengers do not count.";
            case "travel_water_driver" -> "Swim or drive the boat/raft yourself; passengers do not count.";
            case "obtain_item" -> "Collect or produce these items yourself. Trades and ordinary container transfers do not count.";
            case "travel_personal" -> "Walk or swim. Mounts, vehicles, flight and teleports do not count.";
            case "butcher_raw_input" -> "Use the eligible cutting-board recipes yourself.";
            case "fish_caught" -> "Catch eligible fish yourself with a vanilla fishing rod.";
            case "kill_food_animal" -> "Kill animals that drop eligible raw meat or fish.";
            default -> "";
        };
    }

    private void renderClasses(GuiGraphics graphics, int mouseX, int mouseY) {
        drawBeveledPanel(graphics, contentX, contentY, contentWidth, contentHeight, PANEL, EDGE_LIGHT);
        boolean choosingInitialPair = snapshot.selectedClassIds().size() < 2;
        String heading = choosingInitialPair
                ? "Choose two classes  •  " + pendingInitialClasses.size() + "/2 selected"
                : "Active classes: " + String.join(" + ", snapshot.classes())
                + "  •  Tokens: " + snapshot.jobTokens();
        graphics.drawCenteredString(
                font,
                ellipsize(heading, contentWidth - 12),
                contentX + contentWidth / 2,
                contentY + 7,
                GOLD
        );

        List<TechClass> classes = selectableClasses();
        for (int index = 0; index < classes.size(); index++) {
            TechClass definition = classes.get(index);
            Rect card = classCardRect(index);
            boolean selected = snapshot.selectedClassIds().contains(definition.id());
            boolean pending = pendingInitialClasses.contains(definition.id());
            boolean swapFrom = definition.id().equals(swapFromClassId);
            boolean swapTo = definition.id().equals(swapToClassId);
            boolean hovered = contains(mouseX, mouseY, card.x, card.y, card.width, card.height);
            int fill = selected ? 0xFF3F5140 : pending || swapTo ? 0xFF564B31 : PANEL_DARK;
            int border = swapFrom ? 0xFFE08067
                    : swapTo ? 0xFF73A7DC
                    : selected ? 0xFF85C17E
                    : pending ? GOLD
                    : hovered ? 0xFFFFFFFF : EDGE_LIGHT;
            drawBeveledPanel(graphics, card.x, card.y, card.width, card.height, fill, border);

            ResourceLocation iconId = ResourceLocation.tryParse(definition.icon());
            graphics.renderItem(iconFor(iconId == null ? ResourceLocation.withDefaultNamespace("barrier") : iconId),
                    card.x + 6, card.y + 7);
            graphics.drawString(font, ellipsize(definition.displayName(), card.width - 31),
                    card.x + 27, card.y + 7, TEXT, false);

            String state = swapFrom ? "Replacing"
                    : swapTo ? "Replacement"
                    : selected ? "Selected"
                    : pending ? "Pending"
                    : "Available";
            graphics.drawString(font, state, card.x + 27, card.y + 20,
                    selected ? 0xFFB7E5B2 : pending || swapTo ? 0xFFF2D58C : MUTED, false);
        }

        TechClass focusedClass = classes.stream()
                .filter(definition -> definition.id().equals(focusedClassId))
                .findFirst()
                .orElse(classes.getFirst());
        Rect overview = classOverviewRect();
        drawBeveledPanel(graphics, overview.x, overview.y, overview.width, overview.height, FIELD, GOLD_DARK);
        graphics.drawString(font, focusedClass.displayName(), overview.x + 7, overview.y + 6, GOLD, false);
        graphics.enableScissor(overview.x + 3, overview.y + 3, overview.right() - 3, overview.bottom() - 3);
        drawWrappedAll(graphics, focusedClass.description(), overview.x + 7, overview.y + 18,
                overview.width - 14, TEXT);
        graphics.disableScissor();

        Rect confirm = classConfirmRect();
        boolean canConfirm = choosingInitialPair
                ? pendingInitialClasses.size() == 2
                : swapFromClassId != null && swapToClassId != null && snapshot.jobTokens() > 0;
        boolean confirmHovered = contains(mouseX, mouseY, confirm.x, confirm.y, confirm.width, confirm.height);
        drawBeveledPanel(
                graphics,
                confirm.x,
                confirm.y,
                confirm.width,
                confirm.height,
                canConfirm ? (confirmHovered ? 0xFF66562F : 0xFF4F472F) : 0xFF343638,
                canConfirm ? GOLD : 0xFF55585A
        );
        graphics.drawCenteredString(
                font,
                choosingInitialPair ? "Confirm pair" : "Swap • 1 token",
                confirm.x + confirm.width / 2,
                confirm.y + 5,
                canConfirm ? TEXT : 0xFF858585
        );

        String guidance;
        if (!classFeedback.isBlank()) {
            guidance = classFeedback;
        } else if (choosingInitialPair) {
            guidance = "Select two roles. Your initial pair is free.";
        } else if (snapshot.jobTokens() <= 0) {
            guidance = "No job token available for a class swap.";
        } else {
            guidance = "Select an active class, then its replacement.";
        }
        graphics.drawString(font, ellipsize(guidance, confirm.x - contentX - 12),
                contentX + 7, confirm.y + 5, MUTED, false);
    }

    private void renderTimeline(GuiGraphics graphics, int mouseX, int mouseY) {
        TimelineView timeline = snapshot.timeline();
        List<TechEra> playableEras = TechnologyManager.current().eras().values().stream()
                .filter(era -> era.id().equals("ancient") || era.id().equals("classical"))
                .filter(era -> technologyEraAllowed(era.id()))
                .toList();
        TechEra activeEra = playableEras.stream()
                .filter(era -> era.id().equals(timeline.eraId()))
                .findFirst()
                .orElse(playableEras.getFirst());
        int activeEraIndex = playableEras.indexOf(activeEra);
        String nextEraName = activeEraIndex + 1 < playableEras.size()
                ? playableEras.get(activeEraIndex + 1).displayName()
                : "Future expansion";
        double totalEraUptimeDays = eraUptimeDays(activeEra);
        double remainingEraUptimeDays = totalEraUptimeDays * (1.0D - timeline.eraProgress());
        drawBeveledPanel(graphics, contentX, contentY, contentWidth, contentHeight, PANEL, EDGE_LIGHT);

        int currentHeight = 59;
        drawBeveledPanel(graphics, contentX + 5, contentY + 5, contentWidth - 10, currentHeight, FIELD, EDGE_LIGHT);
        graphics.drawString(font, timeline.eraName(), contentX + 12, contentY + 12, GOLD, false);
        Rect hudToggle = timelineHudToggleRect();
        boolean hudToggleHovered = contains(mouseX, mouseY, hudToggle.x, hudToggle.y,
                hudToggle.width, hudToggle.height);
        drawBeveledPanel(graphics, hudToggle.x, hudToggle.y, hudToggle.width, hudToggle.height,
                hudToggleHovered ? 0xFF574A30 : PANEL_DARK, GOLD);
        String hudToggleLabel = HistoriaErasClientPreferences.calendarBarVisible()
                ? "Hide calendar"
                : "Show calendar";
        graphics.drawCenteredString(font, hudToggleLabel, hudToggle.x + hudToggle.width / 2,
                hudToggle.y + 5, TEXT);
        if (snapshot.operator()) {
            Rect adminToggle = timelineAdminModeToggleRect();
            boolean adminHovered = contains(mouseX, mouseY, adminToggle.x, adminToggle.y,
                    adminToggle.width, adminToggle.height);
            int adminBorder = snapshot.timelineAdmin() ? 0xFF9ED697 : 0xFFC36B5C;
            drawBeveledPanel(graphics, adminToggle.x, adminToggle.y, adminToggle.width, adminToggle.height,
                    adminHovered ? 0xFF574A30 : PANEL_DARK, adminBorder);
            graphics.drawCenteredString(font, snapshot.timelineAdmin() ? "Admin: ON" : "Admin: OFF",
                    adminToggle.x + adminToggle.width / 2, adminToggle.y + 5, TEXT);
        }
        String date = timeline.calendarDate() + ", " + timeline.historicalYearDisplay()
                + " • " + timeline.seasonName()
                + (timeline.sereneSeasonsSynced() ? " • Seasons synced" : "");
        graphics.drawString(font, ellipsize(date, contentWidth - 24), contentX + 12, contentY + 25, TEXT, false);
        String state = timeline.eraCompleted() ? "Era complete • awaiting administrator"
                : timeline.paused() ? "Calendar paused by an administrator"
                : "Next: " + nextEraName + " • " + formatUptime(remainingEraUptimeDays) + " remaining";
        graphics.drawString(font, ellipsize(state, contentWidth - 24), contentX + 12, contentY + 38,
                timeline.paused() ? 0xFFE2B76B : 0xFF9ED697, false);
        int percentage = (int) Math.round(timeline.eraProgress() * 100.0D);
        int barX = contentX + 12;
        int barY = contentY + 52;
        int barWidth = contentWidth - 58;
        renderProgressBar(graphics, barX, barY, barWidth, 6, (float) timeline.eraProgress(), GOLD);
        graphics.drawString(font, percentage + "%", barX + barWidth + 5, barY - 1, MUTED, false);

        int eraY = contentY + 69;
        int eraGap = 5;
        int eraWidth = (contentWidth - 15) / 2;
        for (int index = 0; index < playableEras.size(); index++) {
            TechEra era = playableEras.get(index);
            int x = contentX + 5 + index * (eraWidth + eraGap);
            boolean active = era.id().equals(timeline.eraId());
            drawBeveledPanel(graphics, x, eraY, eraWidth, 48,
                    active ? 0xFF51472E : PANEL_DARK, active ? GOLD : EDGE_LIGHT);
            graphics.drawCenteredString(font, era.displayName(), x + eraWidth / 2, eraY + 7,
                    active ? GOLD : TEXT);
            graphics.drawCenteredString(font,
                    formatHistoricalYear(era.startDate().year()) + " → " + formatHistoricalYear(era.endDate().year()),
                    x + eraWidth / 2, eraY + 20, MUTED);
            graphics.drawCenteredString(font, String.format(Locale.ROOT, "~%.1f uptime days", eraUptimeDays(era)),
                    x + eraWidth / 2, eraY + 33, MUTED);
        }

        int rulesY = eraY + 53;
        int adminHeight = snapshot.timelineAdmin() ? 29 : 0;
        int rulesHeight = contentY + contentHeight - rulesY - adminHeight - 5;
        drawBeveledPanel(graphics, contentX + 5, rulesY, contentWidth - 10, rulesHeight, PANEL_DARK, EDGE_LIGHT);
        double minutes = timeline.ticksPerCalendarDay() / 1_200.0D;
        double centuryUptimeDays = 365.0D * minutes / (24.0D * 60.0D);
        double rolloverUptimeDays = (365 - timeline.dayOfYear()) * minutes / (24.0D * 60.0D);
        int rolloverYear = timeline.historicalYear() + 100;
        graphics.drawString(font, "1 calendar day: " + formatNumber(minutes) + " real minutes",
                contentX + 12, rulesY + 7, TEXT, false);
        graphics.drawString(font, "100 historical years: " + formatUptime(centuryUptimeDays),
                contentX + 12, rulesY + 19, TEXT, false);
        if (rulesHeight >= 39) {
            graphics.drawString(font, "Next rollover: " + formatHistoricalYear(rolloverYear)
                            + " after Dec 31 • " + formatUptime(rolloverUptimeDays),
                    contentX + 12, rulesY + 31, MUTED, false);
        }
        if (rulesHeight >= 51) {
            String seasonStatus = timeline.sereneSeasonsSynced()
                    ? " • Serene Seasons synchronized"
                    : " • Serene Seasons not installed";
            String calendarStatus = "Calendar position: day " + (timeline.dayOfYear() + 1) + "/365"
                    + seasonStatus;
            graphics.drawString(font, ellipsize(calendarStatus, contentWidth - 24),
                    contentX + 12, rulesY + 43, timeline.sereneSeasonsSynced() ? 0xFF9ED697 : MUTED, false);
        }

        if (snapshot.timelineAdmin()) {
            renderTimelineAdminControls(graphics, mouseX, mouseY);
        }
    }

    private List<TechClass> selectableClasses() {
        return TechnologyManager.current().classes().values().stream().filter(TechClass::enabled).toList();
    }

    private Rect classCardRect(int index) {
        int gap = 4;
        int columns = 3;
        int width = (contentWidth - 10 - gap * (columns - 1)) / columns;
        int gridTop = contentY + 24;
        int height = 34;
        int column = index % columns;
        int row = index / columns;
        return new Rect(contentX + 5 + column * (width + gap), gridTop + row * (height + gap), width, height);
    }

    private Rect classOverviewRect() {
        Rect finalCard = classCardRect(5);
        int y = finalCard.bottom() + 4;
        int bottom = contentY + contentHeight - 27;
        return new Rect(contentX + 5, y, contentWidth - 10, Math.max(17, bottom - y));
    }

    private Rect classConfirmRect() {
        return new Rect(contentX + contentWidth - 112, contentY + contentHeight - 23, 105, 17);
    }

    private void renderTimelineAdminControls(GuiGraphics graphics, int mouseX, int mouseY) {
        List<Rect> buttons = timelineAdminButtons();
        TimelineView timeline = snapshot.timeline();
        String[] labels = {
                timeline.paused() && !timeline.eraCompleted() ? "Resume" : "Pause",
                armedTimelineAction.equals("advance") ? "Confirm advance" : "Advance era",
                armedTimelineAction.equals("reset") ? "Confirm reset" : "Reset Ancient"
        };
        for (int index = 0; index < buttons.size(); index++) {
            Rect button = buttons.get(index);
            boolean enabled = timelineAdminButtonEnabled(index);
            boolean hovered = contains(mouseX, mouseY, button.x, button.y, button.width, button.height);
            int fill = enabled ? (hovered ? 0xFF574A30 : PANEL_DARK) : 0xFF343638;
            int border = enabled ? (index == 2 ? 0xFFC36B5C : GOLD) : 0xFF55585A;
            drawBeveledPanel(graphics, button.x, button.y, button.width, button.height, fill, border);
            graphics.drawCenteredString(font, ellipsize(labels[index], button.width - 6),
                    button.x + button.width / 2, button.y + 5, enabled ? TEXT : 0xFF858585);
        }
    }

    private List<Rect> timelineAdminButtons() {
        int gap = 4;
        int width = (contentWidth - 10 - gap * 2) / 3;
        int y = contentY + contentHeight - 23;
        return List.of(
                new Rect(contentX + 5, y, width, 17),
                new Rect(contentX + 5 + width + gap, y, width, 17),
                new Rect(contentX + 5 + (width + gap) * 2, y, width, 17)
        );
    }

    private Rect timelineHudToggleRect() {
        return new Rect(contentX + contentWidth - 98, contentY + 9, 86, 17);
    }

    private Rect timelineAdminModeToggleRect() {
        return new Rect(contentX + contentWidth - 188, contentY + 9, 86, 17);
    }

    private boolean timelineAdminButtonEnabled(int index) {
        TimelineView timeline = snapshot.timeline();
        return switch (index) {
            case 0 -> !timeline.eraCompleted();
            case 1 -> timeline.eraCompleted() && timeline.eraId().equals("ancient");
            case 2 -> true;
            default -> false;
        };
    }

    private static String formatHistoricalYear(int year) {
        if (year < 0) {
            return Math.abs(year) + " BCE";
        }
        if (year > 0) {
            return year + " CE";
        }
        return "Year 0";
    }

    private static String formatNumber(double value) {
        long rounded = Math.round(value);
        return Math.abs(value - rounded) < 0.000_001D
                ? Long.toString(rounded)
                : String.format(Locale.ROOT, "%.1f", value);
    }

    private static double eraUptimeDays(TechEra era) {
        return Math.abs(era.endDate().year() - era.startDate().year())
                / 100.0D * 365.0D / era.calendarDaysPerRealDay();
    }

    private static String formatUptime(double days) {
        if (days >= 2.0D) {
            return String.format(Locale.ROOT, "%.1f uptime days", days);
        }
        double hours = days * 24.0D;
        if (hours >= 2.0D) {
            return String.format(Locale.ROOT, "%.1f uptime hours", hours);
        }
        return String.format(Locale.ROOT, "%.0f uptime minutes", hours * 60.0D);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && activePage == Page.TECHNOLOGY
                && (tutorialStep < 0 || tutorialStep == 4
                    && !contains(mouseX, mouseY, tutorialCard().x, tutorialCard().y, tutorialCard().width, tutorialCard().height))
                && beginDetailsScrollbarDrag(mouseX, mouseY)) return true;
        if (tutorialStep >= 0) {
            Rect card = tutorialCard();
            Rect tree = technologyTreePanel();
            if (contains(mouseX, mouseY, card.x, card.y, card.width, card.height)
                    || tutorialStep != 3 || !contains(mouseX, mouseY, tree.x, tree.y, tree.width, tree.height))
                return tutorialClick(mouseX, mouseY, button);
            // Only map navigation/selection is allowed through the tour, never admin or Track actions.
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            Rect help = tutorialHelpRect();
            if (contains(mouseX, mouseY, help.x, help.y, help.width, help.height)) {
                startTutorial();
                return true;
            }
            Page clickedPage = navigationAt(mouseX, mouseY);
            if (clickedPage != null) {
                if (clickedPage.enabled) {
                    selectPage(clickedPage);
                }
                return true;
            }

            if (activePage == Page.TECHNOLOGY) {
                Rect search = technologySearchRect();
                technologySearch.visible = tutorialStep < 0;
                technologySearch.active = tutorialStep < 0;
                if (tutorialStep < 0 && contains(mouseX, mouseY, search.x, search.y, search.width, search.height)) {
                    setFocused(technologySearch);
                    technologySearch.mouseClicked(mouseX, mouseY, button);
                    return true;
                }
                technologySearch.setFocused(false);
                for (int i = 0; i < 3; i++) {
                    Rect nav = treeNavigationRect(i);
                    if (contains(mouseX, mouseY, nav.x, nav.y, nav.width, nav.height)) {
                        if (i == 0) fitTreeToViewport(technologyViewport());
                        else if (i == 1) centerSelectedNode();
                        else jumpToNode(snapshot.node(trackedNodeId));
                        return true;
                    }
                }
                for (GroupHit hit : unlockGroupHits) {
                    Rect bounds = hit.bounds();
                    if (contains(mouseX, mouseY, bounds.x, bounds.y, bounds.width, bounds.height)) {
                        if (!expandedUnlockGroups.remove(hit.key())) expandedUnlockGroups.add(hit.key());
                        return true;
                    }
                }
                for (PrerequisiteHit hit : prerequisiteHits) {
                    Rect bounds = hit.bounds();
                    if (contains(mouseX, mouseY, bounds.x, bounds.y, bounds.width, bounds.height)) {
                        jumpToNode(snapshot.node(hit.id()));
                        return true;
                    }
                }
                Rect panel = technologyTreePanel();
                int filterX = panel.x + 4;
                int filterY = panel.y + 3;
                int available = Math.max(60, panel.width - 8);
                int eraWidth = Math.max(52, Math.min(76, available / 2));
                int branchWidth = Math.max(52, available - eraWidth - 3);
                if (contains(mouseX, mouseY, filterX, filterY, eraWidth, 15)) {
                    cycleEra();
                    return true;
                }
                if (contains(mouseX, mouseY, filterX + eraWidth + 3, filterY, branchWidth, 15)) {
                    cycleBranch();
                    return true;
                }

                Rect viewport = technologyViewport();

                Rect details = technologyDetails();
                NodeDefinition selected = snapshot.node(selectedNodeId);
                int trackWidth = Math.min(62, details.width - 14);
                if (selected != null && !isConcealed(selected) && contains(
                        mouseX,
                        mouseY,
                        details.right() - trackWidth - 7,
                        details.bottom() - 17,
                        trackWidth,
                        13
                )) {
                    if (selected.id().equals(trackedNodeId)) {
                        trackedNodeId = null;
                        HistoriaErasNetwork.trackNode("");
                    } else {
                        trackedNodeId = selected.id();
                        HistoriaErasNetwork.trackNode(selected.id().getPath());
                    }
                    return true;
                }

                NodeDefinition clickedNode = nodeAt(mouseX, mouseY, viewport);
                if (clickedNode != null) {
                    selectedNodeId = clickedNode.id();
                    technologyDetailsScroll = 0;
                    technologyDetailsMaxScroll = 0;
                    return true;
                }

                if (contains(mouseX, mouseY, viewport.x, viewport.y, viewport.width, viewport.height)) {
                    draggingTree = true;
                    return true;
                }
            }

            if (activePage == Page.OVERVIEW && snapshot.timelineAdmin()
                    && !snapshot.onlinePlayerNames().isEmpty()) {
                Rect target = overviewAdminTargetRect();
                if (contains(mouseX, mouseY, target.x, target.y, target.width, target.height)) {
                    cycleAdminResetTarget();
                    return true;
                }
                Rect node = overviewAdminNodeRect();
                if (contains(mouseX, mouseY, node.x, node.y, node.width, node.height)) {
                    cycleAdminGrantNode();
                    return true;
                }
                Rect grant = overviewAdminGrantRect();
                if (contains(mouseX, mouseY, grant.x, grant.y, grant.width, grant.height)) {
                    if (adminGrantNodeId != null) {
                        sendDashboardCommand("historiaeras admin node grant " + adminResetTarget + " "
                                + adminGrantNodeId.getPath());
                    }
                    return true;
                }
                Rect reset = overviewAdminResetRect();
                if (contains(mouseX, mouseY, reset.x, reset.y, reset.width, reset.height)) {
                    if (!armedProgressReset) {
                        armedProgressReset = true;
                    } else {
                        armedProgressReset = false;
                        sendDashboardCommand("historiaeras admin reset progress " + adminResetTarget);
                    }
                    return true;
                }
            }

            if (activePage == Page.CLASSES) {
                List<TechClass> classes = selectableClasses();
                for (int index = 0; index < classes.size(); index++) {
                    Rect card = classCardRect(index);
                    if (contains(mouseX, mouseY, card.x, card.y, card.width, card.height)) {
                        handleClassSelection(classes.get(index).id());
                        return true;
                    }
                }

                Rect confirm = classConfirmRect();
                if (contains(mouseX, mouseY, confirm.x, confirm.y, confirm.width, confirm.height)) {
                    submitClassChange();
                    return true;
                }
            }

            if (activePage == Page.TIMELINE) {
                Rect hudToggle = timelineHudToggleRect();
                if (contains(mouseX, mouseY, hudToggle.x, hudToggle.y, hudToggle.width, hudToggle.height)) {
                    HistoriaErasClientPreferences.toggleCalendarBar();
                    return true;
                }
                if (snapshot.operator()) {
                    Rect adminToggle = timelineAdminModeToggleRect();
                    if (contains(mouseX, mouseY, adminToggle.x, adminToggle.y,
                            adminToggle.width, adminToggle.height)) {
                        sendDashboardCommand("historiaeras admin mode "
                                + (snapshot.timelineAdmin() ? "off" : "on"));
                        return true;
                    }
                }
            }

            if (activePage == Page.TIMELINE && snapshot.timelineAdmin()) {
                List<Rect> buttons = timelineAdminButtons();
                for (int index = 0; index < buttons.size(); index++) {
                    Rect control = buttons.get(index);
                    if (contains(mouseX, mouseY, control.x, control.y, control.width, control.height)) {
                        handleTimelineAdminAction(index);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void handleClassSelection(String classId) {
        focusedClassId = classId;
        classFeedback = "";
        if (snapshot.selectedClassIds().size() < 2) {
            if (pendingInitialClasses.remove(classId)) {
                return;
            }
            if (pendingInitialClasses.size() >= 2) {
                classFeedback = "Choose at most two classes.";
                return;
            }
            pendingInitialClasses.add(classId);
            return;
        }

        if (snapshot.selectedClassIds().contains(classId)) {
            swapFromClassId = classId;
            if (classId.equals(swapToClassId)) {
                swapToClassId = null;
            }
        } else {
            swapToClassId = classId;
        }
    }

    private void cycleAdminResetTarget() {
        List<String> players = snapshot.onlinePlayerNames();
        if (players.isEmpty()) {
            return;
        }
        int index = Math.max(0, players.indexOf(adminResetTarget));
        adminResetTarget = players.get((index + 1) % players.size());
        armedProgressReset = false;
    }

    private void cycleAdminGrantNode() {
        if (snapshot.nodes().isEmpty()) {
            adminGrantNodeId = null;
            return;
        }
        int index = 0;
        for (int candidate = 0; candidate < snapshot.nodes().size(); candidate++) {
            if (snapshot.nodes().get(candidate).id().equals(adminGrantNodeId)) {
                index = candidate + 1;
                break;
            }
        }
        adminGrantNodeId = snapshot.nodes().get(index % snapshot.nodes().size()).id();
        armedProgressReset = false;
    }

    private void submitClassChange() {
        if (snapshot.selectedClassIds().size() < 2) {
            if (pendingInitialClasses.size() != 2) {
                classFeedback = "Select exactly two classes first.";
                return;
            }
            List<String> classes = List.copyOf(pendingInitialClasses);
            if (sendDashboardCommand("historiaeras class choose " + classes.get(0) + " " + classes.get(1))) {
                classFeedback = "Submitting class pair…";
            }
            return;
        }

        if (swapFromClassId == null || swapToClassId == null) {
            classFeedback = "Select an active class and a replacement.";
            return;
        }
        if (snapshot.jobTokens() <= 0) {
            classFeedback = "A job token is required for this swap.";
            return;
        }
        if (sendDashboardCommand("historiaeras class swap " + swapFromClassId + " " + swapToClassId)) {
            classFeedback = "Submitting class swap…";
        }
    }

    private void handleTimelineAdminAction(int index) {
        if (!timelineAdminButtonEnabled(index)) {
            return;
        }
        TimelineView timeline = snapshot.timeline();
        if (index == 0) {
            armedTimelineAction = "";
            sendDashboardCommand("historiaeras admin time " + (timeline.paused() ? "resume" : "pause"));
            return;
        }

        String action = index == 1 ? "advance" : "reset";
        if (!armedTimelineAction.equals(action)) {
            armedTimelineAction = action;
            return;
        }
        armedTimelineAction = "";
        sendDashboardCommand("historiaeras admin time " + action);
    }

    private boolean sendDashboardCommand(String command) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) {
            classFeedback = "Not connected to a server.";
            return false;
        }
        minecraft.getConnection().sendCommand(command);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingDetailsScrollbar && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragDetailsScrollbar(mouseY);
            return true;
        }
        if (tutorialStep >= 0 && tutorialStep != 3) return true;
        if (draggingTree && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            treePanX += dragX;
            treePanY += dragY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && draggingDetailsScrollbar) {
            draggingDetailsScrollbar = false;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && draggingTree) {
            draggingTree = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean beginDetailsScrollbarDrag(double mouseX, double mouseY) {
        if (detailsScrollbarTrack == null || detailsScrollbarThumb == null || technologyDetailsMaxScroll <= 0
                || !contains(mouseX, mouseY, detailsScrollbarTrack.x, detailsScrollbarTrack.y,
                    detailsScrollbarTrack.width, detailsScrollbarTrack.height)) return false;
        draggingDetailsScrollbar = true;
        draggingTree = false;
        if (technologySearch != null) technologySearch.setFocused(false);
        detailsScrollbarGrabOffset = contains(mouseX, mouseY, detailsScrollbarThumb.x, detailsScrollbarThumb.y,
                detailsScrollbarThumb.width, detailsScrollbarThumb.height)
                ? mouseY - detailsScrollbarThumb.y : detailsScrollbarThumb.height / 2.0D;
        dragDetailsScrollbar(mouseY);
        return true;
    }

    private void dragDetailsScrollbar(double mouseY) {
        if (detailsScrollbarTrack == null || detailsScrollbarThumb == null) return;
        double travel = detailsScrollbarTrack.height - detailsScrollbarThumb.height;
        if (travel <= 0) return;
        double ratio = (mouseY - detailsScrollbarTrack.y - detailsScrollbarGrabOffset) / travel;
        technologyDetailsScroll = Math.clamp((int) Math.round(ratio * technologyDetailsMaxScroll), 0, technologyDetailsMaxScroll);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tutorialStep >= 0) {
            Rect card = tutorialCard();
            if (contains(mouseX, mouseY, card.x, card.y, card.width, card.height)) {
                tutorialTextScroll = Math.clamp(tutorialTextScroll - (int) (scrollY * 20), 0, tutorialTextMaxScroll);
                return true;
            }
            Rect target = tutorialStep == 3 ? technologyViewport() : technologyDetails();
            if ((tutorialStep != 3 && tutorialStep != 4)
                    || !contains(mouseX, mouseY, target.x, target.y, target.width, target.height)) return true;
        }
        if (activePage == Page.TECHNOLOGY) {
            Rect details = technologyDetails();
            if (contains(mouseX, mouseY, details.x, details.y, details.width, details.height)
                    && scrollY != 0.0D && technologyDetailsMaxScroll > 0) {
                technologyDetailsScroll = Math.clamp(
                        technologyDetailsScroll - (int) Math.round(scrollY * 24.0D),
                        0,
                        technologyDetailsMaxScroll
                );
                return true;
            }
            Rect viewport = technologyViewport();
            if (contains(mouseX, mouseY, viewport.x, viewport.y, viewport.width, viewport.height) && scrollY != 0.0D) {
                double worldX = (mouseX - (viewport.x + 18.0D + treePanX)) / treeZoom;
                double worldY = (mouseY - (viewport.y + viewport.height / 2.0D + treePanY)) / treeZoom + 66.0D;
                double nextZoom = Math.clamp(treeZoom * Math.pow(1.18D, scrollY), 0.08D, 1.45D);
                treePanX = mouseX - (viewport.x + 18.0D) - worldX * nextZoom;
                treePanY = mouseY - (viewport.y + viewport.height / 2.0D) - (worldY - 66.0D) * nextZoom;
                treeZoom = nextZoom;
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (tutorialStep >= 0) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) dismissTutorial();
            else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_RIGHT) advanceTutorial(1);
            else if (keyCode == GLFW.GLFW_KEY_LEFT) advanceTutorial(-1);
            return true;
        }
        if (activePage == Page.TECHNOLOGY && technologySearch.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { technologySearch.setFocused(false); return true; }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                selectNextSearchMatch();
                return true;
            }
            return technologySearch.keyPressed(keyCode, scanCode, modifiers);
        }
        if (HistoriaErasClient.OPEN_DASHBOARD.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        if (activePage == Page.TECHNOLOGY) {
            if (keyCode == GLFW.GLFW_KEY_F) { fitTreeToViewport(technologyViewport()); return true; }
            if (keyCode == GLFW.GLFW_KEY_C) { centerSelectedNode(); return true; }
            if (keyCode == GLFW.GLFW_KEY_T) { jumpToNode(snapshot.node(trackedNodeId)); return true; }
            if (keyCode == GLFW.GLFW_KEY_SLASH) { setFocused(technologySearch); return true; }
        }
        if (keyCode == GLFW.GLFW_KEY_1) {
            selectPage(Page.OVERVIEW);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_2) {
            selectPage(Page.TECHNOLOGY);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_3 && visiblePages().length >= 3) {
            selectPage(Page.CLASSES);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_4 && visiblePages().length >= 4) {
            selectPage(Page.TIMELINE);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (tutorialStep < 0 && activePage == Page.TECHNOLOGY && technologySearch.isFocused())
            return technologySearch.charTyped(codePoint, modifiers);
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private Page navigationAt(double mouseX, double mouseY) {
        int x = panelX + 5;
        int width = navigationWidth - 10;
        int buttonY = panelY + 42;
        for (Page page : visiblePages()) {
            if (contains(mouseX, mouseY, x, buttonY, width, 27)) {
                return page;
            }
            buttonY += 31;
        }
        return null;
    }

    private Rect technologyViewport() {
        int detailsWidth = technologyDetailsWidth();
        return new Rect(contentX, contentY + 61, contentWidth - detailsWidth - 4, Math.max(1, contentHeight - 77));
    }

    private Rect treeNavigationRect(int index) {
        Rect tree = technologyTreePanel();
        int w = (tree.width - 14) / 3;
        return new Rect(tree.x + 4 + index * (w + 3), tree.y + 21, w, 16);
    }

    private Rect technologySearchRect() {
        Rect tree = technologyTreePanel();
        return new Rect(tree.x + 5, tree.y + 41, Math.max(20, tree.width - 10), 16);
    }

    private Rect overviewAdminTargetRect() {
        int progressHeight = contentHeight >= 120 ? 35 : 27;
        int upperHeight = contentHeight - progressHeight - 4;
        int characterWidth = Math.max(76, Math.min(150, (int) (contentWidth * 0.43F)));
        int x = contentX + characterWidth + 11;
        int width = contentWidth - characterWidth - 18;
        return new Rect(x, contentY + upperHeight - 64, width, 17);
    }

    private Rect overviewAdminNodeRect() {
        Rect target = overviewAdminTargetRect();
        return new Rect(target.x, target.y + 21, target.width, 17);
    }

    private Rect overviewAdminGrantRect() {
        Rect node = overviewAdminNodeRect();
        int gap = 4;
        int width = (node.width - gap) / 2;
        return new Rect(node.x, node.y + 21, width, 17);
    }

    private Rect overviewAdminResetRect() {
        Rect grant = overviewAdminGrantRect();
        return new Rect(grant.right() + 4, grant.y, grant.width, grant.height);
    }

    private Rect technologyTreePanel() {
        int detailsWidth = technologyDetailsWidth();
        return new Rect(contentX, contentY, contentWidth - detailsWidth - 4, contentHeight);
    }

    private Rect technologyDetails() {
        int detailsWidth = technologyDetailsWidth();
        return new Rect(contentX + contentWidth - detailsWidth, contentY, detailsWidth, contentHeight);
    }

    private int technologyDetailsWidth() {
        return Math.clamp((int) (contentWidth * 0.38), 94, 210);
    }

    private Page[] visiblePages() {
        if (panelHeight < 205) {
            return new Page[]{Page.OVERVIEW, Page.TECHNOLOGY};
        }
        return Page.values();
    }

    private void fitTreeToViewport(Rect viewport) {
        List<NodeDefinition> visible = visibleNodes();
        if (visible.isEmpty() || viewport.width <= 0 || viewport.height <= 0) {
            treePanX = 0.0D;
            treePanY = 0.0D;
            treeZoom = 0.78D;
            return;
        }

        int minimumX = visible.stream().mapToInt(n -> graphPosition(n).x()).min().orElse(0);
        int maximumX = visible.stream().mapToInt(n -> graphPosition(n).x()).max().orElse(0);
        int minimumY = visible.stream().mapToInt(n -> graphPosition(n).y()).min().orElse(0);
        int maximumY = visible.stream().mapToInt(n -> graphPosition(n).y()).max().orElse(0);
        int spanX = Math.max(1, maximumX - minimumX);
        int spanY = Math.max(1, maximumY - minimumY);
        double horizontalZoom = Math.max(1.0D, viewport.width - 100.0D) / spanX;
        double verticalZoom = Math.max(1.0D, viewport.height - 64.0D) / spanY;

        treeZoom = Math.clamp(Math.min(horizontalZoom, verticalZoom), 0.08D, 1.0D);
        double centerX = (minimumX + maximumX) / 2.0D;
        double centerY = (minimumY + maximumY) / 2.0D;
        treePanX = viewport.width / 2.0D - 18.0D - centerX * treeZoom;
        treePanY = -(centerY - 66.0D) * treeZoom;
    }

    private void centerSelectedNode() {
        NodeDefinition node = snapshot.node(selectedNodeId);
        if (node == null || visibleNodes().stream().noneMatch(n -> n.id().equals(node.id()))) return;
        Rect viewport = technologyViewport();
        treeZoom = Math.max(0.78, treeZoom);
        treePanX = viewport.width / 2.0 - 18 - graphPosition(node).x() * treeZoom;
        treePanY = -(graphPosition(node).y() - 66) * treeZoom;
    }

    private void jumpToNode(NodeDefinition node) {
        if (node == null || isConcealed(node) || !technologyBranchAllowed(node.classId())
                || !technologyEraAllowed(node.eraId())) return;
        selectedEraId = node.eraId();
        selectedBranchId = node.classId();
        selectedNodeId = node.id();
        technologyDetailsScroll = 0;
        technologyDetailsMaxScroll = 0;
        unlockGroupHits.clear();
        prerequisiteHits.clear();
        centerSelectedNode();
    }

    private void selectNextSearchMatch() {
        List<NodeDefinition> matches = searchMatches();
        if (matches.isEmpty() || searchQuery.isBlank()) return;
        int previous = -1;
        for (int i = 0; i < matches.size(); i++) if (matches.get(i).id().equals(selectedNodeId)) previous = i;
        jumpToNode(matches.get((previous + 1) % matches.size()));
    }

    private List<NodeDefinition> searchMatches() {
        return visibleNodes().stream().filter(n -> !isConcealed(n))
                .filter(this::matchesSearch).toList();
    }

    private boolean matchesSearch(NodeDefinition node) {
        if (TechnologyPresentation.matches(node.title(), node.unlocks(), searchQuery)) return true;
        String needle = searchQuery.strip().toLowerCase(Locale.ROOT);
        return TechnologyPresentation.groups(node.unlocks()).stream().flatMap(g -> g.entries().stream()).anyMatch(unlock -> {
            ItemStack result = unlockResult(unlock);
            return !result.isEmpty() && (result.getHoverName().getString().toLowerCase(Locale.ROOT).contains(needle)
                    || BuiltInRegistries.ITEM.getKey(result.getItem()).toString().contains(needle));
        });
    }

    private ItemStack unlockResult(TechnologyPresentation.Unlock unlock) {
        Minecraft client = Minecraft.getInstance();
        ResourceLocation id = ResourceLocation.tryParse(unlock.id());
        if (client.level == null || id == null || (!unlock.kind().equals("Recipe") && !unlock.kind().equals("Process")))
            return ItemStack.EMPTY;
        return client.level.getRecipeManager().byKey(id)
                .map(holder -> holder.value().getResultItem(client.level.registryAccess())).orElse(ItemStack.EMPTY);
    }

    private ItemStack unlockIcon(TechnologyPresentation.Unlock unlock, ItemStack result, NodeDefinition node) {
        if (!result.isEmpty()) return result;
        // An absent/special recipe may have no static result (e.g. dyeing); never infer a result name.
        // Exact registered item IDs can still supply their own illustration, independently of recipe availability.
        ResourceLocation id = ResourceLocation.tryParse(unlock.id());
        if (id != null && (unlock.kind().equals("Recipe") || unlock.kind().equals("Process"))) {
            Item exact = BuiltInRegistries.ITEM.get(id);
            if (exact != null && exact != Items.AIR) return exact.getDefaultInstance();
        }
        String representative = switch (unlock.id()) {
            case "crafting_table_use", "crafting_table_rough_stone" -> "minecraft:crafting_table";
            case "furnace_use", "furnace_cornbread", "furnace_fish_cooking" -> "minecraft:furnace";
            case "smoker_use" -> "minecraft:smoker";
            case "stonecutter_use" -> "minecraft:stonecutter";
            case "loom_use" -> "minecraft:loom";
            case "grindstone_use" -> "minecraft:grindstone";
            case "smithing_table_use" -> "minecraft:smithing_table";
            case "composter_use", "organic_compost", "rich_soil", "rich_soil_farmland" -> "minecraft:composter";
            case "food_cutting_board_use" -> "farmersdelight:cutting_board";
            case "cooking_pot_use", "limited_cooking_pot_use", "basic_stews" -> "farmersdelight:cooking_pot";
            case "keg_use" -> "brewinandchewin:keg";
            case "rain_collector_use" -> "toughasnails:rain_collector";
            case "dye_cauldron_use" -> "minecraft:cauldron";
            case "campfire_cooking_whitelist" -> "minecraft:campfire";
            case "animal_feeding", "animal_breeding", "cultivated_crop_yield_5", "wild_seed_yield_5" -> "minecraft:wheat";
            case "basic_rod_use", "basic_tackle_use" -> "minecraft:fishing_rod";
            case "wooden_storage_use" -> "minecraft:chest";
            case "wooden_boat_operation" -> "minecraft:oak_boat";
            case "shear_use" -> "minecraft:shears";
            case "charcoal_recipe" -> "minecraft:charcoal";
            case "copper_smelting" -> "minecraft:copper_ingot";
            case "iron_smelting" -> "minecraft:iron_ingot";
            case "glass_smelting" -> "minecraft:glass";
            case "brush_archaeology" -> "minecraft:brush";
            case "honey_harvesting" -> "minecraft:honey_bottle";
            case "honeycomb_harvesting", "bee_breeding" -> "minecraft:honeycomb";
            default -> "";
        };
        if (!representative.isEmpty()) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(representative));
            if (item != null && item != Items.AIR) return item.getDefaultInstance();
        }
        Item fallback = BuiltInRegistries.ITEM.get(node.icon());
        return (fallback != null && fallback != Items.AIR ? fallback : Items.BOOK).getDefaultInstance();
    }

    public void runInteractionSmokeTestForVisualTest() {
        if (activePage != Page.TECHNOLOGY) {
            throw new IllegalStateException("Technology page is not active");
        }

        Rect viewport = technologyViewport();
        NodeDefinition selectionTarget = visibleNodes().stream()
                .filter(node -> !isConcealed(node))
                .findFirst()
                .orElseThrow();
        // The isolated smoke-test world is intentionally reusable, so a prior
        // run may have left this node tracked. Clear that state before testing
        // the Track button; the normal UI still toggles as usual.
        if (selectionTarget.id().equals(trackedNodeId)) {
            trackedNodeId = null;
            HistoriaErasNetwork.trackNode("");
        }
        selectedEraId = selectionTarget.eraId();
        selectedBranchId = selectionTarget.classId();
        fitTreeToViewport(viewport);
        Point target = treePoint(viewport, selectionTarget);
        if (!mouseClicked(target.x, target.y, GLFW.GLFW_MOUSE_BUTTON_LEFT)
                || !selectionTarget.id().equals(selectedNodeId)) {
            throw new IllegalStateException("Technology node selection did not work");
        }

        Rect details = technologyDetails();
        int trackWidth = Math.min(62, details.width - 14);
        if (!mouseClicked(
                details.right() - trackWidth - 5.0D,
                details.bottom() - 11.0D,
                GLFW.GLFW_MOUSE_BUTTON_LEFT
        ) || !selectionTarget.id().equals(trackedNodeId)) {
            throw new IllegalStateException("Technology node tracking did not work");
        }

        double initialPanX = treePanX;
        double dragX = viewport.x + viewport.width / 2.0D;
        double dragY = viewport.bottom() - 8.0D;
        if (nodeAt(dragX, dragY, viewport) != null) {
            dragX = viewport.right() - 8.0D;
            dragY = viewport.y + 28.0D;
        }
        mouseClicked(dragX, dragY, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        mouseDragged(dragX + 8.0D, dragY + 4.0D, GLFW.GLFW_MOUSE_BUTTON_LEFT, 8.0D, 4.0D);
        mouseReleased(dragX + 8.0D, dragY + 4.0D, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        if (treePanX == initialPanX) {
            throw new IllegalStateException("Technology tree dragging did not work");
        }

        double initialZoom = treeZoom;
        mouseScrolled(viewport.x + viewport.width / 2.0D, viewport.y + viewport.height / 2.0D, 0.0D, 1.0D);
        if (treeZoom <= initialZoom) {
            throw new IllegalStateException("Technology tree zooming did not work");
        }

        int recenterX = treeNavigationRect(0).x;
        int recenterY = treeNavigationRect(0).y;
        if (!mouseClicked(recenterX + 2.0D, recenterY + 2.0D, GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
            throw new IllegalStateException("Technology tree recenter did not work");
        }
    }

    public void runTechnologyRedesignSmokeTestForVisualTest() {
        if (!Boolean.getBoolean("townyeras.prototype.visualTest")) throw new IllegalStateException("Test-only method");
        expandedUnlockGroups.clear();
        int originalWidth = width, originalHeight = height;
        for (int testWidth : new int[]{280, 320, 400, originalWidth}) {
            init(Minecraft.getInstance(), testWidth, Math.min(240, originalHeight));
            Rect tree = technologyTreePanel(), details = technologyDetails(), search = technologySearchRect();
            if (tree.right() > details.x || search.right() > tree.right() || search.y != technologySearch.getY()
                    || treeNavigationRect(2).right() > tree.right() || technologyViewport().height < 1)
                throw new IllegalStateException("Responsive technology layout overlaps/clips its controls");
        }
        init(Minecraft.getInstance(), originalWidth, originalHeight);
        NodeDefinition hidden = visibleNodes().stream().filter(this::isConcealed).findFirst().orElse(null);
        if (hidden != null) {
            technologySearch.setValue(hidden.title());
            if (searchMatches().stream().anyMatch(n -> n.id().equals(hidden.id())))
                throw new IllegalStateException("Search exposed a concealed technology");
        }
        technologySearch.setValue("minecraft:chest");
        if (searchMatches().isEmpty()) throw new IllegalStateException("Revealed recipe search failed");
        Rect search = technologySearchRect();
        mouseClicked(search.x + 3, search.y + 4, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        NodeDefinition selected = snapshot.node(selectedNodeId);
        if (selected == null || isConcealed(selected) || treeZoom < 0.78)
            throw new IllegalStateException("Search could not select and center its revealed match");
        for (NodeDefinition definition : snapshot.nodes())
            for (TechnologyPresentation.Group group : TechnologyPresentation.groups(definition.unlocks()))
                for (TechnologyPresentation.Unlock unlock : group.entries())
                    if (unlockIcon(unlock, unlockResult(unlock), definition).isEmpty())
                        throw new IllegalStateException("An unlock has no illustration: " + unlock.id());
        if (!unlockIcon(new TechnologyPresentation.Unlock("Recipe", "minecraft:chest", "Chest"),
                ItemStack.EMPTY, selected).is(Items.CHEST))
            throw new IllegalStateException("Absent chest recipe lost its registered item icon");
        if (!unlockIcon(new TechnologyPresentation.Unlock("Action", "crafting_table_use", "Crafting Table Use"),
                ItemStack.EMPTY, selected).is(Items.CRAFTING_TABLE))
            throw new IllegalStateException("Workstation action lost its representative icon");
        technologySearch.setValue("");
        technologySearch.setFocused(false);
        treePanX += 30;
        jumpToNode(snapshot.node(trackedNodeId));
        Point center = treePoint(technologyViewport(), selected);
        if (Math.abs(center.x - (technologyViewport().x + technologyViewport().width / 2)) > 1)
            throw new IllegalStateException("Tracked navigation failed to center its node");
        fitTreeToViewport(technologyViewport());
        technologyDetailsScroll = 0;
    }

    public void runUnlockGroupScrollForVisualTest() {
        if (!Boolean.getBoolean("townyeras.prototype.visualTest")) throw new IllegalStateException("Test-only method");
        Rect details = technologyDetails();
        mouseScrolled(details.x + 8, details.y + details.height / 2.0, 0, -100);
    }

    public void prepareDetailsScrollForVisualTest() {
        if (!Boolean.getBoolean("townyeras.prototype.visualTest")) throw new IllegalStateException("Test-only method");
        // Collapsed details now fit; explicitly open the large exact list to test scrolling.
        expandedUnlockGroups.add(selectedNodeId + "|Slabs and stairs");
    }

    public void runUnlockGroupClickForVisualTest() {
        if (!Boolean.getBoolean("townyeras.prototype.visualTest")) throw new IllegalStateException("Test-only method");
        GroupHit hit = unlockGroupHits.stream().filter(h -> h.bounds().height >= 10).findFirst().orElseThrow();
        expandedUnlockGroups.remove(hit.key());
        ResourceLocation tracked = trackedNodeId;
        mouseClicked(hit.bounds().x + 3, hit.bounds().y + 4, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        if (!expandedUnlockGroups.contains(hit.key()) || !java.util.Objects.equals(tracked, trackedNodeId))
            throw new IllegalStateException("Unlock-group expansion failed or changed tracking");
    }

    public void showLayeredBranchForVisualTest(boolean showBranch) {
        if (!Boolean.getBoolean("townyeras.prototype.visualTest")) throw new IllegalStateException("Test-only method");
        if (showBranch && !technologyBranchAllowed("farmer")) throw new IllegalStateException("Test branch unavailable");
        selectedBranchId = showBranch ? "farmer" : "universal";
        selectedEraId = "ancient";
        selectFirstVisibleNode();
    }

    public void showRevealedClassForVisualTest(String branch, boolean closeUp) {
        if (!Boolean.getBoolean("townyeras.prototype.visualTest"))
            throw new IllegalStateException("Visual fixture is unavailable outside the test client");
        visualTestRevealNodes = true; // Display fixture only: never grants nodes or modifies server progress.
        showClassBranchForVisualTest(branch);
        if (closeUp) {
            List<NodeDefinition> nodes = visibleNodes();
            for (double zoom : new double[]{0.60, 0.78, 1.0, 1.45}) {
                List<Rect> icons = new ArrayList<>();
                List<Rect> labels = new ArrayList<>();
                int size = Math.max(21, (int) Math.round(29 * zoom));
                for (NodeDefinition node : nodes) {
                    var p = graphPosition(node);
                    int x = (int) Math.round(p.x() * zoom), y = (int) Math.round(p.y() * zoom);
                    icons.add(new Rect(x - size / 2, y - size / 2, size, size));
                    var lines = font.split(Component.literal(node.title()), Math.min(112, (int) Math.round(130 * zoom)));
                    int width = lines.stream().mapToInt(font::width).max().orElse(0);
                    labels.add(new Rect(x - width / 2 - 3, y - size / 2 + size + 1,
                            width + 6, Math.max(10, lines.size() * 10) + 2));
                }
                for (int i = 0; i < labels.size(); i++) {
                    Rect label = labels.get(i);
                    for (Rect icon : icons) if (rectanglesOverlap(label, icon))
                        throw new IllegalStateException("Node title overlaps icon in " + branch + " at " + zoom);
                    for (int j = i + 1; j < labels.size(); j++) if (rectanglesOverlap(label, labels.get(j)))
                        throw new IllegalStateException("Node titles overlap in " + branch + " at " + zoom);
                }
            }
            centerSelectedNode();
        }
    }

    public void runExtendedZoomForVisualTest() {
        if (!Boolean.getBoolean("townyeras.prototype.visualTest")) throw new IllegalStateException("Test only");
        Rect viewport = technologyViewport();
        mouseScrolled(viewport.x + viewport.width / 2.0, viewport.y + viewport.height / 2.0, 0, -100);
        if (Math.abs(treeZoom - 0.08D) > 0.00001D) throw new IllegalStateException("Extended zoom did not reach 8%");
    }

    public void runSyncedGrantVisibilityForVisualTest() {
        if (!Boolean.getBoolean("townyeras.prototype.visualTest")) throw new IllegalStateException("Test only");
        visualTestRevealNodes = false;
        snapshot = new SyncedDashboardDataProvider().snapshot(minecraft.player);
        snapshotRevision = ClientSnapshotCache.revision();
        ResourceLocation granted = ResourceLocation.fromNamespaceAndPath("townyeras", "early_agriculture");
        ResourceLocation successor = ResourceLocation.fromNamespaceAndPath("townyeras", "fiber_crops");
        if (snapshot.progress(granted).status() != NodeStatus.GRANTED || !snapshot.progress(granted).accessible()
                || isConcealed(snapshot.node(granted)) || isConcealed(snapshot.node(successor)))
            throw new IllegalStateException("Real synchronized admin grant did not reveal its successor");
        showClassBranchForVisualTest("farmer");
        selectedNodeId = successor;
    }

    private static boolean rectanglesOverlap(Rect a, Rect b) {
        return a.x < b.right() && a.right() > b.x && a.y < b.bottom() && a.bottom() > b.y;
    }

    public void showClassBranchForVisualTest(String branch) {
        if (!Boolean.getBoolean("townyeras.prototype.visualTest") || !technologyBranchAllowed(branch))
            throw new IllegalStateException("Test branch unavailable");
        selectPage(Page.TECHNOLOGY);
        selectedBranchId = branch;
        selectedEraId = "ancient";
        selectFirstVisibleNode();
    }

    public void runAdminResetSmokeTestForVisualTest() {
        if (activePage != Page.OVERVIEW || !snapshot.timelineAdmin()
                || snapshot.onlinePlayerNames().isEmpty()) {
            throw new IllegalStateException("Operator progress-reset controls are unavailable");
        }
        if (snapshot.nodes().size() > 1) {
            ResourceLocation initialNode = adminGrantNodeId;
            Rect node = overviewAdminNodeRect();
            mouseClicked(node.x + 3.0D, node.y + 3.0D, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            if (adminGrantNodeId == null || adminGrantNodeId.equals(initialNode)) {
                throw new IllegalStateException("Operator node-grant selector did not cycle");
            }
        }
        Rect reset = overviewAdminResetRect();
        mouseClicked(reset.x + 3.0D, reset.y + 3.0D, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        if (!armedProgressReset) {
            throw new IllegalStateException("Operator progress-reset confirmation did not arm");
        }
        armedProgressReset = false;
    }

    public void runClassesSmokeTestForVisualTest() {
        if (activePage != Page.CLASSES) {
            throw new IllegalStateException("Classes page is not active");
        }
        List<TechClass> classes = selectableClasses();
        if (classes.size() < 2) {
            throw new IllegalStateException("Classes page does not contain enough selectable classes");
        }

        if (snapshot.selectedClassIds().size() < 2) {
            pendingInitialClasses.clear();
            Rect first = classCardRect(0);
            Rect second = classCardRect(1);
            mouseClicked(first.x + 3.0D, first.y + 3.0D, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            mouseClicked(second.x + 3.0D, second.y + 3.0D, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            if (pendingInitialClasses.size() != 2) {
                throw new IllegalStateException("Initial class selection did not work");
            }
            return;
        }

        int activeIndex = -1;
        int replacementIndex = -1;
        for (int index = 0; index < classes.size(); index++) {
            if (activeIndex < 0 && snapshot.selectedClassIds().contains(classes.get(index).id())) {
                activeIndex = index;
            }
            if (replacementIndex < 0 && !snapshot.selectedClassIds().contains(classes.get(index).id())) {
                replacementIndex = index;
            }
        }
        if (activeIndex < 0 || replacementIndex < 0) {
            throw new IllegalStateException("Class swap choices could not be found");
        }
        Rect active = classCardRect(activeIndex);
        Rect replacement = classCardRect(replacementIndex);
        mouseClicked(active.x + 3.0D, active.y + 3.0D, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        mouseClicked(replacement.x + 3.0D, replacement.y + 3.0D, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        if (swapFromClassId == null || swapToClassId == null) {
            throw new IllegalStateException("Class swap selection did not work");
        }
    }

    public void runTimelineHudToggleSmokeTestForVisualTest() {
        if (activePage != Page.TIMELINE) {
            throw new IllegalStateException("Timeline page is not active");
        }
        boolean initial = HistoriaErasClientPreferences.calendarBarVisible();
        Rect toggle = timelineHudToggleRect();
        mouseClicked(toggle.x + 3.0D, toggle.y + 3.0D, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        if (HistoriaErasClientPreferences.calendarBarVisible() == initial) {
            throw new IllegalStateException("Calendar-bar visibility toggle did not change");
        }
        mouseClicked(toggle.x + 3.0D, toggle.y + 3.0D, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        if (HistoriaErasClientPreferences.calendarBarVisible() != initial) {
            throw new IllegalStateException("Calendar-bar visibility preference did not restore");
        }
    }

    public void runDetailsScrollSmokeTestForVisualTest() {
        if (activePage != Page.TECHNOLOGY || technologyDetailsMaxScroll <= 0) {
            throw new IllegalStateException("Technology details are not scrollable");
        }
        Rect details = technologyDetails();
        int previous = technologyDetailsScroll;
        mouseScrolled(details.x + details.width / 2.0D, details.y + details.height / 2.0D, 0.0D, -4.0D);
        if (technologyDetailsScroll <= previous) {
            throw new IllegalStateException("Technology detail scrolling did not work");
        }
        if (detailsScrollbarTrack == null || detailsScrollbarThumb == null)
            throw new IllegalStateException("Scrollbar was not rendered");
        double x = detailsScrollbarThumb.x + 4;
        double y = detailsScrollbarThumb.y + detailsScrollbarThumb.height / 2.0D;
        double panX = treePanX, panY = treePanY;
        if (!mouseClicked(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT))
            throw new IllegalStateException("Scrollbar thumb is not clickable");
        mouseDragged(x, detailsScrollbarTrack.bottom() + 50, GLFW.GLFW_MOUSE_BUTTON_LEFT, 0, 50);
        if (technologyDetailsScroll != technologyDetailsMaxScroll)
            throw new IllegalStateException("Scrollbar drag did not reach bottom");
        mouseDragged(x, detailsScrollbarTrack.y - 50, GLFW.GLFW_MOUSE_BUTTON_LEFT, 0, -100);
        if (technologyDetailsScroll != 0)
            throw new IllegalStateException("Scrollbar drag did not reach top");
        mouseReleased(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        mouseDragged(x, detailsScrollbarTrack.bottom() + 50, GLFW.GLFW_MOUSE_BUTTON_LEFT, 0, 50);
        if (technologyDetailsScroll != 0 || treePanX != panX || treePanY != panY)
            throw new IllegalStateException("Scrollbar release or map isolation failed");
        mouseClicked(x, detailsScrollbarTrack.bottom() - 1, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        if (technologyDetailsScroll <= 0)
            throw new IllegalStateException("Scrollbar track click did not jump");
        mouseReleased(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        com.historiamc.townyeras.HistoriaEras.LOGGER.info("Scrollbar passed thumb drag, track click, top/bottom clamping, release and map isolation");
    }

    private NodeDefinition nodeAt(double mouseX, double mouseY, Rect viewport) {
        int nodeSize = treeNodeSize();
        for (NodeDefinition node : visibleNodes()) {
            Point center = treePoint(viewport, node);
            int x = center.x - nodeSize / 2;
            int y = center.y - nodeSize / 2;
            if (contains(mouseX, mouseY, x, y, nodeSize, nodeSize)
                    && contains(mouseX, mouseY, viewport.x + 2, viewport.y + 2, viewport.width - 4, viewport.height - 4)) {
                return node;
            }
        }
        return null;
    }

    private List<NodeDefinition> visibleNodes() {
        return snapshot.nodes().stream()
                .filter(node -> node.eraId().equals(selectedEraId))
                .filter(node -> node.classId().equals(selectedBranchId))
                .filter(node -> technologyBranchAllowed(node.classId()))
                .filter(node -> technologyEraAllowed(node.eraId()))
                .toList();
    }

    private boolean isConcealed(NodeDefinition node) {
        if (visualTestRevealNodes && Boolean.getBoolean("townyeras.prototype.visualTest")) return false;
        return node != null && !prerequisitesRevealed(node);
    }

    private boolean prerequisitesRevealed(NodeDefinition node) {
        NodeStatus ownStatus = snapshot.progress(node.id()).status();
        return TechnologyNodeVisibility.revealed(ownStatus == NodeStatus.COMPLETED || ownStatus == NodeStatus.GRANTED,
                node.prerequisites(), id -> snapshot.progress(id).accessible());
    }

    private List<String> availableEraIds() {
        return new ArrayList<>(snapshot.nodes().stream()
                .map(NodeDefinition::eraId)
                .filter(this::technologyEraAllowed)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
    }

    private List<String> availableBranchIds() {
        return new ArrayList<>(snapshot.nodes().stream()
                .filter(node -> node.eraId().equals(selectedEraId))
                .filter(node -> technologyBranchAllowed(node.classId()))
                .map(NodeDefinition::classId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
    }

    private boolean technologyBranchAllowed(String classId) {
        return TechnologyBranchVisibility.allowed(
                snapshot.timelineAdmin(), snapshot.selectedClassIds(), classId
        );
    }

    private boolean technologyEraAllowed(String eraId) {
        return TechnologyEraVisibility.allowed(snapshot.timelineAdmin(), snapshot.eraId(), eraId,
                TechnologyManager.current().eras());
    }

    public void runEraVisibilitySmokeTestForVisualTest() {
        if (!Boolean.getBoolean("townyeras.prototype.visualTest")) return;
        DashboardSnapshot saved = snapshot;
        String savedEra = selectedEraId;
        String savedBranch = selectedBranchId;
        ResourceLocation savedNode = selectedNodeId;
        RememberedView savedMemory = rememberedView;
        try {
            // Retain all definitions, including future nodes: the screen itself must enforce visibility.
            snapshot = eraVisibilityFixture(saved, "ancient", false);
            selectedEraId = "classical";
            selectedBranchId = "universal";
            if (!visibleNodes().isEmpty() || availableEraIds().contains("classical"))
                throw new IllegalStateException("Normal-player menu exposed a future era");
            NodeDefinition future = saved.nodes().stream().filter(n -> n.eraId().equals("classical")).findFirst().orElseThrow();
            selectedNodeId = null;
            jumpToNode(future);
            if (selectedNodeId != null) throw new IllegalStateException("Future detail navigation was allowed");
            rememberedView = new RememberedView(saved.playerId(), Page.TECHNOLOGY, "classical", "universal",
                    future.id(), 0, 0, 1, 0, 0, "", List.of(), focusedClassId);
            HistoriaErasScreen reopened = new HistoriaErasScreen(snapshot);
            if (!reopened.selectedEraId.equals("ancient"))
                throw new IllegalStateException("Remembered admin view leaked future-era details");
            snapshot = eraVisibilityFixture(saved, "classical", false);
            selectedEraId = "ancient";
            if (visibleNodes().isEmpty() || !availableEraIds().contains("ancient"))
                throw new IllegalStateException("Historical tech tree was hidden");
            snapshot = eraVisibilityFixture(saved, "ancient", true);
            if (!availableEraIds().contains("classical"))
                throw new IllegalStateException("Admin Mode lost future-era browsing");
        } finally {
            snapshot = saved;
            selectedEraId = savedEra;
            selectedBranchId = savedBranch;
            selectedNodeId = savedNode;
            rememberedView = savedMemory;
        }
    }

    private static DashboardSnapshot eraVisibilityFixture(DashboardSnapshot source, String era, boolean admin) {
        return new DashboardSnapshot(source.playerId(), source.displayName(), era, source.eraName(),
                source.calendarDate(), source.nationName(), source.classes(), source.jobTokens(),
                source.completedNodeCount(), source.nodes(), source.progress(), null, source.selectedClassIds(),
                source.timeline(), source.operator(), admin, source.onlinePlayerNames());
    }

    private void cycleEra() {
        List<String> eras = availableEraIds();
        if (eras.isEmpty()) {
            return;
        }
        int index = Math.max(0, eras.indexOf(selectedEraId));
        selectedEraId = eras.get((index + 1) % eras.size());
        List<String> branches = availableBranchIds();
        selectedBranchId = branches.contains("universal") ? "universal" : branches.getFirst();
        selectFirstVisibleNode();
    }

    private void cycleBranch() {
        List<String> branches = availableBranchIds();
        if (branches.isEmpty()) {
            return;
        }
        int index = Math.max(0, branches.indexOf(selectedBranchId));
        selectedBranchId = branches.get((index + 1) % branches.size());
        selectFirstVisibleNode();
    }

    private void selectFirstVisibleNode() {
        List<NodeDefinition> visible = visibleNodes();
        selectedNodeId = visible.isEmpty() ? null : visible.getFirst().id();
        technologyDetailsScroll = 0;
        technologyDetailsMaxScroll = 0;
        unlockGroupHits.clear();
        prerequisiteHits.clear();
        fitTreeToViewport(technologyViewport());
    }

    private static String displayId(String id) {
        if (id == null || id.isBlank()) {
            return "Unknown";
        }
        String[] words = id.toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private Point treePoint(Rect viewport, NodeDefinition node) {
        int x = (int) Math.round(viewport.x + 18.0D + treePanX + graphPosition(node).x() * treeZoom);
        int y = (int) Math.round(viewport.y + viewport.height / 2.0D + treePanY + (graphPosition(node).y() - 66.0D) * treeZoom);
        return new Point(x, y);
    }

    private int treeNodeSize() {
        // Preserve familiar icons at normal zoom, shrinking only when lane spacing needs it.
        return Math.max(8, Math.min(Math.max(21, (int) Math.round(29.0D * treeZoom)),
                (int) Math.floor(100.0D * treeZoom)));
    }

    private TechnologyTreeLayout.Position graphPosition(NodeDefinition node) {
        List<NodeDefinition> current = visibleNodes();
        if (!current.equals(layoutNodes)) {
            layoutNodes = current;
            treeLayout = TechnologyTreeLayout.layout(current.stream().map(n -> new TechnologyTreeLayout.Vertex(
                    n.id().toString(), n.prerequisites().stream().map(ResourceLocation::toString).toList(), n.graphY())).toList());
        }
        return treeLayout.getOrDefault(node.id().toString(), new TechnologyTreeLayout.Position(node.graphX(), node.graphY()));
    }

    private ItemStack iconFor(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == null || item == Items.AIR) {
            item = Items.BARRIER;
        }
        return item.getDefaultInstance();
    }

    private int drawList(GuiGraphics graphics, List<String> entries, int x, int y, int width, int maximumY) {
        for (String entry : entries) {
            List<FormattedCharSequence> lines = font.split(Component.literal("• " + entry), width);
            for (FormattedCharSequence line : lines) {
                if (y + 9 > maximumY) {
                    graphics.drawString(font, "…", x, y, MUTED, false);
                    return y + 11;
                }
                graphics.drawString(font, line, x, y, TEXT, false);
                y += 10;
            }
        }
        return y;
    }

    private int drawRequirements(
            GuiGraphics graphics,
            List<RequirementProgress> requirements,
            int x,
            int y,
            int width,
            int maximumY
    ) {
        for (RequirementProgress requirement : requirements) {
            String prefix = requirement.complete() ? "✓ " : "• ";
            String value = prefix + requirement.display() + "  " + requirement.formattedProgress();
            List<FormattedCharSequence> lines = font.split(Component.literal(value), width);
            for (FormattedCharSequence line : lines) {
                if (y + 9 > maximumY) {
                    graphics.drawString(font, "…", x, y, MUTED, false);
                    return y + 11;
                }
                graphics.drawString(font, line, x, y, requirement.complete() ? 0xFF9ED697 : TEXT, false);
                y += 10;
            }
        }
        return y;
    }

    private int drawWrapped(GuiGraphics graphics, String value, int x, int y, int width, int color, int maximumLines) {
        List<FormattedCharSequence> lines = font.split(Component.literal(value), width);
        int count = Math.min(maximumLines, lines.size());
        for (int i = 0; i < count; i++) {
            graphics.drawString(font, lines.get(i), x, y, color, false);
            y += 10;
        }
        return y;
    }

    private int drawWrappedAll(GuiGraphics graphics, String value, int x, int y, int width, int color) {
        for (FormattedCharSequence line : font.split(Component.literal(value), width)) {
            graphics.drawString(font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    private String ellipsize(String value, int maximumWidth) {
        if (font.width(value) <= maximumWidth) {
            return value;
        }
        String ellipsis = "…";
        return font.plainSubstrByWidth(value, Math.max(0, maximumWidth - font.width(ellipsis))) + ellipsis;
    }

    private static void renderProgressBar(GuiGraphics graphics, int x, int y, int width, int height, float progress, int color) {
        graphics.fill(x, y, x + width, y + height, OUTER);
        int innerWidth = Math.max(0, width - 2);
        int filled = Math.round(innerWidth * progress);
        graphics.fill(x + 1, y + 1, x + 1 + filled, y + height - 1, color);
    }

    private static void drawBeveledPanel(GuiGraphics graphics, int x, int y, int width, int height, int fill, int border) {
        if (width <= 0 || height <= 0) {
            return;
        }
        graphics.fill(x, y, x + width, y + height, OUTER);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, border);
        graphics.fill(x + 1, y + 1, x + 2, y + height - 1, border);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, fill);
        graphics.fill(x + 2, y + height - 3, x + width - 1, y + height - 1, EDGE_DARK);
        graphics.fill(x + width - 3, y + 2, x + width - 1, y + height - 1, EDGE_DARK);
    }

    private static void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color, int thickness) {
        if (y1 == y2) {
            int left = Math.min(x1, x2);
            int right = Math.max(x1, x2);
            graphics.fill(left, y1 - thickness / 2, right + 1, y1 - thickness / 2 + thickness, color);
        } else if (x1 == x2) {
            int top = Math.min(y1, y2);
            int bottom = Math.max(y1, y2);
            graphics.fill(x1 - thickness / 2, top, x1 - thickness / 2 + thickness, bottom + 1, color);
        }
    }

    private static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    public void runGettingStartedStartForVisualTest() {
        if (!Boolean.getBoolean("townyeras.prototype.visualTest")) throw new IllegalStateException("Test-only method");
        Rect help = tutorialHelpRect();
        mouseClicked(help.x + 3, help.y + 3, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        if (tutorialStep != 0 || activePage != Page.OVERVIEW) throw new IllegalStateException("Tutorial replay did not open");
    }
    public void runGettingStartedNextForVisualTest(int expectedStep) {
        if (!Boolean.getBoolean("townyeras.prototype.visualTest")) throw new IllegalStateException("Test-only method");
        Rect card = tutorialCard();
        mouseClicked(card.right() - 30, card.bottom() - 14, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        if (tutorialStep != expectedStep) throw new IllegalStateException("Tutorial Next hitbox failed: " + tutorialStep);
        if (activePage != Page.OVERVIEW && (contentY != headerY || contentHeight != panelHeight - 10))
            throw new IllegalStateException("Non-Overview page did not reclaim header space");
        if (activePage == Page.OVERVIEW && contentY != headerY + 46)
            throw new IllegalStateException("Overview lost its identity header");
        if (tutorialStep == 2) {
            Rect focus = tutorialFocus();
            for (int i = 0; i < 6; i++) {
                Rect role = classCardRect(i);
                if (role.x < focus.x || role.y < focus.y || role.right() > focus.right() || role.bottom() > focus.bottom())
                    throw new IllegalStateException("Classes spotlight clips a class card");
            }
        }
        if (tutorialStep == 3 || tutorialStep == 4 || tutorialStep == 5) {
            Rect focus = tutorialFocus(), popup = tutorialCard();
            if (popup.x < focus.right() && popup.right() > focus.x && popup.y < focus.bottom() && popup.bottom() > focus.y)
                throw new IllegalStateException("Tutorial popup covers its highlighted feature");
        }
        if (tutorialStep == 3) {
            Rect viewport = technologyViewport();
            double oldZoom = treeZoom;
            mouseScrolled(viewport.x + viewport.width / 2.0, viewport.y + viewport.height / 2.0, 0, 1);
            if (treeZoom <= oldZoom) throw new IllegalStateException("Tutorial blocked map zoom");
            double x = viewport.right() - 6, y = viewport.bottom() - 6;
            double oldPan = treePanX;
            mouseClicked(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            mouseDragged(x - 5, y - 3, GLFW.GLFW_MOUSE_BUTTON_LEFT, -5, -3);
            mouseReleased(x - 5, y - 3, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            if (treePanX == oldPan) throw new IllegalStateException("Tutorial blocked map dragging");
            Rect fit = treeNavigationRect(0);
            mouseClicked(fit.x + 2, fit.y + 2, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        }
        if (tutorialStep == 4 && technologyDetailsMaxScroll > 0) {
            int previous = technologyDetailsScroll;
            runDetailsScrollSmokeTestForVisualTest();
            technologyDetailsScroll = previous;
        }
    }
    public void runGettingStartedFinishForVisualTest() {
        runGettingStartedNextForVisualTest(7);
        keyPressed(GLFW.GLFW_KEY_LEFT, 0, 0);
        if (tutorialStep != 6) throw new IllegalStateException("Tutorial Back failed");
        runGettingStartedNextForVisualTest(7);
        runGettingStartedNextForVisualTest(-1);
        runGettingStartedStartForVisualTest();
        Rect card = tutorialCard();
        mouseClicked(card.x + 70, card.bottom() - 14, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        if (tutorialStep != -1) throw new IllegalStateException("Tutorial Skip failed");
    }

    private void updateContentLayout() {
        boolean overview = activePage == Page.OVERVIEW;
        contentY = headerY + (overview ? 46 : 0);
        contentHeight = panelHeight - (overview ? 56 : 10);
        if (technologySearch != null) {
            Rect search = technologySearchRect();
            technologySearch.setX(search.x);
            technologySearch.setY(search.y);
            technologySearch.setWidth(search.width);
        }
    }

    private void selectPage(Page page) {
        boolean changed = activePage != page;
        activePage = page;
        updateContentLayout();
        if (technologySearch != null && page != Page.TECHNOLOGY) technologySearch.setFocused(false);
        if (changed) {
            draggingTree = false;
            draggingDetailsScrollbar = false;
            detailsScrollbarTrack = null;
            detailsScrollbarThumb = null;
            technologyDetailsScroll = 0;
            if (page == Page.TECHNOLOGY) fitTreeToViewport(technologyViewport());
        }
        if (page == Page.TECHNOLOGY && !treeViewedAcknowledged) {
            treeViewedAcknowledged = true;
            HistoriaErasNetwork.acknowledgeOnboarding(true, false);
        }
    }

    /** A seven-pixel-high, shadowed glyph: intentionally not an item or a barrier texture. */
    private void renderQuestionMark(GuiGraphics graphics, int centerX, int centerY, int nodeSize) {
        int pixel = Math.max(1, Math.min(3, (nodeSize - 6) / 7));
        int left = centerX - 5 * pixel / 2;
        int top = centerY - 7 * pixel / 2;
        for (int pass = 0; pass < 2; pass++) {
            int offset = pass == 0 ? pixel : 0;
            int color = pass == 0 ? 0xFF5E397C : LAVENDER;
            for (int row = 0; row < QUESTION_PIXELS.length; row++)
                for (int column = 0; column < 5; column++)
                    if (QUESTION_PIXELS[row].charAt(column) == '1')
                        graphics.fill(left + column * pixel + offset, top + row * pixel + offset,
                                left + (column + 1) * pixel + offset, top + (row + 1) * pixel + offset, color);
        }
    }

    private static final String[][] TOUR = {
            {"Welcome to HistoriaEras", "Develop your character by completing technology objectives. This short tour shows you where everything is. You can skip it or replay it with Tutorial."},
            {"Overview: your civilization", "Your portrait, name, era and calendar date live here. The summary shows your Towny nation, chosen classes and progress. The identity header appears only on Overview."},
            {"Classes: choose your roles", "Choose two classes. Select a card to read its description, then confirm your pair. Your first pair is free; later swaps require job tokens. The tutorial does not choose classes for you."},
            {"Technology: explore the tree", "Use the top selectors for the era and your Universal or class branch. Drag to pan, scroll over the map to zoom, and press Fit to fit the tree. Selected centers your node; Tracked returns to your goal. Purple question marks hide technologies until their prerequisites are complete."},
            {"Read a technology", "Select a revealed node. The right panel lists prerequisites, exact requirements, progress and unlocks. Scroll inside this panel and expand unlock groups to read every exact ID. Prerequisites must be completed before a node's objectives start counting."},
            {"Track your next goal", "Select a revealed node and press Track. Its objective progress appears on Overview and the in-game bar. Complete every requirement to unlock its listed recipes and abilities. Trades do not count for personal-harvest objectives."},
            {"Timeline: dates and pace", "Timeline shows era progress, time until the next era, and calendar/historical rollover speeds. Its calendar-bar toggle lets you hide the in-game bar. Normal players cannot change server time."},
            {"You're ready", "Return to Classes if you still need your pair, then start with Universal technologies. Tutorial replays this guide whenever you need it. Your controls can be rebound under Options > Controls > Key Binds > HistoriaEras."}
    };

    private Rect tutorialHelpRect() { return new Rect(panelX + 5, panelY + panelHeight - 45, navigationWidth - 10, 17); }
    private Rect tutorialCard() {
        // Dock beside, not over, the feature being explained. Keep the whole map/detail panel exposed.
        if (tutorialStep == 3) {
            Rect details = technologyDetails();
            return new Rect(details.x + 2, details.y + 2, details.width - 4, details.height - 4);
        }
        if (tutorialStep == 4 || tutorialStep == 5) {
            Rect tree = technologyTreePanel();
            int w = Math.min(300, tree.width - 8);
            int h = Math.min(133, tree.height - 16);
            return new Rect(tree.x + (tree.width - w) / 2, tree.y + 8, w, h);
        }
        int w = Math.min(300, contentWidth - 8);
        int h = Math.min(113, panelHeight - 22);
        return new Rect(contentX + (contentWidth - w) / 2,
                tutorialStep == 5 ? contentY + 8 : panelY + panelHeight - h - 8, w, h);
    }
    private Rect tutorialFocus() {
        return switch (tutorialStep) {
            case 1 -> new Rect(headerX, headerY, headerWidth, 41);
            case 2 -> {
                Rect first = classCardRect(0), last = classCardRect(5);
                yield new Rect(first.x - 2, first.y - 2, last.right() - first.x + 4,
                        last.bottom() - first.y + 4);
            }
            case 3 -> technologyTreePanel();
            case 4 -> technologyDetails();
            case 5 -> {
                Rect details = technologyDetails();
                yield new Rect(details.right() - 69, details.bottom() - 18, 62, 14);
            }
            case 6 -> new Rect(contentX, contentY, contentWidth, Math.min(90, contentHeight));
            case 7 -> tutorialHelpRect();
            default -> new Rect(contentX, contentY, contentWidth, contentHeight);
        };
    }
    private void startTutorial() { tutorialStep = 0; applyTutorialStep(); }
    private void applyTutorialStep() {
        tutorialTextScroll = 0;
        selectPage(switch (tutorialStep) {
            case 2 -> Page.CLASSES;
            case 3, 4, 5 -> Page.TECHNOLOGY;
            case 6 -> Page.TIMELINE;
            default -> Page.OVERVIEW;
        });
        if (activePage == Page.TECHNOLOGY) {
            selectedEraId = snapshot.eraId();
            selectedBranchId = "universal";
            selectFirstVisibleNode();
        }
    }
    private void advanceTutorial(int direction) {
        if (direction > 0 && tutorialStep == TOUR.length - 1) { dismissTutorial(); return; }
        tutorialStep = Math.clamp(tutorialStep + direction, 0, TOUR.length - 1);
        applyTutorialStep();
    }
    private void dismissTutorial() {
        tutorialStep = -1;
        tutorialTextScroll = 0;
        HistoriaErasNetwork.acknowledgeOnboarding(false, true);
    }
    private boolean tutorialClick(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        Rect next = tutorialButton(2), back = tutorialButton(0), skip = tutorialButton(1);
        if (contains(mouseX, mouseY, next.x, next.y, next.width, next.height)) advanceTutorial(1);
        else if (contains(mouseX, mouseY, back.x, back.y, back.width, back.height) && tutorialStep > 0) advanceTutorial(-1);
        else if (contains(mouseX, mouseY, skip.x, skip.y, skip.width, skip.height)) dismissTutorial();
        return true;
    }
    private Rect tutorialButton(int index) {
        Rect card = tutorialCard();
        if (card.width < 170) {
            int half = (card.width - 20) / 2;
            return index == 2 ? new Rect(card.x + 8, card.bottom() - 23, card.width - 16, 17)
                    : new Rect(card.x + 8 + index * (half + 4), card.bottom() - 44, half, 17);
        }
        return switch (index) {
            case 0 -> new Rect(card.x + 8, card.bottom() - 23, 42, 17);
            case 1 -> new Rect(card.x + 55, card.bottom() - 23, 42, 17);
            default -> new Rect(card.right() - 62, card.bottom() - 23, 54, 17);
        };
    }
    private void renderTutorial(GuiGraphics graphics, int mouseX, int mouseY) {
        // Finish the page's batched text/entity rendering before drawing the modal above it.
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 1000);
        Rect focus = tutorialFocus();
        // Dim outside the highlighted region; keep the actual page visible in the spotlight.
        graphics.fill(panelX, panelY, panelX + panelWidth, focus.y, 0xB0000000);
        graphics.fill(panelX, focus.bottom(), panelX + panelWidth, panelY + panelHeight, 0xB0000000);
        graphics.fill(panelX, focus.y, focus.x, focus.bottom(), 0xB0000000);
        graphics.fill(focus.right(), focus.y, panelX + panelWidth, focus.bottom(), 0xB0000000);
        graphics.renderOutline(focus.x, focus.y, focus.width, focus.height, LAVENDER);
        int navIndex = java.util.Arrays.asList(visiblePages()).indexOf(activePage);
        if (navIndex >= 0) {
            int navY = panelY + 42 + navIndex * 31;
            drawBeveledPanel(graphics, panelX + 5, navY, navigationWidth - 10, 27, FIELD, LAVENDER);
            graphics.drawCenteredString(font, navigationWidth < 50 ? activePage.shortLabel : activePage.label,
                    panelX + navigationWidth / 2, navY + 9, TEXT);
        }
        Rect card = tutorialCard();
        drawBeveledPanel(graphics, card.x, card.y, card.width, card.height, FIELD, LAVENDER);
        graphics.drawString(font, (card.width < 110 ? "Tour " : "Getting Started ") + (tutorialStep + 1) + "/" + TOUR.length, card.x + 8, card.y + 7, GOLD, false);
        var titleLines = font.split(Component.literal(TOUR[tutorialStep][0]), card.width - 16);
        for (int i = 0; i < titleLines.size(); i++)
            graphics.drawString(font, titleLines.get(i), card.x + 8, card.y + 20 + i * 10, LAVENDER, false);
        String body = TOUR[tutorialStep][1];
        if (tutorialStep == 0) body += " Menu key: " + HistoriaErasClient.OPEN_DASHBOARD.getTranslatedKeyMessage().getString() + ".";
        var lines = font.split(Component.literal(body), Math.max(40, card.width - 20));
        int top = card.y + 24 + titleLines.size() * 10;
        int bottom = card.bottom() - (card.width < 170 ? 49 : 28);
        tutorialTextMaxScroll = Math.max(0, lines.size() * 10 - (bottom - top));
        tutorialTextScroll = Math.clamp(tutorialTextScroll, 0, tutorialTextMaxScroll);
        graphics.enableScissor(card.x + 6, top, card.right() - 6, bottom);
        int textY = top - tutorialTextScroll;
        for (var line : lines) { graphics.drawString(font, line, card.x + 8, textY, TEXT, false); textY += 10; }
        graphics.disableScissor();
        if (tutorialTextMaxScroll > 0) {
            int thumb = Math.max(8, (bottom - top) / 3);
            int scrollY = top + (bottom - top - thumb) * tutorialTextScroll / tutorialTextMaxScroll;
            graphics.fill(card.right() - 4, top, card.right() - 2, bottom, EDGE_DARK);
            graphics.fill(card.right() - 4, scrollY, card.right() - 2, scrollY + thumb, LAVENDER);
        }
        for (int i = 0; i < 3; i++) {
            Rect button = tutorialButton(i);
            drawBeveledPanel(graphics, button.x, button.y, button.width, button.height, PANEL_DARK,
                    i == 2 ? GOLD : i == 0 && tutorialStep == 0 ? EDGE_DARK : EDGE_LIGHT);
            graphics.drawCenteredString(font, i == 0 ? "Back" : i == 1 ? "Skip" : tutorialStep == TOUR.length - 1 ? "Done" : "Next",
                    button.x + button.width / 2, button.y + 5, i == 1 ? MUTED : i == 0 && tutorialStep == 0 ? EDGE_LIGHT : TEXT);
        }
        graphics.flush();
        graphics.pose().popPose();
    }

    private enum Page {
        OVERVIEW("Overview", "O", true),
        TECHNOLOGY("Technology", "T", true),
        CLASSES("Classes", "C", true),
        TIMELINE("Timeline", "L", true);

        private final String label;
        private final String shortLabel;
        private final boolean enabled;

        Page(String label, String shortLabel, boolean enabled) {
            this.label = label;
            this.shortLabel = shortLabel;
            this.enabled = enabled;
        }
    }

    private record Rect(int x, int y, int width, int height) {
        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }
    }

    private record Point(int x, int y) {
    }
    private record GroupHit(String key, Rect bounds) {}
    private record PrerequisiteHit(ResourceLocation id, Rect bounds) {}
    private record ObjectiveHint(String text, Rect bounds) {}
}
