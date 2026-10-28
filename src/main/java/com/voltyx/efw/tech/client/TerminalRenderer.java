package com.voltyx.efw.tech.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.voltyx.efw.EFW;
import com.voltyx.efw.tech.BodycamEntry;
import com.voltyx.efw.tech.BodycamItem;
import com.voltyx.efw.tech.TerminalBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import org.joml.Matrix4f;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/** Wall terminal (port of TileEntityTerminalRenderer): bedrock model with the keyboard animation and the glowing CRT console. */
public class TerminalRenderer implements BlockEntityRenderer<TerminalBlockEntity> {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final BedrockModel MODEL = new BedrockModel();
    private static final ResourceLocation GEO = new ResourceLocation(EFW.MOD_ID, "bedrock/terminal.geo.json");
    private static final ResourceLocation ANIM = new ResourceLocation(EFW.MOD_ID, "animations/terminal.animation.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(EFW.MOD_ID, "textures/block/terminal.png");
    private static final int LINE = 10;

    private final Font font;

    public TerminalRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    static float facingAngle(Direction facing) {
        return switch (facing) {
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            case EAST -> 270.0F;
            default -> 0.0F;
        };
    }

    @Override
    public void render(TerminalBlockEntity te, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        MODEL.load(GEO, ANIM);
        pose.pushPose();
        pose.translate(0.5F, 0.0F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(facingAngle(te.getBlockState().getValue(HorizontalDirectionalBlock.FACING))));
        MODEL.applyAnimation("keyboardopen", te.getInterpolatedAnimTime(partialTicks));
        MODEL.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, overlay);

        TerminalSession session = TerminalSession.get();
        boolean isSessionTerminal = te.getBlockPos().equals(session.getTerminalPos());
        boolean streaming = isSessionTerminal && session.getStage() == TerminalSession.Stage.BODYCAM_VIEW;
        boolean interacting = te.getBlockPos().equals(TerminalCamera.getTerminalPos()) && session.getScreenFade() > 0.01F
                && TerminalCamera.getTransitionProgress() > 0.08F;
        if (streaming || interacting) {
            renderMonitor(pose, buffers, session);
        }
        pose.popPose();
    }

    private static int fade(int color, float fade) {
        int a = (color >> 24) & 0xFF;
        if (a == 0) {
            a = 255;
        }
        int fa = Math.max(4, Math.min(255, (int) (a * fade)));
        return (fa << 24) | (color & 0x00FFFFFF);
    }

