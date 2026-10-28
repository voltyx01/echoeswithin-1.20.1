package com.voltyx.efw.sins.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.voltyx.efw.EFW;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.joml.Matrix4f;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * The lycoris flower of the Skills tab (port of the 1.12 BedrockFlowerRenderer): a bedrock geometry with its open/close
 * animation, procedural petal sway, and the smoulder wave that burns one petal per unlocked skill, with 3D sparks.
 */
public class FlowerRenderer {
    public static final ResourceLocation MODEL_GEO = new ResourceLocation(EFW.MOD_ID, "geo/flower.geo.json");
    public static final ResourceLocation MODEL_ANIM = new ResourceLocation(EFW.MOD_ID, "animations/flower.animation.json");
    public static final ResourceLocation MODEL_TEX = new ResourceLocation(EFW.MOD_ID, "textures/entity/flower.png");

    private float textureWidth = 32f;
    private float textureHeight = 32f;

    private final Map<String, Bone> bonesByName = new LinkedHashMap<>();
    private final List<Bone> rootBones = new ArrayList<>();
    private final Map<String, Animation> animations = new HashMap<>();

    private boolean loaded = false;
    private float animTime = 0f;
    private final String currentAnimName = "flower_open";
    private boolean animPlaying = true;

    // smoulder wave
    public static float waveFireWidth = 0.045f; // thickness of the orange burning wave
    public static float waveAshWidth = 0.16f; // grey ash gradient ahead of it
    public static float waveSpeed = 1.0f;
    public static float waveAngle = 20.0f; // wave angle relative to the petal base

    private final List<Petal> petals = new ArrayList<>();
    private final List<Spark3D> sparks3D = new ArrayList<>();
    private final Random rand = new Random();
    private float glowTime = 0f;

    // model-space matrices of the bone hierarchy (spark spawn points and petal obstacles)
    private final ModelMatrix[] matrixStack = new ModelMatrix[16];
    private final ModelMatrix cubeMatrix = new ModelMatrix();

    {
        for (int i = 0; i < matrixStack.length; i++) {
            matrixStack[i] = new ModelMatrix();
        }
    }

    /** Small petals as quads: sparks that fly under them are removed. */
    public static class PetalObstacle {
        public final float[] v0 = new float[3];
        public final float[] v1 = new float[3];
        public final float[] v2 = new float[3];
        public final float[] v3 = new float[3];
        public int petalId = -1;
        public boolean valid = false;
    }

    private final PetalObstacle[] smallPetalObstacles = new PetalObstacle[16];
    private int smallPetalObstacleCount = 0;

    {
        for (int i = 0; i < smallPetalObstacles.length; i++) {
            smallPetalObstacles[i] = new PetalObstacle();
        }
    }

    /** Order in which petals ignite as skills get unlocked. */
    public static final int[] PETAL_BURN_QUEUE = new int[] {5, 7, 4, 8, 3, 9, 10, 0, 11, 1, 6, 2, 7};

    /** Which petal each skill (0..12) ignited. */
    private final int[] skillToPetal = new int[13];

    private void initPetals() {
        if (!petals.isEmpty()) {
            return;
        }
        Arrays.fill(skillToPetal, -1);
        // 6 big petals: base -> mid -> mid2 -> tip bones
        petals.add(new Petal(0, true, 0f, "group27", "group28", "group29", "bone2"));
        petals.add(new Petal(1, true, 60f, "group30", "group31", "group32", "bone"));
        petals.add(new Petal(2, true, 120f, "group33", "group34", "group35", "bone6"));
        petals.add(new Petal(3, true, -60f, "group36", "group37", "group38", "bone3"));
        petals.add(new Petal(4, true, -120f, "group39", "group40", "group41", "bone4"));
        petals.add(new Petal(5, true, 180f, "group42", "group43", "group44", "bone5"));
        // 6 small petals
        petals.add(new Petal(6, false, 90f, "groupx", "group19"));
        petals.add(new Petal(7, false, 150f, "groupx2", "group20"));
        petals.add(new Petal(8, false, 210f, "groupx3", "group21"));
        petals.add(new Petal(9, false, 270f, "groupx4", "group22"));
        petals.add(new Petal(10, false, 330f, "groupx5", "group23"));
        petals.add(new Petal(11, false, 30f, "groupx6", "group24"));
    }

    public static class Petal {
        public final int id;
        public final boolean isBig;
        public final List<String> boneNames = new ArrayList<>();
        /** Direction around the flower centre (XZ plane, degrees). */
        public final float centerAngleDeg;

        public boolean burning = false;
        public float burnProgress = 0f; // 0..1, kept at 1.2 when burnt out
        public boolean flipDirection = false;
        public float burnSwayBlend = 0f; // 0 = calm sway, 1 = burning sway

        public Petal(int id, boolean isBig, float angleDeg, String... bones) {
            this.id = id;
            this.isBig = isBig;
            this.centerAngleDeg = angleDeg;
            boneNames.addAll(Arrays.asList(bones));
        }

        public boolean containsBone(String bName) {
            return boneNames.contains(bName);
        }

        public int getBoneSegmentIndex(String bName) {
            return boneNames.indexOf(bName);
        }
    }

    public Petal getPetalForBone(String boneName) {
        for (Petal p : petals) {
            if (p.containsBone(boneName)) {
                return p;
            }
        }
        return null;
    }

    public static class Spark3D {
        public float x, y, z;
        public float vx, vy, vz;
        public float length;
        public float age, maxAge;
        public int sourcePetalId;

        public Spark3D(float x, float y, float z, Random rand, int sourcePetalId) {
            this.x = x + (rand.nextFloat() - 0.5f) * 0.015f;
            this.y = y + (rand.nextFloat() - 0.5f) * 0.015f;
            this.z = z + (rand.nextFloat() - 0.5f) * 0.015f;
            this.sourcePetalId = sourcePetalId;
            // upwards and slightly outwards
            float theta = rand.nextFloat() * (float) (Math.PI * 2.0);
            float speedOut = 0.20f + rand.nextFloat() * 0.45f;
            this.vx = (float) Math.cos(theta) * speedOut;
            this.vz = (float) Math.sin(theta) * speedOut;
            this.vy = 0.85f + rand.nextFloat() * 1.35f;
            this.length = 0.25f + rand.nextFloat() * 0.35f;
            this.age = 0f;
            this.maxAge = 0.35f + rand.nextFloat() * 0.40f;
        }

        public boolean update(float deltaSec) {
            age += deltaSec;
            if (age >= maxAge) {
                return false;
            }
            x += vx * deltaSec;
            y += vy * deltaSec;
            z += vz * deltaSec;
            // gentle gravity and air drag
            vy -= 1.6f * deltaSec;
            vx *= (1.0f - 0.8f * deltaSec);
            vz *= (1.0f - 0.8f * deltaSec);
            return true;
        }

        public void getColor(float[] out) {
            float t = age / maxAge;
            float alpha = t < 0.2f ? (t / 0.2f) : (1.0f - (t - 0.2f) / 0.8f);
            out[3] = Math.max(0.0f, Math.min(1.0f, alpha));
            if (t < 0.25f) {
                // hot white-yellow core
                float p = t / 0.25f;
                out[0] = 1.0f;
                out[1] = 0.95f - 0.2f * p;
                out[2] = 0.8f - 0.6f * p;
            } else if (t < 0.65f) {
                // fiery orange
                float p = (t - 0.25f) / 0.40f;
                out[0] = 1.0f;
                out[1] = 0.75f - 0.45f * p;
                out[2] = 0.1f * (1.0f - p);
            } else {
                // cooling embers
                float p = (t - 0.65f) / 0.35f;
                out[0] = 0.9f - 0.5f * p;
                out[1] = 0.25f - 0.15f * p;
                out[2] = 0.05f + 0.1f * p;
            }
        }
    }

