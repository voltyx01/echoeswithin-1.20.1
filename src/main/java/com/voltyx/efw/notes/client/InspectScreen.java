package com.voltyx.efw.notes.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.voltyx.efw.client.loading.LoadingEntry;
import com.voltyx.efw.notes.DiaryItem;
import com.voltyx.efw.notes.NoteItem;
import com.voltyx.efw.notes.NotesConfig;
import com.voltyx.efw.notes.NotesContent;
import com.voltyx.efw.sins.SinContent;
import com.voltyx.efw.sins.client.DustParticles;
import com.voltyx.efw.sins.client.ScreenTransition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TieredItem;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen 3D inspection of an item with its name and description (port of GuiItemInspect): LMB drag rotates, RMB drag
 * pans and springs back, the wheel zooms or scrolls the text. Notes show their text, the diary pages through its notes.
 */
public class InspectScreen extends Screen {
    enum Group {
        NOTE(2.0f), DIARY(2.0f), DPOR(2.0f), HELMET(2.0f), ARMOR(1.8f), TACZ(4.5f), TOOLS_2D(1.8f), ITEMS_2D(1.8f), BLOCKS(1.8f);

        final float scale;

        Group(float scale) {
            this.scale = scale;
        }
    }

    private static final int TITLE_COLOR = 0xC8B86A;
    private static final int DIVIDER_COLOR = 0xFF3E3E3E;
    private static final int DESC_COLOR = 0xA0A098;
    private static final int LORE_COLOR = 0x666660;

    protected final ItemStack targetStack;
    protected final Screen parentScreen;
    protected final Group group;
    private ItemStack activeRenderStack;

    private float currentYaw, targetYaw;
    private float currentPitch, targetPitch;
    private float currentZoom = 95.0f, targetZoom = 95.0f;
    private float currentPanX, targetPanX, currentPanY, targetPanY;
    protected boolean isDragging;
    protected boolean isPanning;
    private long lastFrameTime;
    private final DustParticles dust = new DustParticles();

    private double scrollAmount;
    private double targetScrollAmount;

    private ListTag storedNotes;
    private int currentDiaryPage;

    public InspectScreen(ItemStack targetStack, Screen parentScreen) {
        super(targetStack.getHoverName());
        this.targetStack = targetStack;
        this.activeRenderStack = targetStack;
        this.parentScreen = parentScreen;
        this.group = resolveGroup(targetStack);
        if (group == Group.DIARY) {
            storedNotes = DiaryItem.getStoredNotes(targetStack);
            updateDiaryActiveNote();
        }
        resetView();
    }

    /** The transform the item is shown with now (read every frame: the maker edits it live). */
    protected InspectLayout.Transform transform() {
        return InspectLayout.resolve(targetStack, group);
    }

    /** Idle turn, degrees per second. */
    protected float idleSpin(InspectLayout.Transform t) {
        return t.spin;
    }

    /** Back to the start angles and zoom of the transform, no pan. */
    protected void resetView() {
        InspectLayout.Transform t = transform();
        currentYaw = targetYaw = t.yaw;
        currentPitch = targetPitch = t.pitch;
        currentZoom = targetZoom = 95.0f;
        currentPanX = targetPanX = currentPanY = targetPanY = 0.0f;
    }

    static Group resolveGroup(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem) {
            return Group.BLOCKS; // blocks are never armour
        }
        if (net.minecraftforge.fml.ModList.get().isLoaded("tacz") && TaczInspect.isGun(stack.getItem())) {
            return Group.TACZ;
        }
        if (stack.getItem() == SinContent.DPOR.get()) {
            return Group.DPOR;
        }
        if (stack.getItem() instanceof NoteItem) {
            return Group.NOTE;
        }
        if (stack.getItem() instanceof DiaryItem) {
            return Group.DIARY;
        }
        if (stack.getItem() instanceof ArmorItem armor) {
            return armor.getType() == ArmorItem.Type.HELMET ? Group.HELMET : Group.ARMOR;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String reg = id == null ? "" : id.getPath();
        if (stack.getItem() instanceof TieredItem || stack.getItem() instanceof BowItem || stack.getItem() instanceof ShieldItem
                || reg.contains("sword") || reg.contains("pickaxe") || reg.contains("axe") || reg.contains("shovel")
                || reg.contains("hoe") || reg.contains("bow") || reg.contains("gun") || reg.contains("dagger")
                || reg.contains("knife") || reg.contains("shield")) {
            return Group.TOOLS_2D;
        }
        return Group.ITEMS_2D;
    }

