package com.historiamc.townyeras.server;

import com.historiamc.townyeras.HistoriaEras;
import com.historiamc.townyeras.tech.TechClass;
import com.historiamc.townyeras.tech.TechRegistry;
import com.historiamc.townyeras.tech.TechnologyManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/** Registers and implements the server-side {@code /historiaeras} command tree. */
@EventBusSubscriber(modid = HistoriaEras.MOD_ID)
public final class HistoriaErasCommands {

    private static final SuggestionProvider<CommandSourceStack> CLASS_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(selectableClassIds(), builder);
    private static final SuggestionProvider<CommandSourceStack> NODE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(technology().nodes().keySet(), builder);
    private static final SuggestionProvider<CommandSourceStack> YEAR_SUGGESTIONS = (context, builder) -> {
        String era = TimelineService.snapshot(context.getSource().getServer()).eraId();
        int start = era.equals(TimelineState.CLASSICAL_ERA_ID)
                ? TimelineState.CLASSICAL_START_YEAR : TimelineState.ANCIENT_START_YEAR;
        int end = era.equals(TimelineState.CLASSICAL_ERA_ID)
                ? TimelineState.CLASSICAL_END_YEAR : TimelineState.ANCIENT_END_YEAR;
        return SharedSuggestionProvider.suggest(IntStream.iterate(
                start,
                year -> year <= end,
                year -> year + TimelineState.HISTORICAL_YEARS_PER_CALENDAR_YEAR
        ).mapToObj(Integer::toString), builder);
    };

