package com.voltyx.efw.tech;

import net.minecraft.network.FriendlyByteBuf;

/** One camera in the terminal's bodycam list (port of BodycamEntry). */
public class BodycamEntry {
    public String camId = "CAM-0000";
    public String carrierName = "Unknown";
    public int carrierEntityId = -1;
    public boolean isOnline;
    public int batteryPercent;
    /** TUI check mark */
    public boolean isChecked = true;

    public BodycamEntry() {
    }

    public BodycamEntry(String camId, String carrierName, int carrierEntityId, boolean isOnline, int batteryPercent) {
        this.camId = camId;
        this.carrierName = carrierName;
        this.carrierEntityId = carrierEntityId;
        this.isOnline = isOnline;
        this.batteryPercent = batteryPercent;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(camId);
        buf.writeUtf(carrierName);
        buf.writeInt(carrierEntityId);
        buf.writeBoolean(isOnline);
        buf.writeInt(batteryPercent);
        buf.writeBoolean(isChecked);
    }

    public static BodycamEntry read(FriendlyByteBuf buf) {
        BodycamEntry e = new BodycamEntry();
        e.camId = buf.readUtf();
        e.carrierName = buf.readUtf();
        e.carrierEntityId = buf.readInt();
        e.isOnline = buf.readBoolean();
        e.batteryPercent = buf.readInt();
        e.isChecked = buf.readBoolean();
        return e;
    }
}
