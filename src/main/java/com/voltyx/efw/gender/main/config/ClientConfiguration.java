/*
    Wildfire's Female Gender Mod is a female gender mod created for Minecraft.
    Copyright (C) 2023 WildfireRomeo

    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU Lesser General Public
    License as published by the Free Software Foundation; either
    version 3 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
    Lesser General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

package com.voltyx.efw.gender.main.config;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class ClientConfiguration extends Configuration {

    public static final UUIDConfigKey USERNAME = new UUIDConfigKey("username", UUID.nameUUIDFromBytes("UNKNOWN".getBytes(StandardCharsets.UTF_8)));
    public static final GenderConfigKey GENDER = new GenderConfigKey("gender");
    public static final FloatConfigKey BUST_SIZE = new FloatConfigKey("bust_size", 0.6F, 0, 1.2f);
    public static final BooleanConfigKey HURT_SOUNDS = new BooleanConfigKey("hurt_sounds", true);

    public static final FloatConfigKey BREASTS_OFFSET_X = new FloatConfigKey("breasts_xOffset", 0.0F, -1, 1);
    public static final FloatConfigKey BREASTS_OFFSET_Y = new FloatConfigKey("breasts_yOffset", 0.0F, -1, 1);
    public static final FloatConfigKey BREASTS_OFFSET_Z = new FloatConfigKey("breasts_zOffset", 0.0F, -1, 0);
    public static final BooleanConfigKey BREASTS_UNIBOOB = new BooleanConfigKey("breasts_uniboob", true);
    public static final FloatConfigKey BREASTS_CLEAVAGE = new FloatConfigKey("breasts_cleavage", 0.05F, 0, 0.1F);

    public static final BooleanConfigKey BREAST_PHYSICS = new BooleanConfigKey("breast_physics", false);
    public static final BooleanConfigKey ARMOR_PHYSICS_OVERRIDE = new BooleanConfigKey("armor_physics_override", false);
    public static final BooleanConfigKey SHOW_IN_ARMOR = new BooleanConfigKey("show_in_armor", true);
    public static final FloatConfigKey BOUNCE_MULTIPLIER = new FloatConfigKey("bounce_multiplier", 0.34F, 0, 1f);
    public static final FloatConfigKey FLOPPY_MULTIPLIER = new FloatConfigKey("floppy_multiplier", 0.95F, 0, 1);

    // blinking eyes (ported from mwccf): where the eyes are on the face and which skin pixels are the eyelids
    public static final BooleanConfigKey BLINK_ENABLED = new BooleanConfigKey("blink_enabled", false);
    public static final IntConfigKey EYE_SIZE = new IntConfigKey("eye_size", 1, 0, 2);
    public static final IntConfigKey EYE_DISTANCE = new IntConfigKey("eye_distance", 1, 1, 4);
    public static final IntConfigKey EYE_HEIGHT = new IntConfigKey("eye_height", 4, 0, 8);
    public static final IntConfigKey EYELID_SIZE = new IntConfigKey("eyelid_size", 1, 0, 2);
    public static final BooleanConfigKey DUAL_EYELID = new BooleanConfigKey("dual_eyelid", false);
    public static final FloatConfigKey BLINK_FREQUENCY = new FloatConfigKey("blink_frequency", 1.15F, 0.8F, 1.35F);
    public static final IntConfigKey EYELID_OFFSET_X = new IntConfigKey("eyelid_offset_x", 0, -8, 8);
    public static final IntConfigKey EYELID_OFFSET_Y = new IntConfigKey("eyelid_offset_y", -2, -8, 8);


    public ClientConfiguration(String saveLoc, String cfgName) {
        super(saveLoc, cfgName);
    }
}