    private HistoriaErasCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    /** Public entry point for tests or hosts that register commands explicitly. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = dispatcher.register(Commands.literal("historiaeras")
                .then(Commands.literal("status")
                        .executes(context -> showStatus(context.getSource())))
                .then(Commands.literal("class")
                        .then(Commands.literal("choose")
                                .then(Commands.argument("first", StringArgumentType.word())
                                        .suggests(CLASS_SUGGESTIONS)
                                        .then(Commands.argument("second", StringArgumentType.word())
                                                .suggests(CLASS_SUGGESTIONS)
                                                .executes(HistoriaErasCommands::chooseClasses))))
                        .then(Commands.literal("swap")
                                .then(Commands.argument("old", StringArgumentType.word())
                                        .suggests(HistoriaErasCommands::suggestSelectedClasses)
                                        .then(Commands.argument("new", StringArgumentType.word())
                                                .suggests(CLASS_SUGGESTIONS)
                                                .executes(HistoriaErasCommands::swapClass)))))
                .then(Commands.literal("track")
                        .then(Commands.argument("node", StringArgumentType.word())
                                .suggests(NODE_SUGGESTIONS)
                                .executes(HistoriaErasCommands::trackNode)))
                .then(Commands.literal("untrack")
                        .executes(context -> untrackNode(context.getSource())))
                .then(Commands.literal("admin")
                        .requires(source -> source.hasPermission(3))
                        .then(Commands.literal("mode")
                                .executes(context -> toggleAdminMode(context.getSource()))
                                .then(Commands.literal("status")
                                        .executes(context -> showAdminMode(context.getSource())))
                                .then(Commands.literal("on")
                                        .executes(context -> setAdminMode(context.getSource(), true)))
                                .then(Commands.literal("off")
                                        .executes(context -> setAdminMode(context.getSource(), false))))
                        .then(Commands.literal("node")
                                .requires(AdminModeService::canManage)
                                .then(nodeAdminCommand("grant"))
                                .then(nodeAdminCommand("revoke"))
                                .then(selfNodeAdminCommand("grantself"))
                                .then(selfNodeAdminCommand("revokeself")))
                        .then(Commands.literal("class")
                                .requires(AdminModeService::canManage)
                                .then(Commands.literal("set")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("first", StringArgumentType.word())
                                                        .suggests(CLASS_SUGGESTIONS)
                                                        .then(Commands.argument("second", StringArgumentType.word())
                                                                .suggests(CLASS_SUGGESTIONS)
                                                                .executes(HistoriaErasCommands::adminSetClasses)))))
                                .then(Commands.literal("clear")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(HistoriaErasCommands::adminClearClasses))))
                        .then(Commands.literal("reset")
                                .requires(AdminModeService::canManage)
                                .then(resetAdminCommand("progress"))
                                .then(resetAdminCommand("all")))
                        .then(Commands.literal("tokens")
                                .requires(AdminModeService::canManage)
                                .then(Commands.literal("add")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                        .executes(HistoriaErasCommands::addTokens))))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                        .executes(HistoriaErasCommands::setTokens)))))
                        .then(Commands.literal("website")
                                .requires(AdminModeService::canManage)
                                .then(Commands.literal("status")
                                        .executes(context -> websiteStatus(context.getSource())))
                                .then(Commands.literal("publish")
                                        .executes(context -> websitePublish(context.getSource()))))
                        .then(Commands.literal("time")
                                .requires(AdminModeService::canManage)
                                .then(Commands.literal("pause")
                                        .executes(context -> setTimePaused(context.getSource(), true)))
                                .then(Commands.literal("resume")
                                        .executes(context -> setTimePaused(context.getSource(), false)))
                                .then(Commands.literal("reset")
                                        .executes(context -> resetTime(context.getSource())))
                                .then(Commands.literal("advance")
                                        .executes(context -> advanceEra(context.getSource())))
                                .then(Commands.literal("era")
                                        .then(Commands.argument("era", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                        List.of(
                                                                TimelineState.ANCIENT_ERA_ID,
                                                                TimelineState.CLASSICAL_ERA_ID
                                                        ),
                                                        builder
                                                ))
                                                .executes(HistoriaErasCommands::setEra)))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("historicalYear", IntegerArgumentType.integer(
                                                        TimelineState.ANCIENT_START_YEAR,
                                                        TimelineState.CLASSICAL_END_YEAR))
                                                .suggests(YEAR_SUGGESTIONS)
                                                .then(Commands.argument("dayOfYear", IntegerArgumentType.integer(
                                                                0,
                                                                TimelineState.CALENDAR_DAYS_PER_YEAR - 1))
                                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                                List.of("0", "1", "364"), builder))
                                                        .executes(HistoriaErasCommands::setTime))))
                                .then(Commands.literal("setyear")
                                        .then(Commands.argument("historicalYear", IntegerArgumentType.integer(
                                                        TimelineState.ANCIENT_START_YEAR,
                                                        TimelineState.CLASSICAL_END_YEAR))
                                                .suggests(YEAR_SUGGESTIONS)
                                                .executes(HistoriaErasCommands::setYear)))
                                .then(Commands.literal("setdate")
                                        .then(Commands.argument("historicalYear", IntegerArgumentType.integer(
                                                        TimelineState.ANCIENT_START_YEAR,
                                                        TimelineState.CLASSICAL_END_YEAR))
                                                .suggests(YEAR_SUGGESTIONS)
                                                .then(Commands.argument("month", IntegerArgumentType.integer(1, 12))
                                                        .then(Commands.argument("day", IntegerArgumentType.integer(1, 31))
                                                                .executes(HistoriaErasCommands::setCalendarDate))))))));
        // Retain the old command for server scripts; all permissions remain on the shared tree.
        dispatcher.register(Commands.literal("townyeras").redirect(root));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> nodeAdminCommand(
            String operation
    ) {
        return Commands.literal(operation)
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("node", StringArgumentType.word())
                                .suggests(NODE_SUGGESTIONS)
                                .executes(context -> changeNode(context, operation.equals("grant")))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> selfNodeAdminCommand(
            String operation
    ) {
        boolean grant = operation.equals("grantself");
        return Commands.literal(operation)
                .then(Commands.argument("node", StringArgumentType.word())
                        .suggests(NODE_SUGGESTIONS)
                        .executes(context -> changeOwnNode(context, grant)));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> resetAdminCommand(
            String scope
    ) {
        return Commands.literal(scope)
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> resetPlayer(context, scope.equals("all"))));
    }

    private static int showStatus(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ProgressService.PlayerSnapshot snapshot = ProgressService.snapshot(player);
        TimelineService.TimelineSnapshot timeline = snapshot.timeline();
        String classes = snapshot.selectedClasses().isEmpty()
                ? "none"
                : String.join(", ", snapshot.selectedClasses());
        String tracked = snapshot.trackedNodeId().isEmpty() ? "none" : snapshot.trackedNodeId();
        String year = timeline.historicalYear() < 0
                ? Math.abs(timeline.historicalYear()) + " BCE"
                : timeline.historicalYear() > 0 ? timeline.historicalYear() + " CE" : "Year 0";

        source.sendSuccess(() -> Component.literal("HistoriaEras status for " + player.getGameProfile().getName()), false);
        source.sendSuccess(() -> Component.literal("Classes: " + classes + " | job tokens: " + snapshot.jobTokens()), false);
        source.sendSuccess(() -> Component.literal("Completed nodes: " + snapshot.completedNodeIds().size()
                + "/" + technology().nodes().size() + " | tracked: " + tracked), false);
        source.sendSuccess(() -> Component.literal("Timeline: " + timeline.calendarDate() + ", " + year
                + " (day " + (timeline.dayOfYear() + 1) + "/" + TimelineState.CALENDAR_DAYS_PER_YEAR + ")"
                + (timeline.paused() ? " [paused]" : "")), false);
        if (source.hasPermission(AdminModeService.REQUIRED_PERMISSION_LEVEL)) {
            source.sendSuccess(() -> Component.literal("Admin Mode: "
                    + (AdminModeService.isEnabled(player) ? "ON" : "OFF")), false);
        }
        return 1;
    }

    private static int showAdminMode(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean enabled = AdminModeService.isEnabled(player);
        source.sendSuccess(() -> Component.literal("HistoriaEras Admin Mode is " + (enabled ? "ON" : "OFF") + "."), false);
        return enabled ? 1 : 0;
    }

    private static int toggleAdminMode(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return setAdminMode(source, !AdminModeService.isEnabled(player));
    }

    private static int setAdminMode(CommandSourceStack source, boolean enabled) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!AdminModeService.setEnabled(player, enabled)) {
            source.sendFailure(Component.literal("You must be a server operator to use HistoriaEras Admin Mode."));
            return 0;
        }
        sync(player);
        source.sendSuccess(() -> Component.literal("HistoriaEras Admin Mode " + (enabled ? "enabled" : "disabled")
                + "." + (enabled ? " Progression restrictions are bypassed while it is active." : " Normal progression restrictions are active.")), true);
        return 1;
    }

    private static int websiteStatus(CommandSourceStack source) {
        String revision = TechnologyManager.revision();
        source.sendSuccess(() -> Component.literal("HistoriaEras website sync: "
                + (WebsiteTechnologySync.enabled() ? "enabled" : "disabled")
                + " | graph " + revision.substring(0, Math.min(12, revision.length()))
                + " | " + technology().nodes().size() + " nodes"), false);
        return 1;
    }

    private static int websitePublish(CommandSourceStack source) {
        if (!WebsiteTechnologySync.enabled()) {
            source.sendFailure(Component.literal("HistoriaEras website sync is disabled or has no shared secret."));
            return 0;
        }
        WebsiteTechnologySync.requestPublish();
        source.sendSuccess(() -> Component.literal("HistoriaEras queued a fresh website technology snapshot."), true);
        return 1;
    }

    private static int chooseClasses(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ProgressService.ClassChangeResult result = ProgressService.selectClasses(
                player,
                StringArgumentType.getString(context, "first"),
                StringArgumentType.getString(context, "second")
        );
        return reportClassChange(context.getSource(), player, result, "Classes selected");
    }

    private static int swapClass(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ProgressService.ClassChangeResult result = ProgressService.swapClass(
                player,
                StringArgumentType.getString(context, "old"),
                StringArgumentType.getString(context, "new")
        );
        return reportClassChange(context.getSource(), player, result, "Class swapped");
    }

    private static int reportClassChange(
            CommandSourceStack source,
            ServerPlayer player,
            ProgressService.ClassChangeResult result,
            String successMessage
    ) {
        if (!result.successful()) {
            source.sendFailure(Component.literal(classFailure(result.status())));
            return 0;
        }
        sync(player);
        source.sendSuccess(() -> Component.literal(successMessage + ": "
                + String.join(", ", result.selectedClasses())
                + " (job tokens: " + result.remainingJobTokens() + ")"), false);
        return 1;
    }

    private static String classFailure(ProgressService.ClassChangeStatus status) {
        return switch (status) {
            case UNKNOWN_CLASS -> "Unknown or unavailable class.";
            case DUPLICATE_CLASS -> "Choose two different classes.";
            case ALREADY_SELECTED -> "That class is already selected.";
            case ALREADY_CHOSEN -> "Initial classes have already been chosen; use class swap.";
            case CLASS_LIMIT_REACHED -> "The two-class limit has already been reached.";
            case OLD_CLASS_NOT_SELECTED -> "The class being replaced is not selected.";
            case NO_JOB_TOKEN -> "A job token is required to swap classes.";
            case SUCCESS -> "Class change succeeded.";
        };
    }

    private static int trackNode(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String nodeId = StringArgumentType.getString(context, "node");
        if (technology().node(nodeId) == null) {
            context.getSource().sendFailure(Component.literal("Unknown technology node: " + nodeId));
            return 0;
        }
        if (!ProgressService.trackNode(player, nodeId)) {
            context.getSource().sendFailure(Component.literal(
                    "That technology node is not available to either selected class."));
            return 0;
        }
        sync(player);
        context.getSource().sendSuccess(() -> Component.literal("Now tracking technology node: " + nodeId), false);
        return 1;
    }

    private static int untrackNode(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ProgressService.trackNode(player, "");
        sync(player);
        source.sendSuccess(() -> Component.literal("Stopped tracking a technology node."), false);
        return 1;
    }

    private static int changeNode(CommandContext<CommandSourceStack> context, boolean grant)
            throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        String nodeId = StringArgumentType.getString(context, "node");
        if (technology().node(nodeId) == null) {
            context.getSource().sendFailure(Component.literal("Unknown technology node: " + nodeId));
            return 0;
        }

        if (grant) {
            boolean changed = ProgressService.adminGrantNode(target, nodeId);
            sync(target);
            String message = changed ? "Granted " + nodeId + " to " : nodeId + " was already granted to ";
            context.getSource().sendSuccess(() -> Component.literal(message + target.getGameProfile().getName()), true);
            return 1;
        }

        Set<String> revoked = ProgressService.adminRevokeNode(target, nodeId);
        sync(target);
        if (revoked.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal(nodeId + " was not granted to "
                    + target.getGameProfile().getName()), false);
        } else {
            context.getSource().sendSuccess(() -> Component.literal("Revoked " + revoked.size()
                    + " node(s) from " + target.getGameProfile().getName() + ": " + String.join(", ", revoked)), true);
        }
        return 1;
    }

    private static int changeOwnNode(CommandContext<CommandSourceStack> context, boolean grant)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String nodeId = StringArgumentType.getString(context, "node");
        if (technology().node(nodeId) == null) {
            context.getSource().sendFailure(Component.literal("Unknown technology node: " + nodeId));
            return 0;
        }
        if (grant) {
            boolean changed = ProgressService.adminGrantNode(player, nodeId);
            sync(player);
            context.getSource().sendSuccess(() -> Component.literal(changed
                    ? "Granted " + nodeId + " to you."
                    : nodeId + " was already granted to you."), false);
        } else {
            Set<String> revoked = ProgressService.adminRevokeNode(player, nodeId);
            sync(player);
            context.getSource().sendSuccess(() -> Component.literal(revoked.isEmpty()
                    ? nodeId + " was not granted to you."
                    : "Revoked " + revoked.size() + " node(s): " + String.join(", ", revoked)), false);
        }
        return 1;
    }

    private static int adminSetClasses(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        ProgressService.ClassChangeResult result = ProgressService.adminSetClasses(
                target,
                StringArgumentType.getString(context, "first"),
                StringArgumentType.getString(context, "second")
        );
        if (!result.successful()) {
            context.getSource().sendFailure(Component.literal(classFailure(result.status())));
            return 0;
        }
        sync(target);
        context.getSource().sendSuccess(() -> Component.literal("Set " + target.getGameProfile().getName()
                + "'s classes to " + String.join(", ", result.selectedClasses()) + "."), true);
        return 1;
    }

    private static int adminClearClasses(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        ProgressService.adminClearClasses(target);
        sync(target);
        context.getSource().sendSuccess(() -> Component.literal("Cleared classes for "
                + target.getGameProfile().getName() + ". Their earned node history was retained."), true);
        return 1;
    }

    private static int resetPlayer(CommandContext<CommandSourceStack> context, boolean everything)
            throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        if (everything) {
            ProgressService.adminResetEverything(target);
        } else {
            ProgressService.adminResetProgress(target);
        }
        sync(target);
        String scope = everything ? "all HistoriaEras state" : "HistoriaEras progress";
        context.getSource().sendSuccess(() -> Component.literal("Reset " + scope + " for "
                + target.getGameProfile().getName()), true);
        return 1;
    }

    private static int addTokens(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        int amount = IntegerArgumentType.getInteger(context, "amount");
        int total = ProgressService.addJobTokens(target, amount);
        sync(target);
        context.getSource().sendSuccess(() -> Component.literal("Added " + amount + " job token(s) to "
                + target.getGameProfile().getName() + " (total: " + total + ")"), true);
        return 1;
    }

    private static int setTokens(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        int amount = IntegerArgumentType.getInteger(context, "amount");
        int current = ProgressService.jobTokens(target);
        int total = ProgressService.addJobTokens(target, amount - current);
        sync(target);
        context.getSource().sendSuccess(() -> Component.literal("Set " + target.getGameProfile().getName()
                + "'s job tokens to " + total + "."), true);
        return 1;
    }

    private static int setTimePaused(CommandSourceStack source, boolean paused) {
        TimelineService.setPaused(source.getServer(), paused);
        syncAll(source);
        if (!paused && TimelineService.snapshot(source.getServer()).paused()) {
            source.sendFailure(Component.literal("The timeline cannot resume because the current era is complete."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("HistoriaEras timeline " + (paused ? "paused." : "resumed.")), true);
        return 1;
    }

    private static int resetTime(CommandSourceStack source) {
        TimelineService.resetAncient(source.getServer());
        syncAll(source);
        source.sendSuccess(() -> Component.literal("HistoriaEras timeline reset to the start of the Ancient Era."), true);
        return 1;
    }

    private static int advanceEra(CommandSourceStack source) {
        if (!TimelineService.advanceEra(source.getServer())) {
            source.sendFailure(Component.literal("The Ancient Era must be complete before it can advance to Classical."));
            return 0;
        }
        syncAll(source);
        source.sendSuccess(() -> Component.literal("HistoriaEras advanced to the Classical Era."), true);
        return 1;
    }

    private static int setEra(CommandContext<CommandSourceStack> context) {
        String eraId = StringArgumentType.getString(context, "era").toLowerCase(Locale.ROOT);
        if (!TimelineService.startEra(context.getSource().getServer(), eraId)) {
            context.getSource().sendFailure(Component.literal("Supported playable eras are ancient and classical."));
            return 0;
        }
        syncAll(context.getSource());
        context.getSource().sendSuccess(() -> Component.literal("HistoriaEras started the " + eraId + " era."), true);
        return 1;
    }

    private static int setTime(CommandContext<CommandSourceStack> context) {
        int historicalYear = IntegerArgumentType.getInteger(context, "historicalYear");
        int dayOfYear = IntegerArgumentType.getInteger(context, "dayOfYear");
        if (!TimelineService.setDate(context.getSource().getServer(), historicalYear, dayOfYear)) {
            TimelineService.TimelineSnapshot current = TimelineService.snapshot(context.getSource().getServer());
            context.getSource().sendFailure(Component.literal("Invalid date for the " + current.eraName()
                    + ". Use 100-year increments within that era and dayOfYear 0-"
                    + (TimelineState.CALENDAR_DAYS_PER_YEAR - 1) + "."));
            return 0;
        }
        syncAll(context.getSource());
        context.getSource().sendSuccess(() -> Component.literal("HistoriaEras date set to historical year "
                + historicalYear + ", dayOfYear " + dayOfYear + "."), true);
        return 1;
    }

    private static int setYear(CommandContext<CommandSourceStack> context) {
        int historicalYear = IntegerArgumentType.getInteger(context, "historicalYear");
        int dayOfYear = TimelineService.snapshot(context.getSource().getServer()).dayOfYear();
        return applyDate(context.getSource(), historicalYear, dayOfYear,
                "historical year " + historicalYear + " while preserving the current calendar day");
    }

    private static int setCalendarDate(CommandContext<CommandSourceStack> context) {
        int historicalYear = IntegerArgumentType.getInteger(context, "historicalYear");
        int month = IntegerArgumentType.getInteger(context, "month");
        int day = IntegerArgumentType.getInteger(context, "day");
        int[] monthLengths = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        if (day > monthLengths[month - 1]) {
            context.getSource().sendFailure(Component.literal("That day does not exist in the HistoriaEras 365-day calendar."));
            return 0;
        }
        int dayOfYear = day - 1;
        for (int index = 0; index < month - 1; index++) {
            dayOfYear += monthLengths[index];
        }
        return applyDate(context.getSource(), historicalYear, dayOfYear,
                "date to " + month + "/" + day + "/" + historicalYear);
    }

    private static int applyDate(CommandSourceStack source, int historicalYear, int dayOfYear, String description) {
        if (!TimelineService.setDate(source.getServer(), historicalYear, dayOfYear)) {
            TimelineService.TimelineSnapshot current = TimelineService.snapshot(source.getServer());
            source.sendFailure(Component.literal("Invalid date for the " + current.eraName()
                    + ". Use a valid 100-year step within the current era."));
            return 0;
        }
        syncAll(source);
        source.sendSuccess(() -> Component.literal("HistoriaEras set the " + description + "."), true);
        return 1;
    }

    private static Stream<String> selectableClassIds() {
        return technology().classes().values().stream()
                .filter(TechClass::enabled)
                .map(TechClass::id)
                .filter(id -> !id.toLowerCase(Locale.ROOT).equals("universal"));
    }

    private static TechRegistry technology() {
        return TechnologyManager.current();
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>
            suggestSelectedClasses(CommandContext<CommandSourceStack> context,
                                   com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        ServerPlayer player = context.getSource().getPlayer();
        return SharedSuggestionProvider.suggest(
                player == null ? selectableClassIds() : ProgressService.snapshot(player).selectedClasses().stream(),
                builder
        );
    }

    /** Public synchronization hook for command changes and other server integrations. */
    public static void sync(ServerPlayer player) {
        ServerSync.sync(player);
    }

    private static void syncAll(CommandSourceStack source) {
        source.getServer().getPlayerList().getPlayers().forEach(HistoriaErasCommands::sync);
    }
}
