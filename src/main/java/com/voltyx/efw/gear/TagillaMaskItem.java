package com.voltyx.efw.gear;

import com.voltyx.efw.EFW;
import com.voltyx.efw.gear.client.TagillaArmorRenderer;
import com.voltyx.efw.gear.client.TagillaItemRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/**
 * The Tagilla helmet (a mask with a flip-up visor), taken over from Fracture Point: a GeckoLib armor piece drawn from the geometry of
 * that mod. The visor drops shut when the mask is put on.
 */
public class TagillaMaskItem extends ArmorItem implements GeoItem {
    private static final ArmorMaterial MATERIAL = new ArmorMaterial() {
        @Override
        public int getDurabilityForType(ArmorItem.Type type) {
            return 220;
        }

        @Override
        public int getDefenseForType(ArmorItem.Type type) {
            return 3;
        }

        @Override
        public int getEnchantmentValue() {
            return 9;
        }

        @Override
        public SoundEvent getEquipSound() {
            return SoundEvents.ARMOR_EQUIP_IRON;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.of(Items.IRON_INGOT);
        }

        @Override
        public String getName() {
            return EFW.MOD_ID + ":tagilla_mask";
        }

        @Override
        public float getToughness() {
            return 0.0f;
        }

        @Override
        public float getKnockbackResistance() {
            return 0.0f;
        }
    };

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public TagillaMaskItem() {
        super(MATERIAL, ArmorItem.Type.HELMET, new Item.Properties().stacksTo(1));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private TagillaArmorRenderer armor;
            private TagillaItemRenderer item;

            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
                if (armor == null) {
                    armor = new TagillaArmorRenderer();
                }
                armor.prepForRender(entity, stack, slot, original);
                return armor;
            }

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (item == null) {
                    item = new TagillaItemRenderer();
                }
                return item;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "visor", 0,
                state -> state.setAndContinue(RawAnimation.begin().thenPlayAndHold("helmet_closed"))));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
