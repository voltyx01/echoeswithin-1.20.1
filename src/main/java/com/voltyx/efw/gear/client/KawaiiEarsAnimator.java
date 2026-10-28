package com.voltyx.efw.gear.client;

import com.voltyx.efw.client.RollClient;
import com.voltyx.efw.compat.TaczCompat;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

/**
 * What the ears do (port of KawaiiEarsAnimationHandler): they lag behind turns of the head and movement, fold back when the wearer
 * sneaks, sprints, rolls, aims, swims, crawls or gets hurt, lift in a jump, dip on landing, flinch at a shot, twitch now and then at
 * rest and breathe slowly. Angles are offsets in degrees of the two ear bones from their resting pose; the state is advanced once a
 * client tick and mixed between ticks when drawn.
 */
public final class KawaiiEarsAnimator {
    // target rotations of the author's sneak and sneak-move poses (degrees, offsets from the resting pose)
    private static final float SNEAK_EAR2_X = -25.2311f;
    private static final float SNEAK_EAR2_Y = -33.852f;
    private static final float SNEAK_EAR2_Z = 7.7098f;
    private static final float SNEAK_EAR3_X = -31.9642f;
    private static final float SNEAK_EAR3_Y = 35.5497f;
    private static final float SNEAK_EAR3_Z = -12.2912f;
    private static final float SNEAKMOVE_EAR2_X = -55.481f;
    private static final float SNEAKMOVE_EAR2_Y = -24.8891f;
    private static final float SNEAKMOVE_EAR2_Z = 27.2645f;
    private static final float SNEAKMOVE_EAR3_X = -57.0895f;
    private static final float SNEAKMOVE_EAR3_Y = 27.6044f;
    private static final float SNEAKMOVE_EAR3_Z = -30.0239f;

    private static final Map<Player, State> STATES = new WeakHashMap<>();
    private static final Random RNG = new Random();

    private static final class State {
        float prevEar2X, curEar2X, prevEar2Y, curEar2Y, prevEar2Z, curEar2Z;
        float prevEar3X, curEar3X, prevEar3Y, curEar3Y, prevEar3Z, curEar3Z;

        float lastHeadYaw, lastHeadPitch, yawVelocity, pitchVelocity;
        float prevYawInertia, curYawInertia, prevPitchInertia, curPitchInertia;

        float sneakWeight, sneakMoveWeight, sprintWeight, aimWeight, proneWeight, swimWeight, inAirWeight, reloadWeight, rollWeight;

        boolean wasInAir;
        float landingSpring;

        int twitchCooldown = 80 + RNG.nextInt(120);
        int twitchDuration;
        int twitchElapsed;
        int twitchType;

        float shotFlinch;
        float moveInertiaForward, moveInertiaStrafe;

        int idleChanceCooldown;
        boolean wasInActiveState;
        int idleSettleDelay;
    }

    private KawaiiEarsAnimator() {
    }

    private static State state(Player player) {
        return STATES.computeIfAbsent(player, p -> new State());
    }

