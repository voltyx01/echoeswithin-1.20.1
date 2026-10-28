package com.voltyx.efw.gore.pack;

import com.voltyx.efw.gore.pack.BloodDamageClassifier;
import com.voltyx.efw.gore.pack.BloodDamageKind;
import com.voltyx.efw.gore.pack.PacketBloodDamage;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Server: every damage a living (not a player) takes goes to the clients around, which spill the blood. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.voltyx.efw.EFW.MOD_ID)
public class BloodDamageSyncHandler {
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity living = event.getEntity();
        if (living == null || living.level().isClientSide()) {
            return;
        }
        if (living instanceof Player) {
            return;
        }
        float incomingDamage = Math.max(0.0f, event.getAmount());
        if (incomingDamage <= 1.0E-4f) {
            return;
        }
        float maxHealth = Math.max(1.0f, living.getMaxHealth());
        float preHealth = Mth.clamp((float)living.getHealth(), (float)0.0f, (float)maxHealth);
        float postHealth = Mth.clamp((float)(preHealth - incomingDamage), (float)0.0f, (float)maxHealth);
        if (postHealth + 1.0E-4f >= preHealth) {
            return;
        }
        BloodDamageKind kind = BloodDamageClassifier.classify(event.getSource());
        DirectionalSourceData directional = BloodDamageSyncHandler.resolveDirectionalSource(event.getSource(), living);
        com.voltyx.efw.network.EfwNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY.with(() -> living), directional != null ? new PacketBloodDamage(living.getId(), kind, preHealth, postHealth, maxHealth, incomingDamage, directional.sourcePos, directional.sourceLookDir, directional.sourceMotion) : new PacketBloodDamage(living.getId(), kind, preHealth, postHealth, maxHealth, incomingDamage));
    }

    @Nullable
    private static DirectionalSourceData resolveDirectionalSource(@Nullable DamageSource source, @Nonnull LivingEntity target) {
        if (source == null) {
            return null;
        }
        Entity immediate = source.getDirectEntity();
        Entity trueSource = source.getEntity();
        Vec3 sourcePos = null;
        Vec3 sourceLookDir = null;
        Vec3 sourceMotion = new Vec3(0.0, 0.0, 0.0);
        if (immediate != null) {
            sourcePos = BloodDamageSyncHandler.getImpactReferencePosition(immediate);
            Vec3 immediateMotion = BloodDamageSyncHandler.getEntityMotion(immediate);
            if (!(immediate instanceof LivingEntity)) {
                Vec3 motionDir = BloodDamageSyncHandler.normalizeOrNull(immediateMotion);
                if (motionDir != null) {
                    sourceLookDir = motionDir;
                }
            } else {
                Vec3 livingLook = BloodDamageSyncHandler.normalizeOrNull(((LivingEntity)immediate).getLookAngle());
                if (livingLook != null) {
                    sourceLookDir = livingLook;
                }
                sourceMotion = immediateMotion;
            }
        }
        if (trueSource != null) {
            if (sourcePos == null) {
                sourcePos = BloodDamageSyncHandler.getImpactReferencePosition(trueSource);
            }
            if (sourceLookDir == null) {
                Vec3 trueMotionDir;
                Vec3 livingLook;
                if (trueSource instanceof LivingEntity && (livingLook = BloodDamageSyncHandler.normalizeOrNull(((LivingEntity)trueSource).getLookAngle())) != null) {
                    sourceLookDir = livingLook;
                }
                if (sourceLookDir == null && (trueMotionDir = BloodDamageSyncHandler.normalizeOrNull(BloodDamageSyncHandler.getEntityMotion(trueSource))) != null) {
                    sourceLookDir = trueMotionDir;
                }
            }
            if (sourceMotion.lengthSqr() <= 1.0E-6) {
                sourceMotion = BloodDamageSyncHandler.getEntityMotion(trueSource);
            }
        }
        if (sourceLookDir == null && sourcePos != null) {
            sourceLookDir = BloodDamageSyncHandler.normalizeOrNull(BloodDamageSyncHandler.getTargetCenter(target).subtract(sourcePos));
        }
        if (sourcePos == null || sourceLookDir == null) {
            return null;
        }
        return new DirectionalSourceData(sourcePos, sourceLookDir, sourceMotion);
    }

    @Nonnull
    private static Vec3 getImpactReferencePosition(@Nonnull Entity entity) {
        double y = entity.getY() + (entity instanceof LivingEntity ? (double)((LivingEntity)entity).getEyeHeight() : (double)entity.getBbHeight() * 0.5);
        return new Vec3(entity.getX(), y, entity.getZ());
    }

    @Nonnull
    private static Vec3 getEntityMotion(@Nonnull Entity entity) {
        return entity.getDeltaMovement();
    }

    @Nonnull
    private static Vec3 getTargetCenter(@Nonnull LivingEntity target) {
        AABB bb = target.getBoundingBox();
        if (bb != null) {
            return new Vec3((bb.minX + bb.maxX) * 0.5, (bb.minY + bb.maxY) * 0.5, (bb.minZ + bb.maxZ) * 0.5);
        }
        return new Vec3(target.getX(), target.getY() + (double)target.getBbHeight() * 0.5, target.getZ());
    }

    @Nullable
    private static Vec3 normalizeOrNull(@Nullable Vec3 vec) {
        if (vec == null) {
            return null;
        }
        double lenSq = vec.lengthSqr();
        if (lenSq <= 1.0E-6) {
            return null;
        }
        return vec.scale(1.0 / Math.sqrt(lenSq));
    }

    private static final class DirectionalSourceData {
        final Vec3 sourcePos;
        final Vec3 sourceLookDir;
        final Vec3 sourceMotion;

        DirectionalSourceData(Vec3 sourcePos, Vec3 sourceLookDir, Vec3 sourceMotion) {
            this.sourcePos = sourcePos;
            this.sourceLookDir = sourceLookDir;
            this.sourceMotion = sourceMotion;
        }
    }
}

