package net.glowstone.io.nbt;

import net.glowstone.GlowServer;
import net.glowstone.GlowWorld;
import net.glowstone.entity.GlowPlayer;
import net.glowstone.io.WorldMetadataService;
import net.glowstone.io.entity.EntityStoreLookupService;
import net.glowstone.util.nbt.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.io.*;
import java.util.*;
import java.util.logging.Level;


public class NbtWorldMetadataService implements WorldMetadataService {
    private final GlowWorld world;
    private final File dir;
    private final GlowServer server;

    private CompoundTag unknownTags;

    public NbtWorldMetadataService(GlowWorld world, File dir) {
        this.world = world;
        if (!dir.exists())
            dir.mkdirs();
        this.dir = dir;
        server = (GlowServer) Bukkit.getServer();
    }

    public WorldFinalValues readWorldData() throws IOException {
        // determine UUID of world
        UUID uid = null;
        File uuidFile = new File(dir, "uid.dat");
        if (uuidFile.exists()) {
            try (DataInputStream in = new DataInputStream(new FileInputStream(uuidFile))) {
                uid = new UUID(in.readLong(), in.readLong());
            } catch (IOException e) {
                handleWorldException("uid.dat", e);
            }
        }
        if (uid == null) {
            uid = UUID.randomUUID();
        }

        // read in world information
        CompoundTag level = new CompoundTag();
        File levelFile = new File(dir, "level.dat");
        if (levelFile.exists()) {
            try (NBTInputStream in = new NBTInputStream(new FileInputStream(levelFile))) {
                level = in.readCompound();
            } catch (IOException e) {
                handleWorldException("level.dat", e);
            }
        }

        // seed
        long seed = 0L;
        if (level.isLong("RandomSeed")) {
            seed = level.getLong("RandomSeed");
            level.remove("RandomSeed");
        }

        // time of day and weather status
        if (level.isByte("thundering")) {
            world.setThundering(level.getBool("thundering"));
            level.remove("thundering");
        }
        if (level.isByte("raining")) {
            world.setStorm(level.getBool("raining"));
            level.remove("raining");
        }
        if (level.isInt("thunderTime")) {
            world.setThunderDuration(level.getInt("thunderTime"));
            level.remove("thunderTime");
        }
        if (level.isInt("rainTime")) {
            world.setWeatherDuration(level.getInt("rainTime"));
            level.remove("rainTime");
        }
        if (level.isLong("Time")) {
            world.setFullTime(level.getLong("Time"));
            level.remove("Time");
        }
        if (level.isLong("DayTime")) {
            world.setTime(level.getLong("DayTime"));
            level.remove("DayTime");
        }

        // spawn position
        if (level.isInt("SpawnX") && level.isInt("SpawnY") && level.isInt("SpawnZ")) {
            world.setSpawnLocation(level.getInt("SpawnX"), level.getInt("SpawnY"), level.getInt("SpawnZ"));
            level.remove("SpawnX");
            level.remove("SpawnY");
            level.remove("SpawnZ");
        }

        // game rules
        if (level.isCompound("GameRules")) {
            CompoundTag gameRules = level.getCompound("GameRules");
            for (String key : gameRules.getValue().keySet()) {
                if (gameRules.isString(key)) {
                    world.setGameRuleValue(key, gameRules.getString(key));
                }
            }
            level.remove("GameRules");
        }

        // save unknown tags for later
        unknownTags = level;
        for (Map.Entry<String, Tag> entry : unknownTags.getValue().entrySet()) {
            server.getLogger().info("Unknown world tag: " + entry.getKey() + " = " + entry.getValue());
        }

        return new WorldFinalValues(seed, uid);
    }

    private void handleWorldException(String file, IOException e) {
        server.unloadWorld(world, false);
        server.getLogger().log(Level.SEVERE, "Unable to access " + file + " for world " + world.getName(), e);
    }

    public void writeWorldData() throws IOException {
        File uuidFile = new File(dir, "uid.dat");
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(uuidFile))) {
            UUID uuid = world.getUID();
            out.writeLong(uuid.getMostSignificantBits());
            out.writeLong(uuid.getLeastSignificantBits());
        }

        // start with unknown tags from reading
        CompoundTag out = new CompoundTag();
        if (unknownTags != null) {
            out.getValue().putAll(unknownTags.getValue());
        }

        // Seed and core information
        // note: most up-to-date version is 19133
        out.putString("LevelName", world.getName());
        out.putInt("version", 19132);
        out.putLong("LastPlayed", Calendar.getInstance().getTimeInMillis());
        out.putLong("RandomSeed", world.getSeed());

        // Normal level data
        out.putLong("Time", world.getFullTime());
        out.putLong("DayTime", world.getTime());
        out.putBool("thundering", world.isThundering());
        out.putBool("raining", world.hasStorm());
        out.putInt("thunderTime", world.getThunderDuration());
        out.putInt("rainTime", world.getWeatherDuration());

        // Spawn location
        Location loc = world.getSpawnLocation();
        out.putInt("SpawnX", loc.getBlockX());
        out.putInt("SpawnY", loc.getBlockY());
        out.putInt("SpawnZ", loc.getBlockZ());

        // Game rules
        CompoundTag gameRules = new CompoundTag();
        String[] gameRuleKeys = world.getGameRules();
        for (String key : gameRuleKeys) {
            gameRules.putString(key, world.getGameRuleValue(key));
        }
        out.putCompound("GameRules", gameRules);

        // Not sure how to calculate this, so ignoring for now
        out.putLong("SizeOnDisk", 0);

        try (NBTOutputStream nbtOut = new NBTOutputStream(new FileOutputStream(new File(dir, "level.dat")))) {
            nbtOut.writeTag(out);
        } catch (IOException e) {
            handleWorldException("level.dat", e);
        }
    }

    private File playerFile(GlowPlayer player) {
        File playerDir = new File(dir, "playerdata");
        if (!playerDir.isDirectory() && !playerDir.mkdirs()) {
            server.getLogger().warning("Failed to create directory: " + playerDir);
        }
        return new File(playerDir, player.getUniqueId() + ".dat");
    }

    public void readPlayerData(GlowPlayer player) {
        File playerFile = playerFile(player);
        CompoundTag playerTag = new CompoundTag();
        if (playerFile.exists()) {
            try (NBTInputStream in = new NBTInputStream(new FileInputStream(playerFile))) {
                playerTag = in.readCompound();
            } catch (IOException e) {
                player.kickPlayer("Failed to read player data!");
                server.getLogger().log(Level.SEVERE, "Failed to read data for " + player.getName() + ": " + playerFile, e);
            }
        }

        EntityStoreLookupService.find(GlowPlayer.class).load(player, playerTag);
    }

    public void writePlayerData(GlowPlayer player) {
        File playerFile = playerFile(player);
        CompoundTag tag = new CompoundTag();
        EntityStoreLookupService.find(GlowPlayer.class).save(player, tag);
        try (NBTOutputStream out = new NBTOutputStream(new FileOutputStream(playerFile))) {
            out.writeTag(tag);
        } catch (IOException e) {
            player.getSession().disconnect("Failed to save player data!");
            server.getLogger().log(Level.SEVERE, "Failed to write data for " + player.getName() + ": " + playerFile, e);
        }
    }
}