    /** A shot of this player (from the gun fire event): the ears flinch. */
    public static void shot(Player player) {
        state(player).shotFlinch = 1.0f;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Once a client tick for each player who shows ears. */
    public static void update(Player player) {
        State s = state(player);
        s.prevEar2X = s.curEar2X;
        s.prevEar2Y = s.curEar2Y;
        s.prevEar2Z = s.curEar2Z;
        s.prevEar3X = s.curEar3X;
        s.prevEar3Y = s.curEar3Y;
        s.prevEar3Z = s.curEar3Z;
        s.prevYawInertia = s.curYawInertia;
        s.prevPitchInertia = s.curPitchInertia;
        if (s.idleChanceCooldown > 0) {
            s.idleChanceCooldown--;
        }

        // 1. inertia of turns of the head
        float headYaw = player.yHeadRot;
        float headPitch = player.getXRot();
        float yawDelta = Mth.wrapDegrees(headYaw - s.lastHeadYaw);
        float pitchDelta = headPitch - s.lastHeadPitch;
        s.lastHeadYaw = headYaw;
        s.lastHeadPitch = headPitch;
        if (Math.abs(yawDelta) < 0.18f) {
            yawDelta = 0.0f;
        }
        if (Math.abs(pitchDelta) < 0.18f) {
            pitchDelta = 0.0f;
        }
        yawDelta = Mth.clamp(yawDelta, -30.0f, 30.0f);
        pitchDelta = Mth.clamp(pitchDelta, -25.0f, 25.0f);
        s.yawVelocity += (yawDelta * 0.85f - s.yawVelocity) * 0.40f;
        s.pitchVelocity += (pitchDelta * 0.75f - s.pitchVelocity) * 0.40f;
        s.curYawInertia += ((-s.yawVelocity) - s.curYawInertia) * 0.45f;
        s.curPitchInertia += ((-s.pitchVelocity) - s.curPitchInertia) * 0.45f;
        s.yawVelocity *= 0.70f;
        s.pitchVelocity *= 0.70f;

        // 2. inertia of movement, in the player's own forward and sideways directions
        double dx = player.getX() - player.xo;
        double dz = player.getZ() - player.zo;
        float yawRad = (float) Math.toRadians(player.getYRot());
        float cosY = Mth.cos(yawRad);
        float sinY = Mth.sin(yawRad);
        float forwardVel = (float) (-dx * sinY + dz * cosY);
        float strafeVel = (float) (dx * cosY + dz * sinY);
        s.moveInertiaForward += (-forwardVel * 36.0f - s.moveInertiaForward) * 0.40f;
        s.moveInertiaStrafe += (-strafeVel * 28.0f - s.moveInertiaStrafe) * 0.40f;

        // 3. what the player is doing
        boolean isMoving = Math.abs(dx) > 0.003 || Math.abs(dz) > 0.003;
        boolean isSneaking = player.isShiftKeyDown() || player.isCrouching();
        boolean isSprinting = player.isSprinting() && isMoving;
        boolean isInWater = player.isInWater();
        boolean isFlying = player.getAbilities().flying;
        boolean isProne = (player.isVisuallyCrawling() || player.getBbHeight() < 1.0f) && !isInWater;
        boolean isRolling = RollClient.isRolling(player);

        boolean isAiming = TaczCompat.isAiming(player);
        boolean isReloading = TaczCompat.isReloading(player);
        if (!isAiming && player.isUsingItem()) {
            ItemStack active = player.getUseItem();
            if (active.getItem() instanceof BowItem || active.getItem() instanceof CrossbowItem) {
                isAiming = true;
            }
        }

        if (s.shotFlinch > 0.01f) {
            s.shotFlinch *= 0.60f;
        } else {
            s.shotFlinch = 0.0f;
        }

        boolean inAir = !player.onGround() && !isInWater && !isFlying && !player.onClimbable();
        if (inAir) {
            s.inAirWeight += (1.0f - s.inAirWeight) * 0.40f;
            s.wasInAir = true;
        } else {
            s.inAirWeight += (0.0f - s.inAirWeight) * 0.40f;
            if (s.wasInAir) {
                s.wasInAir = false;
                s.landingSpring = 24.0f;
            }
        }
        s.landingSpring *= 0.65f;

        s.sneakWeight += ((isSneaking ? 1.0f : 0.0f) - s.sneakWeight) * 0.45f;
        s.sneakMoveWeight += ((isSneaking && isMoving ? 1.0f : 0.0f) - s.sneakMoveWeight) * 0.45f;
        s.sprintWeight += ((isSprinting ? 1.0f : 0.0f) - s.sprintWeight) * 0.40f;
        s.aimWeight += ((isAiming ? 1.0f : 0.0f) - s.aimWeight) * 0.45f;
        s.reloadWeight += ((isReloading ? 1.0f : 0.0f) - s.reloadWeight) * 0.45f;
        s.rollWeight += ((isRolling ? 1.0f : 0.0f) - s.rollWeight) * 0.55f;
        s.proneWeight += ((isProne ? 1.0f : 0.0f) - s.proneWeight) * 0.35f;
        s.swimWeight += ((isInWater ? 1.0f : 0.0f) - s.swimWeight) * 0.35f;

        boolean isActiveState = isMoving || isSprinting || isAiming || isReloading || isRolling || inAir || isProne || isInWater
                || isSneaking || player.hurtTime > 0 || s.shotFlinch > 0.1f;
        boolean isPhysicallyIdle = !isActiveState;
        boolean settled = s.sneakWeight < 0.05f && s.sneakMoveWeight < 0.05f && s.sprintWeight < 0.05f && s.aimWeight < 0.05f
                && s.reloadWeight < 0.05f && s.rollWeight < 0.05f && s.proneWeight < 0.05f && s.swimWeight < 0.05f
                && s.inAirWeight < 0.05f && s.shotFlinch < 0.05f && Math.abs(s.moveInertiaForward) < 1.0f
                && Math.abs(s.moveInertiaStrafe) < 1.0f;

        if (isActiveState) {
            s.wasInActiveState = true;
            s.idleSettleDelay = 0;
            s.twitchDuration = 0;
        } else if (s.wasInActiveState) {
            // back to rest: once everything has settled, one ear flicks
            if (settled) {
                s.idleSettleDelay++;
                if (s.idleSettleDelay >= 6) {
                    if (s.idleChanceCooldown <= 0) {
                        s.twitchDuration = 12;
                        s.twitchElapsed = 0;
                        s.twitchType = RNG.nextBoolean() ? 0 : 1;
                        s.idleChanceCooldown = 60;
                        s.twitchCooldown = 90 + RNG.nextInt(150);
                    }
                    s.wasInActiveState = false;
                    s.idleSettleDelay = 0;
                }
            }
        } else {
            s.idleSettleDelay = 0;
        }

        // 4. an occasional twitch at rest
        float twitch2Z = 0.0f;
        float twitch3Z = 0.0f;
        if (isPhysicallyIdle) {
            if (s.twitchDuration == 0) {
                if (--s.twitchCooldown <= 0) {
                    s.twitchCooldown = 90 + RNG.nextInt(150);
                    s.twitchDuration = 10 + RNG.nextInt(6);
                    s.twitchElapsed = 0;
                    s.twitchType = RNG.nextInt(3);
                }
            } else {
                s.twitchElapsed++;
                float progress = (float) s.twitchElapsed / s.twitchDuration;
                if (progress >= 1.0f) {
                    s.twitchDuration = 0;
                } else {
                    float envelope = (float) Math.sin(progress * Math.PI);
                    envelope *= envelope;
                    float wave = (float) Math.sin(progress * Math.PI * 3.0) * envelope * 8.0f;
                    if (s.twitchType == 0) {
                        twitch2Z -= wave;
                    } else if (s.twitchType == 1) {
                        twitch3Z += wave;
                    } else {
                        twitch2Z -= wave;
                        twitch3Z += wave;
                    }
                }
            }
        } else {
            s.twitchDuration = 0;
        }

        // 5. bounce of walking
        float walkBounce = 0.0f;
        if (isMoving && !inAir && !isProne) {
            float speedMult = isSprinting ? 1.35f : 0.85f;
            walkBounce = (float) Math.sin(player.walkAnimation.position() * speedMult) * 3.5f * player.walkAnimation.speed();
        }

        // 6. the pose the ears are heading for
        float target2X = 0.0f;
        float target2Y = 0.0f;
        float target2Z = twitch2Z;
        float target3X = 0.0f;
        float target3Y = 0.0f;
        float target3Z = twitch3Z;

        target2Z += s.curYawInertia * 1.3f;
        target3Z += s.curYawInertia * 1.3f;
        target2Y += s.curYawInertia * 0.5f;
        target3Y += s.curYawInertia * 0.5f;
        target2X += s.curPitchInertia * 1.1f;
        target3X += s.curPitchInertia * 1.1f;
        target2X += s.moveInertiaForward;
        target3X += s.moveInertiaForward;
        target2Z += s.moveInertiaStrafe;
        target3Z += s.moveInertiaStrafe;

        if (s.sneakWeight > 0.001f) {
            float staticW = s.sneakWeight * (1.0f - s.sneakMoveWeight);
            float moveW = s.sneakWeight * s.sneakMoveWeight;
            target2X = lerp(target2X, SNEAK_EAR2_X, staticW);
            target2Y = lerp(target2Y, SNEAK_EAR2_Y, staticW);
            target2Z = lerp(target2Z, SNEAK_EAR2_Z, staticW);
            target3X = lerp(target3X, SNEAK_EAR3_X, staticW);
            target3Y = lerp(target3Y, SNEAK_EAR3_Y, staticW);
            target3Z = lerp(target3Z, SNEAK_EAR3_Z, staticW);
            if (moveW > 0.001f) {
                target2X = lerp(target2X, SNEAKMOVE_EAR2_X, moveW);
                target2Y = lerp(target2Y, SNEAKMOVE_EAR2_Y, moveW);
                target2Z = lerp(target2Z, SNEAKMOVE_EAR2_Z, moveW);
                target3X = lerp(target3X, SNEAKMOVE_EAR3_X, moveW);
                target3Y = lerp(target3Y, SNEAKMOVE_EAR3_Y, moveW);
                target3Z = lerp(target3Z, SNEAKMOVE_EAR3_Z, moveW);
            }
        }

        // sprint: folded back a little less than when sneaking
        if (s.sprintWeight > 0.001f && s.swimWeight < 0.2f) {
            target2X = lerp(target2X, SNEAK_EAR2_X * 0.80f, s.sprintWeight);
            target2Y = lerp(target2Y, SNEAK_EAR2_Y * 0.75f, s.sprintWeight);
            target2Z = lerp(target2Z, SNEAK_EAR2_Z * 0.75f - walkBounce, s.sprintWeight);
            target3X = lerp(target3X, SNEAK_EAR3_X * 0.80f, s.sprintWeight);
            target3Y = lerp(target3Y, SNEAK_EAR3_Y * 0.75f, s.sprintWeight);
            target3Z = lerp(target3Z, SNEAK_EAR3_Z * 0.75f + walkBounce, s.sprintWeight);
        } else if (isMoving && !isSneaking && s.swimWeight < 0.2f) {
            target2X -= walkBounce * 0.5f;
            target3X -= walkBounce * 0.5f;
        }

        // roll: like sneaking on the move
        if (s.rollWeight > 0.001f) {
            target2X = lerp(target2X, SNEAKMOVE_EAR2_X, s.rollWeight);
            target2Y = lerp(target2Y, SNEAKMOVE_EAR2_Y, s.rollWeight);
            target2Z = lerp(target2Z, SNEAKMOVE_EAR2_Z, s.rollWeight);
            target3X = lerp(target3X, SNEAKMOVE_EAR3_X, s.rollWeight);
            target3Y = lerp(target3Y, SNEAKMOVE_EAR3_Y, s.rollWeight);
            target3Z = lerp(target3Z, SNEAKMOVE_EAR3_Z, s.rollWeight);
        }

        // aiming: a little weaker than sneaking on the move
        if (s.aimWeight > 0.001f) {
            float aimBlend = s.aimWeight * 0.75f;
            target2X = lerp(target2X, SNEAKMOVE_EAR2_X * 0.75f, aimBlend);
            target2Y = lerp(target2Y, SNEAKMOVE_EAR2_Y * 0.75f, aimBlend);
            target2Z = lerp(target2Z, SNEAKMOVE_EAR2_Z * 0.75f, aimBlend);
            target3X = lerp(target3X, SNEAKMOVE_EAR3_X * 0.75f, aimBlend);
            target3Y = lerp(target3Y, SNEAKMOVE_EAR3_Y * 0.75f, aimBlend);
            target3Z = lerp(target3Z, SNEAKMOVE_EAR3_Z * 0.75f, aimBlend);
        }

        // reloading: the second ear moves
        if (s.reloadWeight > 0.001f) {
            target3Z = lerp(target3Z, target3Z - 15.0f, s.reloadWeight);
            target3X = lerp(target3X, target3X - 6.0f, s.reloadWeight);
        }

        // a shot: the ears are thrown back
        if (s.shotFlinch > 0.01f) {
            float flinch = 20.0f * s.shotFlinch;
            target2X -= flinch;
            target3X -= flinch;
            target2Z += 4.5f * s.shotFlinch;
            target3Z -= 4.5f * s.shotFlinch;
        }

        // jump, fall, landing
        if (s.inAirWeight > 0.001f) {
            float vertMotion = (float) player.getDeltaMovement().y;
            if (vertMotion > 0.05f) {
                target2Z += vertMotion * 15.0f * s.inAirWeight;
                target3Z -= vertMotion * 15.0f * s.inAirWeight;
            } else if (vertMotion < -0.1f) {
                target2X += vertMotion * 14.0f * s.inAirWeight;
                target3X += vertMotion * 14.0f * s.inAirWeight;
            }
        }
        if (s.landingSpring > 0.001f) {
            target2X -= s.landingSpring * 0.45f;
            target3X -= s.landingSpring * 0.45f;
        }

        // crawling: laid flat against the skull
        if (s.proneWeight > 0.001f) {
            float foldX = -50.0f * s.proneWeight;
            target2X += foldX;
            target3X += foldX;
            target2Z = lerp(target2Z, -3.0f, s.proneWeight);
            target3Z = lerp(target3Z, 3.0f, s.proneWeight);
        }

        // swimming: the sneak-move pose with a gentle sway
        if (s.swimWeight > 0.001f) {
            float waterWave = (float) Math.sin(player.tickCount * 0.18) * 1.8f;
            target2X = lerp(target2X, SNEAKMOVE_EAR2_X + waterWave, s.swimWeight);
            target2Y = lerp(target2Y, SNEAKMOVE_EAR2_Y, s.swimWeight);
            target2Z = lerp(target2Z, SNEAKMOVE_EAR2_Z, s.swimWeight);
            target3X = lerp(target3X, SNEAKMOVE_EAR3_X + waterWave, s.swimWeight);
            target3Y = lerp(target3Y, SNEAKMOVE_EAR3_Y, s.swimWeight);
            target3Z = lerp(target3Z, SNEAKMOVE_EAR3_Z, s.swimWeight);
        }

        // hurt: thrown back at once
        if (player.hurtTime > 0) {
            float hurtFactor = player.hurtTime / 10.0f;
            target2X = lerp(target2X, SNEAKMOVE_EAR2_X, hurtFactor);
            target2Y = lerp(target2Y, SNEAKMOVE_EAR2_Y, hurtFactor);
            target2Z = lerp(target2Z, SNEAKMOVE_EAR2_Z, hurtFactor);
            target3X = lerp(target3X, SNEAKMOVE_EAR3_X, hurtFactor);
            target3Y = lerp(target3Y, SNEAKMOVE_EAR3_Y, hurtFactor);
            target3Z = lerp(target3Z, SNEAKMOVE_EAR3_Z, hurtFactor);
        }

        s.curEar2X += (target2X - s.curEar2X) * 0.48f;
        s.curEar2Y += (target2Y - s.curEar2Y) * 0.48f;
        s.curEar2Z += (target2Z - s.curEar2Z) * 0.48f;
        s.curEar3X += (target3X - s.curEar3X) * 0.48f;
        s.curEar3Y += (target3Y - s.curEar3Y) * 0.48f;
        s.curEar3Z += (target3Z - s.curEar3Z) * 0.48f;
    }

    /** The offsets (degrees) of the right (ear2) and left (ear3) ear between two ticks: {x, y, z} each. */
    public static void offsets(Player player, float partialTick, float[] ear2, float[] ear3) {
        State s = state(player);
        // slow breathing at rest
        float breatheX = 0.0f;
        float breatheZ = 0.0f;
        if (s.sneakWeight < 0.5f && s.proneWeight < 0.5f && s.swimWeight < 0.5f) {
            float breatheTime = (player.tickCount + partialTick) * 0.055f;
            var motion = player.getDeltaMovement();
            float idleWeight = 1.0f - Math.min(1.0f, (float) Math.sqrt(motion.x * motion.x + motion.z * motion.z) * 4.0f);
            idleWeight *= (1.0f - s.sneakWeight);
            if (idleWeight > 0.01f) {
                breatheX = (float) Math.sin(breatheTime) * 1.0f * idleWeight;
                breatheZ = (float) Math.cos(breatheTime * 0.9f) * 0.6f * idleWeight;
            }
        }
        ear2[0] = lerp(s.prevEar2X, s.curEar2X, partialTick) + breatheX;
        ear2[1] = lerp(s.prevEar2Y, s.curEar2Y, partialTick);
        ear2[2] = lerp(s.prevEar2Z, s.curEar2Z, partialTick) - breatheZ;
        ear3[0] = lerp(s.prevEar3X, s.curEar3X, partialTick) + breatheX;
        ear3[1] = lerp(s.prevEar3Y, s.curEar3Y, partialTick);
        ear3[2] = lerp(s.prevEar3Z, s.curEar3Z, partialTick) + breatheZ;
    }
}
