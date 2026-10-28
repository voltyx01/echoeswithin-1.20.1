package com.voltyx.efw.search;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.voltyx.efw.EFW;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.io.Reader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * English names of items for the search of the creative inventory and REI, so that items can be found by their English name in any
 * game language (port of mwccf EnglishLanguageMap): every en_us.json of every loaded pack is read once, and reloaded with the resources.
 */
public final class EnglishNames {
    private static volatile Map<String, String> map;

    private EnglishNames() {
    }

    public static void clear() {
        map = null;
    }

    private static Map<String, String> map() {
        Map<String, String> m = map;
        if (m == null) {
            synchronized (EnglishNames.class) {
                if (map == null) {
                    map = load(Minecraft.getInstance().getResourceManager());
                }
                m = map;
            }
        }
        return m;
    }

    private static Map<String, String> load(ResourceManager rm) {
        Map<String, String> out = new HashMap<>();
        for (String ns : rm.getNamespaces()) {
            for (Resource res : rm.getResourceStack(new ResourceLocation(ns, "lang/en_us.json"))) {
                try (Reader reader = res.openAsReader()) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    for (Map.Entry<String, JsonElement> e : json.entrySet()) {
                        if (e.getValue().isJsonPrimitive()) {
                            out.put(e.getKey(), e.getValue().getAsString());
                        }
                    }
                } catch (Exception ignored) {
                    // a broken language file of some pack: skipped
                }
            }
        }
        EFW.LOGGER.info("[Search] {} English names loaded", out.size());
        return out;
    }

    /** The English name of the stack, lower case, or an empty string. */
    public static String of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        try {
            Map<String, String> m = map();
            String key = stack.getDescriptionId();
            Component hover = stack.getHoverName();
            if (hover.getContents() instanceof TranslatableContents tc) {
                key = tc.getKey();
            }
            String name = m.get(key);
            if (name == null) {
                return "";
            }
            if (name.contains("%")) {
                name = name.replace("%s", "").replace("%1$s", "").replace("%2$s", "").replace("%%", "%");
            }
            return name.trim().toLowerCase(Locale.ROOT);
        } catch (RuntimeException e) {
            return "";
        }
    }

    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = EFW.MOD_ID, value = net.minecraftforge.api.distmarker.Dist.CLIENT,
            bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
    public static final class Reload {
        private Reload() {
        }

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void onReload(net.minecraftforge.client.event.RegisterClientReloadListenersEvent event) {
            // read on the background reload thread, so the first search does not stall the game while every en_us.json is parsed
            event.registerReloadListener(new net.minecraft.server.packs.resources.SimplePreparableReloadListener<Map<String, String>>() {
                @Override
                protected Map<String, String> prepare(ResourceManager rm, net.minecraft.util.profiling.ProfilerFiller profiler) {
                    return load(rm);
                }

                @Override
                protected void apply(Map<String, String> names, ResourceManager rm, net.minecraft.util.profiling.ProfilerFiller profiler) {
                    map = names;
                }
            });
        }
    }
}