    private void renderMonitor(PoseStack pose, MultiBufferSource buffers, TerminalSession session) {
        TerminalSession.Stage stage = session.getStage();
        boolean viewing = stage == TerminalSession.Stage.BODYCAM_VIEW;
        float fade = viewing ? 1.0F : session.getScreenFade();
        if (fade <= 0.01F) {
            return;
        }
        pose.pushPose();
        // on the front of the display (model z 7), facing the room
        pose.translate(0.0F, 9.5F / 16.0F, (7.0F - 0.015F) / 16.0F);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        float screenW = 14.0F / 16.0F;
        float screenH = 9.0F / 16.0F;
        float halfW = screenW / 2.0F;
        float halfH = screenH / 2.0F;
        Matrix4f m = pose.last().pose();

        BodycamEntry currentCam = session.getCurrentViewingCamera();
        boolean hasFeed = viewing && currentCam != null && currentCam.isOnline && BodycamFeed.hasValidTexture() && !BodycamFeed.isRendering();
        if (hasFeed) {
            VertexConsumer feed = buffers.getBuffer(TechRenderTypes.feed(BodycamFeed.TEXTURE));
            feed.vertex(m, -halfW, -halfH, 0.0F).uv(0.0F, 0.0F).endVertex();
            feed.vertex(m, halfW, -halfH, 0.0F).uv(1.0F, 0.0F).endVertex();
            feed.vertex(m, halfW, halfH, 0.0F).uv(1.0F, 1.0F).endVertex();
            feed.vertex(m, -halfW, halfH, 0.0F).uv(0.0F, 1.0F).endVertex();
            // faint phosphor tint
            quad(buffers.getBuffer(TechRenderTypes.QUADS), m, -halfW, -halfH, halfW, halfH, 0.001F, 0.0F, 0.06F, 0.02F, 0.08F * fade);
        } else {
            float r = (viewing ? 0.012F : 0.006F) * fade;
            float g = (viewing ? 0.012F : 0.014F) * fade;
            float b = (viewing ? 0.014F : 0.008F) * fade;
            quad(buffers.getBuffer(TechRenderTypes.QUADS), m, -halfW, -halfH, halfW, halfH, 0.0F, r, g, b, fade);
        }

        pose.pushPose();
        pose.translate(0.0F, 0.0F, 0.004F);
        float textScale = viewing ? 0.0030F : 0.0038F;
        pose.scale(textScale, -textScale, textScale);
        Matrix4f t = pose.last().pose();
        float margin = viewing ? 0.024F : 0.018F;
        float startX = (-halfW + margin) / textScale;
        float startY = -(halfH - margin) / textScale;
        int maxW = (int) ((screenW - margin * 2.0F) / textScale);
        int x = (int) startX;
        int y = (int) startY;

        if (viewing) {
            renderBodycamHud(t, buffers, session, fade, startX, startY, maxW, halfW, halfH, textScale);
        } else {
            y = wrapped(t, buffers, "MW-OS 5.15.0-mw (tty1)", x, y, 0xFF55FFBB, fade, maxW) + 2;
            String cursor = session.isCursorVisible() ? "_" : " ";
            switch (stage) {
                case LOGIN_USER -> wrapped(t, buffers, "mw-terminal login: " + session.getInputBuffer() + cursor, x, y, 0xFF44FFAA, fade, maxW);
                case LOGIN_PASS -> {
                    y = wrapped(t, buffers, "mw-terminal login: " + session.getEnteredUser(), x, y, 0xFF44FFAA, fade, maxW);
                    wrapped(t, buffers, "Password: " + "*".repeat(session.getInputBuffer().length()) + cursor, x, y, 0xFF44FFAA, fade, maxW);
                }
                case LOGIN_ERROR -> {
                    y = wrapped(t, buffers, "mw-terminal login: " + session.getEnteredUser(), x, y, 0xFF44FFAA, fade, maxW);
                    y = wrapped(t, buffers, "Password: ****", x, y, 0xFF44FFAA, fade, maxW) + 2;
                    wrapped(t, buffers, "Login incorrect", x, y, 0xFFFF5555, fade, maxW);
                }
                case BOOT_SEQUENCE -> {
                    y = wrapped(t, buffers, "Last login: root on tty1", x, y, 0xFF33CC88, fade, maxW) + 2;
                    for (int i = 0; i < session.getBootStep() && i < TerminalSession.BOOT_LINES.length; i++) {
                        TerminalSession.ConsoleLine bl = TerminalSession.BOOT_LINES[i];
                        if (i == 2 && session.hasModule()) {
                            bl = new TerminalSession.ConsoleLine("OK", 0xFF00FF66, "Network interface eth0: link UP", 0xFF44FFAA);
                        }
                        y = line(t, buffers, bl, x, y, fade, maxW);
                    }
                }
                case SHELL -> {
                    String net = session.isEth0Up() ? "eth0: UP" : "eth0: DOWN";
                    int cx = x;
                    cx += draw(t, buffers, "status: READY | ", cx, y, 0xFF33CC88, fade);
                    cx += draw(t, buffers, net, cx, y, session.isEth0Up() ? 0xFF00FF66 : 0xFFFF4444, fade);
                    draw(t, buffers, " | user: root", cx, y, 0xFF33CC88, fade);
                    y += LINE + 2;
                    for (TerminalSession.ConsoleLine cl : session.getShellOutput()) {
                        y = line(t, buffers, cl, x, y, fade, maxW);
                    }
                    wrapped(t, buffers, "root@mw-terminal:~# " + session.getInputBuffer() + cursor, x, y, 0xFF44FFAA, fade, maxW);
                }
                case CHAT_MENU -> {
                    if (session.getSubMenu() == TerminalSession.ChatSubMenu.GROUPS) {
                        y = wrapped(t, buffers, "=== Groups ===", x, y, 0xFF55FFBB, fade, maxW) + 2;
                        for (int i = 0; i < TerminalSession.GROUPS_MENU_ITEMS.length; i++) {
                            boolean sel = i == session.getGroupsMenuIndex();
                            y = wrapped(t, buffers, (sel ? "> " : "  ") + TerminalSession.GROUPS_MENU_ITEMS[i], x, y, sel ? 0xFF00FF66 : 0xFF44FFAA, fade, maxW);
                        }
                    } else if (session.getSubMenu() == TerminalSession.ChatSubMenu.PRIVATE) {
                        y = wrapped(t, buffers, "=== Private Chats ===", x, y, 0xFF55FFBB, fade, maxW) + 2;
                        List<String> users = session.getModuleUsers();
                        for (int i = 0; i < users.size(); i++) {
                            boolean sel = i == session.getPrivateMenuIndex();
                            y = wrapped(t, buffers, (sel ? "> " : "  ") + users.get(i), x, y, sel ? 0xFF00FF66 : 0xFF44FFAA, fade, maxW);
                        }
                    } else {
                        y = wrapped(t, buffers, "=== Terminal Network Chat ===", x, y, 0xFF55FFBB, fade, maxW) + 2;
                        for (int i = 0; i < TerminalSession.CHAT_MENU_ITEMS.length; i++) {
                            boolean sel = i == session.getChatMenuIndex();
                            y = wrapped(t, buffers, (sel ? "> " : "  ") + TerminalSession.CHAT_MENU_ITEMS[i], x, y, sel ? 0xFF00FF66 : 0xFF44FFAA, fade, maxW);
                        }
                    }
                    y += 2;
                    String nav = session.getSubMenu() != TerminalSession.ChatSubMenu.MAIN
                            ? "[Up/Down] Select  [Enter] Open  [Esc] Back"
                            : "[Up/Down] Select  [Enter] Open  [Esc] Close";
                    wrapped(t, buffers, nav, x, y, 0xFF888888, fade, maxW);
                }
                case APP_CHAT -> {
                    y = wrapped(t, buffers, "=== " + session.getCurrentChatRoom() + " ===", x, y, 0xFF55FFBB, fade, maxW);
                    y = wrapped(t, buffers, "[Ctrl+C] Exit  |  [Esc] Back  |  eth0: UP", x, y, 0xFF448866, fade, maxW) + 1;
                    for (TerminalSession.ConsoleLine cl : session.getChatMessages()) {
                        y = line(t, buffers, cl, x, y, fade, maxW);
                    }
                    wrapped(t, buffers, "> " + session.getInputBuffer() + cursor, x, y, 0xFF00FF66, fade, maxW);
                }
                case BODYCAM_LIST -> {
                    y = wrapped(t, buffers, "=== BODYCAM SURVEILLANCE v1.2 ===", x, y, 0xFF55FFBB, fade, maxW) + 2;
                    List<BodycamEntry> cams = session.getAvailableCameras();
                    if (cams.isEmpty()) {
                        y = wrapped(t, buffers, "No active cameras detected.", x, y, 0xFF888888, fade, maxW);
                        y = wrapped(t, buffers, "Wear a bodycam and power it ON.", x, y, 0xFF666666, fade, maxW);
                    } else {
                        for (int i = 0; i < cams.size(); i++) {
                            BodycamEntry c = cams.get(i);
                            boolean sel = i == session.getBodycamIndex();
                            String status = c.isOnline ? "[ONLINE]" : "[OFFLINE]";
                            draw(t, buffers, (sel ? "> " : "  ") + (c.isChecked ? "[X] " : "[ ] ") + c.camId + " " + c.carrierName, x, y,
                                    sel ? 0xFF55FFDD : 0xFF33CC88, fade);
                            draw(t, buffers, status, x + maxW - font.width(status), y, c.isOnline ? 0xFF00FF66 : 0xFFFF4444, fade);
                            y += LINE;
                        }
                    }
                    y += 2;
                    wrapped(t, buffers, "[Up/Down] Select  [Tab] Check  [Enter] View  [Esc] Exit", x, y, 0xFF888888, fade, maxW);
                }
                default -> {
                }
            }
        }
        pose.popPose();
        pose.popPose();
    }

