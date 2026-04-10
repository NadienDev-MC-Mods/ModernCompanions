package com.majorbonghits.moderncompanions.compat.waystones;

import com.majorbonghits.moderncompanions.entity.AbstractHumanCompanionEntity;
import net.blay09.mods.waystones.api.event.WaystoneTeleportEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.lang.reflect.Method;

@EventBusSubscriber
public class Waystones {

    private static Method chunkMapRemoveEntity = null;
    private static Method chunkMapAddEntity    = null;

    static {
        try {
            Class<?> chunkMapClass = Class.forName("net.minecraft.server.level.ChunkMap");
            chunkMapRemoveEntity = chunkMapClass.getDeclaredMethod("removeEntity", net.minecraft.world.entity.Entity.class);
            chunkMapRemoveEntity.setAccessible(true);
            chunkMapAddEntity = chunkMapClass.getDeclaredMethod("addEntity", net.minecraft.world.entity.Entity.class);
            chunkMapAddEntity.setAccessible(true);
        } catch (Exception e) {
            chunkMapRemoveEntity = null;
            chunkMapAddEntity    = null;
        }
    }

    private static final int TELEPORT_ATTEMPTS = 10;
    private static final int TELEPORT_RANGE     = 3;

    @SubscribeEvent
    public static void onWaystoneTeleport(WaystoneTeleportEvent.Post event) {
        event.getTeleportedEntities().stream()
                .filter(entity -> entity instanceof Player)
                .map(entity -> (Player) entity)
                .filter(player -> player.level() instanceof ServerLevel)
                .forEach(player -> {
                    ServerLevel serverLevel = (ServerLevel) player.level();
                    serverLevel.getEntitiesOfClass(AbstractHumanCompanionEntity.class, player.getBoundingBox().inflate(256))
                            .stream()
                            .filter(companion -> player.equals(companion.getOwner()))
                            .filter(AbstractHumanCompanionEntity::isFollowing)
                            .filter(companion -> !companion.isOrderedToSit())
                            .forEach(companion -> teleportCompanionToPlayer(companion, player, serverLevel));
                });
    }

    private static void teleportCompanionToPlayer(AbstractHumanCompanionEntity companion, Player player, ServerLevel serverLevel) {
        if (!tryTeleportCloseToPlayer(companion, player)) {
            companion.teleportTo(player.getX(), player.getY(), player.getZ());
            companion.getNavigation().stop();
        }
        forceRetrackEntity(companion, serverLevel);
    }

    private static boolean tryTeleportCloseToPlayer(AbstractHumanCompanionEntity companion, Player player) {
        BlockPos ownerPos = player.blockPosition();
        for (int attempt = 0; attempt < TELEPORT_ATTEMPTS; attempt++) {
            int dx = randomBetween(companion, -TELEPORT_RANGE, TELEPORT_RANGE);
            int dz = randomBetween(companion, -TELEPORT_RANGE, TELEPORT_RANGE);
            BlockPos targetPos = ownerPos.offset(dx, 0, dz);
            if (isTeleportFriendly(companion, targetPos)) {
                companion.teleportTo(targetPos.getX() + 0.5D, targetPos.getY(), targetPos.getZ() + 0.5D);
                companion.getNavigation().stop();
                return true;
            }
        }
        return false;
    }

    private static boolean isTeleportFriendly(AbstractHumanCompanionEntity companion, BlockPos pos) {
        return companion.level().isEmptyBlock(pos)
                && companion.level().isEmptyBlock(pos.above())
                && companion.level().noCollision(companion, companion.getBoundingBox().move(
                pos.getX() - companion.getX(),
                pos.getY() - companion.getY(),
                pos.getZ() - companion.getZ()));
    }

    private static int randomBetween(AbstractHumanCompanionEntity companion, int min, int max) {
        return companion.getRandom().nextInt(max - min + 1) + min;
    }

    private static void forceRetrackEntity(AbstractHumanCompanionEntity companion, ServerLevel serverLevel) {
        if (chunkMapRemoveEntity == null || chunkMapAddEntity == null) return;
        try {
            var chunkMap = serverLevel.getChunkSource().chunkMap;
            chunkMapRemoveEntity.invoke(chunkMap, companion);
            chunkMapAddEntity.invoke(chunkMap, companion);
        } catch (Exception ignored) {}
    }
}