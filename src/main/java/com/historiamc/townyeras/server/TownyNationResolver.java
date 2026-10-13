package com.historiamc.townyeras.server;

import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.UUID;

/**
 * Read-only soft bridge to Towny on hybrid NeoForge/Bukkit servers.
 *
 * <p>No Bukkit or Towny classes are linked at compile time, so ordinary NeoForge
 * servers continue to load normally when neither API exists.</p>
 */
public final class TownyNationResolver {
    private TownyNationResolver() {
    }

    public static String nationName(ServerPlayer player) {
        if (player == null) {
            return "No nation";
        }

        try {
            Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
            Object pluginManager = bukkitClass.getMethod("getPluginManager").invoke(null);
            Object townyPlugin = pluginManager.getClass().getMethod("getPlugin", String.class)
                    .invoke(pluginManager, "Towny");
            if (townyPlugin == null || !Boolean.TRUE.equals(invokeNoArg(townyPlugin, "isEnabled"))) {
                return "Towny unavailable";
            }

            ClassLoader townyLoader = townyPlugin.getClass().getClassLoader();
            Class<?> apiClass = Class.forName("com.palmergames.bukkit.towny.TownyAPI", true, townyLoader);
            Object api = apiClass.getMethod("getInstance").invoke(null);
            UUID playerId = player.getUUID();
            Object bukkitPlayer = bukkitClass.getMethod("getPlayer", UUID.class).invoke(null, playerId);

            Object resident = invokeCompatible(api, "getResident", playerId);
            if (resident == null && bukkitPlayer != null) {
                resident = invokeCompatible(api, "getResident", bukkitPlayer);
            }

            Object nation = nationFromHolder(resident);
            if (nation == null && bukkitPlayer != null) {
                nation = invokeCompatible(api, "getNation", bukkitPlayer);
            }
            if (nation == null && bukkitPlayer != null) {
                Object town = invokeCompatible(api, "getTown", bukkitPlayer);
                nation = nationFromHolder(town);
            }
            if (nation == null) {
                return "No nation";
            }

            Object name = invokeNoArg(nation, "getName");
            String nationName = name == null ? "" : String.valueOf(name).trim();
            return nationName.isEmpty() ? "No nation" : nationName;
        } catch (Throwable ignored) {
            return "Towny unavailable";
        }
    }

    private static Object nationFromHolder(Object holder) {
        if (holder == null) {
            return null;
        }
        Object nation = invokeNoArg(holder, "getNationOrNull");
        return nation == null ? invokeNoArg(holder, "getNation") : nation;
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object invokeCompatible(Object target, String methodName, Object argument) {
        if (target == null || argument == null) {
            return null;
        }
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != 1
                    || Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (!method.getParameterTypes()[0].isAssignableFrom(argument.getClass())) {
                continue;
            }
            try {
                return method.invoke(target, argument);
            } catch (Throwable ignored) {
                // Try another overload or fallback path.
            }
        }
        return null;
    }
}