    private void renderBodycamHud(Matrix4f t, MultiBufferSource buffers, TerminalSession session, float fade,
                                  float startX, float startY, int maxW, float halfW, float halfH, float textScale) {
        BodycamEntry cam = session.getCurrentViewingCamera();
        int totalH = (int) ((halfH * 2.0F - 0.048F) / textScale);
        if (cam == null) {
            String msg = "NO ACTIVE CAMERAS FOUND";
            draw(t, buffers, msg, (int) (startX + (maxW - font.width(msg)) / 2f), (int) (startY + totalH / 2f - 5), 0xFFFF5555, fade);
            String nav = "[Esc] Return to Menu";
            draw(t, buffers, nav, (int) (startX + (maxW - font.width(nav)) / 2f), (int) (startY + totalH - 12), 0xFF888888, fade);
            return;
        }
        // live battery of a carrier this client can see
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && cam.carrierEntityId != -1) {
            Entity carrier = mc.level.getEntity(cam.carrierEntityId);
            if (carrier instanceof Player player) {
                ItemStack stack = BodycamItem.getEquipped(player);
                if (stack.getItem() instanceof BodycamItem) {
                    cam.batteryPercent = BodycamItem.terminalPercent(stack);
                    cam.isOnline = BodycamItem.isPowerEnabled(stack) && BodycamItem.getCharge(stack) > 0;
                }
            }
        }
        int sx = (int) startX;
        int sy = (int) startY;
        draw(t, buffers, DATE_FORMAT.format(new Date()) + "  " + cam.camId, sx, sy, 0xFF55FFBB, fade);
        boolean blink = (System.currentTimeMillis() / 600) % 2 == 0;
        String bat = cam.batteryPercent + "%";
        int right = sx + maxW;
        int recX = right - font.width("REC");
        int dotX = recX - font.width("● ");
        int batX = dotX - font.width(bat) - 8;
        draw(t, buffers, "REC", recX, sy, 0xFFFF3333, fade);
        draw(t, buffers, "●", dotX, sy, blink ? 0xFFFF2222 : 0xFF551111, fade);
        draw(t, buffers, bat, batX, sy, cam.batteryPercent > 20 ? 0xFF00FF66 : 0xFFFF4444, fade);