    /** Small 4x4 matrix tracking bone and cube transforms relative to the flower root. */
    public static class ModelMatrix {
        public final float[] m = new float[16];

        public ModelMatrix() {
            identity();
        }

        public void identity() {
            Arrays.fill(m, 0f);
            m[0] = 1;
            m[5] = 1;
            m[10] = 1;
            m[15] = 1;
        }

        public void set(ModelMatrix other) {
            System.arraycopy(other.m, 0, this.m, 0, 16);
        }

        public void translate(float x, float y, float z) {
            for (int i = 0; i < 4; i++) {
                m[12 + i] += m[i] * x + m[4 + i] * y + m[8 + i] * z;
            }
        }

        public void rotateDegX(float deg) {
            if (deg == 0f) {
                return;
            }
            float rad = (float) Math.toRadians(deg);
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);
            for (int i = 0; i < 4; i++) {
                float v1 = m[4 + i];
                float v2 = m[8 + i];
                m[4 + i] = v1 * cos + v2 * sin;
                m[8 + i] = -v1 * sin + v2 * cos;
            }
        }

        public void rotateDegY(float deg) {
            if (deg == 0f) {
                return;
            }
            float rad = (float) Math.toRadians(deg);
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);
            for (int i = 0; i < 4; i++) {
                float v0 = m[i];
                float v2 = m[8 + i];
                m[i] = v0 * cos - v2 * sin;
                m[8 + i] = v0 * sin + v2 * cos;
            }
        }

        public void rotateDegZ(float deg) {
            if (deg == 0f) {
                return;
            }
            float rad = (float) Math.toRadians(deg);
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);
            for (int i = 0; i < 4; i++) {
                float v0 = m[i];
                float v1 = m[4 + i];
                m[i] = v0 * cos + v1 * sin;
                m[4 + i] = -v0 * sin + v1 * cos;
            }
        }

        public float transformX(float x, float y, float z) {
            return m[0] * x + m[4] * y + m[8] * z + m[12];
        }

        public float transformY(float x, float y, float z) {
            return m[1] * x + m[5] * y + m[9] * z + m[13];
        }

        public float transformZ(float x, float y, float z) {
            return m[2] * x + m[6] * y + m[10] * z + m[14];
        }
    }

    private void recordSmallPetalObstacle(float x0, float y0, float z0, float x1, float y1, ModelMatrix mat, int petalId) {
        if (smallPetalObstacleCount >= smallPetalObstacles.length) {
            return;
        }
        PetalObstacle obs = smallPetalObstacles[smallPetalObstacleCount++];
        store(obs.v0, mat, x0, y0, z0);
        store(obs.v1, mat, x1, y0, z0);
        store(obs.v2, mat, x1, y1, z0);
        store(obs.v3, mat, x0, y1, z0);
        obs.petalId = petalId;
        obs.valid = true;
    }

    private static void store(float[] out, ModelMatrix mat, float x, float y, float z) {
        out[0] = mat.transformX(x, y, z);
        out[1] = mat.transformY(x, y, z);
        out[2] = mat.transformZ(x, y, z);
    }

    public void updateObstacles(float scale) {
        matrixStack[0].identity();
        smallPetalObstacleCount = 0;
        for (Bone root : rootBones) {
            collectObstaclesPass(root, null, scale, 0);
        }
    }

    /** Applies the bone transform to the model-space matrix stack; returns the depth of the bone's matrix. */
    private int pushBoneMatrix(Bone bone, Bone parent, float scale, int stackDepth, float[] outT, float[] outR) {
        float ox = parent == null ? bone.pivot[0] : bone.pivot[0] - parent.pivot[0];
        float oy = parent == null ? bone.pivot[1] : bone.pivot[1] - parent.pivot[1];
        float oz = parent == null ? bone.pivot[2] : bone.pivot[2] - parent.pivot[2];
        outT[0] = (ox + bone.animPos[0]) * scale;
        outT[1] = (oy + bone.animPos[1]) * scale;
        outT[2] = (oz + bone.animPos[2]) * scale;
        outR[0] = -(bone.rotation[0] + bone.animRot[0]);
        outR[1] = -(bone.rotation[1] + bone.animRot[1]);
        outR[2] = -(bone.rotation[2] + bone.animRot[2]);

        int nextDepth = Math.min(stackDepth + 1, matrixStack.length - 1);
        ModelMatrix mat = matrixStack[nextDepth];
        mat.set(matrixStack[stackDepth]);
        mat.translate(outT[0], outT[1], outT[2]);
        mat.rotateDegZ(outR[2]);
        mat.rotateDegY(outR[1]);
        mat.rotateDegX(outR[0]);
        return nextDepth;
    }

    /** Model-space matrix of a cube with its own pivot rotation, or the bone matrix. */
    private ModelMatrix cubeMatrix(Cube cube, Bone bone, float scale, ModelMatrix boneMat) {
        if (cube.cubePivot == null || cube.cubeRot == null) {
            return boneMat;
        }
        float cpx = (cube.cubePivot[0] - bone.pivot[0]) * scale;
        float cpy = (cube.cubePivot[1] - bone.pivot[1]) * scale;
        float cpz = (cube.cubePivot[2] - bone.pivot[2]) * scale;
        cubeMatrix.set(boneMat);
        cubeMatrix.translate(cpx, cpy, cpz);
        cubeMatrix.rotateDegZ(-cube.cubeRot[2]);
        cubeMatrix.rotateDegY(-cube.cubeRot[1]);
        cubeMatrix.rotateDegX(-cube.cubeRot[0]);
        cubeMatrix.translate(-cpx, -cpy, -cpz);
        return cubeMatrix;
    }

    private final float[] tmpT = new float[3];
    private final float[] tmpR = new float[3];

    private void collectObstaclesPass(Bone bone, Bone parent, float scale, int stackDepth) {
        int nextDepth = pushBoneMatrix(bone, parent, scale, stackDepth, tmpT, tmpR);
        ModelMatrix boneMat = matrixStack[nextDepth];
        Petal petal = getPetalForBone(bone.name);
        if (petal != null && !petal.isBig) {
            for (Cube cube : bone.cubes) {
                ModelMatrix activeMat = cubeMatrix(cube, bone, scale, boneMat);
                float x0 = (cube.origin[0] - bone.pivot[0]) * scale;
                float y0 = (cube.origin[1] - bone.pivot[1]) * scale;
                float z0 = (cube.origin[2] - bone.pivot[2]) * scale;
                recordSmallPetalObstacle(x0, y0, z0, x0 + cube.size[0] * scale, y0 + cube.size[1] * scale, activeMat, petal.id);
            }
        }
        for (Bone child : bone.children) {
            collectObstaclesPass(child, bone, scale, nextDepth);
        }
    }

    public boolean isUnderSmallPetal(float px, float py, float pz, int sourcePetalId) {
        for (int i = 0; i < smallPetalObstacleCount; i++) {
            PetalObstacle obs = smallPetalObstacles[i];
            if (!obs.valid || obs.petalId == sourcePetalId) {
                continue; // never blocked by the petal that emitted the spark
            }
            if (isPointUnderTriangle(px, py, pz, obs.v0, obs.v1, obs.v2) || isPointUnderTriangle(px, py, pz, obs.v0, obs.v2, obs.v3)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPointUnderTriangle(float px, float py, float pz, float[] a, float[] b, float[] c) {
        float v0x = b[0] - a[0], v0z = b[2] - a[2];
        float v1x = c[0] - a[0], v1z = c[2] - a[2];
        float v2x = px - a[0], v2z = pz - a[2];
        float den = v0x * v1z - v1x * v0z;
        if (Math.abs(den) < 1e-7f) {
            return false;
        }
        float invDen = 1.0f / den;
        float u = (v2x * v1z - v1x * v2z) * invDen;
        float v = (v0x * v2z - v2x * v0z) * invDen;
        float w = 1.0f - u - v;
        float margin = 0.08f;
        if (u >= -margin && v >= -margin && w >= -margin) {
            float ySurface = w * a[1] + u * b[1] + v * c[1];
            return py <= ySurface + 0.025f && py >= ySurface - 0.40f;
        }
        return false;
    }

    public void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        initPetals();
        try {
            String geo = readResource(MODEL_GEO);
            if (geo != null) {
                parseGeometry(geo);
            }
            String anim = readResource(MODEL_ANIM);
            if (anim != null) {
                parseAnimation(anim);
            }
            updateObstacles(1.0f / 16.0f);
        } catch (Exception e) {
            EFW.LOGGER.error("Failed to load the SEVEN flower model", e);
        }
    }

    private static String readResource(ResourceLocation loc) throws java.io.IOException {
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(loc);
        if (res.isEmpty()) {
            EFW.LOGGER.warn("Missing flower resource {}", loc);
            return null;
        }
        try (InputStream in = res.get().open()) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return json.startsWith("﻿") ? json.substring(1) : json;
        }
    }

    public void triggerPetalBurn(int petalId) {
        initPetals();
        if (petalId >= 0 && petalId < petals.size()) {
            Petal p = petals.get(petalId);
            p.burning = true;
            p.burnProgress = 0.0f;
        }
    }

    public void resetPetalBurns() {
        initPetals();
        Arrays.fill(skillToPetal, -1);
        for (Petal p : petals) {
            p.burning = false;
            p.burnProgress = 0.0f;
        }
    }

    public Petal getPetal(int id) {
        initPetals();
        return id >= 0 && id < petals.size() ? petals.get(id) : null;
    }

    private void parseGeometry(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray geoms = root.getAsJsonArray("minecraft:geometry");
        if (geoms == null || geoms.isEmpty()) {
            return;
        }
        JsonObject geom = geoms.get(0).getAsJsonObject();
        if (geom.has("description")) {
            JsonObject desc = geom.getAsJsonObject("description");
            if (desc.has("texture_width")) {
                textureWidth = desc.get("texture_width").getAsFloat();
            }
            if (desc.has("texture_height")) {
                textureHeight = desc.get("texture_height").getAsFloat();
            }
        }
        JsonArray bonesArr = geom.getAsJsonArray("bones");
        if (bonesArr == null) {
            return;
        }
        bonesByName.clear();
        rootBones.clear();
        for (JsonElement el : bonesArr) {
            JsonObject bObj = el.getAsJsonObject();
            String name = bObj.get("name").getAsString();
            String parent = bObj.has("parent") ? bObj.get("parent").getAsString() : null;
            float[] pivot = bObj.has("pivot") ? vec3(bObj.getAsJsonArray("pivot")) : new float[] {0, 0, 0};
            float[] rotation = bObj.has("rotation") ? vec3(bObj.getAsJsonArray("rotation")) : new float[] {0, 0, 0};
            Bone bone = new Bone(name, parent, pivot, rotation);
            if (bObj.has("cubes")) {
                for (JsonElement ce : bObj.getAsJsonArray("cubes")) {
                    JsonObject cObj = ce.getAsJsonObject();
                    float[] o = vec3(cObj.getAsJsonArray("origin"));
                    float[] s = vec3(cObj.getAsJsonArray("size"));
                    float uvU = 0, uvV = 0;
                    if (cObj.has("uv") && cObj.get("uv").isJsonArray()) {
                        JsonArray uvArr = cObj.getAsJsonArray("uv");
                        uvU = uvArr.get(0).getAsFloat();
                        uvV = uvArr.get(1).getAsFloat();
                    }
                    float[] cubePivot = cObj.has("pivot") ? vec3(cObj.getAsJsonArray("pivot")) : null;
                    float[] cubeRot = cObj.has("rotation") ? vec3(cObj.getAsJsonArray("rotation")) : null;
                    bone.cubes.add(new Cube(o, s, uvU, uvV, cubePivot, cubeRot));
                }
            }
            bonesByName.put(name, bone);
        }
        for (Bone bone : bonesByName.values()) {
            if (bone.parentName != null && bonesByName.containsKey(bone.parentName)) {
                bonesByName.get(bone.parentName).children.add(bone);
            } else {
                rootBones.add(bone);
            }
        }
    }

    private static float[] vec3(JsonArray a) {
        return new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }

    private void parseAnimation(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.has("animations")) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("animations").entrySet()) {
            JsonObject aObj = entry.getValue().getAsJsonObject();
            float length = aObj.has("animation_length") ? aObj.get("animation_length").getAsFloat() : 1.0f;
            Animation anim = new Animation(length);
            if (aObj.has("bones")) {
                for (Map.Entry<String, JsonElement> bEntry : aObj.getAsJsonObject("bones").entrySet()) {
                    JsonObject bData = bEntry.getValue().getAsJsonObject();
                    BoneTrack track = new BoneTrack();
                    if (bData.has("rotation")) {
                        parseKeyframes(bData.get("rotation"), track.rotKeyframes);
                    }
                    if (bData.has("position")) {
                        parseKeyframes(bData.get("position"), track.posKeyframes);
                    }
                    anim.tracks.put(bEntry.getKey(), track);
                }
            }
            animations.put(entry.getKey(), anim);
        }
    }

    private void parseKeyframes(JsonElement elem, List<Keyframe> target) {
        if (elem.isJsonObject()) {
            JsonObject obj = elem.getAsJsonObject();
            if (obj.has("vector")) {
                float[] v = vec3(obj.getAsJsonArray("vector"));
                target.add(new Keyframe(0f, v[0], v[1], v[2]));
            } else {
                for (Map.Entry<String, JsonElement> kf : obj.entrySet()) {
                    try {
                        float time = Float.parseFloat(kf.getKey());
                        JsonElement val = kf.getValue();
                        float[] v = null;
                        if (val.isJsonObject()) {
                            JsonObject vo = val.getAsJsonObject();
                            if (vo.has("post")) {
                                v = vec3(vo.getAsJsonArray("post"));
                            } else if (vo.has("vector")) {
                                v = vec3(vo.getAsJsonArray("vector"));
                            }
                        } else if (val.isJsonArray()) {
                            v = vec3(val.getAsJsonArray());
                        }
                        if (v != null) {
                            target.add(new Keyframe(time, v[0], v[1], v[2]));
                        }
                    } catch (RuntimeException ignored) {
                        // a non-numeric key or a molang expression: skipped like in 1.12
                    }
                }
            }
        } else if (elem.isJsonArray()) {
            float[] v = vec3(elem.getAsJsonArray());
            target.add(new Keyframe(0f, v[0], v[1], v[2]));
        }
        target.sort(Comparator.comparingDouble(k -> k.time));
    }

    public enum AnimMode {
        CLOSED,
        OPENING,
        OPEN_IDLE,
        CLOSING
    }

    private AnimMode animMode = AnimMode.OPEN_IDLE;
    private float closeSpeedMultiplier = 1.8f;
    private float idleSwayTime = 0f;

    public void playOpen() {
        load();
        if (animMode == AnimMode.CLOSED || animTime <= 0f) {
            animTime = 0f;
        }
        animMode = AnimMode.OPENING;
        animPlaying = true;
        onFlowerScreenOpened();
    }

    /** Every burnt petal replays its burn wave from the start when the flower is shown again. */
    public void onFlowerScreenOpened() {
        initPetals();
        for (Petal p : petals) {
            if (p.burnProgress > 0.0f || p.burning) {
                p.burning = true;
                p.burnProgress = 0.0f;
            }
        }
    }

    public void playClose(float speedMultiplier) {
        load();
        closeSpeedMultiplier = Math.max(0.1f, speedMultiplier);
        animMode = AnimMode.CLOSING;
        animPlaying = true;
    }

    public boolean isFullyClosed() {
        return animMode == AnimMode.CLOSED || animTime <= 0.001f;
    }

    public void update(float deltaSec, float speed, boolean[] skillUnlocked, List<Integer> unlockOrder) {
        load();
        if (animPlaying) {
            Animation anim = animations.get(currentAnimName);
            if (anim == null && !animations.isEmpty()) {
                anim = animations.values().iterator().next();
            }
            if (anim != null) {
                float animLen = anim.length > 0f ? anim.length : 1.375f;
                float curSpeed = Math.max(0.01f, speed);
                switch (animMode) {
                    case OPENING -> {
                        animTime += deltaSec * curSpeed;
                        if (animTime >= animLen) {
                            animTime = animLen;
                            animMode = AnimMode.OPEN_IDLE;
                        }
                    }
                    case CLOSING -> {
                        animTime -= deltaSec * curSpeed * closeSpeedMultiplier;
                        if (animTime <= 0f) {
                            animTime = 0f;
                            animMode = AnimMode.CLOSED;
                        }
                    }
                    case OPEN_IDLE -> animTime = animLen;
                    case CLOSED -> animTime = 0f;
                }
                idleSwayTime += deltaSec;
                for (Bone bone : bonesByName.values()) {
                    Arrays.fill(bone.animRot, 0f);
                    Arrays.fill(bone.animPos, 0f);
                    BoneTrack track = anim.tracks.get(bone.name);
                    if (track != null) {
                        evaluateKeyframes(track.rotKeyframes, animTime, bone.animRot);
                        evaluateKeyframes(track.posKeyframes, animTime, bone.animPos);
                    }
                }
                float openWeight = Math.max(0f, (animTime - animLen * 0.6f) / (animLen * 0.4f));
                if (openWeight > 0.001f) {
                    applyProceduralPetalSway(idleSwayTime, openWeight);
                }
            }
        }

        glowTime += deltaSec;
        initPetals();

        // newly unlocked skills ignite petals in queue order, whatever skill or branch it was
        if (skillUnlocked != null) {
            int queueIdx = 0;
            if (unlockOrder != null) {
                for (int sId : unlockOrder) {
                    if (sId >= 1 && sId < skillUnlocked.length && skillUnlocked[sId]) {
                        if (skillToPetal[sId] == -1) {
                            igniteForSkill(sId, queueIdx);
                        }
                        queueIdx++;
                    }
                }
            }
            for (int sId = 1; sId < skillUnlocked.length && sId < 13; sId++) {
                if (skillUnlocked[sId] && skillToPetal[sId] == -1) {
                    igniteForSkill(sId, queueIdx);
                    queueIdx++;
                }
            }
        }

        for (Petal petal : petals) {
            if (petal.burning) {
                petal.burnProgress += deltaSec * waveSpeed;
                if (petal.burnProgress >= 1.18f) {
                    petal.burning = false;
                    petal.burnProgress = 1.20f;
                }
            }
            float targetBlend = petal.burning ? 1.0f : 0.0f;
            float blendSpeed = petal.burning ? 4.0f : 1.6f;
            petal.burnSwayBlend += (targetBlend - petal.burnSwayBlend) * Math.min(1.0f, deltaSec * blendSpeed);
        }

        updateObstacles(1.0f / 16.0f);
        for (Iterator<Spark3D> it = sparks3D.iterator(); it.hasNext(); ) {
            Spark3D s = it.next();
            if (!s.update(deltaSec) || isUnderSmallPetal(s.x, s.y, s.z, s.sourcePetalId)) {
                it.remove();
            }
        }
    }

    private void igniteForSkill(int skillId, int queueIdx) {
        int petalId = queueIdx < PETAL_BURN_QUEUE.length ? PETAL_BURN_QUEUE[queueIdx] : queueIdx % petals.size();
        Petal target = getPetal(petalId);
        if (target != null) {
            target.burning = true;
            target.burnProgress = 0.0f;
            skillToPetal[skillId] = target.id;
        }
    }

    private void applyProceduralPetalSway(float time, float weight) {
        String[][] bigPetals = {
                {"group27", "group28", "group29"},
                {"group30", "group31", "group32"},
                {"group33", "group34", "group35"},
                {"group36", "group37", "group38"},
                {"group39", "group40", "group41"},
                {"group42", "group43", "group44"}
        };
        for (int p = 0; p < bigPetals.length; p++) {
            float phase = (float) (p * (Math.PI * 2.0 / 6.0));
            Petal petal = p < petals.size() ? petals.get(p) : null;
            float blend = petal != null ? petal.burnSwayBlend : 0.0f;
            float swayScale = 1.0f + 1.2f * blend;
            float flutter = blend * ((float) Math.sin(time * 6.5f + phase * 2.0f) * 1.6f);
            Bone b0 = bonesByName.get(bigPetals[p][0]);
            if (b0 != null) {
                b0.animRot[0] += ((float) Math.sin(time * 1.8f + phase) * 1.8f * swayScale + flutter * 0.5f) * weight;
            }
            Bone b1 = bonesByName.get(bigPetals[p][1]);
            if (b1 != null) {
                b1.animRot[2] += ((float) Math.sin(time * 2.2f + phase + 0.6f) * 1.5f * swayScale + flutter * 0.8f) * weight;
            }
            Bone b2 = bonesByName.get(bigPetals[p][2]);
            if (b2 != null) {
                b2.animRot[2] += ((float) Math.sin(time * 2.6f + phase + 1.2f) * 2.2f * swayScale + flutter * 1.2f) * weight;
            }
        }
        String[][] smallPetals = {
                {"groupx", "group19"},
                {"groupx2", "group20"},
                {"groupx3", "group21"},
                {"groupx4", "group22"},
                {"groupx5", "group23"},
                {"groupx6", "group24"}
        };
        for (int p = 0; p < smallPetals.length; p++) {
            float phaseSmall = (float) (p * (Math.PI * 2.0 / 6.0) + 0.8f);
            int smallPetalId = 6 + p;
            Petal petal = smallPetalId < petals.size() ? petals.get(smallPetalId) : null;
            float blend = petal != null ? petal.burnSwayBlend : 0.0f;
            float swayScale = 1.0f + 1.2f * blend;
            float flutter = blend * ((float) Math.sin(time * 7.2f + phaseSmall * 2.0f) * 1.8f);
            Bone bx = bonesByName.get(smallPetals[p][0]);
            if (bx != null) {
                bx.animRot[0] += ((float) Math.sin(time * 2.1f + phaseSmall) * 2.0f * swayScale + flutter * 0.6f) * weight;
            }
            Bone bt = bonesByName.get(smallPetals[p][1]);
            if (bt != null) {
                bt.animRot[0] += ((float) Math.sin(time * 2.7f + phaseSmall + 0.8f) * 2.5f * swayScale + flutter * 1.2f) * weight;
            }
        }
    }

    private void evaluateKeyframes(List<Keyframe> kfs, float time, float[] out) {
        if (kfs.isEmpty()) {
            return;
        }
        Keyframe first = kfs.get(0);
        if (kfs.size() == 1 || time <= first.time) {
            out[0] = first.x;
            out[1] = first.y;
            out[2] = first.z;
            return;
        }
        Keyframe last = kfs.get(kfs.size() - 1);
        if (time >= last.time) {
            out[0] = last.x;
            out[1] = last.y;
            out[2] = last.z;
            return;
        }
        for (int i = 0; i < kfs.size() - 1; i++) {
            Keyframe k0 = kfs.get(i);
            Keyframe k1 = kfs.get(i + 1);
            if (time >= k0.time && time <= k1.time) {
                float seg = k1.time - k0.time;
                float t = seg <= 0 ? 0 : (time - k0.time) / seg;
                float f = (1.0f - (float) Math.cos(t * Math.PI)) * 0.5f;
                out[0] = k0.x + (k1.x - k0.x) * f;
                out[1] = k0.y + (k1.y - k0.y) * f;
                out[2] = k0.z + (k1.z - k0.z) * f;
                return;
            }
        }
    }

    // ===================================================================== rendering

    /** Draws the flower in the current pose (unlit, like the 1.12 version). */
    public void render(PoseStack pose, float scale) {
        load();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515); // LEQUAL
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        matrixStack[0].identity();
        for (Bone root : rootBones) {
            renderBone(pose, root, null, scale, 0);
        }
        RenderSystem.enableCull();
    }

    private void renderBone(PoseStack pose, Bone bone, Bone parent, float scale, int stackDepth) {
        pose.pushPose();
        int nextDepth = pushBoneMatrix(bone, parent, scale, stackDepth, tmpT, tmpR);
        pose.translate(tmpT[0], tmpT[1], tmpT[2]);
        if (tmpR[2] != 0f) {
            pose.mulPose(Axis.ZP.rotationDegrees(tmpR[2]));
        }
        if (tmpR[1] != 0f) {
            pose.mulPose(Axis.YP.rotationDegrees(tmpR[1]));
        }
        if (tmpR[0] != 0f) {
            pose.mulPose(Axis.XP.rotationDegrees(tmpR[0]));
        }
        ModelMatrix boneMat = matrixStack[nextDepth];
        for (Cube cube : bone.cubes) {
            renderCube(pose, cube, bone, scale, boneMat);
        }
        for (Bone child : bone.children) {
            renderBone(pose, child, bone, scale, nextDepth);
        }
        pose.popPose();
    }

    /** Sparks as additive lines, in the flower's model space (call right after {@link #render}). */
    public void renderParticles(PoseStack pose, float fadeAlpha) {
        if (sparks3D.isEmpty() || fadeAlpha <= 0.01f) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE); // additive glow
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.lineWidth(2.2f);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f mat = pose.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        float[] col = new float[4];
        for (Spark3D s : sparks3D) {
            s.getColor(col);
            float a = col[3] * fadeAlpha;
            if (a <= 0.01f) {
                continue;
            }
            // the tail is stretched back along the velocity and cooler
            float speed = (float) Math.sqrt(s.vx * s.vx + s.vy * s.vy + s.vz * s.vz);
            float tailScale = speed > 0.001f ? s.length * 0.035f : 0.01f;
            buffer.vertex(mat, s.x - s.vx * tailScale, s.y - s.vy * tailScale, s.z - s.vz * tailScale)
                    .color(Math.max(0f, col[0] - 0.2f), Math.max(0f, col[1] - 0.3f), col[2], a * 0.45f).endVertex();
            buffer.vertex(mat, s.x, s.y, s.z).color(col[0], col[1], col[2], a).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.lineWidth(1.0f);
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
    }

    private void renderCube(PoseStack pose, Cube cube, Bone bone, float scale, ModelMatrix boneMat) {
        pose.pushPose();
        ModelMatrix activeMat = cubeMatrix(cube, bone, scale, boneMat);
        if (cube.cubePivot != null && cube.cubeRot != null) {
            float cpx = (cube.cubePivot[0] - bone.pivot[0]) * scale;
            float cpy = (cube.cubePivot[1] - bone.pivot[1]) * scale;
            float cpz = (cube.cubePivot[2] - bone.pivot[2]) * scale;
            pose.translate(cpx, cpy, cpz);
            if (cube.cubeRot[2] != 0f) {
                pose.mulPose(Axis.ZP.rotationDegrees(-cube.cubeRot[2]));
            }
            if (cube.cubeRot[1] != 0f) {
                pose.mulPose(Axis.YP.rotationDegrees(-cube.cubeRot[1]));
            }
            if (cube.cubeRot[0] != 0f) {
                pose.mulPose(Axis.XP.rotationDegrees(-cube.cubeRot[0]));
            }
            pose.translate(-cpx, -cpy, -cpz);
        }
        Matrix4f mat = pose.last().pose();

        float x0 = (cube.origin[0] - bone.pivot[0]) * scale;
        float y0 = (cube.origin[1] - bone.pivot[1]) * scale;
        float z0 = (cube.origin[2] - bone.pivot[2]) * scale;
        float x1 = x0 + cube.size[0] * scale;
        float y1 = y0 + cube.size[1] * scale;
        float z1 = z0 + cube.size[2] * scale;
        float w = cube.size[0];
        float h = cube.size[1];
        float d = cube.size[2];
        float u = cube.u;
        float v = cube.v;
        float tw = textureWidth;
        float th = textureHeight;

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, MODEL_TEX);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        if (h <= 0.001f) {
            float u0t = (u + d) / tw, u1t = (u + d + w) / tw, v0t = v / th, v1t = (v + d) / th;
            vt(buf, mat, x0, y0, z0, u0t, v0t);
            vt(buf, mat, x1, y0, z0, u1t, v0t);
            vt(buf, mat, x1, y0, z1, u1t, v1t);
            vt(buf, mat, x0, y0, z1, u0t, v1t);
        } else if (d <= 0.001f) {
            float u0n = u / tw, u1n = (u + w) / tw, v0n = v / th, v1n = (v + h) / th;
            vt(buf, mat, x0, y1, z0, u0n, v0n);
            vt(buf, mat, x1, y1, z0, u1n, v0n);
            vt(buf, mat, x1, y0, z0, u1n, v1n);
            vt(buf, mat, x0, y0, z0, u0n, v1n);
        } else {
            float uTop = u + d, vTop = v;
            float uSide = u, vSide = v + d;
            float uFront = u + d, vFront = v + d;
            float uBack = u + d + w + d;
            quad(buf, mat, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, uTop, vTop, uTop + w, vTop + d, tw, th);
            quad(buf, mat, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, uTop + w, vTop, uTop + w + w, vTop + d, tw, th);
            quad(buf, mat, x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0, uFront, vFront, uFront + w, vFront + h, tw, th);
            quad(buf, mat, x1, y1, z1, x0, y1, z0, x0, y0, z1, x1, y0, z1, uBack, vFront, uBack + w, vFront + h, tw, th);
            quad(buf, mat, x0, y1, z1, x0, y1, z0, x0, y0, z0, x0, y0, z1, uSide, vSide, uSide + d, vSide + h, tw, th);
            quad(buf, mat, x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0, uSide + d + w, vSide, uSide + d + w + d, vSide + h, tw, th);
        }
        BufferUploader.drawWithShader(buf.end());

        Petal petal = getPetalForBone(bone.name);
        if (petal != null && (petal.burning || petal.burnProgress > 0.001f)) {
            renderBurnOverlay(mat, cube, bone, petal, activeMat, x0, y0, z0, x1, y1, z1);
        }
        if ("center".equals(bone.name)) {
            renderCenterChar(mat, cube, x0, y0, z0, x1, y1, z1);
        }
        pose.popPose();
    }

    private static void vt(BufferBuilder buf, Matrix4f mat, float x, float y, float z, float u, float v) {
        buf.vertex(mat, x, y, z).uv(u, v).color(1.0f, 1.0f, 1.0f, 1.0f).endVertex();
    }

    private static void quad(BufferBuilder buf, Matrix4f mat, float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             float u1, float v1, float u2, float v2, float tw, float th) {
        float minU = u1 / tw, minV = v1 / th, maxU = u2 / tw, maxV = v2 / th;
        vt(buf, mat, x1, y1, z1, minU, minV);
        vt(buf, mat, x2, y2, z2, maxU, minV);
        vt(buf, mat, x3, y3, z3, maxU, maxV);
        vt(buf, mat, x4, y4, z4, minU, maxV);
    }

    private static BufferBuilder beginOverlay() {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-1.5f, -1.5f);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        return buf;
    }

    private static void endOverlay(BufferBuilder buf) {
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.polygonOffset(0f, 0f);
        RenderSystem.disablePolygonOffset();
    }

    private static void cv(BufferBuilder buf, Matrix4f mat, float x, float y, float z, int r, int g, int b, int a) {
        buf.vertex(mat, x, y, z).color(r, g, b, a).endVertex();
    }

    private static int c255(float v) {
        return (int) (Math.min(1.0f, Math.max(0.0f, v)) * 255);
    }

    /** Burn overlay of one petal cube, subdivided into 1/4 texel cells: charcoal behind the wave, the fire line, ash. */
    private void renderBurnOverlay(Matrix4f mat, Cube cube, Bone bone, Petal petal, ModelMatrix activeMat,
                                   float x0, float y0, float z0, float x1, float y1, float z1) {
        float h = cube.size[1];
        float d = cube.size[2];
        int segIdx = petal.getBoneSegmentIndex(bone.name);

        // the segment's share of the petal, proportional to the real bone lengths
        float totalPetalLen = 0f;
        float boneStartLen = 0f;
        float thisBoneLen = 0f;
        for (int bi = 0; bi < petal.boneNames.size(); bi++) {
            Bone b = bonesByName.get(petal.boneNames.get(bi));
            float blen = 1.0f;
            if (b != null && !b.cubes.isEmpty()) {
                Cube c = b.cubes.get(0);
                blen = c.size[1] <= 0.001f ? c.size[0] : c.size[1];
            }
            if (bi < segIdx) {
                boneStartLen += blen;
            } else if (bi == segIdx) {
                thisBoneLen = blen;
            }
            totalPetalLen += blen;
        }
        if (totalPetalLen <= 0.001f) {
            totalPetalLen = 1.0f;
        }
        if (thisBoneLen <= 0.001f) {
            thisBoneLen = 1.0f;
        }
        float s0 = boneStartLen / totalPetalLen;
        float s1 = (boneStartLen + thisBoneLen) / totalPetalLen;
        float prog = petal.burnProgress;

        int stepsX = Math.max(1, Math.round(cube.size[0] * 4.0f));
        int stepsY = Math.max(1, Math.round(cube.size[1] * 4.0f));
        int stepsZ = Math.max(1, Math.round(cube.size[2] * 4.0f));
        float dx = (x1 - x0) / stepsX;
        float dy = h <= 0.001f ? 0f : (y1 - y0) / stepsY;
        float dz = d <= 0.001f ? 0f : (z1 - z0) / stepsZ;

        float boneLen = h <= 0.001f ? cube.size[0] : cube.size[1];
        float waveHalf = boneLen >= 2.8f ? waveFireWidth * 1.55f : waveFireWidth * 0.85f;
        // the fire fades out once the wave has passed the tip
        float fireFade = prog > 1.00f ? Math.max(0.0f, (1.15f - prog) / 0.15f) : 1.0f;
        float tanAngle = (float) Math.tan(Math.toRadians(waveAngle));

        boolean flat = h <= 0.001f;
        if (!flat && d > 0.001f) {
            return; // thick cubes have no burn overlay
        }
        BufferBuilder obuf = beginOverlay();
        int outer = flat ? stepsX : stepsY;
        int inner = flat ? stepsZ : stepsX;
        float[] rgba = new float[4];
        for (int io = 0; io < outer; io++) {
            float lenFrac = outer <= 1 ? 0.5f : (float) io / (outer - 1);
            float dBase = s0 + (s1 - s0) * lenFrac;
            if (petal.flipDirection) {
                dBase = 1.0f - dBase;
            }
            for (int ii = 0; ii < inner; ii++) {
                float wFrac = inner <= 1 ? 0.0f : ((float) ii / (inner - 1) - 0.5f);
                float dDist = dBase + wFrac * tanAngle * (s1 - s0);
                boolean isEdge = ii == 0 || ii == inner - 1;
                // flat: (ix=io, iz=ii); vertical: (iy=io, ix=ii)
                int a1 = flat ? io : ii;
                int a2 = flat ? ii : io;
                int seed = (bone.name.hashCode() * 37 + a1 * 53 + a2 * 89) & 0x7FFFFFFF;
                float noise = ((seed % 100) / 100.0f) - 0.5f;
                float noise2 = (((seed / 100) % 100) / 100.0f) - 0.5f;
                // patchy edge sections along the petal length
                int sectionSeed = (petal.id * 101 + (io / 8) * 31 + (ii == 0 ? 11 : 73)) & 0x7FFFFFFF;
                boolean edgeSectionActive = (sectionSeed % 100) < 55;
                float sidePhase = petal.id * 1.35f + (ii == 0 ? 0.0f : 2.4f) + (io / 5) * 0.4f;
                float slowBreathe = 0.5f + 0.5f * (float) Math.sin(glowTime * 0.42f + sidePhase);

                if (!burnCell(dDist - prog, noise, noise2, isEdge, edgeSectionActive, slowBreathe, waveHalf, fireFade,
                        prog, flat, flat ? io * 2 + ii * 3 : ii * 2 + io * 3, rgba)) {
                    continue;
                }
                int ir = c255(rgba[0]), ig = c255(rgba[1]), ib = c255(rgba[2]), ia = (int) (rgba[3] * 255);
                if (flat) {
                    float px0 = x0 + io * dx;
                    float px1 = io == stepsX - 1 ? x1 : px0 + dx;
                    float pz0 = z0 + ii * dz;
                    float pz1 = ii == stepsZ - 1 ? z1 : pz0 + dz;
                    if (spawnFromFront) {
                        spawnSpark(activeMat, (px0 + px1) * 0.5f, y0 + 0.02f, (pz0 + pz1) * 0.5f, petal.id);
                    }
                    cv(obuf, mat, px0, y0, pz0, ir, ig, ib, ia);
                    cv(obuf, mat, px1, y0, pz0, ir, ig, ib, ia);
                    cv(obuf, mat, px1, y0, pz1, ir, ig, ib, ia);
                    cv(obuf, mat, px0, y0, pz1, ir, ig, ib, ia);
                    cv(obuf, mat, px0, y0, pz1, ir, ig, ib, ia);
                    cv(obuf, mat, px1, y0, pz1, ir, ig, ib, ia);
                    cv(obuf, mat, px1, y0, pz0, ir, ig, ib, ia);
                    cv(obuf, mat, px0, y0, pz0, ir, ig, ib, ia);
                } else {
                    float py0 = y0 + io * dy;
                    float py1 = io == stepsY - 1 ? y1 : py0 + dy;
                    float px0 = x0 + ii * dx;
                    float px1 = ii == stepsX - 1 ? x1 : px0 + dx;
                    if (spawnFromFront) {
                        spawnSpark(activeMat, (px0 + px1) * 0.5f, (py0 + py1) * 0.5f, z0 + 0.02f, petal.id);
                    }
                    cv(obuf, mat, px0, py0, z0, ir, ig, ib, ia);
                    cv(obuf, mat, px1, py0, z0, ir, ig, ib, ia);
                    cv(obuf, mat, px1, py1, z0, ir, ig, ib, ia);
                    cv(obuf, mat, px0, py1, z0, ir, ig, ib, ia);
                    cv(obuf, mat, px0, py1, z0, ir, ig, ib, ia);
                    cv(obuf, mat, px1, py1, z0, ir, ig, ib, ia);
                    cv(obuf, mat, px1, py0, z0, ir, ig, ib, ia);
                    cv(obuf, mat, px0, py0, z0, ir, ig, ib, ia);
                }
            }
        }
        endOverlay(obuf);
    }

    /** Set by {@link #burnCell} when the cell is on the fire front and rolled a spark. */
    private boolean spawnFromFront;

    /**
     * Colour of one burn cell (1.12 renderCube logic). Returns false when the cell is untouched petal.
     *
     * @param flickerSeed ix*2+iz*3 (flat) or ix*2+iy*3 (vertical) of the 1.12 flicker term
     */
    private boolean burnCell(float diff, float noise, float noise2, boolean isEdge, boolean edgeSectionActive, float slowBreathe,
                             float waveHalf, float fireFade, float prog, boolean flat, int flickerSeed, float[] out) {
        spawnFromFront = false;
        float ashBoundaryJitter = noise * 0.032f;
        if (diff < -waveHalf + ashBoundaryJitter) {
            // burnt: very dark matte charcoal
            float charR = 0.016f + noise * 0.008f;
            float charG = 0.013f + noise * 0.008f;
            float charB = 0.013f + noise2 * 0.008f;
            if (isEdge && edgeSectionActive) {
                // patchy 1-cell edge strip breathing dark graphite
                float greyAdd = 0.085f * slowBreathe + noise * 0.02f;
                charR = Math.min(1.0f, charR + greyAdd);
                charG = Math.min(1.0f, charG + greyAdd * 0.96f);
                charB = Math.min(1.0f, charB + greyAdd * 0.94f);
            }
            // noisy trailing ember plume behind the front
            float wakeDist = -diff - waveHalf;
            float plumeLen = waveHalf * 3.8f;
            if (wakeDist > 0f && wakeDist < plumeLen && fireFade > 0.04f) {
                float trailT = 1.0f - wakeDist / plumeLen;
                if (noise > -0.15f * trailT) {
                    float sparkIntensity = (0.75f + noise * 0.45f) * trailT * fireFade;
                    charR = Math.min(1.0f, charR + 0.96f * sparkIntensity);
                    charG = Math.min(1.0f, charG + (0.42f + noise * 0.25f) * sparkIntensity);
                    charB = Math.min(1.0f, charB + 0.04f * sparkIntensity);
                }
            }
            out[0] = charR;
            out[1] = charG;
            out[2] = charB;
            out[3] = 0.96f;
            return true;
        }
        if (Math.abs(diff) <= waveHalf && fireFade > 0.01f) {
            // the burning front
            boolean isLeadingEdge = diff > -waveHalf * 0.35f;
            float sparkNoise = isLeadingEdge ? 0.0f : noise * 0.40f;
            float flicker = 0.85f + 0.15f * (float) Math.sin(glowTime * 18.0f + flickerSeed);
            out[0] = 1.0f;
            out[1] = Math.min(1.0f, Math.max(0.22f, (0.52f + sparkNoise) * flicker));
            out[2] = Math.max(0.02f, 0.08f * flicker);
            out[3] = fireFade;
            spawnFromFront = sparks3D.size() < 250 && rand.nextFloat() < 0.08f;
            return true;
        }
        if (diff <= 0f || prog >= 1.0f) {
            // ash once the fire has faded or the wave has finished
            float charR = (flat ? 0.025f : 0.017f) + noise * (flat ? 0.012f : 0.008f);
            float charG = (flat ? 0.020f : 0.013f) + noise * (flat ? 0.012f : 0.008f);
            float charB = (flat ? 0.020f : 0.013f) + noise2 * (flat ? 0.012f : 0.008f);
            if (isEdge && edgeSectionActive) {
                float greyAdd = (flat ? 0.13f : 0.085f) * slowBreathe;
                charR += greyAdd;
                charG += greyAdd;
                charB += greyAdd;
            }
            out[0] = charR;
            out[1] = charG;
            out[2] = charB;
            out[3] = 0.96f;
            return true;
        }
        return false; // ahead of the fire: clean petal
    }

    private void spawnSpark(ModelMatrix activeMat, float lx, float ly, float lz, int petalId) {
        float fx = activeMat.transformX(lx, ly, lz);
        float fy = activeMat.transformY(lx, ly, lz);
        float fz = activeMat.transformZ(lx, ly, lz);
        if (!isUnderSmallPetal(fx, fy, fz, petalId)) {
            sparks3D.add(new Spark3D(fx, fy, fz, rand, petalId));
        }
    }

    /** The centre cube chars gradually from the side of each burning petal. */
    private void renderCenterChar(Matrix4f mat, Cube cube, float x0, float y0, float z0, float x1, float y1, float z1) {
        boolean anyBurning = false;
        for (Petal p : petals) {
            if (p.burnProgress > 0.001f) {
                anyBurning = true;
                break;
            }
        }
        if (!anyBurning) {
            return;
        }
        BufferBuilder cbuf = beginOverlay();
        int csX = Math.max(1, Math.round(cube.size[0] * 4.0f));
        int csY = Math.max(1, Math.round(cube.size[1] * 4.0f));
        int csZ = Math.max(1, Math.round(cube.size[2] * 4.0f));
        float cdx = (x1 - x0) / csX;
        float cdy = (y1 - y0) / csY;
        float cdz = (z1 - z0) / csZ;
        float centerX = (x0 + x1) * 0.5f;
        float centerZ = (z0 + z1) * 0.5f;
        float halfW = (x1 - x0) * 0.5f;
        float halfD = (z1 - z0) * 0.5f;

        // top and bottom faces: ash-grey centre, charcoal edges
        for (int cix = 0; cix < csX; cix++) {
            float cpx0 = x0 + cix * cdx;
            float cpx1 = cix == csX - 1 ? x1 : cpx0 + cdx;
            float midX = (cpx0 + cpx1) * 0.5f - centerX;
            for (int ciz = 0; ciz < csZ; ciz++) {
                float cpz0 = z0 + ciz * cdz;
                float cpz1 = ciz == csZ - 1 ? z1 : cpz0 + cdz;
                float midZ = (cpz0 + cpz1) * 0.5f - centerZ;
                float normX = halfW > 0.001f ? Math.abs(midX) / halfW : 0f;
                float normZ = halfD > 0.001f ? Math.abs(midZ) / halfD : 0f;
                float radNorm = Math.min(1.0f, Math.max(normX, normZ));
                float level = centerCharLevelAt((float) Math.toDegrees(Math.atan2(midZ, midX)), radNorm);
                if (level <= 0.01f) {
                    continue;
                }
                int cseed = (cix * 53 + ciz * 89 + 1337) & 0x7FFFFFFF;
                float cnoise = ((cseed % 100) / 100.0f) - 0.5f;
                float cnoise2 = (((cseed / 100) % 100) / 100.0f) - 0.5f;
                float ashCenterMix = 1.0f - radNorm;
                int r = c255(0.018f + ashCenterMix * 0.075f + cnoise * 0.008f);
                int g = c255(0.015f + ashCenterMix * 0.075f + cnoise * 0.008f);
                int b = c255(0.015f + ashCenterMix * 0.075f + cnoise2 * 0.008f);
                int a = (int) (level * 0.98f * 255);
                cv(cbuf, mat, cpx0, y1, cpz0, r, g, b, a);
                cv(cbuf, mat, cpx1, y1, cpz0, r, g, b, a);
                cv(cbuf, mat, cpx1, y1, cpz1, r, g, b, a);
                cv(cbuf, mat, cpx0, y1, cpz1, r, g, b, a);
                cv(cbuf, mat, cpx0, y0, cpz1, r, g, b, a);
                cv(cbuf, mat, cpx1, y0, cpz1, r, g, b, a);
                cv(cbuf, mat, cpx1, y0, cpz0, r, g, b, a);
                cv(cbuf, mat, cpx0, y0, cpz0, r, g, b, a);
            }
        }
        // X sides
        for (int ciy = 0; ciy < csY; ciy++) {
            float cpy0 = y0 + ciy * cdy;
            float cpy1 = ciy == csY - 1 ? y1 : cpy0 + cdy;
            for (int ciz = 0; ciz < csZ; ciz++) {
                float cpz0 = z0 + ciz * cdz;
                float cpz1 = ciz == csZ - 1 ? z1 : cpz0 + cdz;
                float midZ = (cpz0 + cpz1) * 0.5f - centerZ;
                float levelX0 = centerCharLevelAt((float) Math.toDegrees(Math.atan2(midZ, x0 - centerX)), 1.0f);
                float levelX1 = centerCharLevelAt((float) Math.toDegrees(Math.atan2(midZ, x1 - centerX)), 1.0f);
                if (levelX0 <= 0.01f && levelX1 <= 0.01f) {
                    continue;
                }
                int cseed = (ciy * 53 + ciz * 89 + 7777) & 0x7FFFFFFF;
                float cnoise = ((cseed % 100) / 100.0f) - 0.5f;
                int r = c255(0.018f + cnoise * 0.008f), g = c255(0.015f + cnoise * 0.008f), b = c255(0.015f + cnoise * 0.008f);
                if (levelX0 > 0.01f) {
                    int a = (int) (levelX0 * 0.98f * 255);
                    cv(cbuf, mat, x0, cpy0, cpz0, r, g, b, a);
                    cv(cbuf, mat, x0, cpy1, cpz0, r, g, b, a);
                    cv(cbuf, mat, x0, cpy1, cpz1, r, g, b, a);
                    cv(cbuf, mat, x0, cpy0, cpz1, r, g, b, a);
                }
                if (levelX1 > 0.01f) {
                    int a = (int) (levelX1 * 0.98f * 255);
                    cv(cbuf, mat, x1, cpy0, cpz1, r, g, b, a);
                    cv(cbuf, mat, x1, cpy1, cpz1, r, g, b, a);
                    cv(cbuf, mat, x1, cpy1, cpz0, r, g, b, a);
                    cv(cbuf, mat, x1, cpy0, cpz0, r, g, b, a);
                }
            }
        }
        // Z sides
        for (int ciy = 0; ciy < csY; ciy++) {
            float cpy0 = y0 + ciy * cdy;
            float cpy1 = ciy == csY - 1 ? y1 : cpy0 + cdy;
            for (int cix = 0; cix < csX; cix++) {
                float cpx0 = x0 + cix * cdx;
                float cpx1 = cix == csX - 1 ? x1 : cpx0 + cdx;
                float midX = (cpx0 + cpx1) * 0.5f - centerX;
                float levelZ0 = centerCharLevelAt((float) Math.toDegrees(Math.atan2(z0 - centerZ, midX)), 1.0f);
                float levelZ1 = centerCharLevelAt((float) Math.toDegrees(Math.atan2(z1 - centerZ, midX)), 1.0f);
                if (levelZ0 <= 0.01f && levelZ1 <= 0.01f) {
                    continue;
                }
                int cseed = (ciy * 53 + cix * 89 + 5555) & 0x7FFFFFFF;
                float cnoise = ((cseed % 100) / 100.0f) - 0.5f;
                int r = c255(0.018f + cnoise * 0.008f), g = c255(0.015f + cnoise * 0.008f), b = c255(0.015f + cnoise * 0.008f);
                if (levelZ0 > 0.01f) {
                    int a = (int) (levelZ0 * 0.98f * 255);
                    cv(cbuf, mat, cpx0, cpy0, z0, r, g, b, a);
                    cv(cbuf, mat, cpx1, cpy0, z0, r, g, b, a);
                    cv(cbuf, mat, cpx1, cpy1, z0, r, g, b, a);
                    cv(cbuf, mat, cpx0, cpy1, z0, r, g, b, a);
                }
                if (levelZ1 > 0.01f) {
                    int a = (int) (levelZ1 * 0.98f * 255);
                    cv(cbuf, mat, cpx0, cpy1, z1, r, g, b, a);
                    cv(cbuf, mat, cpx1, cpy1, z1, r, g, b, a);
                    cv(cbuf, mat, cpx1, cpy0, z1, r, g, b, a);
                    cv(cbuf, mat, cpx0, cpy0, z1, r, g, b, a);
                }
            }
        }
        endOverlay(cbuf);
    }

    private static float angularDiff(float a, float b) {
        float d = (a - b) % 360f;
        if (d > 180f) {
            d -= 360f;
        }
        if (d < -180f) {
            d += 360f;
        }
        return d;
    }

    /**
     * Charring level (0..1) of a point of the centre cube, synchronised with the petal burn waves.
     *
     * @param angleDeg       direction from the centre in the XZ plane
     * @param radialDistNorm 0 = exact centre, 1 = outer border facing the petals
     */
    private float centerCharLevelAt(float angleDeg, float radialDistNorm) {
        int n = petals.size();
        if (n == 0) {
            return 0f;
        }
        float wedge = 360f / n;
        float halfWedge = wedge * 0.5f;
        float blendAngle = 8.0f;
        float innerAngle = halfWedge - blendAngle * 0.5f;
        float outerAngle = halfWedge + blendAngle * 0.5f;
        float combined = 0f;
        for (Petal p : petals) {
            if (p.burnProgress <= 0.05f) {
                continue;
            }
            float rawT = Math.min(1.0f, Math.max(0.0f, (p.burnProgress - 0.05f) / 0.95f));
            float burnT = rawT * rawT * (3.0f - 2.0f * rawT);
            float absAngleDiff = Math.abs(angularDiff(angleDeg, p.centerAngleDeg));
            float angularWeight = 0f;
            if (absAngleDiff <= innerAngle) {
                angularWeight = 1.0f;
            } else if (absAngleDiff < outerAngle) {
                float t = (outerAngle - absAngleDiff) / blendAngle;
                angularWeight = t * t * (3.0f - 2.0f * t);
            }
            if (angularWeight <= 0.001f) {
                continue;
            }
            // the front sweeps inwards from the outer edge to the centre
            float frontRad = 1.0f - burnT * 1.25f;
            float transitionWidth = 0.20f;
            float radialFactor = 0f;
            if (radialDistNorm >= frontRad + transitionWidth) {
                radialFactor = 1.0f;
            } else if (radialDistNorm > frontRad) {
                float t = (radialDistNorm - frontRad) / transitionWidth;
                radialFactor = t * t * (3.0f - 2.0f * t);
            }
            if (radialFactor <= 0.001f) {
                continue;
            }
            combined = Math.min(1.0f, combined + burnT * angularWeight * radialFactor);
        }
        return combined;
    }

    public static class Bone {
        public final String name;
        public final String parentName;
        public final float[] pivot;
        public final float[] rotation;
        public final List<Cube> cubes = new ArrayList<>();
        public final List<Bone> children = new ArrayList<>();
        public final float[] animRot = new float[3];
        public final float[] animPos = new float[3];

        public Bone(String name, String parentName, float[] pivot, float[] rotation) {
            this.name = name;
            this.parentName = parentName;
            this.pivot = pivot;
            this.rotation = rotation;
        }
    }

    public static class Cube {
        public final float[] origin;
        public final float[] size;
        public final float u;
        public final float v;
        public final float[] cubePivot;
        public final float[] cubeRot;

        public Cube(float[] origin, float[] size, float u, float v, float[] cubePivot, float[] cubeRot) {
            this.origin = origin;
            this.size = size;
            this.u = u;
            this.v = v;
            this.cubePivot = cubePivot;
            this.cubeRot = cubeRot;
        }
    }

    public static class Animation {
        public final float length;
        public final Map<String, BoneTrack> tracks = new HashMap<>();

        public Animation(float length) {
            this.length = length;
        }
    }

    public static class BoneTrack {
        public final List<Keyframe> rotKeyframes = new ArrayList<>();
        public final List<Keyframe> posKeyframes = new ArrayList<>();
    }

    public record Keyframe(float time, float x, float y, float z) {
    }
}
