package com.voltyx.efw.doll.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.voltyx.efw.EFW;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bedrock model of Saya's doll in the "Java model" space the 1.12 BedrockDollModel used: bones carry a rotation point and angles
 * relative to their parent (y down), cubes have per-face UV with rotation (FloatModelBox), animations are keyframes in degrees
 * with cosine easing that are added to the base angles of the bones.
 */
public final class DollModel {
    private static final float S = 1.0F / 16.0F;

    private static final class Box {
        float x, y, z, dx, dy, dz, delta;
        int u, v;
        Map<String, float[]> faces;
    }

    private static final class Part {
        Box box;
        boolean transformed;
        float rpX, rpY, rpZ, rotX, rotY, rotZ;
    }

    private static final class Bone {
        String name;
        String parent;
        float pivX, pivY, pivZ;
        float baseX, baseY, baseZ;
        float rpX, rpY, rpZ;
        float rotX, rotY, rotZ;
        final List<Part> parts = new ArrayList<>();
        final List<Bone> children = new ArrayList<>();
    }

    private record Key(float time, float x, float y, float z) {
    }

    private static final class Track {
        final List<Key> rot = new ArrayList<>();
    }

    private static final class Anim {
        final float length;
        final Map<String, Track> tracks = new HashMap<>();

        Anim(float length) {
            this.length = length;
        }
    }

    private int texW = 64;
    private int texH = 64;
    private final Map<String, Bone> bones = new LinkedHashMap<>();
    private final List<Bone> roots = new ArrayList<>();
    private final Map<String, Anim> animations = new HashMap<>();
    private boolean loaded;

    public boolean isLoaded() {
        return loaded;
    }

    public float getAnimationLength(String name) {
        Anim a = animations.get(name);
        return a != null ? a.length : 0.5F;
    }

