package com.voltyx.efw.tech.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.voltyx.efw.EFW;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Bedrock geometry + animation for block entities (port of BedrockBlockModel): bones, box or per-face UV cubes, keyframe
 * rotation/position tracks with cosine interpolation. Rendered mirrored on X like the 1.12 renderer.
 */
public class BedrockModel {
    public static class FaceUV {
        public final float u1, v1, u2, v2;

        FaceUV(float u1, float v1, float u2, float v2) {
            this.u1 = u1;
            this.v1 = v1;
            this.u2 = u2;
            this.v2 = v2;
        }
    }

    public static class Cube {
        public final float[] origin = new float[3];
        public final float[] size = new float[3];
        public final float[] cubePivot;
        public final float[] cubeRot;
        public float boxU, boxV;
        public boolean hasPerFaceUV;
        public final Map<String, FaceUV> faceUVMap = new HashMap<>();

        Cube(float ox, float oy, float oz, float sx, float sy, float sz, float[] cubePivot, float[] cubeRot) {
            origin[0] = ox;
            origin[1] = oy;
            origin[2] = oz;
            size[0] = sx;
            size[1] = sy;
            size[2] = sz;
            this.cubePivot = cubePivot;
            this.cubeRot = cubeRot;
        }
    }

    public static class Bone {
        public final String name;
        public final String parentName;
        public final float[] pivot = new float[3];
        public final float[] rotation = new float[3];
        public final List<Cube> cubes = new ArrayList<>();
        public final List<Bone> children = new ArrayList<>();
        public final float[] animPos = new float[3];
        public final float[] animRot = new float[3];

        Bone(String name, String parentName, float[] pivot, float[] rotation) {
            this.name = name;
            this.parentName = parentName;
            System.arraycopy(pivot, 0, this.pivot, 0, 3);
            System.arraycopy(rotation, 0, this.rotation, 0, 3);
        }
    }

    record Keyframe(float time, float x, float y, float z) {
    }

    static class BoneTrack {
        final List<Keyframe> rot = new ArrayList<>();
        final List<Keyframe> pos = new ArrayList<>();
    }

    static class Animation {
        final float length;
        final Map<String, BoneTrack> tracks = new HashMap<>();

        Animation(float length) {
            this.length = length;
        }
    }

    private float textureWidth = 64f;
    private float textureHeight = 64f;
    private final Map<String, Bone> bonesByName = new LinkedHashMap<>();
    private final List<Bone> rootBones = new ArrayList<>();
    private final Map<String, Animation> animations = new HashMap<>();
    private boolean loaded;

    public boolean isLoaded() {
        return loaded;
    }

    public float textureWidth() {
        return textureWidth;
    }

    public float textureHeight() {
        return textureHeight;
    }

