package com.voltyx.efw.client.dark;

import com.mojang.blaze3d.platform.NativeImage;
import com.voltyx.efw.EFW;
import com.voltyx.efw.config.ConfigRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.File;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Universal dark mode for every GUI screen, vanilla or modded: GUI textures are recoloured procedurally the first time a
 * screen binds them (port of DarkGuiManager). Pre-made replacements can be dropped into config/efw/darkmode/assets/NAMESPACE/PATH.
 */
@Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = Dist.CLIENT)
public final class DarkGui {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.BooleanValue MENU_STYLE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        ConfigRegistry.section("Dark GUI");
        b.push("darkgui");
        ENABLED = ConfigRegistry.bool(b, "enabled", true, "Dark theme for all GUI screens, tooltips and container labels.");
        MENU_STYLE = ConfigRegistry.bool(b, "menuStyle", false,
                "Dark theme v2: the colours and the buttons of the modpack main menu (warm dark grey, dark red when hovered).");
        b.pop();
        SPEC = b.build();
        ConfigRegistry.bind(b, SPEC);
    }

    private static final Map<ResourceLocation, ResourceLocation> CACHE = new ConcurrentHashMap<>();
    private static Boolean cachedStyle;
    private static boolean generating;
    private static int screenDepth;
    private static int hudDepth;

    private DarkGui() {
    }

    public static boolean isEnabled() {
        return ENABLED.get();
    }

    /** True only while a screen is drawing, so the HUD keeps its normal look when nothing is open. */
    private static boolean inScreen() {
        return screenDepth > 0 && Minecraft.getInstance().screen != null;
    }

    /** The hotbar and the hearts (the vanilla HUD) take the theme too, with or without a screen on top. */
    private static boolean inHudUnderContainer() {
        return false; // the hotbar theme is switched off (hudDepth is still tracked)
    }

    private static boolean isVanillaHud(ResourceLocation res) {
        return res.getNamespace().equals("minecraft") && (res.getPath().equals("textures/gui/widgets.png") || res.getPath().equals("textures/gui/icons.png"));
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onHudPre(net.minecraftforge.client.event.RenderGuiEvent.Pre e) {
        hudDepth++;
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onHudPost(net.minecraftforge.client.event.RenderGuiEvent.Post e) {
        if (hudDepth > 0) {
            hudDepth--;
        }
    }

    public static boolean isContainerOpen() {
        return inScreen() && Minecraft.getInstance().screen instanceof AbstractContainerScreen<?>;
    }

    @SubscribeEvent
    public static void onScreenPre(ScreenEvent.Render.Pre e) {
        screenDepth++;
    }

    @SubscribeEvent
    public static void onScreenPost(ScreenEvent.Render.Post e) {
        if (screenDepth > 0) {
            screenDepth--;
        }
    }

    @SubscribeEvent
    public static void onTooltipColor(RenderTooltipEvent.Color e) {
        if (isEnabled() && MENU_STYLE.get()) {
            e.setBackgroundStart(0xF0211D20);
            e.setBackgroundEnd(0xF0211D20);
            e.setBorderStart(0xFF481F21);
            e.setBorderEnd(0xFF2B2729);
        } else if (isEnabled()) {
            e.setBackgroundStart(0xF0181818);
            e.setBackgroundEnd(0xF0181818);
            e.setBorderStart(0xFF383838);
            e.setBorderEnd(0xFF383838);
        }
    }

    /** Called by the RenderSystem.setShaderTexture mixin. */
    public static ResourceLocation replace(ResourceLocation original) {
        if (original == null || generating || !isEnabled()) {
            return original;
        }
        boolean menu = MENU_STYLE.get();
        if (cachedStyle == null || cachedStyle != menu) {
            // the style was switched: the recoloured textures are made again
            CACHE.clear();
            cachedStyle = menu;
        }
        boolean hud = !inScreen() && inHudUnderContainer() && isVanillaHud(original);
        if (!hud && (!inScreen() || !shouldProcess(original))) {
            return original;
        }
        ResourceLocation cached = CACHE.get(original);
        return cached != null ? cached : create(original);
    }

    /** Container label colours (vanilla 0x404040 and friends) become light. */
    public static int adjustTextColor(int color) {
        if (isEnabled() && isContainerOpen()) {
            int rgb = color & 0xFFFFFF;
            if (rgb == 0x404040 || rgb == 0x373737 || rgb == 0x303030 || rgb == 0x222222) {
                return (color & 0xFF000000) | 0xE0E0E0;
            }
        }
        return color;
    }

    /** A grey panel drawn by code instead of a texture (ragdollifiedpc's screens): the grey the theme turns the textures into. */
    public static int fillColor(int argb) {
        int r = (argb >> 16) & 0xFF;
        if (!isEnabled() || r != ((argb >> 8) & 0xFF) || r != (argb & 0xFF)) {
            return argb;
        }
        return (argb & 0xFF000000) | DarkGuiFilter.shadeRgb(r, MENU_STYLE.get());
    }

    /** Dark grey text drawn on such a panel, outside a container (where adjustTextColor does it), becomes light. */
    public static int lightText(int color) {
        if (!isEnabled()) {
            return color;
        }
        return switch (color & 0xFFFFFF) {
            case 0x404040, 0x333333 -> (color & 0xFF000000) | 0xE0E0E0;
            case 0x555555 -> (color & 0xFF000000) | 0xA8A8A8;
            default -> color;
        };
    }

    /** Quark's button sheet (inventory sort / transfer buttons): grey buttons with white glyphs, so it needs the glyph-keeping filter. */
    private static boolean isButtonSheet(ResourceLocation res) {
        return (res.getNamespace().equals("quark") || res.getNamespace().equals("zeta")) && res.getPath().endsWith("gui/general_icons.png");
    }

    private static boolean shouldProcess(ResourceLocation res) {
        String path = res.getPath();
        String domain = res.getNamespace();
        if (!path.endsWith(".png")) {
            return false;
        }
        if (!path.startsWith("textures/gui/") && !path.contains("container") && !path.contains("/gui/") && !path.contains("gui_")) {
            return false;
        }
        if (domain.equals(EFW.MOD_ID) || domain.contains("fancymenu") || domain.contains("sins") || domain.contains("wildfire")
                || domain.equals("cosmeticarmorreworked") || domain.equals("curios")) {
            return false;
        }
        if (isButtonSheet(res)) {
            return true;
        }
        // HUD, backgrounds, title screen and our own overlays stay as they are
        return !(path.contains("icons") || path.contains("options_background") || path.contains("background") || path.contains("dirt")
                || path.contains("bars.png") || path.contains("/title/") || path.contains("toasts") || path.contains("overlay")
                || path.contains("hud") || path.contains("crosshair") || path.contains("blood") || path.contains("stamina")
                || path.contains("dash") || path.contains("roll") || path.contains("dodge") || path.contains("wardrobe")
                || path.contains("wildfire") || path.contains("seven") || path.contains("sins"));
    }

    /** The hand-drawn dark versions of the vanilla screens shipped with the mod, used when the size matches the real texture. */
    private static NativeImage handDrawn(ResourceLocation original, NativeImage base) {
        ResourceLocation pre = new ResourceLocation(EFW.MOD_ID, "darkmode/" + original.getNamespace() + "/" + original.getPath());
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(pre);
        if (res.isEmpty()) {
            return null;
        }
        try (InputStream in = res.get().open()) {
            NativeImage img = NativeImage.read(in);
            if (img.getWidth() == base.getWidth() && img.getHeight() == base.getHeight()) {
                return img;
            }
            img.close();
        } catch (Exception ignored) {
            // procedural filter instead
        }
        return null;
    }

    private static ResourceLocation create(ResourceLocation original) {
        // a texture made for the other style must not be found again under the same name
        String style = MENU_STYLE.get() ? "menu/" : "";
        generating = true;
        try {
            NativeImage img = null;
            File override = new File(net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().toFile(),
                    "efw/darkmode/assets/" + original.getNamespace() + "/" + original.getPath());
            if (override.isFile()) {
                try (java.io.FileInputStream in = new java.io.FileInputStream(override)) {
                    img = NativeImage.read(in);
                } catch (Exception ignored) {
                    // fall back to the procedural filter
                }
            }
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(original);
            if (res.isPresent()) {
                try (InputStream in = res.get().open(); NativeImage base = NativeImage.read(in)) {
                    if (img == null) {
                        img = handDrawn(original, base);
                    }
                    if (img == null) {
                        boolean widgets = original.getPath().endsWith("widgets.png");
                        img = isButtonSheet(original) ? DarkGuiFilter.transformButtonSheet(base) : DarkGuiFilter.transform(base, widgets, MENU_STYLE.get());
                        if (widgets && MENU_STYLE.get() && original.getNamespace().equals("minecraft")) {
                            DarkGuiFilter.paintMenuButtons(img);
                        }
                    }
                }
            }
            if (img == null) {
                CACHE.put(original, original);
                return original;
            }
            ResourceLocation dyn = new ResourceLocation(EFW.MOD_ID, "darkgui/" + style + Math.abs(original.toString().hashCode()));
            Minecraft.getInstance().getTextureManager().register(dyn, new DynamicTexture(img));
            CACHE.put(original, dyn);
            return dyn;
        } catch (Throwable t) {
            EFW.LOGGER.warn("Dark GUI skipped {}: {}", original, t.toString());
            CACHE.put(original, original);
            return original;
        } finally {
            generating = false;
        }
    }
}
