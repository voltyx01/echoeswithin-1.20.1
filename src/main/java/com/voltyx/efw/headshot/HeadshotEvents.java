package com.voltyx.efw.headshot;

import com.voltyx.efw.EFW;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Headshots for every projectile (port of HeadshotDamageHandler): the bullet's path is traced through the head box; a hit
 * multiplies the damage. Hits on players also remember the body part for Legendary Survival Overhaul. TACZ bullets are judged
 * in {@link TaczHeadshots} and only get their body part here.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID)
public final class HeadshotEvents {
    private static final Set<DamageSource> HEADSHOT_SOURCES = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Map<DamageSource, BodyPart> HIT_PARTS = Collections.synchronizedMap(new WeakHashMap<>());
    /** TACZ bullets already judged in the gun event: true = headshot. */
    static final Map<Entity, Boolean> TACZ_RESULTS = Collections.synchronizedMap(new WeakHashMap<>());

    private HeadshotEvents() {
    }

    public static boolean isHeadshot(DamageSource source) {
        return source != null && HEADSHOT_SOURCES.contains(source);
    }

    /** Body part the hit landed on; null when it was not determined. */
    public static BodyPart getHitPart(DamageSource source) {
        return source == null ? null : HIT_PARTS.get(source);
    }

    private static void markHeadshot(DamageSource source) {
        HEADSHOT_SOURCES.add(source);
        HIT_PARTS.put(source, BodyPart.HEAD);
    }

    /** Does the projectile's path of this tick go through the head (1.12 checks, ray lengthened 1.5x)? */
    public static boolean hitsHead(Entity projectile, AABB headBox) {
        Vec3 pos = projectile.position();
        Vec3 motion = projectile.getDeltaMovement();
        Vec3 start = pos.subtract(motion);
        Vec3 end = pos.add(motion.scale(1.5D));
        return headBox.clip(start, end).isPresent() || headBox.intersects(projectile.getBoundingBox()) || headBox.contains(pos);
    }

    /** Body part by the height and side of the hit; the head comes only from the head box, the upper body is the chest. */
    static BodyPart resolveBodyPart(Entity projectile, Player target) {
        double relHeight = (projectile.getY() - target.getY()) / target.getBbHeight();
        double yaw = Math.toRadians(target.getYRot());
        // the player's right side in world coordinates; positive = hit on the right
        double rightX = -Math.cos(yaw);
        double rightZ = -Math.sin(yaw);
        double lateral = (projectile.getX() - target.getX()) * rightX + (projectile.getZ() - target.getZ()) * rightZ;
        boolean right = lateral >= 0.0D;
        if (relHeight >= 0.45D) {
            if (Math.abs(lateral) > 0.3D && relHeight < 0.85D) {
                return right ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM;
            }
            return BodyPart.CHEST;
        }
        if (relHeight >= 0.12D) {
            return right ? BodyPart.RIGHT_LEG : BodyPart.LEFT_LEG;
        }
        return right ? BodyPart.RIGHT_FOOT : BodyPart.LEFT_FOOT;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();
        Entity projectile = source.getDirectEntity();
        if (projectile == null || target.level().isClientSide || source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)
                || source.is(DamageTypeTags.IS_EXPLOSION)) {
            return;
        }
        Boolean taczHeadshot = TACZ_RESULTS.get(projectile);
        if (taczHeadshot != null) {
            // TACZ already applied the multiplier; only remember where it landed
            if (taczHeadshot) {
                markHeadshot(source);
            } else if (target instanceof Player player) {
                HIT_PARTS.put(source, resolveBodyPart(projectile, player));
            }
            return;
        }
        if (hitsHead(projectile, HeadshotManager.getHeadBox(target, source.getEntity()))) {
            event.setAmount(event.getAmount() * HeadshotConfig.DAMAGE_MULTIPLIER.get().floatValue());
            markHeadshot(source);
        } else if (target instanceof Player player) {
            HIT_PARTS.put(source, resolveBodyPart(projectile, player));
        }
    }

    @Mod.EventBusSubscriber(modid = EFW.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ConfigEvents {
        private ConfigEvents() {
        }

        @SubscribeEvent
        public static void onLoad(ModConfigEvent.Loading e) {
            if (e.getConfig().getSpec() == HeadshotConfig.SPEC) {
                HeadshotManager.reloadConfig();
            }
        }

        @SubscribeEvent
        public static void onReload(ModConfigEvent.Reloading e) {
            if (e.getConfig().getSpec() == HeadshotConfig.SPEC) {
                HeadshotManager.reloadConfig();
            }
        }
    }
}