    public void load(ResourceLocation geo, ResourceLocation anim) {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            String g = read(geo);
            if (g != null) {
                parseGeometry(g);
            }
            if (anim != null) {
                String a = read(anim);
                if (a != null) {
                    parseAnimations(a);
                }
            }
        } catch (Exception e) {
            EFW.LOGGER.error("[Doll] cannot load {}", geo, e);
        }
    }

    private static String read(ResourceLocation loc) {
        try (InputStream in = Minecraft.getInstance().getResourceManager().getResourceOrThrow(loc).open()) {
            String s = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return s.startsWith("﻿") ? s.substring(1) : s;
        } catch (Exception e) {
            EFW.LOGGER.error("[Doll] missing resource {}", loc);
            return null;
        }
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
            JsonObject d = geom.getAsJsonObject("description");
            if (d.has("texture_width")) {
                texW = d.get("texture_width").getAsInt();
            }
            if (d.has("texture_height")) {
                texH = d.get("texture_height").getAsInt();
            }
        }
        JsonArray list = geom.getAsJsonArray("bones");
        if (list == null) {
            return;
        }
        for (JsonElement be : list) {
            JsonObject bo = be.getAsJsonObject();
            Bone bone = new Bone();
            bone.name = bo.get("name").getAsString();
            bone.parent = bo.has("parent") ? bo.get("parent").getAsString() : null;
            float[] piv = bo.has("pivot") ? vec(bo.getAsJsonArray("pivot")) : new float[3];
            bone.pivX = piv[0];
            bone.pivY = piv[1];
            bone.pivZ = piv[2];
            if (bo.has("rotation")) {
                float[] r = vec(bo.getAsJsonArray("rotation"));
                bone.baseX = (float) Math.toRadians(r[0]);
                bone.baseY = (float) Math.toRadians(r[1]);
                bone.baseZ = (float) Math.toRadians(r[2]);
            }
            bone.rotX = bone.baseX;
            bone.rotY = bone.baseY;
            bone.rotZ = bone.baseZ;
            if (bo.has("cubes")) {
                for (JsonElement ce : bo.getAsJsonArray("cubes")) {
                    parseCube(bone, ce.getAsJsonObject());
                }
            }
            bones.put(bone.name, bone);
        }
        for (Bone bone : bones.values()) {
            Bone parent = bone.parent != null ? bones.get(bone.parent) : null;
            if (parent != null) {
                bone.rpX = bone.pivX - parent.pivX;
                bone.rpY = parent.pivY - bone.pivY;
                bone.rpZ = bone.pivZ - parent.pivZ;
                parent.children.add(bone);
            } else {
                bone.rpX = bone.pivX;
                bone.rpY = 24.0F - bone.pivY;
                bone.rpZ = bone.pivZ;
                roots.add(bone);
            }
        }
    }

    private void parseCube(Bone bone, JsonObject c) {
        float[] o = vec(c.getAsJsonArray("origin"));
        float[] s = vec(c.getAsJsonArray("size"));
        Box box = new Box();
        box.dx = s[0];
        box.dy = s[1];
        box.dz = s[2];
        box.delta = c.has("inflate") ? c.get("inflate").getAsFloat() : 0.0F;
        if (c.has("uv")) {
            JsonElement uv = c.get("uv");
            if (uv.isJsonArray()) {
                box.u = uv.getAsJsonArray().get(0).getAsInt();
                box.v = uv.getAsJsonArray().get(1).getAsInt();
            } else if (uv.isJsonObject()) {
                box.faces = new HashMap<>();
                for (Map.Entry<String, JsonElement> fe : uv.getAsJsonObject().entrySet()) {
                    if (!fe.getValue().isJsonObject()) {
                        continue;
                    }
                    JsonObject fo = fe.getValue().getAsJsonObject();
                    if (fo.has("uv") && fo.has("uv_size")) {
                        JsonArray ua = fo.getAsJsonArray("uv");
                        JsonArray us = fo.getAsJsonArray("uv_size");
                        float rot = fo.has("uv_rotation") ? fo.get("uv_rotation").getAsFloat() : 0.0F;
                        box.faces.put(fe.getKey(), new float[] {ua.get(0).getAsFloat(), ua.get(1).getAsFloat(),
                                us.get(0).getAsFloat(), us.get(1).getAsFloat(), rot});
                    }
                }
            }
        }
        Part part = new Part();
        part.box = box;
        if (c.has("rotation") || c.has("pivot")) {
            float[] cp = c.has("pivot") ? vec(c.getAsJsonArray("pivot")) : o;
            part.transformed = true;
            part.rpX = cp[0] - bone.pivX;
            part.rpY = bone.pivY - cp[1];
            part.rpZ = cp[2] - bone.pivZ;
            if (c.has("rotation")) {
                float[] r = vec(c.getAsJsonArray("rotation"));
                part.rotX = (float) Math.toRadians(r[0]);
                part.rotY = (float) Math.toRadians(r[1]);
                part.rotZ = (float) Math.toRadians(r[2]);
            }
            box.x = o[0] - cp[0];
            box.y = cp[1] - o[1] - s[1];
            box.z = o[2] - cp[2];
        } else {
            box.x = o[0] - bone.pivX;
            box.y = bone.pivY - o[1] - s[1];
            box.z = o[2] - bone.pivZ;
        }
        bone.parts.add(part);
    }

    private void parseAnimations(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.has("animations")) {
            return;
        }
        for (Map.Entry<String, JsonElement> ae : root.getAsJsonObject("animations").entrySet()) {
            JsonObject ao = ae.getValue().getAsJsonObject();
            Anim anim = new Anim(ao.has("animation_length") ? ao.get("animation_length").getAsFloat() : 1.0F);
            if (ao.has("bones")) {
                for (Map.Entry<String, JsonElement> be : ao.getAsJsonObject("bones").entrySet()) {
                    JsonObject bd = be.getValue().getAsJsonObject();
                    Track track = new Track();
                    if (bd.has("rotation")) {
                        parseKeys(bd.get("rotation"), track.rot);
                    }
                    anim.tracks.put(be.getKey(), track);
                }
            }
            animations.put(ae.getKey(), anim);
        }
    }

    private static void parseKeys(JsonElement elem, List<Key> out) {
        if (elem.isJsonArray()) {
            float[] v = vec(elem.getAsJsonArray());
            out.add(new Key(0, v[0], v[1], v[2]));
        } else if (elem.isJsonObject()) {
            JsonObject obj = elem.getAsJsonObject();
            if (obj.has("vector")) {
                float[] v = vec(obj.getAsJsonArray("vector"));
                out.add(new Key(0, v[0], v[1], v[2]));
            } else {
                for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                    try {
                        float t = Float.parseFloat(e.getKey());
                        JsonElement val = e.getValue();
                        JsonArray a = null;
                        if (val.isJsonArray()) {
                            a = val.getAsJsonArray();
                        } else if (val.isJsonObject()) {
                            JsonObject vo = val.getAsJsonObject();
                            if (vo.has("vector")) {
                                a = vo.getAsJsonArray("vector");
                            } else if (vo.has("post")) {
                                a = vo.getAsJsonArray("post");
                            }
                        }
                        if (a != null) {
                            float[] v = vec(a);
                            out.add(new Key(t, v[0], v[1], v[2]));
                        }
                    } catch (RuntimeException ignored) {
                    }
                }
            }
        }
        out.sort(Comparator.comparingDouble(Key::time));
    }

    private static void eval(List<Key> keys, float time, float[] out) {
        out[0] = out[1] = out[2] = 0.0F;
        if (keys.isEmpty()) {
            return;
        }
        Key first = keys.get(0);
        if (keys.size() == 1 || time <= first.time) {
            out[0] = first.x;
            out[1] = first.y;
            out[2] = first.z;
            return;
        }
        Key last = keys.get(keys.size() - 1);
        if (time >= last.time) {
            out[0] = last.x;
            out[1] = last.y;
            out[2] = last.z;
            return;
        }
        for (int i = 0; i < keys.size() - 1; i++) {
            Key k0 = keys.get(i);
            Key k1 = keys.get(i + 1);
            if (time >= k0.time && time <= k1.time) {
                float seg = k1.time - k0.time;
                float t = seg <= 0 ? 0 : (time - k0.time) / seg;
                float f = (1.0F - (float) Math.cos(t * Math.PI)) * 0.5F;
                out[0] = k0.x + (k1.x - k0.x) * f;
                out[1] = k0.y + (k1.y - k0.y) * f;
                out[2] = k0.z + (k1.z - k0.z) * f;
                return;
            }
        }
    }

    private void resetPose() {
        for (Bone b : bones.values()) {
            b.rotX = b.baseX;
            b.rotY = b.baseY;
            b.rotZ = b.baseZ;
        }
    }

    public void applyAnimation(String name, float time) {
        resetPose();
        Anim anim = animations.get(name);
        if (anim == null) {
            return;
        }
        float[] tmp = new float[3];
        for (Map.Entry<String, Track> e : anim.tracks.entrySet()) {
            Bone bone = bones.get(e.getKey());
            if (bone == null) {
                continue;
            }
            eval(e.getValue().rot, time, tmp);
            bone.rotX += (float) Math.toRadians(tmp[0]);
            bone.rotY += (float) Math.toRadians(tmp[1]);
            bone.rotZ += (float) Math.toRadians(tmp[2]);
        }
    }

    /** Pose between two animations: weightB of the second, the rest of the first. */
    public void applyAnimationBlended(String animA, float timeA, String animB, float timeB, float weightB) {
        Anim a = animations.get(animA);
        Anim b = animations.get(animB);
        if (a == null || b == null) {
            applyAnimation(a != null ? animA : animB, a != null ? timeA : timeB);
            return;
        }
        resetPose();
        float wB = Math.max(0.0F, Math.min(1.0F, weightB));
        float wA = 1.0F - wB;
        Set<String> names = new HashSet<>(a.tracks.keySet());
        names.addAll(b.tracks.keySet());
        float[] ta = new float[3];
        float[] tb = new float[3];
        for (String name : names) {
            Bone bone = bones.get(name);
            if (bone == null) {
                continue;
            }
            Track tra = a.tracks.get(name);
            Track trb = b.tracks.get(name);
            if (tra != null) {
                eval(tra.rot, timeA, ta);
            } else {
                ta[0] = ta[1] = ta[2] = 0;
            }
            if (trb != null) {
                eval(trb.rot, timeB, tb);
            } else {
                tb[0] = tb[1] = tb[2] = 0;
            }
            bone.rotX += (float) Math.toRadians(ta[0] * wA + tb[0] * wB);
            bone.rotY += (float) Math.toRadians(ta[1] * wA + tb[1] * wB);
            bone.rotZ += (float) Math.toRadians(ta[2] * wA + tb[2] * wB);
        }
    }

    public void addBoneRotation(String bone, float x, float y, float z) {
        Bone b = bones.get(bone);
        if (b != null) {
            b.rotX += x;
            b.rotY += y;
            b.rotZ += z;
        }
    }

    public void setBoneRotation(String bone, float x, float y, float z) {
        Bone b = bones.get(bone);
        if (b != null) {
            b.rotX = x;
            b.rotY = y;
            b.rotZ = z;
        }
    }

    /** The whole model; the origin is the model's point at the bedrock height of 24. */
    public void render(PoseStack pose, VertexConsumer buf, int light, int overlay, float r, float g, float b, float a) {
        for (Bone root : roots) {
            renderBone(pose, buf, root, false, 0, 0, 0, 0, 0, 0, 1.0F, light, overlay, r, g, b, a);
        }
    }

    /** One bone with its children, put at the given point and angles (Java px / radians) instead of its own. */
    public void renderBranch(PoseStack pose, VertexConsumer buf, String boneName, float x, float y, float z,
                             float rotX, float rotY, float rotZ, float scale, int light, int overlay, float r, float g, float b, float a) {
        Bone bone = bones.get(boneName);
        if (bone != null) {
            renderBone(pose, buf, bone, true, x, y, z, rotX, rotY, rotZ, scale, light, overlay, r, g, b, a);
        }
    }

    private void renderBone(PoseStack pose, VertexConsumer buf, Bone bone, boolean override, float ox, float oy, float oz,
                            float orx, float ory, float orz, float scale, int light, int overlay, float r, float g, float b, float a) {
        pose.pushPose();
        if (override) {
            pose.translate(ox * S, oy * S, oz * S);
            pose.mulPose(new Quaternionf().rotationZYX(orz, ory, orx));
            if (scale != 1.0F) {
                pose.scale(scale, scale, scale);
            }
        } else {
            pose.translate(bone.rpX * S, bone.rpY * S, bone.rpZ * S);
            pose.mulPose(new Quaternionf().rotationZYX(bone.rotZ, bone.rotY, bone.rotX));
        }
        for (Part part : bone.parts) {
            if (part.transformed) {
                pose.pushPose();
                pose.translate(part.rpX * S, part.rpY * S, part.rpZ * S);
                pose.mulPose(new Quaternionf().rotationZYX(part.rotZ, part.rotY, part.rotX));
                drawBox(pose, buf, part.box, light, overlay, r, g, b, a);
                pose.popPose();
            } else {
                drawBox(pose, buf, part.box, light, overlay, r, g, b, a);
            }
        }
        for (Bone child : bone.children) {
            renderBone(pose, buf, child, false, 0, 0, 0, 0, 0, 0, 1.0F, light, overlay, r, g, b, a);
        }
        pose.popPose();
    }

    private void drawBox(PoseStack pose, VertexConsumer buf, Box box, int light, int overlay, float r, float g, float b, float a) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float x = box.x - box.delta, y = box.y - box.delta, z = box.z - box.delta;
        float f = box.x + box.dx + box.delta, f1 = box.y + box.dy + box.delta, f2 = box.z + box.dz + box.delta;
        // corners: 7 = (x,y,z) 0 = (f,y,z) 1 = (f,f1,z) 2 = (x,f1,z) 3 = (x,y,f2) 4 = (f,y,f2) 5 = (f,f1,f2) 6 = (x,f1,f2)
        float[][] c = {
                {f, y, z}, {f, f1, z}, {x, f1, z}, {x, y, f2}, {f, y, f2}, {f, f1, f2}, {x, f1, f2}, {x, y, z}
        };
        if (box.faces != null) {
            face(buf, m, n, box.faces.get("east"), 1, 0, 0, light, overlay, r, g, b, a, new float[][] {c[4], c[0], c[1], c[5]});
            face(buf, m, n, box.faces.get("west"), -1, 0, 0, light, overlay, r, g, b, a, new float[][] {c[7], c[3], c[6], c[2]});
            face(buf, m, n, box.faces.get("up"), 0, -1, 0, light, overlay, r, g, b, a, new float[][] {c[4], c[3], c[7], c[0]});
            face(buf, m, n, box.faces.get("down"), 0, 1, 0, light, overlay, r, g, b, a, new float[][] {c[1], c[2], c[6], c[5]});
            face(buf, m, n, box.faces.get("north"), 0, 0, -1, light, overlay, r, g, b, a, new float[][] {c[0], c[7], c[2], c[1]});
            face(buf, m, n, box.faces.get("south"), 0, 0, 1, light, overlay, r, g, b, a, new float[][] {c[3], c[4], c[5], c[6]});
        } else {
            float u = box.u, v = box.v;
            float w = Math.round(box.dx), h = Math.round(box.dy), d = Math.round(box.dz);
            face(buf, m, n, new float[] {u + d + w, v + d, d, h, 0}, 1, 0, 0, light, overlay, r, g, b, a, new float[][] {c[4], c[0], c[1], c[5]});
            face(buf, m, n, new float[] {u, v + d, d, h, 0}, -1, 0, 0, light, overlay, r, g, b, a, new float[][] {c[7], c[3], c[6], c[2]});
            face(buf, m, n, new float[] {u + d, v, w, d, 0}, 0, -1, 0, light, overlay, r, g, b, a, new float[][] {c[4], c[3], c[7], c[0]});
            face(buf, m, n, new float[] {u + d + w, v + d, w, -d, 0}, 0, 1, 0, light, overlay, r, g, b, a, new float[][] {c[1], c[2], c[6], c[5]});
            face(buf, m, n, new float[] {u + d, v + d, w, h, 0}, 0, 0, -1, light, overlay, r, g, b, a, new float[][] {c[0], c[7], c[2], c[1]});
            face(buf, m, n, new float[] {u + d + w + d, v + d, w, h, 0}, 0, 0, 1, light, overlay, r, g, b, a, new float[][] {c[3], c[4], c[5], c[6]});
        }
    }

    /** FloatModelBox.makeQuadRot: the corner of the texture a vertex gets depends on the rotation of the face. */
    private void face(VertexConsumer buf, Matrix4f m, Matrix3f n, float[] uv, float nx, float ny, float nz, int light, int overlay,
                      float r, float g, float b, float a, float[][] p) {
        if (uv == null) {
            return;
        }
        float u0 = uv[0], v0 = uv[1], u1 = uv[0] + uv[2], v1 = uv[1] + uv[3];
        float[] us, vs;
        switch (((((int) uv[4]) % 360) + 360) % 360) {
            case 90 -> {
                us = new float[] {u0, u0, u1, u1};
                vs = new float[] {v1, v0, v0, v1};
            }
            case 180 -> {
                us = new float[] {u0, u1, u1, u0};
                vs = new float[] {v1, v1, v0, v0};
            }
            case 270 -> {
                us = new float[] {u1, u1, u0, u0};
                vs = new float[] {v0, v1, v1, v0};
            }
            default -> {
                us = new float[] {u1, u0, u0, u1};
                vs = new float[] {v0, v0, v1, v1};
            }
        }
        for (int i = 0; i < 4; i++) {
            buf.vertex(m, p[i][0] * S, p[i][1] * S, p[i][2] * S).color(r, g, b, a).uv(us[i] / texW, vs[i] / texH)
                    .overlayCoords(overlay).uv2(light).normal(n, nx, ny, nz).endVertex();
        }
    }
}
