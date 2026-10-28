package com.voltyx.efw.sins.stat;

/** Moments an effect can follow ("after": the effect works for N seconds after it). Fired on the server, mirrored to the client. */
public enum Trigger {
    ROLL("roll", "после кувырка"),
    KILL("kill", "после убийства"),
    KILL_MELEE("kill_melee", "после убийства в ближнем бою"),
    KILL_GUN("kill_gun", "после убийства из огнестрела"),
    HEADSHOT("headshot", "после попадания в голову"),
    HIT_MELEE("hit_melee", "после удара"),
    HURT("hurt", "после ранения"),
    RELOAD("reload", "после перезарядки"),
    SEARCH("search", "после обыска"),
    EAT("eat", "после еды"),
    STIM("stim", "после стимулятора"),
    WAKE("wake", "после сна"),
    VOMIT("vomit", "после приступа рвоты"),
    DOLL("doll", "после срыва с куклой");

    public final String id;
    public final String textRu;

    Trigger(String id, String textRu) {
        this.id = id;
        this.textRu = textRu;
    }

    public static Trigger byId(String id) {
        for (Trigger t : values()) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        return null;
    }
}
