package com.voltyx.efw.doll;

import com.voltyx.efw.config.ConfigRegistry;
import net.minecraftforge.common.ForgeConfigSpec;

/** Settings of Saya's doll: the ability (common) and how the doll looks in the hand, in the inventory and on the belt (client). */
public final class DollConfig {
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final ForgeConfigSpec CLIENT_SPEC;

    public static final ForgeConfigSpec.IntValue READY_BLOOD_PERCENT;
    public static final ForgeConfigSpec.IntValue COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.DoubleValue BUFF_SECONDS;
    public static final ForgeConfigSpec.DoubleValue ACTIVATION_SECONDS;
    public static final ForgeConfigSpec.DoubleValue MELEE_DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue KILL_BONUS_MELEE_SECONDS;
    public static final ForgeConfigSpec.DoubleValue KILL_BONUS_FIREARM_SECONDS;
    public static final ForgeConfigSpec.IntValue ACTIVATION_SLOWNESS;

    public static final ForgeConfigSpec.BooleanValue SHOW_ON_BELT;
    public static final ForgeConfigSpec.BooleanValue WORLD_LIGHTING;
    public static final ForgeConfigSpec.DoubleValue INERTIA;
    public static final ForgeConfigSpec.DoubleValue HAND_X;
    public static final ForgeConfigSpec.DoubleValue HAND_Y;
    public static final ForgeConfigSpec.DoubleValue HAND_Z;
    public static final ForgeConfigSpec.DoubleValue HAND_ROT_X;
    public static final ForgeConfigSpec.DoubleValue HAND_ROT_Y;
    public static final ForgeConfigSpec.DoubleValue HAND_ROT_Z;
    public static final ForgeConfigSpec.DoubleValue HAND_SCALE;
    public static final ForgeConfigSpec.DoubleValue ANIM_SPEED;
    public static final ForgeConfigSpec.DoubleValue APPEAR_SPEED;
    public static final ForgeConfigSpec.DoubleValue HIDE_SPEED;
    public static final ForgeConfigSpec.DoubleValue SLIDE_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue GUI_SCALE;
    public static final ForgeConfigSpec.DoubleValue GUI_OFFSET_Y;

    static {
        ForgeConfigSpec.Builder common = new ForgeConfigSpec.Builder();
        ConfigRegistry.section("Saya's doll");
        common.push("doll");
        READY_BLOOD_PERCENT = ConfigRegistry.integer(common, "readyBloodPercent", 75, 1, 100,
                "The doll wakes up when the blood on the body is over this percent.");
        COOLDOWN_SECONDS = ConfigRegistry.integer(common, "cooldownSeconds", 300, 0, 3600, "Time between two activations.");
        BUFF_SECONDS = ConfigRegistry.decimal(common, "buffSeconds", 18.0, 1.0, 120.0, "Length of the frenzy.");
        ACTIVATION_SECONDS = ConfigRegistry.decimal(common, "activationSeconds", 3.0, 1.0, 10.0, "How long the doll is held out before the frenzy starts.");
        MELEE_DAMAGE_MULTIPLIER = ConfigRegistry.decimal(common, "meleeDamageMultiplier", 1.5, 1.0, 5.0, "Damage of melee hits during the frenzy (firearms are not changed).");
        KILL_BONUS_MELEE_SECONDS = ConfigRegistry.decimal(common, "killBonusMeleeSeconds", 2.0, 0.0, 15.0, "Time added to the frenzy by a melee kill.");
        KILL_BONUS_FIREARM_SECONDS = ConfigRegistry.decimal(common, "killBonusFirearmSeconds", 1.0, 0.0, 15.0, "Time added to the frenzy by a firearm kill.");
        ACTIVATION_SLOWNESS = ConfigRegistry.integer(common, "activationSlowness", 3, 0, 5, "Level of slowness (0 = I) while the doll is held out.");
        common.pop();
        COMMON_SPEC = common.build();
        ConfigRegistry.bind(common, COMMON_SPEC);

        ForgeConfigSpec.Builder client = new ForgeConfigSpec.Builder();
        ConfigRegistry.section("Saya's doll (look)");
        client.push("dollLook");
        SHOW_ON_BELT = ConfigRegistry.bool(client, "showOnBelt", true, "Draw the doll on the belt while it is carried.");
        WORLD_LIGHTING = ConfigRegistry.bool(client, "worldLighting", true, "The doll in the hand is lit by the light of the world.");
        INERTIA = ConfigRegistry.decimal(client, "inertia", 1.0, 0.0, 3.0, "How much the doll in the hand lags behind the camera and the steps.");
        HAND_X = ConfigRegistry.decimal(client, "handX", 0.0, -3.0, 3.0, "Position of the doll in the hand, sideways.");
        HAND_Y = ConfigRegistry.decimal(client, "handY", 0.24, -3.0, 3.0, "Position of the doll in the hand, up.");
        HAND_Z = ConfigRegistry.decimal(client, "handZ", -1.32, -4.0, 1.0, "Position of the doll in the hand, forward.");
        HAND_ROT_X = ConfigRegistry.decimal(client, "handRotX", -8.0, -180.0, 180.0, "Pitch of the doll in the hand.");
        HAND_ROT_Y = ConfigRegistry.decimal(client, "handRotY", 180.0, -180.0, 180.0, "Yaw of the doll in the hand.");
        HAND_ROT_Z = ConfigRegistry.decimal(client, "handRotZ", 0.0, -180.0, 180.0, "Roll of the doll in the hand.");
        HAND_SCALE = ConfigRegistry.decimal(client, "handScale", 1.0, 0.1, 3.0, "Size of the doll in the hand.");
        ANIM_SPEED = ConfigRegistry.decimal(client, "animationSpeed", 1.0, 0.1, 3.0, "Speed of the raising animation.");
        APPEAR_SPEED = ConfigRegistry.decimal(client, "appearSpeed", 0.16, 0.02, 1.0, "How fast the doll comes up (progress per tick).");
        HIDE_SPEED = ConfigRegistry.decimal(client, "hideSpeed", 0.145, 0.02, 1.0, "How fast the doll goes down (progress per tick).");
        SLIDE_DISTANCE = ConfigRegistry.decimal(client, "slideDistance", 1.77, 0.0, 4.0, "How far below the screen the doll starts.");
        GUI_SCALE = ConfigRegistry.decimal(client, "guiScale", 0.9, 0.2, 3.0, "Size of the doll in inventory slots.");
        GUI_OFFSET_Y = ConfigRegistry.decimal(client, "guiOffsetY", -1.1, -3.0, 3.0, "Vertical offset of the doll in inventory slots.");
        client.pop();
        CLIENT_SPEC = client.build();
        ConfigRegistry.bind(client, CLIENT_SPEC);
    }

    private DollConfig() {
    }
}
