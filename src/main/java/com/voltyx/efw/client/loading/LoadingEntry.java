package com.voltyx.efw.client.loading;

/**
 * One item of the loading screen config. File format (config/efw/loading_screen_items_LANG.json):
 * [{"item": "minecraft:iron_sword", "description": "...", "lore": "..."}]
 * An optional "nbt" (SNBT) picks one variant of a shared item, e.g. a TACZ gun: {"item": "tacz:modern_kinetic_gun",
 * "nbt": "{GunId:\"tacz:ak47\"}"}. Old 1.12 "meta" fields are ignored.
 */
public class LoadingEntry {
    public String item;
    public String nbt;
    public String description;
    public String lore;
}
