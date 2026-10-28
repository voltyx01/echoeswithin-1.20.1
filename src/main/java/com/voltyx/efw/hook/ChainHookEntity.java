package com.voltyx.efw.hook;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The thrown hook of the chain. It flies where it was thrown (it can miss, nothing aims for it) and only catches creatures; a wall
 * or the end of the chain sends it back. A caught creature is torn by it (SRP's bleeding when SRP is there) and stunned while it
 * is held and a moment after (HookStun), then:
 * <ul>
 * <li>a light one ({@link #isLight}), or any one in the air, is dragged in to the thrower and staggers; a player caught by a
 * player is pulled toward him while he is pulled toward them; when it gets to him he strikes it as with the attack key;</li>
 * <li>a heavy one drags the thrower to it; he lands into it, shoves off whatever stands around and strikes it as with the attack
 * key; A / D in flight swing him round it. While he flies, that creature's blows do not reach him (the Book of Vengeance did the same) and the others' hurt less;
 * on landing he is hard to hurt for a moment.</li>
 * </ul>
 * With the melee manual's last rank (Stat.HOOK_COMBO) the landing goes on: he lands with a kick and springs off it up and back as the
 * hook comes out to his hand, throws it again at once, is dragged straight through the creature striking it on the way and lands
 * rolling behind it.
 * A block in the way stops the drag: the one dragged stops where it is and the chain jerks loose.
 * The state is synced, the clients play the thrower's animations from it (client.ChainAnimation).
 */
public class ChainHookEntity extends Projectile {
    public static final byte FLYING = 0;
    /** A light creature is dragged in. */
    public static final byte DRAG = 1;
    /** The thrower is dragged to a heavy creature. */
    public static final byte PULLED = 2;
    /** The kick at the end of the pull. */
    public static final byte IMPACT = 3;
    public static final byte RETURN = 4;
    /** A dragged creature got to the thrower: the hook stays in it a moment while his client strikes it, then it comes back. */
    public static final byte ARRIVED = 10;
    /**
     * The second run (the melee manual's last rank, Stat.HOOK_COMBO). Landed with a kick, he springs off it up and back while the
     * hook comes out and back to his hand...
     */
    public static final byte BOUNCE = 5;
    /** ...throws it at the creature again at once... */
    public static final byte REHOOK = 9;
    /** ...is dragged at it, straight through... */
    public static final byte THROUGH = 6;
    /** ...strikes it while it is still in front of him (his client, as the attack key) and flies on past... */
    public static final byte PASS = 7;
    /** ...and lands rolling behind it. */
    public static final byte ROLL = 8;

    /** Blocks the chain reaches. */
    static final double RANGE = 15.0;
    private static final int IMPACT_TICKS = 6;
    private static final int ARRIVED_TICKS = 3;
    /** Blocks a tick he swings round the creature with A / D in flight. */
    private static final double STRAFE_SPEED = 0.45;
    /** Player against player: each of them is pulled this fast toward the other. */
    private static final double MUTUAL_SPEED = 0.55;
    private static final int COMBO_IMPACT_TICKS = 5;
    private static final int BOUNCE_TICKS = 10;
    private static final double REHOOK_SPEED = 2.0;
    /** The second throw is not made at a creature this many blocks above or below him. */
    private static final double REHOOK_MAX_HEIGHT = 4.0;
    private static final double THROUGH_SPEED = 1.25;
    /** Blocks past the creature the second run carries him. */
    private static final double THROUGH_PAST = 4.5;
    /** The blow of the second run lands this soon after the launch, or this many blocks before the creature, whichever is first. */
    private static final int STRIKE_AFTER_TICKS = 2;
    private static final double STRIKE_AHEAD = 4.5;
    private static final float KICK_DAMAGE = 4.0F;
    private static final int ROLL_TICKS = 8;
    /** The roll carries him on this fast for ROLL_PUSH_TICKS (a single push was eaten by the ground at once). */
    private static final double ROLL_SPEED = 0.6;
    private static final int ROLL_PUSH_TICKS = 7;

    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(ChainHookEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> HOOKED = SynchedEntityData.defineId(ChainHookEntity.class, EntityDataSerializers.INT);
    /** The landing goes on into the second run: he lands with a kick instead of the blow of the attack key. */
    private static final EntityDataAccessor<Boolean> COMBO = SynchedEntityData.defineId(ChainHookEntity.class, EntityDataSerializers.BOOLEAN);
    /** The creature the hook caught last, kept after it let go of it (the combo's blow on the way past, the finish). */
    private static final EntityDataAccessor<Integer> LAST_TARGET = SynchedEntityData.defineId(ChainHookEntity.class, EntityDataSerializers.INT);

    private int stateTicks;
    private int unseenTicks;
    private byte steer;
    private Vec3 passPoint;
    /** The creature of the combo, still known after the hook let go of it for the roll (the finish shoots it). */
    private int comboTarget = -1;
    /** Where the run through went: the roll goes on that way. */
    private Vec3 rollDir = Vec3.ZERO;
    /** Where the one being dragged (the creature or the thrower) was last tick, and for how many ticks it has not got on. */
    private Vec3 lastMoverPos;
    private int stuckTicks;

    public ChainHookEntity(EntityType<? extends ChainHookEntity> type, Level level) {
        super(type, level);
    }

    public ChainHookEntity(Level level, Player owner) {
        this(HookContent.HOOK.get(), level);
        setOwner(owner);
        Vec3 hand = owner.getEyePosition().add(owner.getLookAngle().scale(0.6)).subtract(0.0, 0.25, 0.0);
        setPos(hand.x, hand.y, hand.z);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(STATE, FLYING);
        entityData.define(HOOKED, -1);
        entityData.define(COMBO, false);
        entityData.define(LAST_TARGET, -1);
    }

    /** The creature the hook is in, or the one it was in last. */
    @Nullable
    public LivingEntity lastTarget() {
        LivingEntity hooked = hooked();
        if (hooked != null) {
            return hooked;
        }
        int id = entityData.get(LAST_TARGET);
        return id >= 0 && level().getEntity(id) instanceof LivingEntity living ? living : null;
    }

    public boolean combo() {
        return entityData.get(COMBO);
    }

    public byte state() {
        return entityData.get(STATE);
    }

    @Nullable
    public LivingEntity hooked() {
        int id = entityData.get(HOOKED);
        return id >= 0 && level().getEntity(id) instanceof LivingEntity living ? living : null;
    }

    /** The hook this player has out, or null (both sides: the hook is looked for around the player). */
    @Nullable
    public static ChainHookEntity activeHook(Player player) {
        List<ChainHookEntity> hooks = player.level().getEntitiesOfClass(ChainHookEntity.class, player.getBoundingBox().inflate(RANGE + 16.0),
                hook -> hook.getOwner() == player && hook.isAlive());
        return hooks.isEmpty() ? null : hooks.get(0);
    }

    /** Small and light enough to be dragged in; anything bigger drags the thrower. */
    public static boolean isLight(LivingEntity target) {
        return target.getBbWidth() <= 1.0F && target.getBbHeight() <= 2.3F && target.getMaxHealth() <= 60.0F;
    }

    private void setState(byte state) {
        entityData.set(STATE, state);
        stateTicks = 0;
        lastMoverPos = null;
        stuckTicks = 0;
    }

    /**
     * The one being dragged ran into something: it hit a block side on, or it has hardly got on ({@code speed} is how far it was
     * sent a tick) for a few ticks. The chain cannot drag through walls.
     */
    private boolean blocked(net.minecraft.world.entity.Entity mover, double speed) {
        Vec3 pos = mover.position();
        Vec3 last = lastMoverPos;
        lastMoverPos = pos;
        if (last == null || stateTicks <= 2) {
            return false; // it is only getting going
        }
        double moved = pos.distanceTo(last);
        stuckTicks = moved < speed * 0.3 ? stuckTicks + 1 : 0;
        return stuckTicks >= 3 || (mover.horizontalCollision && moved < speed * 0.6);
    }

    /** Stopped by a block: the one dragged stops where it is, the chain jerks loose. */
    private void stopAgainst(net.minecraft.world.entity.Entity mover) {
        mover.setDeltaMovement(mover.getDeltaMovement().multiply(0.1, 0.5, 0.1));
        mover.hurtMarked = true;
        mover.fallDistance = 0.0F;
        level().playSound(null, mover.getX(), mover.getY(), mover.getZ(), HookSounds.thud(), SoundSource.PLAYERS, 0.7F, 0.8F);
        snap();
    }

    @Override
    public void tick() {
        super.tick();
        stateTicks++;
        LivingEntity target = hooked();
        if (target != null && holding(state())) {
            // the hook sits in the creature
            setPos(target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ());
        }
        if (level().isClientSide) {
            if (state() == FLYING || state() == RETURN) {
                Vec3 v = getDeltaMovement();
                setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
            }
            return;
        }
        if (!(getOwner() instanceof Player owner) || !owner.isAlive() || owner.level() != level() || tickCount > 400
                || (state() != RETURN && !(owner.getOffhandItem().getItem() instanceof ChainHookItem))) {
            discard();
            return;
        }
        switch (state()) {
            case FLYING -> tickFlying(owner);
            case DRAG -> tickDrag(owner, target);
            case PULLED -> tickPulled(owner, target);
            case IMPACT -> {
                if (combo() && target != null && target.isAlive()) {
                    HookStun.hold(target);
                    if (stateTicks >= COMBO_IMPACT_TICKS) {
                        startBounce(owner);
                    }
                } else if (stateTicks >= IMPACT_TICKS) {
                    startReturn();
                }
            }
            case ARRIVED -> {
                if (target != null && target.isAlive()) {
                    HookStun.hold(target);
                }
                if (stateTicks >= ARRIVED_TICKS || target == null || !target.isAlive()) {
                    startReturn();
                }
            }
            case BOUNCE -> tickBounce(owner, target);
            case REHOOK -> tickRehook(owner, target);
            case THROUGH -> tickThrough(owner, target);
            case PASS -> tickPass(owner);
            case ROLL -> {
                owner.fallDistance = 0.0F;
                if (stateTicks <= ROLL_PUSH_TICKS && rollDir.lengthSqr() > 0.0) {
                    Vec3 v = owner.getDeltaMovement();
                    owner.setDeltaMovement(rollDir.x * ROLL_SPEED, Math.min(v.y, 0.0), rollDir.z * ROLL_SPEED);
                    owner.hurtMarked = true;
                }
                if (stateTicks >= ROLL_TICKS) {
                    startReturn();
                    if (owner instanceof net.minecraft.server.level.ServerPlayer server && level().getEntity(comboTarget) instanceof LivingEntity creature) {
                        HookFinisher.offer(server, creature); // up with the shotgun, when he has one and knows it
                    }
                }
            }
            default -> tickReturn(owner);
        }
    }

    private void tickFlying(Player owner) {
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() == HitResult.Type.ENTITY && ((EntityHitResult) hit).getEntity() instanceof LivingEntity living) {
            latch(owner, living);
            return;
        }
        if (hit.getType() == HitResult.Type.BLOCK) {
            // only creatures: off a wall it comes back
            level().playSound(null, getX(), getY(), getZ(), HookSounds.chain(), SoundSource.PLAYERS, 0.6F, 1.4F);
            startReturn();
            return;
        }
        Vec3 v = getDeltaMovement();
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
        setDeltaMovement(v.scale(0.99).add(0.0, -0.03, 0.0));
        if (distanceTo(owner) > RANGE || stateTicks > 30) {
            startReturn();
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && entity instanceof LivingEntity && entity != getOwner();
    }

    private void latch(Player owner, LivingEntity target) {
        entityData.set(HOOKED, target.getId());
        entityData.set(LAST_TARGET, target.getId());
        setDeltaMovement(Vec3.ZERO);
        target.hurt(damageSources().thrown(this, owner), 3.0F);
        MobEffect bleed = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("srparasites", "bleed"));
        if (bleed != null) {
            target.addEffect(new MobEffectInstance(bleed, 100, 0, false, true));
        }
        level().playSound(null, target.getX(), target.getY(), target.getZ(), HookSounds.chain(), SoundSource.PLAYERS, 1.0F, 1.0F);
        level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.8F, 0.7F);
        if (level() instanceof ServerLevel server) {
            // the hook bites in: sparks off the iron, a spray of blood
            server.sendParticles(ParticleTypes.CRIT, getX(), getY(), getZ(), 14, 0.15, 0.15, 0.15, 0.35);
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), getX(), getY(), getZ(),
                    10, 0.1, 0.1, 0.1, 0.15);
        }
        if (!target.isAlive()) {
            startReturn();
            return;
        }
        steer = 0;
        HookStun.hold(target);
        // in the air nothing holds it up against the pull: whatever its size, it comes to the thrower
        setState(isLight(target) || !target.onGround() ? DRAG : PULLED);
    }

    /** While the chain drags him: -1 round the creature to the right, 1 to the left, 0 straight in (HookStrafe from his client). */
    public void steer(byte dir) {
        steer = (byte) Math.max(-1, Math.min(1, dir));
    }

    /** The chain goes slack and snaps loose when the creature is out of sight for a moment or too far. */
    private boolean holds(Player owner, LivingEntity target) {
        if (target == null || !target.isAlive() || owner.distanceTo(target) > RANGE + 4.0) {
            return false;
        }
        unseenTicks = owner.hasLineOfSight(target) ? 0 : unseenTicks + 1;
        return unseenTicks < 6;
    }

    private void tickDrag(Player owner, LivingEntity target) {
        if (!holds(owner, target)) {
            snap();
            return;
        }
        Vec3 to = owner.position().subtract(target.position());
        double dist = to.length();
        if (dist <= 2.0 || stateTicks > 16) {
            target.setDeltaMovement(target.getDeltaMovement().scale(0.2));
            target.hurtMarked = true;
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2, false, false));
            level().playSound(null, target.getX(), target.getY(), target.getZ(), HookSounds.thud(), SoundSource.PLAYERS, 0.7F, 1.2F);
            if (ChainHookItem.combo(owner)) {
                impact(owner, target); // the second run springs off the kick here too
            } else {
                setState(ARRIVED); // his client strikes it now (HookClient)
            }
            return;
        }
        HookStun.hold(target);
        // a player caught by a player is not a carcass: the chain pulls them both, they meet half way
        boolean mutual = target instanceof Player;
        double speed = mutual ? MUTUAL_SPEED : 0.9;
        if (blocked(target, speed)) {
            stopAgainst(target);
            return;
        }
        Vec3 dir = to.normalize();
        double lift = stateTicks <= 2 ? 0.25 : 0.04;
        target.setDeltaMovement(dir.scale(speed).add(0.0, lift, 0.0));
        target.hurtMarked = true;
        target.fallDistance = 0.0F;
        if (mutual) {
            owner.setDeltaMovement(dir.scale(-speed).add(0.0, lift, 0.0));
            owner.hurtMarked = true;
            owner.fallDistance = 0.0F;
        }
    }

    private void tickPulled(Player owner, LivingEntity target) {
        if (!holds(owner, target)) {
            snap();
            return;
        }
        Vec3 from = owner.position().add(0.0, owner.getBbHeight() * 0.5, 0.0);
        Vec3 to = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0).subtract(from);
        double dist = to.length();
        if (dist <= 1.8 + target.getBbWidth() * 0.5 || stateTicks > 30) {
            impact(owner, target);
            return;
        }
        HookStun.hold(target);
        if (blocked(owner, 1.15)) {
            stopAgainst(owner);
            return;
        }
        Vec3 dir = to.normalize();
        // off the ground at the start, then straight along the chain; A / D swing him round the creature on the way
        double lift = stateTicks <= 2 ? 0.3 : 0.0;
        Vec3 left = new Vec3(dir.z, 0.0, -dir.x);
        left = left.lengthSqr() > 1.0E-4 ? left.normalize().scale(STRAFE_SPEED * steer) : Vec3.ZERO;
        owner.setDeltaMovement(dir.x * 1.15 + left.x, Math.max(-0.8, Math.min(0.9, dir.y * 1.15 + lift)), dir.z * 1.15 + left.z);
        if (level() instanceof ServerLevel server) {
            // the air torn behind him
            server.sendParticles(ParticleTypes.POOF, owner.getX(), owner.getY() + 0.9, owner.getZ(), 2, 0.15, 0.3, 0.15, 0.01);
            grit(server, owner.getX(), owner.getY() + 0.9, owner.getZ(), 3, 0.25, 0.3);
            if (stateTicks % 2 == 0) {
                server.sendParticles(ParticleTypes.CRIT, owner.getX(), owner.getY() + 1.0, owner.getZ(), 2, 0.3, 0.4, 0.3, 0.1);
            }
        }
        owner.hurtMarked = true;
        owner.fallDistance = 0.0F;
        // in flight the rest of them can still reach him: they hurt less
        owner.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 5, 1, false, false, false));
    }

    /**
     * He lands into the creature: those standing round it are shoved off, he stops and bounces back a little, and for a moment blows
     * hardly hurt him (they landed the instant he arrived). His own blow is struck by his client, as a press of the attack key
     * (HookClient), so the weapon, its cooldown and Better Combat all work as usual.
     */
    private void impact(Player owner, LivingEntity target) {
        entityData.set(COMBO, ChainHookItem.combo(owner));
        setState(IMPACT);
        Vec3 look = new Vec3(owner.getLookAngle().x, 0.0, owner.getLookAngle().z);
        look = look.lengthSqr() > 1.0E-4 ? look.normalize() : Vec3.ZERO;
        for (LivingEntity near : level().getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(2.5))) {
            if (near == owner || near == target || !near.isAlive()) {
                continue;
            }
            Vec3 away = near.position().subtract(target.position());
            away = new Vec3(away.x, 0.0, away.z);
            away = away.lengthSqr() > 1.0E-4 ? away.normalize() : look;
            near.hurt(damageSources().playerAttack(owner), 2.0F);
            near.setDeltaMovement(near.getDeltaMovement().add(away.scale(0.5)).add(0.0, 0.2, 0.0));
            near.hurtMarked = true;
        }
        ChainHookEvents.shieldAfterLanding(owner, target);
        if (combo()) {
            // the kick he springs off (his client plays it); the blow of the attack key comes on the run through
            target.hurt(damageSources().playerAttack(owner), KICK_DAMAGE);
            target.setDeltaMovement(target.getDeltaMovement().add(look.scale(0.25)).add(0.0, 0.15, 0.0));
            target.hurtMarked = true;
        }
        owner.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 2, false, false, true));
        owner.setDeltaMovement(owner.getDeltaMovement().scale(0.15).add(look.scale(-0.15)).add(0.0, 0.3, 0.0));
        owner.hurtMarked = true;
        owner.fallDistance = 0.0F;
        level().playSound(null, target.getX(), target.getY(), target.getZ(), HookSounds.impact(), SoundSource.PLAYERS, 1.0F, 1.0F);
        level().playSound(null, target.getX(), target.getY(), target.getZ(), HookSounds.thud(), SoundSource.PLAYERS, 0.8F, 0.9F);
        // not an explosion sound: the heartbeat takes those for a blast and jumps the pulse
        level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.3F, 1.6F);
        if (level() instanceof ServerLevel server) {
            landingBurst(server, target);
        }
    }

    /** The landing: a ring of dirt kicked up off the ground round the creature, dust, and the blow on it. */
    private static void landingBurst(ServerLevel server, LivingEntity target) {
        BlockPos below = BlockPos.containing(target.getX(), target.getY() - 0.2, target.getZ());
        BlockState ground = server.getBlockState(below);
        if (ground.isAir()) {
            ground = server.getBlockState(below.below());
        }
        double y = target.getY() + 0.1;
        for (int i = 0; i < 28; i++) {
            double a = Math.PI * 2.0 * i / 28.0;
            double r = 1.2 + server.random.nextDouble() * 0.5;
            double x = target.getX() + Math.cos(a) * r;
            double z = target.getZ() + Math.sin(a) * r;
            if (!ground.isAir()) {
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), x, y, z, 2, 0.05, 0.15, 0.05, 0.25);
            }
            server.sendParticles(ParticleTypes.POOF, x, y + 0.1, z, 1, Math.cos(a) * 0.1, 0.02, Math.sin(a) * 0.1, 0.12);
        }
        server.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        server.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 20, 0.3, 0.4, 0.3, 0.5);
        // a shockwave: a ring of dust and sparks tearing out along the ground, and a flash at the heart of it
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2.0 * i / 24.0;
            server.sendParticles(ParticleTypes.LARGE_SMOKE, target.getX(), y + 0.05, target.getZ(), 0, Math.cos(a), 0.04, Math.sin(a), 0.3);
            if (i % 3 == 0) {
                grit(server, target.getX() + Math.cos(a), y + 0.2, target.getZ() + Math.sin(a), 2, 0.1, 0.35);
            }
        }
        blood(server, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 14, 0.5);
    }

    /** Chips of rusted iron and ground knocked loose: what the blow and the chain tear off, nothing bright. */
    private static void grit(ServerLevel server, double x, double y, double z, int count, double spread, double speed) {
        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.IRON_BLOCK.defaultBlockState()), x, y, z, count, spread, spread, spread, speed);
        server.sendParticles(ParticleTypes.SMOKE, x, y, z, 1 + count / 4, spread, spread, spread, 0.02);
    }

    /** A spray of dark blood. */
    private static void blood(ServerLevel server, double x, double y, double z, int count, double spread) {
        server.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.42F, 0.02F, 0.02F), 1.2F), x, y, z, count,
                spread, spread, spread, 0.05);
        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), x, y, z, count / 2, spread * 0.5,
                spread * 0.5, spread * 0.5, 0.2);
    }

    /** The hook sits in the creature in these states. */
    public static boolean holding(byte state) {
        return state == DRAG || state == ARRIVED || state == PULLED || state == IMPACT || state == THROUGH;
    }

    /** The thrower is on the creature in these states: the hook in it, or out of it for a moment in the middle of the second run. */
    public static boolean engaged(byte state) {
        return holding(state) || state == BOUNCE || state == REHOOK || state == PASS;
    }

    /** Off the kick he springs up and back; the hook comes out of the creature and back to his hand. */
    private void startBounce(Player owner) {
        setState(BOUNCE);
        level().playSound(null, owner.getX(), owner.getY(), owner.getZ(), HookSounds.chain(), SoundSource.PLAYERS, 0.9F, 0.8F);
    }

    private void tickBounce(Player owner, LivingEntity target) {
        if (target == null || !target.isAlive()) {
            startReturn();
            return;
        }
        HookStun.hold(target);
        Vec3 away = owner.position().subtract(target.position());
        away = new Vec3(away.x, 0.0, away.z);
        away = away.lengthSqr() > 1.0E-4 ? away.normalize() : Vec3.ZERO;
        if (stateTicks <= 3) {
            owner.setDeltaMovement(away.x * 0.6, 0.42, away.z * 0.6); // more back than up
        } else {
            Vec3 v = owner.getDeltaMovement();
            owner.setDeltaMovement(v.x * 0.6, Math.max(v.y, -0.05), v.z * 0.6); // hangs at the top for a moment
        }
        owner.hurtMarked = true;
        owner.fallDistance = 0.0F;
        // the hook flies back to the hand
        Vec3 hand = owner.getEyePosition().subtract(0.0, 0.4, 0.0);
        Vec3 back = hand.subtract(position());
        if (back.length() > 0.6) {
            Vec3 step = back.normalize().scale(Math.min(back.length(), REHOOK_SPEED));
            setPos(getX() + step.x, getY() + step.y, getZ() + step.z);
        }
        if (stateTicks >= BOUNCE_TICKS) {
            if (Math.abs(target.getY() - owner.getY()) > REHOOK_MAX_HEIGHT) {
                startReturn(); // too far above or below him for the second throw
                return;
            }
            setState(REHOOK);
            level().playSound(null, owner.getX(), owner.getY(), owner.getZ(), HookSounds.whoosh(), SoundSource.PLAYERS, 1.0F, 1.1F);
        }
    }

    /** Thrown again at once, straight at it; when it bites, the run through. */
    private void tickRehook(Player owner, LivingEntity target) {
        if (target == null || !target.isAlive()) {
            startReturn();
            return;
        }
        HookStun.hold(target);
        Vec3 v = owner.getDeltaMovement();
        owner.setDeltaMovement(v.x * 0.6, Math.max(v.y, -0.05), v.z * 0.6);
        owner.hurtMarked = true;
        owner.fallDistance = 0.0F;
        Vec3 aim = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0);
        Vec3 to = aim.subtract(position());
        if (to.length() > REHOOK_SPEED && stateTicks < 12) {
            Vec3 step = to.normalize().scale(REHOOK_SPEED);
            setPos(getX() + step.x, getY() + step.y, getZ() + step.z);
            return;
        }
        Vec3 across = target.position().subtract(owner.position());
        across = new Vec3(across.x, 0.0, across.z);
        if (across.lengthSqr() < 1.0E-4) {
            across = new Vec3(owner.getLookAngle().x, 0.0, owner.getLookAngle().z);
        }
        across = across.lengthSqr() > 1.0E-4 ? across.normalize() : new Vec3(1.0, 0.0, 0.0);
        passPoint = target.position().add(across.scale(THROUGH_PAST + target.getBbWidth() * 0.5));
        setState(THROUGH);
        level().playSound(null, target.getX(), target.getY(), target.getZ(), HookSounds.chain(), SoundSource.PLAYERS, 1.0F, 1.1F);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.CRIT, target.getX(), aim.y, target.getZ(), 10, 0.15, 0.15, 0.15, 0.35);
        }
    }

    /** Down at the creature and on through it: while it is still in front of him, the blow (PASS). */
    private void tickThrough(Player owner, LivingEntity target) {
        if (target == null || !target.isAlive() || passPoint == null) {
            rollOut(owner);
            return;
        }
        HookStun.hold(target);
        if (blocked(owner, THROUGH_SPEED)) {
            stopAgainst(owner);
            return;
        }
        flyAt(owner, passPoint);
        if (stateTicks >= STRIKE_AFTER_TICKS || owner.distanceTo(target) <= STRIKE_AHEAD + target.getBbWidth() * 0.5) {
            // the blow: the hook comes out of it and back to the hand while he flies on past (the chain must not trail back to it)
            comboTarget = target.getId();
            Vec3 run = passPoint.subtract(owner.position());
            rollDir = new Vec3(run.x, 0.0, run.z).lengthSqr() > 1.0E-4 ? new Vec3(run.x, 0.0, run.z).normalize() : Vec3.ZERO;
            entityData.set(HOOKED, -1);
            Vec3 hand = owner.getEyePosition().subtract(0.0, 0.4, 0.0);
            setPos(hand.x, hand.y, hand.z); // the chain lets go at once, nothing trails between them
            setState(PASS);
            level().playSound(null, target.getX(), target.getY(), target.getZ(), HookSounds.impact(), SoundSource.PLAYERS, 1.0F, 1.2F);
            if (level() instanceof ServerLevel server) {
                double y = target.getY() + target.getBbHeight() * 0.6;
                server.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), y, target.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
                server.sendParticles(ParticleTypes.CRIT, target.getX(), y, target.getZ(), 16, 0.3, 0.4, 0.3, 0.5);
                blood(server, target.getX(), y, target.getZ(), 18, 0.6);
                grit(server, target.getX(), y, target.getZ(), 14, 0.3, 0.5);
            }
        }
    }

    private void tickPass(Player owner) {
        if (level().getEntity(comboTarget) instanceof LivingEntity creature && creature.isAlive()) {
            HookStun.hold(creature);
        }
        // the hook flies back to the hand
        Vec3 hand = owner.getEyePosition().subtract(0.0, 0.4, 0.0);
        Vec3 back = hand.subtract(position());
        if (back.length() > 0.6) {
            Vec3 step = back.normalize().scale(Math.min(back.length(), REHOOK_SPEED));
            setPos(getX() + step.x, getY() + step.y, getZ() + step.z);
        }
        // he rolls the moment he touches the ground
        if (passPoint == null || owner.position().distanceTo(passPoint) < 1.0 || stateTicks > 10 || (stateTicks >= 2 && owner.onGround())) {
            rollOut(owner);
            return;
        }
        if (blocked(owner, THROUGH_SPEED)) {
            rollOut(owner); // past it already: he rolls off the wall
            return;
        }
        flyAt(owner, passPoint);
    }

    /** Past it, he comes down rolling: the hook lets go. */
    private void rollOut(Player owner) {
        Vec3 v = owner.getDeltaMovement();
        if (rollDir.lengthSqr() == 0.0) {
            Vec3 flat = new Vec3(v.x, 0.0, v.z);
            rollDir = flat.lengthSqr() > 1.0E-4 ? flat.normalize() : Vec3.ZERO;
        }
        owner.setDeltaMovement(rollDir.x * ROLL_SPEED, Math.min(v.y, 0.0), rollDir.z * ROLL_SPEED);
        owner.hurtMarked = true;
        owner.fallDistance = 0.0F;
        owner.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20, 2, false, false, true));
        if (entityData.get(HOOKED) >= 0) {
            comboTarget = entityData.get(HOOKED);
        }
        entityData.set(HOOKED, -1);
        setState(ROLL);
    }

    private static void flyAt(Player owner, Vec3 point) {
        Vec3 to = point.subtract(owner.position());
        Vec3 dir = to.lengthSqr() > 1.0E-4 ? to.normalize() : Vec3.ZERO;
        owner.setDeltaMovement(dir.x * THROUGH_SPEED, Math.max(-1.0, Math.min(0.6, dir.y * THROUGH_SPEED)), dir.z * THROUGH_SPEED);
        owner.hurtMarked = true;
        owner.fallDistance = 0.0F;
    }

    private void snap() {
        level().playSound(null, getX(), getY(), getZ(), HookSounds.snap(), SoundSource.PLAYERS, 0.8F, 1.1F);
        startReturn();
    }

    private void startReturn() {
        entityData.set(HOOKED, -1);
        setState(RETURN);
    }

    private void tickReturn(Player owner) {
        Vec3 hand = owner.getEyePosition().subtract(0.0, 0.4, 0.0);
        Vec3 to = hand.subtract(position());
        if (to.length() < 1.5 || stateTicks > 20) {
            level().playSound(null, owner.getX(), owner.getY(), owner.getZ(), HookSounds.chain(), SoundSource.PLAYERS, 0.4F, 1.5F);
            discard();
            return;
        }
        Vec3 v = to.normalize().scale(Math.min(2.2, to.length()));
        setDeltaMovement(v);
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
    }

    /** The thrower is being dragged to this creature: its blows do not reach him on the way (ChainHookEvents). */
    public boolean shields(Player owner, Entity attacker) {
        byte state = state();
        return getOwner() == owner && attacker != null && attacker == lastTarget()
                && (state == PULLED || engaged(state));
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96.0 * 96.0;
    }

    @Override
    public boolean shouldBeSaved() {
        return false; // a hook is never kept over a reload
    }
}
