package com.voltyx.efw.client.autohud;

import com.voltyx.efw.config.ConfigRegistry;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Settings of the auto-hiding HUD (port of Auto HUD by Crendgrim, config/autohud.json5 -> efw-autohud.toml). Every part of the HUD
 * has its own section: whether it hides at all, what makes it show up, where it slides to and how far.
 */
public final class AutoHudConfig {
    /** What shows a value-driven part (health, food...): as in Auto HUD. */
    public enum Policy {
        ALWAYS, DISABLED, CHANGING, INCREASING, DECREASING, NOT_FULL, LOW
    }

    /** Which other parts come along when one shows up. */
    public enum RevealType {
        /** only the part itself */
        INDIVIDUAL,
        /** the parts stacked on it stay up while it is up, so nothing slides through another */
        STACKED,
        /** every part of the main HUD */
        GROUPED,
        /** all parts */
        COMBINED
    }

    public enum Direction {
        UP, DOWN, LEFT, RIGHT
    }

    public enum EventPolicy {
        NOTHING, REVEAL, HIDE
    }

    /** The settings of one part of the HUD. */
    public static final class Part {
        public final ForgeConfigSpec.BooleanValue active;
        public final ForgeConfigSpec.EnumValue<Policy> policy;
        public final ForgeConfigSpec.EnumValue<Direction> direction;
        public final ForgeConfigSpec.IntValue distance;
        public final ForgeConfigSpec.DoubleValue maximumFade;
        public final ForgeConfigSpec.DoubleValue speed;

        Part(ForgeConfigSpec.Builder b, String name, String what, Policy policy, Direction direction, int distance) {
            ConfigRegistry.section("Auto HUD: " + name);
            b.push(name);
            this.active = ConfigRegistry.bool(b, "active", true, "Hide " + what + " while nothing happens to it.");
            this.policy = b.comment("What shows " + what + ": ALWAYS, DISABLED (only the keys), CHANGING, INCREASING, DECREASING,"
                    + " NOT_FULL, LOW (a third or less).").defineEnum("policy", policy);
            this.direction = b.comment("Where " + what + " slides to when it hides.").defineEnum("direction", direction);
            this.distance = ConfigRegistry.integer(b, "distance", distance, 0, 200, "How far " + what + " slides away.");
            this.maximumFade = ConfigRegistry.decimal(b, "maximumFade", 0.0, 0.0, 1.0,
                    "How visible " + what + " stays when hidden (0 = gone).");
            this.speed = ConfigRegistry.decimal(b, "speed", 1.0, 0.1, 5.0, "Animation speed multiplier of " + what + ".");
            b.pop();
        }
    }

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.IntValue TICKS_REVEALED;
    public static final ForgeConfigSpec.BooleanValue MOVE;
    public static final ForgeConfigSpec.BooleanValue FADE;
    public static final ForgeConfigSpec.DoubleValue SPEED;
    public static final ForgeConfigSpec.EnumValue<RevealType> REVEAL_TYPE;
    public static final ForgeConfigSpec.EnumValue<EventPolicy> ON_SCREEN;
    public static final ForgeConfigSpec.EnumValue<EventPolicy> ON_SNEAK;
    public static final ForgeConfigSpec.BooleanValue ON_USING_ITEM;
    public static final ForgeConfigSpec.BooleanValue ON_MINING;
    public static final ForgeConfigSpec.BooleanValue ON_ATTACKING;
    public static final ForgeConfigSpec.BooleanValue CONSUMABLES;
    public static final ForgeConfigSpec.BooleanValue HOTBAR_ON_SLOT;
    public static final ForgeConfigSpec.BooleanValue HOTBAR_ON_ITEM;
    public static final ForgeConfigSpec.BooleanValue HOTBAR_ON_COUNT;
    public static final ForgeConfigSpec.IntValue HOTBAR_DURABILITY;

