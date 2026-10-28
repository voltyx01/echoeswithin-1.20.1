package com.voltyx.efw.fireweapon;

import com.voltyx.efw.EFW;
import com.voltyx.efw.network.EfwNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Gameplay of the fire weapons: wrapping, lighting, burning down, the fire damage and the smoldering of struck mobs. */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID)
public final class FireWeaponEvents {
    private FireWeaponEvents() {
    }

    // ------------------------------------------------------------------------------------------ in-world right click

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (handle(event.getEntity(), event.getHand())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (handle(event.getEntity(), event.getHand())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /** The item in {@code hand} is used on the weapon in the other hand. Returns true when the click was taken. */
    private static boolean handle(Player player, InteractionHand hand) {
        InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack weapon = player.getItemInHand(other);
        ItemStack used = player.getItemInHand(hand);
        if (!FireWeaponHelper.isWeapon(weapon) || used.isEmpty()) {
            return false;
        }
        if (!(player instanceof ServerPlayer sp)) {
            // client: swallow the click only if the server is going to do something with it
            return FireWeaponActions.canAffect(weapon, used);
        }
        FireWeaponActions.Result result = FireWeaponActions.apply(sp, weapon, used);
        if (result == FireWeaponActions.Result.NONE) {
            return false;
        }
        if (result == FireWeaponActions.Result.APPLIED) {
            player.swing(hand, true);
            sp.inventoryMenu.broadcastChanges();
        }
        return true;
    }

    // ------------------------------------------------------------------------------------------ click in an inventory

    /** Server side of the click with the item on the cursor over a weapon slot. */
    public static void onSlotClick(ServerPlayer player, int containerId, int slotIndex, int invSlot, ItemStack clientCarried) {
        if (invSlot >= 0) {
            // creative screen: the cursor lives on the client only, so the stack it sent is used (creative players may have any item anyway)
            var inv = player.getInventory();
            if (!player.getAbilities().instabuild || invSlot >= inv.getContainerSize() || clientCarried.isEmpty()) {
                return;
            }
            if (FireWeaponActions.apply(player, inv.getItem(invSlot), clientCarried.copy()) == FireWeaponActions.Result.APPLIED) {
                inv.setChanged();
                player.inventoryMenu.broadcastChanges();
                player.containerMenu.broadcastChanges();
            }
            return;
        }
        if (player.containerMenu.containerId != containerId || slotIndex < 0 || slotIndex >= player.containerMenu.slots.size()) {
            return;
        }
        Slot slot = player.containerMenu.getSlot(slotIndex);
        ItemStack weapon = slot.getItem();
        ItemStack carried = player.containerMenu.getCarried();
        if (FireWeaponActions.apply(player, weapon, carried) == FireWeaponActions.Result.APPLIED) {
            slot.setChanged();
            player.containerMenu.setCarried(carried);
            player.containerMenu.broadcastChanges();
        }
    }

    // ------------------------------------------------------------------------------------------ burning

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || !player.isAlive()) {
            return;
        }
        long now = player.level().getGameTime();
        boolean wet = player.isInWaterOrRain();
        var inv = player.getInventory();
        boolean changed = false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || !FireWeaponHelper.hasVisibleCloth(stack) && !FireWeaponHelper.isInCharPhase(stack)) {
                continue;
            }
            if (FireWeaponHelper.isIgnited(stack)) {
                if (wet) {
                    // rain and water: SOAKED loses all its cloth, CLOTH keeps what is left
                    FireWeaponHelper.extinguish(stack, FireWeaponHelper.isSoaked(stack), now);
                    sound(player);
                    changed = true;
                    continue;
                }
                if (FireWeaponHelper.checkBurn(stack, now)) {
                    sound(player);
                    changed = true;
                }
            } else if (FireWeaponHelper.isInCharPhase(stack)) {
                FireWeaponHelper.checkBurn(stack, now);
                changed |= !FireWeaponHelper.isInCharPhase(stack);
            }
        }
        if (changed) {
            player.inventoryMenu.broadcastChanges();
        }
    }

    private static void sound(ServerPlayer player) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.7F, 1.3F);
    }

    /** A dying player puts every burning weapon out (SOAKED loses its cloth). */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        long now = player.level().getGameTime();
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (FireWeaponHelper.isIgnited(stack)) {
                FireWeaponHelper.extinguish(stack, FireWeaponHelper.isSoaked(stack), now);
            }
        }
    }

    // ------------------------------------------------------------------------------------------ combat

    /**
     * An ignited weapon adds fire damage (+3 CLOTH, +5 SOAKED) and leaves the struck mob smoldering: black burn marks and damage
     * over a few seconds, without really setting it on fire. Fire immune mobs take no bonus.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || event.getSource().getDirectEntity() != attacker) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) {
            return;
        }
        ItemStack weapon = attacker.getMainHandItem();
        if (!FireWeaponHelper.isIgnited(weapon)) {
            return;
        }
        if (!victim.fireImmune() && !victim.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            event.setAmount(event.getAmount() + FireWeaponHelper.getFireDamageBonus(weapon));
            if (victim.isAlive() && victim.level() instanceof ServerLevel) {
                SmolderManager.apply(victim, FireWeaponHelper.getSmolderTicks(weapon), FireWeaponHelper.getSmolderDamage(weapon),
                        FireWeaponHelper.isSoaked(weapon));
            }
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) {
            SmolderManager.update(level);
        }
    }

    /** Sends the click on a weapon slot while carrying cloth, flint or alcohol. */
    public static void sendSlotClick(int containerId, int slotIndex, int invSlot, ItemStack carried) {
        EfwNetwork.CHANNEL.sendToServer(new EfwNetwork.FireWeaponClick(containerId, slotIndex, invSlot, invSlot >= 0 ? carried : ItemStack.EMPTY));
    }
}
