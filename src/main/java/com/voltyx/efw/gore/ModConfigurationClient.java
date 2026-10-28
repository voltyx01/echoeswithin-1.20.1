package com.voltyx.efw.gore;

import com.voltyx.efw.config.ConfigRegistry;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Client settings of the blood (Extra Gore, from mwccf_config "extragore" -> efw-gore.toml). The blood code reads the public fields as
 * in 1.12; they are filled from the config file whenever it is loaded or changed ({@link #apply}).
 */
public class ModConfigurationClient {
    public static final CeilingSettings ceiling = new CeilingSettings();
    public static final FadeSettings fade = new FadeSettings();
    public static final HealthScaleSettings healthScale = new HealthScaleSettings();
    public static final DamageSourceSettings damageSources = new DamageSourceSettings();
    public static final HitDispersionSettings hitDispersion = new HitDispersionSettings();
    public static final GroundSettings ground = new GroundSettings();
    public static final WallSettings wall = new WallSettings();
    public static final HotBlockSettings hotBlocks = new HotBlockSettings();

    public static final ForgeConfigSpec SPEC;
    private static final List<Runnable> APPLY = new ArrayList<>();

    public static class HotBlockSettings {
        public boolean hotBlocks = true;
        public String[] blockList = new String[]{"minecraft:magma_block"};
    }

    public static class WallSettings {
        public boolean drip = true;
        public BlockListMode blockListMode = BlockListMode.NONE;
        public String[] blockList = new String[0];
        public double stretchMin = 0.75;
        public double stretchMax = 1.35;
        public double slideSpeed = 0.003;
    }

    public static class GroundSettings {
        public boolean amalgamation = true;
        public double spreadMin = 0.85;
        public double spreadMax = 1.25;
    }

    public static class HitDispersionSettings {
        public double coneRange = 2.0;
        public double yConeRange = 3.0;
        public double force = 0.7;
        public int fixedCount = 10;
    }

    public static class DamageSourceSettings {
        public boolean fire = false;
        public boolean drowning = false;
        public boolean suffocation = false;
        public boolean cactus = false;
        public boolean effects = false;
        public boolean fall = false;
        public boolean lightning = false;
        public boolean voidDamage = false;
    }

    public static class HealthScaleSettings {
        public boolean enableSizeReduction = false;
        public double startHealthFraction = 0.8;
        public double endHealthFraction = 0.3;
        public double sizeAtStart = 0.2;
        public double sizeAtLow = 1.0;
    }

    public static class FadeSettings {
        public double startFade = 0.15;
    }

    public static class CeilingSettings {
        public boolean stick = true;
        public BlockListMode blockListMode = BlockListMode.NONE;
        public String[] blockList = new String[0];
    }

    public static enum BlockListMode {
        NONE,
        BLACKLIST,
        WHITELIST;
    }

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        ConfigRegistry.section("Blood: ceiling");
        b.push("ceiling");
        { ForgeConfigSpec.BooleanValue v = ConfigRegistry.bool(b, "stick", true, "If true, some blood styles can stick to the ceiling surfaces."); APPLY.add(() -> ceiling.stick = v.get()); }
        { ForgeConfigSpec.EnumValue<BlockListMode> v = b.comment("Controls how blockList (Block List) is applied. NONE the list is not applied. BLACKLIST excludes listed blocks. WHITELIST allows only listed blocks.").defineEnum("blockListMode", BlockListMode.NONE); APPLY.add(() -> ceiling.blockListMode = v.get()); }
        { ForgeConfigSpec.ConfigValue<List<? extends String>> v = b.comment("List of block registry names which can or cannot interact with the ceiling. If blockListMode (Block List Mode) is NONE, the list won't be considered. Examples include minecraft:stone, minecraft:concrete and modid:your_block.").defineListAllowEmpty(List.of("blockList"), () -> List.of(), o -> o instanceof String); APPLY.add(() -> ceiling.blockList = v.get().toArray(new String[0])); }
        b.pop();
        ConfigRegistry.section("Blood: fade");
        b.push("fade");
        { ForgeConfigSpec.DoubleValue v = ConfigRegistry.decimal(b, "startFade", 0.15, 0.0, 1.0, "Controls when alpha fading starts. 0.0 fades from the initial spawn. 1.0 never fades."); APPLY.add(() -> fade.startFade = v.get()); }
        b.pop();
        ConfigRegistry.section("Blood: health scale");
        b.push("health_scale");
        { ForgeConfigSpec.BooleanValue v = ConfigRegistry.bool(b, "enableSizeReduction", false, "If true, blood particle size on hit is reduced on healthier entities and ramps toward full size as health gets lower."); APPLY.add(() -> healthScale.enableSizeReduction = v.get()); }
        { ForgeConfigSpec.DoubleValue v = ConfigRegistry.decimal(b, "startHealthFraction", 0.8, 0.0, 1.0, "Health fraction where the size ramp starts. At or above this value, sizeAtStart is used."); APPLY.add(() -> healthScale.startHealthFraction = v.get()); }
        { ForgeConfigSpec.DoubleValue v = ConfigRegistry.decimal(b, "endHealthFraction", 0.3, 0.0, 1.0, "Health fraction where the size ramp ends. At or below this value, sizeAtLow is used."); APPLY.add(() -> healthScale.endHealthFraction = v.get()); }
        { ForgeConfigSpec.DoubleValue v = ConfigRegistry.decimal(b, "sizeAtStart", 0.2, 0.0, 5.0, "Blood size multiplier used at or above startHealthFraction. Lower values make healthier targets spawn smaller blood particles."); APPLY.add(() -> healthScale.sizeAtStart = v.get()); }
        { ForgeConfigSpec.DoubleValue v = ConfigRegistry.decimal(b, "sizeAtLow", 1.0, 0.0, 5.0, "Blood size multiplier used at or below endHealthFraction."); APPLY.add(() -> healthScale.sizeAtLow = v.get()); }
        b.pop();
        ConfigRegistry.section("Blood: damage sources");
        b.push("damage_sources");
        { ForgeConfigSpec.BooleanValue v = ConfigRegistry.bool(b, "fire", false, "If true, blood can spawn from fire, lava and hot floor damage."); APPLY.add(() -> damageSources.fire = v.get()); }
        { ForgeConfigSpec.BooleanValue v = ConfigRegistry.bool(b, "drowning", false, "If true, blood can spawn from drowning damage."); APPLY.add(() -> damageSources.drowning = v.get()); }
        { ForgeConfigSpec.BooleanValue v = ConfigRegistry.bool(b, "suffocation", false, "If true, blood can spawn from suffocation damage."); APPLY.add(() -> damageSources.suffocation = v.get()); }
        { ForgeConfigSpec.BooleanValue v = ConfigRegistry.bool(b, "cactus", false, "If true, blood can spawn from cactus damage."); APPLY.add(() -> damageSources.cactus = v.get()); }
        { ForgeConfigSpec.BooleanValue v = ConfigRegistry.bool(b, "effects", false, "If true, blood can spawn from effect based damage such as magic, wither, dragon breath and starvation."); APPLY.add(() -> damageSources.effects = v.get()); }
        { ForgeConfigSpec.BooleanValue v = ConfigRegistry.bool(b, "fall", false, "If true, blood can spawn from fall like damage such as fall impact, fly into wall, anvils and falling blocks."); APPLY.add(() -> damageSources.fall = v.get()); }
        { ForgeConfigSpec.BooleanValue v = ConfigRegistry.bool(b, "lightning", false, "If true, blood can spawn from lightning damage."); APPLY.add(() -> damageSources.lightning = v.get()); }
        { ForgeConfigSpec.BooleanValue v = ConfigRegistry.bool(b, "void", false, "If true, blood can spawn from void damage."); APPLY.add(() -> damageSources.voidDamage = v.get()); }
        b.pop();
        ConfigRegistry.section("Blood: hit dispersion");
        b.push("hit_dispersion");
        { ForgeConfigSpec.DoubleValue v = ConfigRegistry.decimal(b, "coneRange", 2.0, 0.0, 5.0, "Horizontal cone width multiplier for directional blood spray. Lower values tighten the cone. Higher values widen it."); APPLY.add(() -> hitDispersion.coneRange = v.get()); }
        { ForgeConfigSpec.DoubleValue v = ConfigRegistry.decimal(b, "yConeRange", 3.0, 0.0, 5.0, "Vertical cone width multiplier for directional blood spray. Lower values flatten the splash arc. Higher values widen the vertical dispersion."); APPLY.add(() -> hitDispersion.yConeRange = v.get()); }
        { ForgeConfigSpec.DoubleValue v = ConfigRegistry.decimal(b, "force", 0.7, 0.0, 5.0, "Force multiplier for directional blood spray velocity and vertical kick. Lower values soften the hit. Higher values throw particles further."); APPLY.add(() -> hitDispersion.force = v.get()); }
        { ForgeConfigSpec.IntValue v = ConfigRegistry.integer(b, "fixedCount", 10, 0, 500, "Fixed amount of blood particles to spawn per hit. Set to 0 to use dynamic count."); APPLY.add(() -> hitDispersion.fixedCount = v.get()); }
        b.pop();
        ConfigRegistry.section("Blood: ground");
        b.push("ground");
        { ForgeConfigSpec.BooleanValue v = ConfigRegistry.bool(b, "amalgamation", true, "If true, blood on top surfaces can merge into larger puddles."); APPLY.add(() -> ground.amalgamation = v.get()); }
        { ForgeConfigSpec.DoubleValue v = ConfigRegistry.decimal(b, "spreadMin", 0.85, 0.0, 5.0, "Minimum size for ground and ceiling spread."); APPLY.add(() -> ground.spreadMin = v.get()); }
        { ForgeConfigSpec.DoubleValue v = ConfigRegistry.decimal(b, "spreadMax", 1.25, 0.0, 5.0, "Maximum size for ground and ceiling spread."); APPLY.add(() -> ground.spreadMax = v.get()); }
        b.pop();
        ConfigRegistry.section("Blood: wall");
        b.push("wall");
        { ForgeConfigSpec.BooleanValue v = ConfigRegistry.bool(b, "drip", true, "If true, blood particles on the wall can drip."); APPLY.add(() -> wall.drip = v.get()); }
        { ForgeConfigSpec.EnumValue<BlockListMode> v = b.comment("Controls how blockList (Block List) is applied. NONE the list is not applied. BLACKLIST excludes listed blocks. WHITELIST allows only listed blocks.").defineEnum("blockListMode", BlockListMode.NONE); APPLY.add(() -> wall.blockListMode = v.get()); }
        { ForgeConfigSpec.ConfigValue<List<? extends String>> v = b.comment("List of block registry names which can or cannot attach to the wall. If blockListMode (Block List Mode) is NONE, the list won't be considered. Examples include minecraft:glass, minecraft:ice and modid:your_block.").defineListAllowEmpty(List.of("blockList"), () -> List.of(), o -> o instanceof String); APPLY.add(() -> wall.blockList = v.get().toArray(new String[0])); }
        { ForgeConfigSpec.DoubleValue v = ConfigRegistry.decimal(b, "stretchMin", 0.75, 0.0, 5.0, "Minimum length for wall stretch."); APPLY.add(() -> wall.stretchMin = v.get()); }
        { ForgeConfigSpec.DoubleValue v = ConfigRegistry.decimal(b, "stretchMax", 1.35, 0.0, 5.0, "Maximum length for wall stretch."); APPLY.add(() -> wall.stretchMax = v.get()); }
        { ForgeConfigSpec.DoubleValue v = ConfigRegistry.decimal(b, "slideSpeed", 0.003, 0.0, 0.05, "Slide speed over the wall per ticks. Set to 0.0 to disable sliding."); APPLY.add(() -> wall.slideSpeed = v.get()); }
        b.pop();
        ConfigRegistry.section("Blood: hot blocks");
        b.push("hot_blocks");
        { ForgeConfigSpec.BooleanValue v = ConfigRegistry.bool(b, "hotBlocks", true, "If true, hot blocks, listed in the blockList (Block List), will make the blood particle behave as if they were over the lava surface."); APPLY.add(() -> hotBlocks.hotBlocks = v.get()); }
        { ForgeConfigSpec.ConfigValue<List<? extends String>> v = b.comment("List of block registry names treated as hot. Examples include minecraft:magma_block, minecraft:furnace and minecraft:campfire.").defineListAllowEmpty(List.of("blockList"), () -> List.of("minecraft:magma_block"), o -> o instanceof String); APPLY.add(() -> hotBlocks.blockList = v.get().toArray(new String[0])); }
        b.pop();
        SPEC = b.build();
        ConfigRegistry.bind(b, SPEC);
    }

    /** Copies the values of the config file into the fields (on load and on change). */
    public static void apply() {
        APPLY.forEach(Runnable::run);
    }
}
