package com.voltyx.efw.compat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.voltyx.efw.EFW;
import com.voltyx.efw.doll.client.DollBeltLayer;
import com.voltyx.efw.gear.WornOlympicItem;
import com.voltyx.efw.gear.client.OlympicSkin;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A dead player's body as Ragdollified draws it (RagdollBodyMixin): the falling ragdoll and the corpse it settles into. The look the
 * server took at death (RagdollLooks) gives the blood and the doll; the jacket comes from the body's own Curios slots (or the look).
 * While the body is drawn, its skin is swapped for the plain or the jacket version and every texture of it gets the blood
 * (BloodTextures asks here); after the body the doll is drawn on its belt.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class RagdollBodies {
    private record Look(float blood, boolean doll, boolean jacket) {
    }

    /** By the dead player's entity id (Integer) for a ragdoll, by the corpse entity's UUID for a corpse. */
    private static final Map<Object, Look> LOOKS = new ConcurrentHashMap<>();

    private static final class Body {
        ResourceLocation skin;
        ResourceLocation drawn;
        float blood;
        boolean doll;
        ItemStack chest;
        MultiBufferSource buffers;
        int light;
        PoseStack dollPose;
    }

    private static Body current;
    private static Method curioStack;

    private RagdollBodies() {
    }

    public static void setLook(Object key, float blood, boolean doll, boolean jacket) {
        LOOKS.put(key, new Look(blood, doll, jacket));
    }

    /** Ragdollified starts drawing a player body with this skin; {@code key} is its visual key, {@code curios} what it wears. */
    public static void begin(ResourceLocation skin, boolean slim, ItemStack chest, MultiBufferSource buffers, int light, Object key, List<?> curios) {
        Look look = key != null ? LOOKS.get(key) : null;
        Body body = new Body();
        body.skin = skin;
        body.drawn = OlympicSkin.forBody(skin, slim, (look != null && look.jacket) || wearsJacket(curios));
        body.blood = look != null ? look.blood : 0.0F;
        body.doll = look != null && look.doll;
        body.chest = chest == null ? ItemStack.EMPTY : chest;
        body.buffers = buffers;
        body.light = light;
        current = body;
    }

    /** The first torso pass (the skin): where the doll will hang. */
    public static void torso(PoseStack pose, ModelPart part) {
        Body body = current;
        if (body == null || !body.doll || body.dollPose != null) {
            return;
        }
        PoseStack copy = new PoseStack();
        copy.last().pose().set(pose.last().pose());
        copy.last().normal().set(pose.last().normal());
        part.translateAndRotate(copy);
        body.dollPose = copy;
    }

    public static void end() {
        Body body = current;
        if (body == null) {
            return;
        }
        try {
            if (body.dollPose != null) {
                DollBeltLayer.renderOnBody(body.dollPose, body.buffers, body.light, body.chest);
            }
        } finally {
            current = null;
        }
    }

    public static boolean isDrawing() {
        return current != null;
    }

    /** The texture a body texture is drawn with: the skin swapped for the plain or the jacket version. */
    public static ResourceLocation texture(ResourceLocation original) {
        Body body = current;
        return body != null && original.equals(body.skin) ? body.drawn : original;
    }

    public static ResourceLocation skin() {
        Body body = current;
        return body != null ? body.drawn : null;
    }

    public static float blood() {
        Body body = current;
        return body != null ? body.blood : 0.0F;
    }

    private static boolean wearsJacket(List<?> curios) {
        if (curios == null) {
            return false;
        }
        for (Object worn : curios) {
            if (stackOf(worn).getItem() instanceof WornOlympicItem) {
                return true;
            }
        }
        return false;
    }

    /** Ragdollified's WornCurio record: its stack(). */
    private static ItemStack stackOf(Object worn) {
        try {
            if (curioStack == null || curioStack.getDeclaringClass() != worn.getClass()) {
                curioStack = worn.getClass().getMethod("stack");
            }
            return curioStack.invoke(worn) instanceof ItemStack stack ? stack : ItemStack.EMPTY;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        LOOKS.clear();
        current = null;
    }
}
