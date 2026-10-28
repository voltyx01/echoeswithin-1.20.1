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

package com.voltyx.efw.gender.main.networking;

import com.voltyx.efw.gender.main.Breasts;
import com.voltyx.efw.gender.main.GenderPlayer;
import com.voltyx.efw.gender.main.GenderPlayer.Gender;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;

public abstract class PacketGenderInfo {
    protected final UUID uuid;
    private final Gender gender;
    private final float bust_size;

    //physics variables
    private final boolean breast_physics;
    private final boolean show_in_armor;
    private final float bounceMultiplier;
    private final float floppyMultiplier;

    private final float xOffset, yOffset, zOffset;
    private final boolean uniboob;
    private final float cleavage;

    private final boolean hurtSounds;

    //blinking eyes
    private final boolean blinkEnabled;
    private final byte eyeSize, eyeDistance, eyeHeight, eyelidSize, eyelidOffsetX, eyelidOffsetY;
    private final boolean dualEyelid;
    private final float blinkFrequency;

    protected PacketGenderInfo(GenderPlayer plr) {
        this.uuid = plr.uuid;
        this.gender = plr.getGender();
        this.bust_size = plr.getBustSize();
        this.hurtSounds = plr.hasHurtSounds();

        //physics variables
        this.breast_physics = plr.hasBreastPhysics();
        this.show_in_armor = plr.showBreastsInArmor();
        this.bounceMultiplier = plr.getBounceMultiplierRaw();
        this.floppyMultiplier = plr.getFloppiness();

        Breasts breasts = plr.getBreasts();
        this.xOffset = breasts.getXOffset();
        this.yOffset = breasts.getYOffset();
        this.zOffset = breasts.getZOffset();

        this.uniboob = breasts.isUniboob();
        this.cleavage = breasts.getCleavage();

        this.blinkEnabled = plr.isBlinkEnabled();
        this.eyeSize = (byte) plr.getEyeSize();
        this.eyeDistance = (byte) plr.getEyeDistance();
        this.eyeHeight = (byte) plr.getEyeHeight();
        this.eyelidSize = (byte) plr.getEyelidSize();
        this.eyelidOffsetX = (byte) plr.getEyelidOffsetX();
        this.eyelidOffsetY = (byte) plr.getEyelidOffsetY();
        this.dualEyelid = plr.isDualEyelid();
        this.blinkFrequency = plr.getBlinkFrequency();
    }

    protected PacketGenderInfo(FriendlyByteBuf buffer) {
        this.uuid = buffer.readUUID();
        this.gender = buffer.readEnum(Gender.class);
        this.bust_size = buffer.readFloat();
        this.hurtSounds = buffer.readBoolean();

        //physics variables
        this.breast_physics = buffer.readBoolean();
        this.show_in_armor = buffer.readBoolean();
        this.bounceMultiplier = buffer.readFloat();
        this.floppyMultiplier = buffer.readFloat();

        this.xOffset = buffer.readFloat();
        this.yOffset = buffer.readFloat();
        this.zOffset = buffer.readFloat();
        this.uniboob = buffer.readBoolean();
        this.cleavage = buffer.readFloat();

        this.blinkEnabled = buffer.readBoolean();
        this.eyeSize = buffer.readByte();
        this.eyeDistance = buffer.readByte();
        this.eyeHeight = buffer.readByte();
        this.eyelidSize = buffer.readByte();
        this.eyelidOffsetX = buffer.readByte();
        this.eyelidOffsetY = buffer.readByte();
        this.dualEyelid = buffer.readBoolean();
        this.blinkFrequency = buffer.readFloat();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(this.uuid);
        buffer.writeEnum(this.gender);
        buffer.writeFloat(this.bust_size);
        buffer.writeBoolean(this.hurtSounds);
        buffer.writeBoolean(this.breast_physics);
        buffer.writeBoolean(this.show_in_armor);
        buffer.writeFloat(this.bounceMultiplier);
        buffer.writeFloat(this.floppyMultiplier);

        buffer.writeFloat(this.xOffset);
        buffer.writeFloat(this.yOffset);
        buffer.writeFloat(this.zOffset);
        buffer.writeBoolean(this.uniboob);
        buffer.writeFloat(this.cleavage);

        buffer.writeBoolean(this.blinkEnabled);
        buffer.writeByte(this.eyeSize);
        buffer.writeByte(this.eyeDistance);
        buffer.writeByte(this.eyeHeight);
        buffer.writeByte(this.eyelidSize);
        buffer.writeByte(this.eyelidOffsetX);
        buffer.writeByte(this.eyelidOffsetY);
        buffer.writeBoolean(this.dualEyelid);
        buffer.writeFloat(this.blinkFrequency);
    }

    protected void updatePlayerFromPacket(GenderPlayer plr) {
        plr.updateGender(gender);
        plr.updateBustSize(bust_size);
        plr.updateHurtSounds(hurtSounds);

        //physics
        plr.updateBreastPhysics(breast_physics);
        plr.updateShowBreastsInArmor(show_in_armor);
        plr.updateBounceMultiplier(bounceMultiplier);
        plr.updateFloppiness(floppyMultiplier);
        //WildfireGender.logger.debug("{} - {}", plr.username, plr.gender);

        Breasts breasts = plr.getBreasts();
        breasts.updateXOffset(xOffset);
        breasts.updateYOffset(yOffset);
        breasts.updateZOffset(zOffset);
        breasts.updateUniboob(uniboob);
        breasts.updateCleavage(cleavage);

        plr.updateBlinking(blinkEnabled);
        plr.updateEyeSize((int) eyeSize);
        plr.updateEyeDistance((int) eyeDistance);
        plr.updateEyeHeight((int) eyeHeight);
        plr.updateEyelidSize((int) eyelidSize);
        plr.updateEyelidOffsetX((int) eyelidOffsetX);
        plr.updateEyelidOffsetY((int) eyelidOffsetY);
        plr.updateDualEyelid(dualEyelid);
        plr.updateBlinkFrequency(blinkFrequency);
    }
}
