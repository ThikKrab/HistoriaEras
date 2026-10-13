package com.historiamc.townyeras.client.data;

import net.minecraft.client.player.LocalPlayer;

public interface DashboardDataProvider {
    DashboardSnapshot snapshot(LocalPlayer player);
}
