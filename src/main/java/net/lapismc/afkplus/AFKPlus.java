/*
 * Copyright 2021 Benjamin Martin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.lapismc.afkplus;

import net.lapismc.afkplus.api.AFKPlusAPI;
import net.lapismc.afkplus.api.AFKPlusPlayerAPI;
import net.lapismc.afkplus.commands.AFK;
import net.lapismc.afkplus.commands.AFKPlusCmd;
import net.lapismc.afkplus.playerdata.AFKPlusPlayer;
import net.lapismc.lapiscore.LapisCoreConfiguration;
import net.lapismc.lapiscore.LapisCorePlugin;
import net.lapismc.lapiscore.utils.LapisCoreFileWatcher;
import net.lapismc.lapiscore.utils.LapisUpdater;
import net.lapismc.lapiscore.utils.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.ocpsoft.prettytime.Duration;
import org.ocpsoft.prettytime.PrettyTime;
import org.ocpsoft.prettytime.units.JustNow;
import org.ocpsoft.prettytime.units.Millisecond;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class AFKPlus extends LapisCorePlugin {

    public PrettyTime prettyTime;
    public LapisUpdater updater;
    private LapisCoreFileWatcher fileWatcher;
    private final HashMap<UUID, AFKPlusPlayer> players = new HashMap<>();
    private AFKPlusListeners listeners;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        registerConfiguration(new LapisCoreConfiguration(this, 10, 2));
        registerPermissions(new AFKPlusPermissions(this));
        fileWatcher = new LapisCoreFileWatcher(this);
        Locale loc = new Locale(config.getMessage("PrettyTimeLocale"));
        prettyTime = new PrettyTime(loc);
        prettyTime.removeUnit(JustNow.class);
        prettyTime.removeUnit(Millisecond.class);
        new AFK(this);
        new AFKPlusCmd(this);
        listeners = new AFKPlusListeners(this);
        new AFKPlusAPI(this);
        new AFKPlusPlayerAPI(this);
        new Metrics(this);
        tasks.addTask(Bukkit.getScheduler().runTaskTimerAsynchronously(this, getRepeatingTasks(), 20, 20));
        getLogger().info(getName() + " v." + getDescription().getVersion() + " has been enabled!");
    }

    @Override
    public void onDisable() {
        fileWatcher.stop();
        //Stop the repeating tasks
        tasks.stopALlTasks();
        //Also stop the AFK Machine detection task
        listeners.getAfkMachineDetectionTask().cancel();
        //Safely handle the stopping of AFKPlus in regard to player data
        disposeOfPlayers();
        getLogger().info(getName() + " has been disabled!");
    }

    public AFKPlusPlayer getPlayer(UUID uuid) {
        if (!players.containsKey(uuid)) {
            players.put(uuid, new AFKPlusPlayer(this, uuid));
        }
        return players.get(uuid);
    }

    public AFKPlusPlayer getPlayer(OfflinePlayer op) {
        return getPlayer(op.getUniqueId());
    }

    private void disposeOfPlayers() {
        //Stop all AFK sessions for the sake of AFK time statistics
        for (AFKPlusPlayer p : players.values()) {
            p.forceStopAFK();
        }
    }

    private Runnable getRepeatingTasks() {
        return () -> {
            for (AFKPlusPlayer player : players.values()) {
                player.getRepeatingTask().run();
            }
        };
    }

    public List<Duration> reduceDurationList(List<Duration> durationList) {
        while (durationList.size() > 2) {
            Duration smallest = null;
            for (Duration current : durationList) {
                if (smallest == null || smallest.getUnit().getMillisPerUnit() > current.getUnit().getMillisPerUnit()) {
                    smallest = current;
                }
            }
            durationList.remove(smallest);
        }
        return durationList;
    }

}
