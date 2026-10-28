package com.voltyx.efw.gore.particle.state;

import com.voltyx.efw.gore.ModConfigurationClient;
import com.voltyx.efw.gore.json.BloodStyle;
import com.voltyx.efw.gore.particle.ParticleBlood;
import com.voltyx.efw.gore.particle.common.Util;
import com.voltyx.efw.gore.particle.common.alpha.BloodAlphaHot;
import com.voltyx.efw.gore.particle.common.cache.BloodCachesHot;
import com.voltyx.efw.gore.particle.common.surface.BloodSurfaceAttach;
import com.voltyx.efw.gore.particle.state.BloodMagic;
import com.voltyx.efw.gore.particle.state.BloodSlimy;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.resources.ResourceLocation;
public final class BloodHotBlocks {
    public static boolean isHotBlock(@Nullable BlockState state) {
        if (!ModConfigurationClient.hotBlocks.hotBlocks) {
            return false;
        }
        if (state == null) {
            return false;
        }
        Block b = state.getBlock();
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(b);
        if (id == null) {
            return false;
        }
        return BloodCachesHot.hotSet(ModConfigurationClient.hotBlocks.hotBlocks, ModConfigurationClient.hotBlocks.blockList).contains(id);
    }

    @Nonnull
    public static HotStyle resolveStyle(@Nonnull ParticleBlood p) {
        if (BloodMagic.isMagic(p)) {
            return HotStyle.MAGIC;
        }
        if (BloodSlimy.isSlimy(p)) {
            return HotStyle.SLIMY;
        }
        BloodStyle style = ParticleBlood.normalizeWeight(p.fluidWeight);
        if (style == BloodStyle.HEAVY) {
            return HotStyle.HEAVY;
        }
        return HotStyle.LIGHT;
    }

    public static boolean isHotGroundTopHost(@Nullable ParticleBlood p) {
        if (p == null) {
            return false;
        }
        if (!p.isGroundTop()) {
            return false;
        }
        BlockState host = p.cache.host.base;
        if (host == null) {
            host = BloodSurfaceAttach.baseState(p);
        }
        return BloodHotBlocks.isHotBlock(host);
    }

    public static float groundConsume01(@Nullable ParticleBlood p, float partialTicks) {
        if (p == null) {
            return 0.0f;
        }
        if (!BloodHotBlocks.isHotGroundTopHost(p)) {
            return 0.0f;
        }
        return BloodAlphaHot.hotAlpha(p, partialTicks);
    }

    public static float hotBurstVisual01(@Nullable ParticleBlood p, float partialTicks) {
        if (p == null || p.hotVisualTicks <= 0 || p.hotVisualTotal <= 0) {
            return 0.0f;
        }
        float t = ((float)p.hotVisualTicks - partialTicks) / (float)Math.max(1, p.hotVisualTotal);
        t = Util.clamp01(t);
        return Util.smoothstep01(t);
    }

    public static boolean shouldRenderHotAirBillboard(@Nullable ParticleBlood p) {
        return p != null && !p.isExpiredSafe() && !p.isStuck && !p.fallingDripActive && p.hotSurfaceStartAge >= 0 && p.hotVisualTicks > 0 && p.getAlpha() > 0.001f && BloodAlphaHot.hotAlpha(p, 0.0f) > 0.001f;
    }

    public static void tickCounters(@Nonnull ParticleBlood p) {
        if (p.hotVisualTicks > 0) {
            --p.hotVisualTicks;
        }
        if (p.hotVisualTicks <= 0) {
            p.hotVisualTicks = 0;
            p.hotVisualTotal = 0;
            p.hotVisualPower = 1.0f;
        }
        if (p.hotBurstTicks > 0) {
            if (p.isStuck || p.fallingDripActive) {
                p.hotBurstTicks = 0;
                p.hotBurstTotal = 0;
            } else {
                --p.hotBurstTicks;
            }
        }
        if (p.hotBurstTicks <= 0) {
            p.hotBurstTicks = 0;
            p.hotBurstTotal = 0;
        }
    }

    public static enum HotStyle {
        LIGHT,
        SLIMY,
        HEAVY,
        MAGIC;

    }
}