        if (cam.isOnline) {
            renderTacticalBoxes(t, buffers, fade, halfW, halfH, textScale);
            if (session.getConnectTimer() <= 1.4f && ((int) (session.getConnectTimer() * 6)) % 2 == 0) {
                String msg = "[ CAM CONNECTED ]";
                draw(t, buffers, msg, (int) (startX + (maxW - font.width(msg)) / 2f), (int) (startY + totalH / 2f - 5), 0xFF00FF66, fade);
            }
        } else {
            String msg = "[ CAM DISCONNECTED - NO SIGNAL ]";
            draw(t, buffers, msg, (int) (startX + (maxW - font.width(msg)) / 2f), (int) (startY + totalH / 2f - 8), blink ? 0xFFFF3333 : 0xFFAA2222, fade);
            String sub = "Carrier device powered off or out of range";
            draw(t, buffers, sub, (int) (startX + (maxW - font.width(sub)) / 2f), (int) (startY + totalH / 2f + 4), 0xFF888888, fade);
        }

        int bottomY = (int) (startY + totalH - 9);
        String idx = String.format("CAM %d/%d", session.getBodycamIndex() + 1, session.getAvailableCameras().size());
        if (cam.carrierName != null && !cam.carrierName.isEmpty()) {
            idx += " [" + cam.carrierName + "]";
        }
        draw(t, buffers, idx, sx, bottomY, 0xFF33CC88, fade);
        boolean interacting = TerminalCamera.isActive();
        String nav = interacting ? "[◄/►] Cam  [Esc] List  [E] Exit" : "● LIVE FEED";
        draw(t, buffers, nav, sx + maxW - font.width(nav), bottomY, interacting ? 0xFF88AA99 : 0xFF00FF66, fade);
    }

    private static void renderTacticalBoxes(Matrix4f t, MultiBufferSource buffers, float fade, float halfW, float halfH, float textScale) {
        List<BodycamFeed.TacticalRect> rects = BodycamFeed.getDetectedRects();
        if (rects.isEmpty()) {
            return;
        }
        VertexConsumer lines = buffers.getBuffer(TechRenderTypes.LINES);
        float fx = -halfW / textScale;
        float fy = -halfH / textScale;
        float fw = halfW * 2.0F / textScale;
        float fh = halfH * 2.0F / textScale;
        for (BodycamFeed.TacticalRect r : rects) {
            float l = fx + r.left() * fw;
            float rr = fx + r.right() * fw;
            float top = fy + r.top() * fh;
            float bot = fy + r.bottom() * fh;
            segment(lines, t, l, top, rr, top, fade);
            segment(lines, t, rr, top, rr, bot, fade);
            segment(lines, t, rr, bot, l, bot, fade);
            segment(lines, t, l, bot, l, top, fade);
        }
    }

    private static void segment(VertexConsumer buf, Matrix4f m, float x0, float y0, float x1, float y1, float a) {
        buf.vertex(m, x0, y0, 0.0F).color(0.0F, 1.0F, 0.3F, a).endVertex();
        buf.vertex(m, x1, y1, 0.0F).color(0.0F, 1.0F, 0.3F, a).endVertex();
    }

    static void quad(VertexConsumer buf, Matrix4f m, float x0, float y0, float x1, float y1, float z, float r, float g, float b, float a) {
        buf.vertex(m, x0, y0, z).color(r, g, b, a).endVertex();
        buf.vertex(m, x1, y0, z).color(r, g, b, a).endVertex();
        buf.vertex(m, x1, y1, z).color(r, g, b, a).endVertex();
        buf.vertex(m, x0, y1, z).color(r, g, b, a).endVertex();
    }

    /** Draws a string, returns its width. */
    private int draw(Matrix4f m, MultiBufferSource buffers, String text, int x, int y, int color, float fade) {
        font.drawInBatch(text, x, y, fade(color, fade), false, m, buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        return font.width(text);
    }

    private int wrapped(Matrix4f m, MultiBufferSource buffers, String text, int x, int y, int color, float fade, int maxW) {
        if (font.width(text) <= maxW) {
            draw(m, buffers, text, x, y, color, fade);
            return y + LINE;
        }
        for (FormattedText part : font.getSplitter().splitLines(text, maxW, Style.EMPTY)) {
            draw(m, buffers, part.getString(), x, y, color, fade);
            y += LINE;
        }
        return y;
    }

    /** "[TAG] message" line with the wrapped rest indented (drawStatusLine). */
    private int line(Matrix4f m, MultiBufferSource buffers, TerminalSession.ConsoleLine cl, int x, int y, float fade, int maxW) {
        if (cl.tag() == null) {
            return wrapped(m, buffers, cl.text(), x, y, cl.textColor(), fade, maxW);
        }
        int cx = x;
        cx += draw(m, buffers, "[", cx, y, 0xFF88AA99, fade);
        cx += draw(m, buffers, cl.tag(), cx, y, cl.tagColor(), fade);
        cx += draw(m, buffers, "] ", cx, y, 0xFF88AA99, fade);
        int avail = maxW - font.width("[" + cl.tag() + "] ");
        if (font.width(cl.text()) <= avail) {
            draw(m, buffers, cl.text(), cx, y, cl.textColor(), fade);
            return y + LINE;
        }
        List<FormattedText> parts = font.getSplitter().splitLines(cl.text(), avail, Style.EMPTY);
        for (int i = 0; i < parts.size(); i++) {
            draw(m, buffers, parts.get(i).getString(), i == 0 ? cx : x + 8, y, cl.textColor(), fade);
            y += LINE;
        }
        return y;
    }

    @Override
    public boolean shouldRenderOffScreen(TerminalBlockEntity te) {
        return true;
    }
}