    public static final Part HOTBAR;
    public static final Part HEALTH;
    public static final Part ARMOR;
    public static final Part FOOD;
    public static final Part AIR;
    public static final Part EXPERIENCE;
    public static final Part MOUNT_HEALTH;
    public static final Part JUMP_BAR;
    public static final Part STAMINA;
    public static final Part THIRST;
    public static final Part TEMPERATURE;
    public static final Part TACZ_AMMO;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        ConfigRegistry.section("Auto HUD");
        b.push("general");
        ENABLED = ConfigRegistry.bool(b, "enabled", true,
                "Parts of the HUD hide while nothing happens to them and come back when they change (also toggled with its key)."
                        + " The replicas above the hotbar are never hidden.");
        TICKS_REVEALED = ConfigRegistry.integer(b, "ticksRevealed", 60, 10, 400, "How long a part stays after it showed up (ticks).");
        MOVE = ConfigRegistry.bool(b, "animationMove", true, "Hidden parts slide off the screen.");
        FADE = ConfigRegistry.bool(b, "animationFade", true, "Hidden parts fade out.");
        SPEED = ConfigRegistry.decimal(b, "animationSpeed", 2.0, 0.5, 4.0, "Speed of the slide and fade.");
        REVEAL_TYPE = b.comment("INDIVIDUAL, STACKED (parts above one that shows up stay with it), GROUPED (the whole main HUD)"
                + " or COMBINED (everything).").defineEnum("revealType", RevealType.STACKED);
        ON_SCREEN = b.comment("While a screen (inventory, chest...) is open: NOTHING, REVEAL or HIDE the HUD.")
                .defineEnum("onScreenOpen", EventPolicy.REVEAL);
        ON_SNEAK = b.comment("While sneaking: NOTHING, REVEAL or HIDE the HUD.").defineEnum("onSneaking", EventPolicy.NOTHING);
        ON_USING_ITEM = ConfigRegistry.bool(b, "onUsingItem", false, "Show the whole HUD while an item is used (eating, aiming a bow).");
        ON_MINING = ConfigRegistry.bool(b, "onMining", false, "Show the whole HUD while mining.");
        ON_ATTACKING = ConfigRegistry.bool(b, "onAttacking", false, "Show the whole HUD when hitting something.");
        CONSUMABLES = ConfigRegistry.bool(b, "revealBarsWhenHoldingConsumable", true,
                "Holding food shows the food bar (when not full), holding armour shows the armour bar.");
        HOTBAR_ON_SLOT = ConfigRegistry.bool(b, "hotbarOnSlotChange", true, "Show the bottom HUD (hotbar, hearts, food, stamina, LSO bars) when another slot is selected.");
        HOTBAR_ON_ITEM = ConfigRegistry.bool(b, "hotbarOnItemChange", true, "Show the bottom HUD when the held item changes.");
        HOTBAR_ON_COUNT = ConfigRegistry.bool(b, "hotbarOnStackSizeChange", true, "Show the bottom HUD when the held stack grows or shrinks.");
        HOTBAR_DURABILITY = ConfigRegistry.integer(b, "hotbarOnLowDurability", 10, 0, 100,
                "Keep the hotbar up while the held tool has this percentage of durability or less left (0 = off).");
        b.pop();

        b.push("parts");
        HOTBAR = new Part(b, "hotbar", "the hotbar", Policy.CHANGING, Direction.DOWN, 60);
        HEALTH = new Part(b, "health", "the health bar (with LSO's hearts and body damage)", Policy.CHANGING, Direction.DOWN, 60);
        ARMOR = new Part(b, "armor", "the armour bar", Policy.CHANGING, Direction.DOWN, 60);
        FOOD = new Part(b, "food", "the food bar", Policy.LOW, Direction.DOWN, 60);
        AIR = new Part(b, "air", "the air bubbles", Policy.NOT_FULL, Direction.DOWN, 60);
        EXPERIENCE = new Part(b, "experience", "the experience bar", Policy.CHANGING, Direction.DOWN, 60);
        MOUNT_HEALTH = new Part(b, "mountHealth", "the mount's health", Policy.CHANGING, Direction.DOWN, 60);
        JUMP_BAR = new Part(b, "jumpBar", "the mount's jump bar", Policy.CHANGING, Direction.DOWN, 60);
        STAMINA = new Part(b, "stamina", "the stamina bar (and the roll cooldown)", Policy.NOT_FULL, Direction.DOWN, 60);
        THIRST = new Part(b, "thirst", "LSO's thirst bar", Policy.LOW, Direction.DOWN, 60);
        TEMPERATURE = new Part(b, "temperature", "LSO's temperature and wetness (cold, heat and getting wet are told by a replica instead)",
                Policy.CHANGING, Direction.DOWN, 60);
        TACZ_AMMO = new Part(b, "taczAmmo", "the TACZ ammo counter (shows up when the gun, its ammo or fire mode change)",
                Policy.CHANGING, Direction.RIGHT, 100);
        b.pop();

        SPEC = b.build();
        ConfigRegistry.bind(b, SPEC);
    }

    private AutoHudConfig() {
    }
}