    private static String read(ResourceLocation loc) throws Exception {
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(loc);
        if (res.isEmpty()) {
            return null;
        }
        try (InputStream in = res.get().open()) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return json.startsWith("﻿") ? json.substring(1) : json;
        }
    }

    public void load(ResourceLocation geo, ResourceLocation anim) {
        if (loaded) {
            return;
        }
        try {
            String geoJson = read(geo);
            if (geoJson != null) {
                parseGeometry(geoJson);
            }
            if (anim != null) {
                String animJson = read(anim);
                if (animJson != null) {
                    parseAnimation(animJson);
                }
            }
        } catch (Exception e) {
            EFW.LOGGER.error("[BedrockModel] cannot load {}", geo, e);
        }
        loaded = true;
    }

    private static float[] vec(JsonArray a) {
        return new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
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
        JsonArray bones = geom.getAsJsonArray("bones");
        if (bones == null) {
            return;
        }
        for (JsonElement be : bones) {
            JsonObject b = be.getAsJsonObject();
            String name = b.get("name").getAsString();
            String parent = b.has("parent") ? b.get("parent").getAsString() : null;
            Bone bone = new Bone(name, parent, b.has("pivot") ? vec(b.getAsJsonArray("pivot")) : new float[3],
                    b.has("rotation") ? vec(b.getAsJsonArray("rotation")) : new float[3]);
            if (b.has("cubes")) {
                for (JsonElement ce : b.getAsJsonArray("cubes")) {
                    JsonObject c = ce.getAsJsonObject();
                    float[] o = vec(c.getAsJsonArray("origin"));
                    float[] s = vec(c.getAsJsonArray("size"));
                    Cube cube = new Cube(o[0], o[1], o[2], s[0], s[1], s[2], c.has("pivot") ? vec(c.getAsJsonArray("pivot")) : null,
                            c.has("rotation") ? vec(c.getAsJsonArray("rotation")) : null);
                    if (c.has("uv")) {
                        JsonElement uv = c.get("uv");
                        if (uv.isJsonObject()) {
                            cube.hasPerFaceUV = true;
                            for (Map.Entry<String, JsonElement> face : uv.getAsJsonObject().entrySet()) {
                                if (!face.getValue().isJsonObject()) {
                                    continue;
                                }
                                JsonObject f = face.getValue().getAsJsonObject();
                                float u = 0, v = 0, us = 0, vs = 0;
                                if (f.has("uv")) {
                                    u = f.getAsJsonArray("uv").get(0).getAsFloat();
                                    v = f.getAsJsonArray("uv").get(1).getAsFloat();
                                }
                                if (f.has("uv_size")) {
                                    us = f.getAsJsonArray("uv_size").get(0).getAsFloat();
                                    vs = f.getAsJsonArray("uv_size").get(1).getAsFloat();
                                }
                                cube.faceUVMap.put(face.getKey().toLowerCase(Locale.ROOT), new FaceUV(u, v, u + us, v + vs));
                            }
                        } else if (uv.isJsonArray()) {
                            cube.boxU = uv.getAsJsonArray().get(0).getAsFloat();
                            cube.boxV = uv.getAsJsonArray().get(1).getAsFloat();
                        }
                    }
                    bone.cubes.add(cube);
                }
            }
            bonesByName.put(name, bone);
        }
        for (Bone bone : bonesByName.values()) {
            Bone parent = bone.parentName != null ? bonesByName.get(bone.parentName) : null;
            if (parent != null) {
                parent.children.add(bone);
            } else {
                rootBones.add(bone);
            }
        }
    }

    private void parseAnimation(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.has("animations")) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("animations").entrySet()) {
            JsonObject a = entry.getValue().getAsJsonObject();
            Animation anim = new Animation(a.has("animation_length") ? a.get("animation_length").getAsFloat() : 1.0f);
            if (a.has("bones")) {
                for (Map.Entry<String, JsonElement> be : a.getAsJsonObject("bones").entrySet()) {
                    JsonObject data = be.getValue().getAsJsonObject();
                    BoneTrack track = new BoneTrack();
                    if (data.has("rotation")) {
                        parseKeyframes(data.get("rotation"), track.rot);
                    }
                    if (data.has("position")) {
                        parseKeyframes(data.get("position"), track.pos);
                    }
                    anim.tracks.put(be.getKey(), track);
                }
            }
            animations.put(entry.getKey(), anim);
        }
    }

    private static void parseKeyframes(JsonElement elem, List<Keyframe> target) {
        if (elem.isJsonObject()) {
            JsonObject obj = elem.getAsJsonObject();
            if (obj.has("vector")) {
                float[] v = vec(obj.getAsJsonArray("vector"));
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
                                v = vec(vo.getAsJsonArray("post"));
                            } else if (vo.has("vector")) {
                                v = vec(vo.getAsJsonArray("vector"));
                            }
                        } else if (val.isJsonArray()) {
                            v = vec(val.getAsJsonArray());
                        }
                        if (v != null) {
                            target.add(new Keyframe(time, v[0], v[1], v[2]));
                        }
                    } catch (RuntimeException ignored) {
                    }
                }
            }
        } else if (elem.isJsonArray()) {
            float[] v = vec(elem.getAsJsonArray());
            target.add(new Keyframe(0f, v[0], v[1], v[2]));
        }
        target.sort(Comparator.comparingDouble(Keyframe::time));
    }

    public void resetAnimations() {
        for (Bone bone : bonesByName.values()) {
            for (int i = 0; i < 3; i++) {
                bone.animRot[i] = 0;
                bone.animPos[i] = 0;
            }
        }
    }

    public void applyAnimation(String name, float time) {
        resetAnimations();
        blendAnimation(name, time);
    }

    /** Adds an animation at a time on top of the current pose. */
    public void blendAnimation(String name, float time) {
        Animation anim = animations.get(name);
        if (anim == null) {
            return;
        }
        float[] r = new float[3];
        float[] p = new float[3];
        for (Map.Entry<String, BoneTrack> e : anim.tracks.entrySet()) {
            Bone bone = bonesByName.get(e.getKey());
            if (bone == null) {
                continue;
            }
            r[0] = r[1] = r[2] = 0;
            p[0] = p[1] = p[2] = 0;
            evaluate(e.getValue().rot, time, r);
            evaluate(e.getValue().pos, time, p);
            for (int i = 0; i < 3; i++) {
                bone.animRot[i] += r[i];
                bone.animPos[i] += p[i];
            }
        }
    }

    public Bone getBone(String name) {
        return bonesByName.get(name);
    }

    private static void evaluate(List<Keyframe> kfs, float time, float[] out) {
        if (kfs.isEmpty()) {
            return;
        }
        Keyframe first = kfs.get(0);
        if (kfs.size() == 1 || time <= first.time()) {
            out[0] = first.x();
            out[1] = first.y();
            out[2] = first.z();
            return;
        }
        Keyframe last = kfs.get(kfs.size() - 1);
        if (time >= last.time()) {
            out[0] = last.x();
            out[1] = last.y();
            out[2] = last.z();
            return;
        }
        for (int i = 0; i < kfs.size() - 1; i++) {
            Keyframe k0 = kfs.get(i);
            Keyframe k1 = kfs.get(i + 1);
            if (time >= k0.time() && time <= k1.time()) {
                float seg = k1.time() - k0.time();
                float t = seg <= 0 ? 0 : (time - k0.time()) / seg;
                float f = (1.0f - (float) Math.cos(t * Math.PI)) * 0.5f;
                out[0] = k0.x() + (k1.x() - k0.x()) * f;
                out[1] = k0.y() + (k1.y() - k0.y()) * f;
                out[2] = k0.z() + (k1.z() - k0.z()) * f;
                return;
            }
        }
    }

    /** Renders the whole model at 1/16 scale, mirrored on X (BedrockBlockModel.render). */
    public void render(PoseStack pose, VertexConsumer buf, int light, int overlay) {
        pose.pushPose();
        pose.scale(-1.0F, 1.0F, 1.0F);
        for (Bone root : rootBones) {
            renderBone(pose, buf, root, null, light, overlay);
        }
        pose.popPose();
    }

    private void renderBone(PoseStack pose, VertexConsumer buf, Bone bone, Bone parent, int light, int overlay) {
        final float scale = 1.0F / 16.0F;
        pose.pushPose();
        float ox = parent == null ? bone.pivot[0] : bone.pivot[0] - parent.pivot[0];
        float oy = parent == null ? bone.pivot[1] : bone.pivot[1] - parent.pivot[1];
        float oz = parent == null ? bone.pivot[2] : bone.pivot[2] - parent.pivot[2];
        pose.translate((ox + bone.animPos[0]) * scale, (oy + bone.animPos[1]) * scale, (oz + bone.animPos[2]) * scale);
        float rx = -(bone.rotation[0] + bone.animRot[0]);
        float ry = -(bone.rotation[1] + bone.animRot[1]);
        float rz = -(bone.rotation[2] + bone.animRot[2]);
        if (rz != 0f) {
            pose.mulPose(Axis.ZP.rotationDegrees(rz));
        }
        if (ry != 0f) {
            pose.mulPose(Axis.YP.rotationDegrees(ry));
        }
        if (rx != 0f) {
            pose.mulPose(Axis.XP.rotationDegrees(rx));
        }
        for (Cube cube : bone.cubes) {
            renderCube(pose, buf, cube, bone, light, overlay);
        }
        for (Bone child : bone.children) {
            renderBone(pose, buf, child, bone, light, overlay);
        }
        pose.popPose();
    }

    private void renderCube(PoseStack pose, VertexConsumer buf, Cube cube, Bone bone, int light, int overlay) {
        final float scale = 1.0F / 16.0F;
        pose.pushPose();
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
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float x0 = (cube.origin[0] - bone.pivot[0]) * scale;
        float y0 = (cube.origin[1] - bone.pivot[1]) * scale;
        float z0 = (cube.origin[2] - bone.pivot[2]) * scale;
        float x1 = x0 + cube.size[0] * scale;
        float y1 = y0 + cube.size[1] * scale;
        float z1 = z0 + cube.size[2] * scale;
        float tw = textureWidth;
        float th = textureHeight;
        if (cube.hasPerFaceUV) {
            FaceUV f = cube.faceUVMap.get("east");
            if (f != null) {
                quad(buf, m, n, light, overlay, 1, 0, 0,
                        x1, y1, z0, f.u1 / tw, f.v1 / th, x1, y1, z1, f.u2 / tw, f.v1 / th,
                        x1, y0, z1, f.u2 / tw, f.v2 / th, x1, y0, z0, f.u1 / tw, f.v2 / th);
            }
            f = cube.faceUVMap.get("west");
            if (f != null) {
                quad(buf, m, n, light, overlay, -1, 0, 0,
                        x0, y1, z1, f.u1 / tw, f.v1 / th, x0, y1, z0, f.u2 / tw, f.v1 / th,
                        x0, y0, z0, f.u2 / tw, f.v2 / th, x0, y0, z1, f.u1 / tw, f.v2 / th);
            }
            f = cube.faceUVMap.get("up");
            if (f != null) {
                quad(buf, m, n, light, overlay, 0, 1, 0,
                        x1, y1, z0, f.u2 / tw, f.v2 / th, x0, y1, z0, f.u1 / tw, f.v2 / th,
                        x0, y1, z1, f.u1 / tw, f.v1 / th, x1, y1, z1, f.u2 / tw, f.v1 / th);
            }
            f = cube.faceUVMap.get("down");
            if (f != null) {
                quad(buf, m, n, light, overlay, 0, -1, 0,
                        x1, y0, z0, f.u1 / tw, f.v1 / th, x0, y0, z0, f.u2 / tw, f.v1 / th,
                        x0, y0, z1, f.u2 / tw, f.v2 / th, x1, y0, z1, f.u1 / tw, f.v2 / th);
            }
            f = cube.faceUVMap.get("north");
            if (f != null) {
                quad(buf, m, n, light, overlay, 0, 0, -1,
                        x0, y1, z0, f.u1 / tw, f.v1 / th, x1, y1, z0, f.u2 / tw, f.v1 / th,
                        x1, y0, z0, f.u2 / tw, f.v2 / th, x0, y0, z0, f.u1 / tw, f.v2 / th);
            }
            f = cube.faceUVMap.get("south");
            if (f != null) {
                quad(buf, m, n, light, overlay, 0, 0, 1,
                        x1, y1, z1, f.u1 / tw, f.v1 / th, x0, y1, z1, f.u2 / tw, f.v1 / th,
                        x0, y0, z1, f.u2 / tw, f.v2 / th, x1, y0, z1, f.u1 / tw, f.v2 / th);
            }
        } else {
            float u = cube.boxU, v = cube.boxV;
            float w = cube.size[0], h = cube.size[1], d = cube.size[2];
            // up
            quad(buf, m, n, light, overlay, 0, 1, 0,
                    x1, y1, z0, (u + d + w) / tw, (v + d) / th, x0, y1, z0, (u + d) / tw, (v + d) / th,
                    x0, y1, z1, (u + d) / tw, v / th, x1, y1, z1, (u + d + w) / tw, v / th);
            // down
            quad(buf, m, n, light, overlay, 0, -1, 0,
                    x1, y0, z0, (u + d + w) / tw, v / th, x0, y0, z0, (u + d + w + w) / tw, v / th,
                    x0, y0, z1, (u + d + w + w) / tw, (v + d) / th, x1, y0, z1, (u + d + w) / tw, (v + d) / th);
            // north
            quad(buf, m, n, light, overlay, 0, 0, -1,
                    x0, y1, z0, (u + d) / tw, (v + d) / th, x1, y1, z0, (u + d + w) / tw, (v + d) / th,
                    x1, y0, z0, (u + d + w) / tw, (v + d + h) / th, x0, y0, z0, (u + d) / tw, (v + d + h) / th);
            // south
            quad(buf, m, n, light, overlay, 0, 0, 1,
                    x1, y1, z1, (u + d + w + d) / tw, (v + d) / th, x0, y1, z1, (u + d + w + d + w) / tw, (v + d) / th,
                    x0, y0, z1, (u + d + w + d + w) / tw, (v + d + h) / th, x1, y0, z1, (u + d + w + d) / tw, (v + d + h) / th);
            // west
            quad(buf, m, n, light, overlay, -1, 0, 0,
                    x0, y1, z1, u / tw, (v + d) / th, x0, y1, z0, (u + d) / tw, (v + d) / th,
                    x0, y0, z0, (u + d) / tw, (v + d + h) / th, x0, y0, z1, u / tw, (v + d + h) / th);
            // east
            quad(buf, m, n, light, overlay, 1, 0, 0,
                    x1, y1, z0, (u + d + w) / tw, (v + d) / th, x1, y1, z1, (u + d + w + d) / tw, (v + d) / th,
                    x1, y0, z1, (u + d + w + d) / tw, (v + d + h) / th, x1, y0, z0, (u + d + w) / tw, (v + d + h) / th);
        }
        pose.popPose();
    }

    static void quad(VertexConsumer buf, Matrix4f m, Matrix3f n, int light, int overlay, float nx, float ny, float nz,
                     float ax, float ay, float az, float au, float av, float bx, float by, float bz, float bu, float bv,
                     float cx, float cy, float cz, float cu, float cv, float dx, float dy, float dz, float du, float dv) {
        vertex(buf, m, n, ax, ay, az, au, av, nx, ny, nz, light, overlay);
        vertex(buf, m, n, bx, by, bz, bu, bv, nx, ny, nz, light, overlay);
        vertex(buf, m, n, cx, cy, cz, cu, cv, nx, ny, nz, light, overlay);
        vertex(buf, m, n, dx, dy, dz, du, dv, nx, ny, nz, light, overlay);
    }

    static void vertex(VertexConsumer buf, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v,
                       float nx, float ny, float nz, int light, int overlay) {
        buf.vertex(m, x, y, z).color(1.0F, 1.0F, 1.0F, 1.0F).uv(u, v).overlayCoords(overlay).uv2(light).normal(n, nx, ny, nz).endVertex();
    }

    /**
     * Renders one bone as a Java model part attached to a vanilla bone (GeoArmorModel + FloatModelBox): Java space is
     * y-down at 1/16 scale, X is not mirrored; the bone is placed relative to a parent whose bedrock pivot is (0, 24, 0).
     */
    public void renderAsJavaPart(PoseStack pose, VertexConsumer buf, String boneName, float pointZShift, int light, int overlay) {
        Bone bone = bonesByName.get(boneName);
        if (bone == null) {
            return;
        }
        final float s = 1.0F / 16.0F;
        pose.pushPose();
        pose.translate(bone.pivot[0] * s, (24.0F - bone.pivot[1]) * s, (bone.pivot[2] - pointZShift) * s);
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        for (Cube cube : bone.cubes) {
            // box relative to the bone point, Java orientation
            float x = cube.origin[0] - bone.pivot[0];
            float y = bone.pivot[1] - cube.origin[1] - cube.size[1];
            float z = cube.origin[2] - bone.pivot[2];
            float x0 = x * s, y0 = y * s, z0 = z * s;
            float x1 = (x + cube.size[0]) * s, y1 = (y + cube.size[1]) * s, z1 = (z + cube.size[2]) * s;
            if (cube.hasPerFaceUV) {
                javaFace(buf, m, n, light, overlay, cube.faceUVMap.get("east"), 1, 0, 0,
                        x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
                javaFace(buf, m, n, light, overlay, cube.faceUVMap.get("west"), -1, 0, 0,
                        x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
                javaFace(buf, m, n, light, overlay, cube.faceUVMap.get("up"), 0, -1, 0,
                        x1, y0, z1, x0, y0, z1, x0, y0, z0, x1, y0, z0);
                javaFace(buf, m, n, light, overlay, cube.faceUVMap.get("down"), 0, 1, 0,
                        x1, y1, z0, x0, y1, z0, x0, y1, z1, x1, y1, z1);
                javaFace(buf, m, n, light, overlay, cube.faceUVMap.get("north"), 0, 0, -1,
                        x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
                javaFace(buf, m, n, light, overlay, cube.faceUVMap.get("south"), 0, 0, 1,
                        x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
            } else {
                // vanilla box UV with sizes rounded like FloatModelBox
                float u = cube.boxU, v = cube.boxV;
                float w = Math.round(cube.size[0]), h = Math.round(cube.size[1]), d = Math.round(cube.size[2]);
                javaFace(buf, m, n, light, overlay, new FaceUV(u + d + w, v + d, u + d + w + d, v + d + h), 1, 0, 0,
                        x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
                javaFace(buf, m, n, light, overlay, new FaceUV(u, v + d, u + d, v + d + h), -1, 0, 0,
                        x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
                javaFace(buf, m, n, light, overlay, new FaceUV(u + d, v, u + d + w, v + d), 0, -1, 0,
                        x1, y0, z1, x0, y0, z1, x0, y0, z0, x1, y0, z0);
                javaFace(buf, m, n, light, overlay, new FaceUV(u + d + w, v + d, u + d + w + w, v), 0, 1, 0,
                        x1, y1, z0, x0, y1, z0, x0, y1, z1, x1, y1, z1);
                javaFace(buf, m, n, light, overlay, new FaceUV(u + d, v + d, u + d + w, v + d + h), 0, 0, -1,
                        x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
                javaFace(buf, m, n, light, overlay, new FaceUV(u + d + w + d, v + d, u + d + w + d + w, v + d + h), 0, 0, 1,
                        x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
            }
        }
        pose.popPose();
    }

    /** FloatModelBox.makeQuadRot at 0 degrees: vertex 0 -> (u2, v1), 1 -> (u1, v1), 2 -> (u1, v2), 3 -> (u2, v2). */
    private void javaFace(VertexConsumer buf, Matrix4f m, Matrix3f n, int light, int overlay, FaceUV f, float nx, float ny, float nz,
                          float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz) {
        if (f == null) {
            return;
        }
        float tw = textureWidth, th = textureHeight;
        quad(buf, m, n, light, overlay, nx, ny, nz,
                ax, ay, az, f.u2 / tw, f.v1 / th, bx, by, bz, f.u1 / tw, f.v1 / th,
                cx, cy, cz, f.u1 / tw, f.v2 / th, dx, dy, dz, f.u2 / tw, f.v2 / th);
    }
}
