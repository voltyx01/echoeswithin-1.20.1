package com.voltyx.efw.tech.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.voltyx.efw.EFW;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Where the bodycam sits on its carrier, per stance, in config/efw/bodycam_pose.json; tuned in game with /bodycamdebug
 * ({@link BodycamDebugScreen}). Offsets are in blocks from the carrier's feet along its torso; angles in degrees.
 */
public final class BodycamPose {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public enum Stance {
        STAND("Стоя"), CROUCH("Присед"), CRAWL("Лёжа");

        public final String label;

        Stance(String label) {
            this.label = label;
        }
    }

    public static class Mount {
        public double height;
        public double forward;
        /** To the carrier's left; negative is the right side of the chest. */
        public double left = -0.06;
        public float pitch;
        /** Added to the torso yaw. */
        public float yaw = -3.0f;
        /** Tilt of the picture. */
        public float roll;

        Mount(double height, double forward, float pitch) {
            this.height = height;
            this.forward = forward;
            this.pitch = pitch;
        }
    }

    private static class Data {
        float fov = 85.0f;
        /** The carrier's own arms and the items in its hands, seen from inside the chest, can be left out of the picture. */
        boolean hideArms;
        boolean hideHeld;
        Mount stand = defaults(Stance.STAND);
        Mount crouch = defaults(Stance.CROUCH);
        Mount crawl = defaults(Stance.CRAWL);
    }

    private static Data data;

    private BodycamPose() {
    }

    /** The original BodycamFeed values: collarbone mount, lower when sneaking, near the ground when crawling. */
    public static Mount defaults(Stance s) {
        return switch (s) {
            case STAND -> new Mount(1.53, 0.14, 10.0f);
            case CROUCH -> new Mount(1.25, 0.22, 16.0f);
            case CRAWL -> new Mount(0.32, 0.35, 5.0f);
        };
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("efw").resolve("bodycam_pose.json");
    }

    private static Data data() {
        if (data == null) {
            try {
                if (Files.exists(file())) {
                    try (Reader r = Files.newBufferedReader(file(), StandardCharsets.UTF_8)) {
                        data = GSON.fromJson(r, Data.class);
                    }
                }
            } catch (Exception e) {
                EFW.LOGGER.warn("[Bodycam] cannot read bodycam_pose.json: {}", e.toString());
            }
            if (data == null) {
                data = new Data();
            }
            for (Stance s : Stance.values()) {
                if (get(s) == null) {
                    reset(s);
                }
            }
        }
        return data;
    }

    public static Mount get(Stance s) {
        Data d = data == null ? data() : data;
        return switch (s) {
            case STAND -> d.stand;
            case CROUCH -> d.crouch;
            case CRAWL -> d.crawl;
        };
    }

    public static void reset(Stance s) {
        Data d = data == null ? data() : data;
        switch (s) {
            case STAND -> d.stand = defaults(s);
            case CROUCH -> d.crouch = defaults(s);
            case CRAWL -> d.crawl = defaults(s);
        }
    }

    public static float fov() {
        return data().fov;
    }

    public static void setFov(float fov) {
        data().fov = fov;
    }

    public static boolean hideArms() {
        return data().hideArms;
    }

    public static void setHideArms(boolean hide) {
        data().hideArms = hide;
    }

    public static boolean hideHeld() {
        return data().hideHeld;
    }

    public static void setHideHeld(boolean hide) {
        data().hideHeld = hide;
    }

    public static void save() {
        try {
            Files.createDirectories(file().getParent());
            try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
                GSON.toJson(data(), w);
            }
        } catch (Exception e) {
            EFW.LOGGER.warn("[Bodycam] cannot write bodycam_pose.json: {}", e.toString());
        }
    }
}