    private void updateDiaryActiveNote() {
        if (storedNotes != null && currentDiaryPage < storedNotes.size()) {
            CompoundTag noteTag = storedNotes.getCompound(currentDiaryPage);
            ItemStack fakeNote = new ItemStack(NotesContent.NOTE.get());
            NoteItem.setNoteData(fakeNote, noteTag.getInt("noteId"), noteTag.contains("variant") ? noteTag.getInt("variant") : 1,
                    noteTag.getBoolean("isQuest"));
            activeRenderStack = fakeNote;
        } else {
            activeRenderStack = targetStack;
        }
        scrollAmount = 0.0;
        targetScrollAmount = 0.0;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        lastFrameTime = System.currentTimeMillis();
        dust.init(width, height);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseY >= height + InspectLayout.text().blockY - 20) {
            targetScrollAmount -= delta > 0 ? 20.0 : -20.0; // text
        } else if (delta > 0) {
            targetZoom = Math.min(260.0f, targetZoom + 14.0f);
        } else {
            targetZoom = Math.max(35.0f, targetZoom - 14.0f);
        }
        return true;
    }

    private boolean hasPages() {
        return group == Group.DIARY && storedNotes != null && storedNotes.size() > 1;
    }

    private void turnPage(int dir) {
        currentDiaryPage += dir;
        updateDiaryActiveNote();
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(SinContent.NOTES.get(), 1.0f, 1.0f);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (hasPages() && button == 0) {
            int midY = height / 2;
            if (currentDiaryPage > 0 && Math.abs(mouseX - 30) <= 20 && Math.abs(mouseY - midY) <= 30) {
                turnPage(-1);
                return true;
            }
            if (currentDiaryPage < storedNotes.size() - 1 && Math.abs(mouseX - (width - 30)) <= 20 && Math.abs(mouseY - midY) <= 30) {
                turnPage(1);
                return true;
            }
        }
        if (button == 0) {
            isDragging = true;
        } else if (button == 1) {
            isPanning = true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            isDragging = false;
        } else if (button == 1) {
            isPanning = false;
            targetPanX = 0.0f;
            targetPanY = 0.0f;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (isDragging && button == 0) {
            targetYaw += (float) dx * 0.8f;
            targetPitch = Math.max(-85.0f, Math.min(85.0f, targetPitch + (float) dy * 0.8f));
        } else if (isPanning && button == 1) {
            targetPanX += (float) dx;
            targetPanY += (float) dy;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_M && canEditLayout()) {
            if (minecraft != null) {
                minecraft.setScreen(new InspectMakerScreen(targetStack, parentScreen));
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_I
                || (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode))) {
            ScreenTransition.to(parentScreen);
            return true;
        }
        if (hasPages()) {
            if (keyCode == GLFW.GLFW_KEY_LEFT && currentDiaryPage > 0) {
                turnPage(-1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_RIGHT && currentDiaryPage < storedNotes.size() - 1) {
                turnPage(1);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** The maker is for building the pack: creative or an operator. */
    static boolean canEditLayout() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && (mc.player.isCreative() || mc.player.hasPermissions(2));
    }

    // the plain screen handling, for the widgets of the maker
    protected boolean widgetClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }

    protected boolean widgetReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(mouseX, mouseY, button);
    }

    protected boolean widgetDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    protected boolean widgetScrolled(double mouseX, double mouseY, double delta) {
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    protected boolean widgetKey(int keyCode, int scanCode, int modifiers) {
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    protected void widgetRender(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        super.render(g, mouseX, mouseY, partialTicks);
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        // painted in render
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        long now = System.currentTimeMillis();
        float deltaSec = lastFrameTime == 0 ? 0.016f : Math.min((now - lastFrameTime) / 1000.0f, 0.1f);
        lastFrameTime = now;
        InspectLayout.Transform t = transform();
        if (!isDragging && !isPanning) {
            targetYaw += idleSpin(t) * deltaSec; // idle turn
        }
        float lerp = Math.min(1.0f, deltaSec * 10.0f);
        currentYaw += (targetYaw - currentYaw) * lerp;
        currentPitch += (targetPitch - currentPitch) * lerp;
        currentZoom += (targetZoom - currentZoom) * lerp;
        currentPanX += (targetPanX - currentPanX) * lerp;
        currentPanY += (targetPanY - currentPanY) * lerp;
        scrollAmount += (targetScrollAmount - scrollAmount) * Math.min(1.0f, deltaSec * 12.0f);

        // dark midnight background and ash dust
        g.fill(0, 0, width, height, 0xFF020306);
        dust.updateAndRender(g, width, height);

        // the item behind the text
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        render3D(g, t, width / 2.0f + t.posX + currentPanX, height / 2.0f + InspectLayout.text().itemY + t.posY + currentPanY);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);

        // black gradient over the lower three quarters
        g.fillGradient(0, height / 4, width, height, 0x00000000, 0xF2000000);
        if (hasPages()) {
            drawDiarySideArrows(g, mouseX, mouseY);
        }
        drawItemInfo(g);
        if (!(this instanceof InspectMakerScreen) && canEditLayout()) {
            g.drawString(font, "M — мейкер осмотра", 6, 6, 0x555550, false);
        }
    }

    private void render3D(GuiGraphics g, InspectLayout.Transform t, float x, float y) {
        Minecraft mc = Minecraft.getInstance();
        // the model keeps the same physical size whatever the GUI scale (reference: scale 2)
        float guiScaleRatio = 2.0f / (float) Math.max(1.0, mc.getWindow().getGuiScale());
        float finalScale = currentZoom * t.scale * guiScaleRatio;
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 150.0f);
        pose.scale(finalScale, -finalScale, finalScale);
        pose.mulPose(Axis.XP.rotationDegrees(currentPitch));
        pose.mulPose(Axis.YP.rotationDegrees(currentYaw));
        pose.mulPose(Axis.ZP.rotationDegrees(t.roll));
        // items are upside down after the Y flip
        pose.mulPose(Axis.ZP.rotationDegrees(180.0f));
        float depth = group == Group.DIARY && activeRenderStack != targetStack ? 0.2f : t.depth; // a diary page is a thin sheet
        pose.scale(1.0f, 1.0f, depth);
        // a turned-around gun (the default TACZ pack) also sits 0.1 off along Z against the CIBR ones the layout is made for
        float packYaw = packYaw();
        pose.translate(-t.pivotX, -t.pivotY, -t.pivotZ - (packYaw != 0.0f ? 0.1f : 0.0f));
        // inside the pivot, so a turned-around gun turns about its own middle and the pivot stays at the same end for every pack
        if (packYaw != 0.0f) {
            pose.mulPose(Axis.YP.rotationDegrees(packYaw));
        }
        if (t.fit) {
            // fitted: the largest side of the model is one unit, its middle is the centre, its longest side lies across
            InspectBounds.Box box = bounds();
            if (box != null) {
                if (t.alignLongest && box.sizeZ() > box.sizeX()) {
                    pose.mulPose(Axis.YP.rotationDegrees(90.0f));
                }
                float f = 1.0f / box.maxSize();
                pose.scale(f, f, f);
                pose.translate(-box.centerX(), -box.centerY(), -box.centerZ());
            }
        }
        RenderSystem.enableDepthTest();
        Lighting.setupFor3DItems();
        renderItem(mc, pose, g.bufferSource());
        g.flush();
        Lighting.setupForFlatItems();
        pose.popPose();
    }

    /** Turn on the spin axis that evens out how gun packs face their guns. */
    private float packYaw() {
        return group == Group.TACZ ? TaczInspect.yawOffset(activeRenderStack) : 0.0f;
    }

    /** The item as the inspect screen draws it; TACZ models at full detail, not their far-away LOD; armour as the piece itself. */
    private void renderItem(Minecraft mc, PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers) {
        if (InspectArmor.isArmor(activeRenderStack)) {
            InspectArmor.render(activeRenderStack, pose, buffers);
            return;
        }
        if (net.minecraftforge.fml.ModList.get().isLoaded("tacz")) {
            TaczInspect.fullDetail();
        }
        mc.getItemRenderer().renderStatic(activeRenderStack, ItemDisplayContext.FIXED, 0xF000F0, OverlayTexture.NO_OVERLAY, pose,
                buffers, mc.level, 0);
    }

    private ItemStack measuredStack;
    private InspectBounds.Box measured;

    /** The bounds of the model as drawn (measured once per shown stack), null when nothing was drawn. */
    private InspectBounds.Box bounds() {
        if (measuredStack != activeRenderStack) {
            measuredStack = activeRenderStack;
            InspectBounds recorder = new InspectBounds();
            renderItem(Minecraft.getInstance(), new PoseStack(), recorder);
            measured = recorder.box();
        }
        return measured;
    }

    private void drawDiarySideArrows(GuiGraphics g, int mouseX, int mouseY) {
        int midY = height / 2;
        if (currentDiaryPage > 0) {
            boolean hover = Math.abs(mouseX - 30) <= 20 && Math.abs(mouseY - midY) <= 30;
            drawFlatMountainArrow(g, 30, midY, 14, 28, true, hover ? 0xFFFFFFFF : 0x88FFFFFF, hover);
        }
        if (currentDiaryPage < storedNotes.size() - 1) {
            boolean hover = Math.abs(mouseX - (width - 30)) <= 20 && Math.abs(mouseY - midY) <= 30;
            drawFlatMountainArrow(g, width - 30, midY, 14, 28, false, hover ? 0xFFFFFFFF : 0x88FFFFFF, hover);
        }
    }

    /** A thin pixel "<" or ">" taller than it is wide. */
    private static void drawFlatMountainArrow(GuiGraphics g, int centerX, int centerY, int w, int h, boolean pointLeft, int color, boolean hovered) {
        int halfH = h / 2;
        int stroke = hovered ? 2 : 1;
        for (int dy = -halfH; dy <= halfH; dy++) {
            int xOffset = (int) ((float) Math.abs(dy) / halfH * w);
            int px = pointLeft ? centerX + xOffset : centerX - xOffset;
            g.fill(px, centerY + dy, px + stroke, centerY + dy + 1, color);
        }
    }

    private void drawItemInfo(GuiGraphics g) {
        String displayName = targetStack.getHoverName().getString();
        if (group == Group.DIARY && storedNotes != null && !storedNotes.isEmpty()) {
            displayName += "  §7(" + (currentDiaryPage + 1) + " / " + storedNotes.size() + ")";
        }
        InspectLayout.Text ts = InspectLayout.text();
        int centerX = width / 2;
        int infoY = height + ts.blockY;

        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(centerX + ts.titleX - font.width(displayName) * ts.titleScale / 2.0f, infoY + ts.titleY, 0);
        pose.scale(ts.titleScale, ts.titleScale, 1.0f);
        g.drawString(font, displayName, 0, 0, TITLE_COLOR, true);
        pose.popPose();

        String desc = "";
        String lore = "";
        if (group == Group.NOTE) {
            desc = NotesConfig.getText(NoteItem.getNoteId(targetStack), NoteItem.isQuest(targetStack));
        } else if (group == Group.DPOR) {
            desc = I18n.get("tooltip.efw.dpor.inspect"); // fixed text, not random
        } else if (group == Group.DIARY) {
            if (storedNotes != null && currentDiaryPage < storedNotes.size()) {
                CompoundTag noteTag = storedNotes.getCompound(currentDiaryPage);
                desc = NotesConfig.getText(noteTag.getInt("noteId"), noteTag.getBoolean("isQuest"));
            } else {
                desc = I18n.get("inspect.efw.diary_empty");
            }
        } else {
            LoadingEntry entry = InspectDescConfig.entryFor(targetStack);
            if (entry != null) {
                desc = entry.description != null ? entry.description : "";
                lore = entry.lore != null ? entry.lore : "";
            }
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(targetStack.getItem());
            if (id != null) {
                String path = id.getPath();
                if (desc.isEmpty()) {
                    if (I18n.exists("inspect." + path + ".desc")) {
                        desc = I18n.get("inspect." + path + ".desc");
                    } else if (I18n.exists("inspect." + path)) {
                        desc = I18n.get("inspect." + path);
                    }
                }
                if (lore.isEmpty() && I18n.exists("inspect." + path + ".lore")) {
                    lore = I18n.get("inspect." + path + ".lore");
                }
            }
        }
        if (desc.isEmpty() && lore.isEmpty()) {
            return;
        }

        int maxW = Math.min(ts.maxWidth, width - 60);
        int currentY = (int) (infoY + font.lineHeight * ts.titleScale + ts.dividerGap);
        int divHalfW = Math.min(ts.dividerWidth, maxW) / 2;
        g.fill(centerX - divHalfW, currentY, centerX + divHalfW, currentY + 1, DIVIDER_COLOR);
        currentY += ts.descGap + ts.descY;
        centerX += ts.descX;

        float descScale = ts.descScale;
        int scaledMaxW = (int) (maxW / descScale);
        int lineHeight = Math.max(1, (int) ((font.lineHeight + 3) * descScale));
        List<FormattedCharSequence> lines = new ArrayList<>();
        List<Boolean> loreLine = new ArrayList<>();
        if (!desc.isEmpty()) {
            for (FormattedCharSequence l : font.split(Component.literal(desc), scaledMaxW)) {
                lines.add(l);
                loreLine.add(false);
            }
        }
        if (!lore.isEmpty()) {
            for (FormattedCharSequence l : font.split(Component.literal("§o" + lore), scaledMaxW)) {
                lines.add(l);
                loreLine.add(true);
            }
        }

        int maxLines = Math.max(1, group == Group.NOTE || group == Group.DIARY || group == Group.DPOR ? ts.paperLines : ts.maxLines);
        int totalTextHeight = lines.size() * lineHeight;
        int maxVisibleHeight = maxLines * lineHeight;
        int maxScroll = Math.max(0, totalTextHeight - maxVisibleHeight);
        targetScrollAmount = Math.max(0, Math.min(maxScroll, targetScrollAmount));
        scrollAmount = Math.max(0, Math.min(maxScroll, scrollAmount));

        g.enableScissor(centerX - maxW / 2 - 4, currentY, centerX + maxW / 2 + 4, currentY + maxVisibleHeight);
        int drawY = currentY - (int) scrollAmount;
        for (int i = 0; i < lines.size(); i++) {
            if (drawY + lineHeight >= currentY && drawY <= currentY + maxVisibleHeight) {
                FormattedCharSequence line = lines.get(i);
                pose.pushPose();
                pose.translate(centerX - font.width(line) * descScale / 2.0f, drawY, 0);
                pose.scale(descScale, descScale, 1.0f);
                g.drawString(font, line, 0, 0, loreLine.get(i) ? LORE_COLOR : DESC_COLOR, true);
                pose.popPose();
            }
            drawY += lineHeight;
        }
        g.disableScissor();

        if (lines.size() > maxLines) {
            int barX = centerX + divHalfW + 6;
            g.fill(barX, currentY, barX + 2, currentY + maxVisibleHeight, 0x44FFFFFF);
            int knobH = Math.max(8, maxVisibleHeight * maxVisibleHeight / totalTextHeight);
            int knobPos = (int) (scrollAmount * (maxVisibleHeight - knobH) / (double) (totalTextHeight - maxVisibleHeight));
            g.fill(barX, currentY + knobPos, barX + 2, currentY + knobPos + knobH, 0xFFFFFFFF);
        }
    }
}
