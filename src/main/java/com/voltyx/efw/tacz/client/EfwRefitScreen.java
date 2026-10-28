package com.voltyx.efw.tacz.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.api.modifier.IAttachmentModifier;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.gui.components.refit.HSVSliderGroup;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ClientMessageLaserColor;
import com.tacz.guns.network.message.ClientMessageRefitGun;
import com.tacz.guns.network.message.ClientMessageUnloadAttachment;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.sound.SoundManager;
import com.voltyx.efw.network.EfwNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import com.mojang.blaze3d.platform.Lighting;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The TACZ modification screen with the EFW layout: collapsible statistics in four tabs, a column of attachment categories on the
 * right and a list of the matching attachments from the inventory next to it. It extends the TACZ screen only so that the rest of
 * TACZ (camera, first person rendering) keeps treating it as its own. The gun is addressed by its real inventory slot, never by
 * the hand, and the gun is drawn by this screen, so the hand of the player is not involved at all.
 */
public class EfwRefitScreen extends GunRefitScreen {
    // ---- colours of the layout
    private static final int PANEL = 0xEE0E0E10;
    private static final int PANEL_EDGE = 0x14FFFFFF;
    private static final int ACCENT = 0xFFD8A24A;
    private static final int MUTED = 0xFF9A9DA3;
    private static final int GREEN = 0xFF6FD36F;
    private static final int RED = 0xFFE5605A;
    private static final int HOVER = 0xFFFFFFA0;
    private static final int SLOT_SELECTED = 0xF5483A22;
    private static final int LINE = 0x14FFFFFF;

    // ---- geometry (GUI pixels)
    private static final int MARGIN = 12;
    private static final int STATS_W = 190;
    private static final int STATS_H = 222;
    private static final int SLOT = 22;
    private static final int SLOT_STEP = 25;
    private static final int FLY_W = 192;
    private static final int ROW_H = 26;

    /** TACZ order of the category column and the icon of each type in refit_slot_icons.png (16 px frames of the 224 x 32 sheet). */
    private static final AttachmentType[] COLUMN = {AttachmentType.EXTENDED_MAG, AttachmentType.LASER, AttachmentType.STOCK, AttachmentType.GRIP,
            AttachmentType.MUZZLE, AttachmentType.SCOPE};
    private static final String[] TAB_NAMES = {"Урон", "Точность", "Огонь", "Баланс"};

    /** Whether the statistics panel is open (kept between openings, as in TACZ). */
    private static boolean statsOpen;
    private static int tab;

    private final int gunSlot;
    private boolean renderFailed;
    private int listScroll;
    private int hoverRow = -1;
    private ItemStack previewCandidate = ItemStack.EMPTY;
    private List<Stat> stats = List.of();
    private List<Stat> preview;
    private int statsHash;
    private final List<AbstractWidget> sliders = new ArrayList<>();
    private AttachmentType slidersFor;

    private boolean startedAnimation;
    private ItemStack animatedGun = ItemStack.EMPTY;

    @Override
    public void removed() {
        Object renderer = animatedGun.isEmpty() ? null : IClientItemExtensions.of(animatedGun.getItem()).getCustomRenderer();
        if (startedAnimation && renderer != null && GunRenderer.supports(renderer)) {
            try {
                GunRenderer.exit(renderer, animatedGun);
            } catch (ReflectiveOperationException | RuntimeException e) {
                // nothing to stop
            }
        }
        startedAnimation = false;
        animatedGun = ItemStack.EMPTY;
        GunRenderer.resetOpening();
        GunRefitBackground.reset();
        super.removed();
    }

    /** TACZ's gun renderer by reflection (its base class comes from a bedrock model library that is not on the compile classpath). */
    private static final class GunRenderer {
        private static java.lang.reflect.Method needReInit;
        private static java.lang.reflect.Method tryInit;
        private static java.lang.reflect.Method renderFirstPerson;
        private static java.lang.reflect.Method tryExit;
        private static boolean resolved;
        private static Class<?> type;

        static boolean supports(Object renderer) {
            if (!resolved) {
                resolved = true;
                try {
                    type = Class.forName("com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer");
                    needReInit = type.getMethod("needReInit", ItemStack.class);
                    tryInit = type.getMethod("tryInit", ItemStack.class, net.minecraft.world.entity.player.Player.class, float.class);
                    tryExit = type.getMethod("tryExit", ItemStack.class, long.class);
                    renderFirstPerson = type.getMethod("renderFirstPerson", LocalPlayer.class, ItemStack.class, ItemDisplayContext.class, PoseStack.class,
                            MultiBufferSource.class, int.class, float.class);
                } catch (ReflectiveOperationException e) {
                    type = null;
                }
            }
            return type != null && type.isInstance(renderer);
        }

        /** Starts the animation of the gun when it is not running (a gun in the hand has it running already). True when started here. */
        static boolean init(Object renderer, ItemStack gun, LocalPlayer player, float partial) throws ReflectiveOperationException {
            if (Boolean.TRUE.equals(needReInit.invoke(renderer, gun))) {
                tryInit.invoke(renderer, gun, player, partial);
                return true;
            }
            return false;
        }

        static void exit(Object renderer, ItemStack gun) throws ReflectiveOperationException {
            tryExit.invoke(renderer, gun, 0L);
        }

        /**
         * TACZ fades the refit camera out over a quarter of a second after the screen closes and draws the gun of the hand during it,
         * which looks like the hands being drawn again. Both the progress and its smoothing are put to zero instead.
         */
        static void resetOpening() {
            try {
                java.lang.reflect.Field progress = com.tacz.guns.client.animation.screen.RefitTransform.class.getDeclaredField("refitScreenOpeningProgress");
                progress.setAccessible(true);
                progress.setFloat(null, 0.0f);
                java.lang.reflect.Field dyn = com.tacz.guns.client.event.FirstPersonRenderGunEvent.class.getDeclaredField("REFIT_OPENING_DYNAMICS");
                dyn.setAccessible(true);
                Object d = dyn.get(null);
                for (String name : new String[]{"py", "pyd", "px", "target"}) {
                    java.lang.reflect.Field f = d.getClass().getDeclaredField(name);
                    f.setAccessible(true);
                    f.setFloat(d, 0.0f);
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                // the fade-out just stays as TACZ has it
            }
        }


        static void render(Object renderer, LocalPlayer player, ItemStack gun, PoseStack pose, MultiBufferSource buffers, int light, float partial)
                throws ReflectiveOperationException {
            renderFirstPerson.invoke(renderer, player, gun, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, pose, buffers, light, partial);
        }
    }

    private record Stat(String titleKey, double defaultPercent, double modifierPercent, double modifier, String positive, String negative,
                        String defaultString, boolean positivelyBetter) {
    }

    private record Entry(ItemStack stack, int invIndex, boolean installed) {
    }

    public EfwRefitScreen(int gunSlot) {
        super();
        this.gunSlot = gunSlot;
    }

    // ------------------------------------------------------------------------------------------------ data

    private ItemStack gun() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p == null ? ItemStack.EMPTY : p.getInventory().getItem(gunSlot);
    }

    private AttachmentType selected() {
        AttachmentType t = RefitTransform.getCurrentTransformType();
        return t == null ? AttachmentType.NONE : t;
    }

    private List<Entry> entries(AttachmentType type) {
        List<Entry> out = new ArrayList<>();
        LocalPlayer p = Minecraft.getInstance().player;
        ItemStack gun = gun();
        IGun igun = IGun.getIGunOrNull(gun);
        if (p == null || igun == null || type == AttachmentType.NONE) {
            return out;
        }
        ItemStack installed = igun.getAttachment(gun, type);
        if (!installed.isEmpty()) {
            out.add(new Entry(installed, -1, true));
        }
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            IAttachment att = IAttachment.getIAttachmentOrNull(s);
            if (i != gunSlot && att != null && att.getType(s) == type && igun.allowAttachment(gun, s)) {
                out.add(new Entry(s, i, false));
            }
        }
        return out;
    }

    private List<Stat> compute(ItemStack gunStack, ItemStack candidate) {
        List<Stat> out = new ArrayList<>();
        IGun igun = IGun.getIGunOrNull(gunStack);
        if (igun == null) {
            return out;
        }
        GunData data = TimelessAPI.getCommonGunIndex(igun.getGunId(gunStack)).map(i -> i.getGunData()).orElse(null);
        if (data == null) {
            return out;
        }
        ItemStack use = gunStack;
        if (!candidate.isEmpty()) {
            use = gunStack.copy();
            igun.installAttachment(use, candidate.copy());
        }
        AttachmentCacheProperty cache = new AttachmentCacheProperty();
        cache.eval(use, data);
        for (IAttachmentModifier<?, ?> modifier : AttachmentPropertyManager.getModifiers().values()) {
            List<IAttachmentModifier.DiagramsData> list = modifier.getPropertyDiagramsData(use, data, cache);
            if (list == null) {
                continue;
            }
            for (IAttachmentModifier.DiagramsData d : list) {
                out.add(new Stat(d.titleKey(), d.defaultPercent(), d.modifierPercent(), d.modifier().doubleValue(), d.positivelyString(),
                        d.negativeString(), d.defaultString(), d.positivelyBetter()));
            }
        }
        return out;
    }

    private static int tabOf(String titleKey) {
        String k = titleKey.substring(titleKey.lastIndexOf('.') + 1);
        switch (k) {
            case "damage", "head_shot", "armor_ignore", "pierce", "knockback", "effective_range":
                return 0;
            case "aim_inaccuracy", "hipfire_inaccuracy", "sneak_inaccuracy", "lie_inaccuracy":
                return 1;
            case "rpm", "ammo_capacity", "ammo_speed", "pitch", "yaw":
                return 2;
            default:
                return 3;
        }
    }

    // ------------------------------------------------------------------------------------------------ screen hooks

    @Override
    public void init() {
        // TACZ builds its own widgets here; this layout draws everything itself and only keeps the laser sliders as widgets
        this.clearWidgets();
        sliders.clear();
        slidersFor = null;
        syncSliders();
    }

    @Override
    public void tick() {
        ItemStack gun = gun();
        int h = gun.isEmpty() ? 0 : gun.getTag() == null ? 1 : gun.getTag().hashCode();
        if (h != statsHash) {
            statsHash = h;
            stats = compute(gun, ItemStack.EMPTY);
            preview = null;
            previewCandidate = ItemStack.EMPTY;
        }
        syncSliders();
    }

    @Override
    public void onClose() {
        Minecraft mc = Minecraft.getInstance();
        ItemStack gun = gun();
        IGun igun = IGun.getIGunOrNull(gun);
        if (igun != null) {
            // TACZ sends the colour with the main hand gun and the selected slot: here neither is the gun
            NetworkHandler.CHANNEL.sendToServer(new ClientMessageLaserColor(gun, gunSlot));
        }
        // straight back to the screen the gun came from (through "no screen" the game would put the cursor in the middle)
        mc.setScreen(GunRefitInventory.takeOrigin());
    }

    private void syncSliders() {
        AttachmentType type = selected();
        if (type != slidersFor) {
            slidersFor = type;
            for (AbstractWidget w : sliders) {
                this.removeWidget(w);
            }
            sliders.clear();
            ItemStack gun = gun();
            IGun igun = IGun.getIGunOrNull(gun);
            LocalPlayer p = Minecraft.getInstance().player;
            if (type == AttachmentType.LASER && igun != null && p != null) {
                ItemStack laser = igun.getAttachment(gun, AttachmentType.LASER);
                IAttachment att = IAttachment.getIAttachmentOrNull(laser);
                if (att != null) {
                    TimelessAPI.getClientAttachmentIndex(att.getAttachmentId(laser)).map(i -> i.getLaserConfig()).ifPresent(cfg -> {
                        if (cfg != null && cfg.canEdit()) {
                            HSVSliderGroup group = new HSVSliderGroup(this.width - 140, this.height - 64, 120, 16, p.getInventory(), gunSlot, AttachmentType.LASER);
                            sliders.add(group.getHueSlider());
                            sliders.add(group.getSaturationSlider());
                            for (AbstractWidget w : sliders) {
                                this.addRenderableWidget(w);
                            }
                        }
                    });
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ drawing

    private int slotX() {
        return this.width - MARGIN - SLOT;
    }

    private int flyX() {
        return slotX() - 6 - FLY_W;
    }

    private int maxRows() {
        return Math.max(1, (this.height - MARGIN * 2 - 24) / ROW_H);
    }

    private void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL);
        g.fill(x, y, x + w, y + 1, ACCENT);
        g.fill(x, y + h - 1, x + w, y + h, PANEL_EDGE);
        g.fill(x, y + 1, x + 1, y + h - 1, PANEL_EDGE);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, PANEL_EDGE);
    }

    /** Draws text shrunk (never grown) so that it fits {@code maxW}. */
    private void fit(GuiGraphics g, Font font, String s, int x, int y, int maxW, int color) {
        int w = font.width(s);
        if (w <= maxW) {
            g.drawString(font, s, x, y, color, true);
            return;
        }
        float k = (float) maxW / w;
        g.pose().pushPose();
        g.pose().translate(x, y + (1 - k) * 4, 0);
        g.pose().scale(k, k, 1);
        g.drawString(font, s, 0, 0, color, true);
        g.pose().popPose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        Font font = Minecraft.getInstance().font;
        ItemStack gun = gun();
        IGun igun = IGun.getIGunOrNull(gun);
        if (igun == null) {
            onClose();
            return;
        }
        GunRefitBackground.draw(g, this.width, this.height);
        AttachmentType sel = selected();
        renderGun(gun, partial);

        // categories
        int sx = slotX();
        int hoveredSlot = -1;
        for (int i = 0; i < COLUMN.length; i++) {
            AttachmentType type = COLUMN[i];
            boolean ok = igun.allowAttachmentType(gun, type);
            int y = MARGIN + i * SLOT_STEP;
            boolean over = mx >= sx && mx < sx + SLOT && my >= y && my < y + SLOT;
            boolean on = sel == type;
            g.fill(sx, y, sx + SLOT, y + SLOT, on ? SLOT_SELECTED : PANEL);
            int edge = on ? ACCENT : over && ok ? 0xFF8E8E8E : PANEL_EDGE;
            g.fill(sx, y, sx + SLOT, y + 1, edge);
            g.fill(sx, y + SLOT - 1, sx + SLOT, y + SLOT, edge);
            g.fill(sx, y, sx + 1, y + SLOT, edge);
            g.fill(sx + SLOT - 1, y, sx + SLOT, y + SLOT, edge);
            // an installed attachment is shown by its own item, an empty slot by the category icon
            ItemStack installed = ok ? igun.getAttachment(gun, type) : ItemStack.EMPTY;
            if (!installed.isEmpty()) {
                g.renderItem(installed, sx + 3, y + 3);
            } else {
                RenderSystem.enableBlend();
                g.setColor(1, 1, 1, ok ? 1 : 0.35f);
                int icon = ok ? iconIndex(type) : 6;
                g.blit(GunRefitScreen.ICONS_TEXTURE, sx + 3, y + 3, 16, 16, icon * 32, 0, 32, 32, 224, 32);
                g.setColor(1, 1, 1, 1);
            }
            if (over && ok) {
                hoveredSlot = i;
            }
        }

        // statistics
        List<Stat> shown = preview != null ? preview : stats;
        if (!statsOpen) {
            drawButton(g, font, MARGIN, MARGIN, 150, 20, Component.translatable("gui.tacz.gun_refit.property_diagrams.show").getString(), mx, my);
        } else {
            drawStats(g, font, igun, gun, shown, mx, my);
        }

        // list of attachments of the selected category
        hoverRow = -1;
        ItemStack hoverStack = ItemStack.EMPTY;
        if (sel != AttachmentType.NONE) {
            List<Entry> list = entries(sel);
            int rows = Math.min(list.size(), maxRows());
            listScroll = Math.max(0, Math.min(listScroll, Math.max(0, list.size() - rows)));
            int fx = flyX();
            int fh = 20 + Math.max(1, rows) * ROW_H + 4;
            panel(g, fx, MARGIN, FLY_W, fh);
            g.drawString(font, categoryName(sel), fx + 8, MARGIN + 6, ACCENT, true);
            g.fill(fx, MARGIN + 20, fx + FLY_W, MARGIN + 21, LINE);
            if (list.isEmpty()) {
                g.drawString(font, "Нет подходящих модулей", fx + 8, MARGIN + 30, MUTED, true);
            }
            for (int r = 0; r < rows; r++) {
                Entry e = list.get(r + listScroll);
                int y = MARGIN + 21 + r * ROW_H;
                boolean over = mx >= fx && mx < fx + FLY_W && my >= y && my < y + ROW_H;
                if (over) {
                    hoverRow = r + listScroll;
                    hoverStack = e.stack;
                    g.fill(fx + 1, y, fx + FLY_W - 1, y + ROW_H, 0x14FFFFFF);
                } else if (e.installed) {
                    g.fill(fx + 1, y, fx + FLY_W - 1, y + ROW_H, 0x24D8A24A);
                }
                g.renderItem(e.stack, fx + 8, y + 5);
                fit(g, font, e.stack.getHoverName().getString(), fx + 30, y + 9, FLY_W - 30 - 20, over ? HOVER : 0xFFFFFFFF);
                int bx = fx + FLY_W - 16, by = y + 9;
                g.fill(bx, by, bx + 7, by + 7, e.installed ? ACCENT : 0xFF6B6E73);
                g.fill(bx + 1, by + 1, bx + 6, by + 6, e.installed ? ACCENT : 0xFF0E0E10);
            }
            if (list.size() > rows) {
                g.drawString(font, (listScroll + 1) + "–" + (listScroll + rows) + " / " + list.size(), fx + 8, MARGIN + fh - 1, MUTED, true);
            }
        }

        // preview of the hovered attachment (the one installed is already in the numbers)
        ItemStack want = hoverStack.isEmpty() || !statsOpen ? ItemStack.EMPTY : hoverStack;
        if (!ItemStack.isSameItemSameTags(want, previewCandidate) || want.isEmpty() != previewCandidate.isEmpty()) {
            previewCandidate = want;
            preview = want.isEmpty() ? null : compute(gun, want);
        }

        for (AbstractWidget w : sliders) {
            w.render(g, mx, my, partial);
        }

        // names of the categories and hint
        if (hoveredSlot >= 0) {
            String n = categoryName(COLUMN[hoveredSlot]);
            int w = font.width(n) + 10;
            int x = sx - 4 - w, y = MARGIN + hoveredSlot * SLOT_STEP + 5;
            if (sel == AttachmentType.NONE || hoveredSlot * SLOT_STEP + MARGIN > MARGIN + 20 + ROW_H) {
                panel(g, x, y, w, 14);
                g.drawString(font, n, x + 5, y + 3, 0xFFFFFFFF, true);
            }
        }
        String hint = "Выберите категорию справа · наведите на модуль, чтобы сравнить";
        g.drawString(font, hint, (this.width - font.width(hint)) / 2, this.height - 14, 0xFF5D6167, true);
        if (!hoverStack.isEmpty()) {
            g.renderTooltip(font, hoverStack, mx, my);
        }
    }

    /**
     * Draws the gun the way TACZ draws it in the first person hand pass (same renderer, same refit camera), but from here: with the
     * hand projection and an empty pose, over a cleared depth buffer. The hand of the player is not involved at all.
     */
    private void renderGun(ItemStack gun, float partial) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Object renderer = player == null ? null : IClientItemExtensions.of(gun.getItem()).getCustomRenderer();
        if (renderer == null || !GunRenderer.supports(renderer)) {
            return;
        }
        Matrix4f oldProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting oldSorting = RenderSystem.getVertexSorting();
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        try {
            if (GunRenderer.init(renderer, gun, player, partial)) {
                startedAnimation = true;
                animatedGun = gun;
            }
            RenderSystem.setProjectionMatrix(mc.gameRenderer.getProjectionMatrix(70.0), VertexSorting.DISTANCE_TO_ORIGIN);
            modelView.setIdentity();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            Lighting.setupFor3DItems();
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            GunRenderer.render(renderer, player, gun, new PoseStack(), buffers, 0xF000F0, partial);
            buffers.endBatch();
        } catch (Throwable t) {
            // a gun whose animation cannot run is simply not drawn
            if (!renderFailed) {
                renderFailed = true;
                com.voltyx.efw.EFW.LOGGER.warn("Could not draw the gun on the modification screen", t);
            }
        } finally {
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(oldProjection, oldSorting);
        }
    }

    private void drawButton(GuiGraphics g, Font font, int x, int y, int w, int h, String text, int mx, int my) {
        panel(g, x, y, w, h);
        boolean over = mx >= x && mx < x + w && my >= y && my < y + h;
        g.drawString(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2 + 1, over ? HOVER : 0xFFFFFFFF, true);
    }

    private void drawStats(GuiGraphics g, Font font, IGun igun, ItemStack gun, List<Stat> shown, int mx, int my) {
        int x = MARGIN, y = MARGIN;
        panel(g, x, y, STATS_W, STATS_H);
        // header: fire mode (the S button switches it)
        boolean overS = mx >= x + 6 && mx < x + 20 && my >= y + 5 && my < y + 19;
        g.fill(x + 6, y + 5, x + 20, y + 19, overS ? 0xFF3A3A3A : 0xFF262626);
        g.drawString(font, "S", x + 11, y + 8, ACCENT, true);
        String mode = Component.translatable("gui.tacz.gun_refit.property_diagrams.fire_mode").getString() + fireModeName(igun.getFireMode(gun));
        fit(g, font, mode, x + 26, y + 8, STATS_W - 32, 0xFFFFFFFF);
        // tabs
        int tabsY = y + 24;
        int tw = STATS_W / TAB_NAMES.length;
        boolean[] changed = new boolean[TAB_NAMES.length];
        if (preview != null) {
            for (Stat s : preview) {
                if (s.modifier != 0.0) {
                    changed[tabOf(s.titleKey)] = true;
                }
            }
        }
        for (int i = 0; i < TAB_NAMES.length; i++) {
            int tx = x + i * tw;
            boolean on = tab == i;
            boolean over = mx >= tx && mx < tx + tw && my >= tabsY && my < tabsY + 18;
            if (on) {
                g.fill(tx, tabsY, tx + tw, tabsY + 18, 0x0FFFFFFF);
                g.fill(tx, tabsY + 17, tx + tw, tabsY + 18, ACCENT);
            }
            String name = TAB_NAMES[i];
            int w = font.width(name);
            fit(g, font, name, tx + Math.max(2, (tw - w) / 2), tabsY + 5, tw - 4, on ? 0xFFFFFFFF : over ? HOVER : MUTED);
            if (changed[i]) {
                g.fill(tx + tw - 5, tabsY + 3, tx + tw - 2, tabsY + 6, ACCENT);
            }
        }
        g.fill(x, tabsY + 18, x + STATS_W, tabsY + 19, LINE);
        // rows of the tab
        int ry = tabsY + 25;
        for (Stat s : shown) {
            if (tabOf(s.titleKey) != tab) {
                continue;
            }
            if (ry + 25 > y + STATS_H - 18) {
                break;
            }
            String label = Component.translatable(s.titleKey).getString();
            String value = s.defaultString;
            String delta = s.modifier > 0 ? s.positive : s.modifier < 0 ? s.negative : "";
            boolean good = (s.modifier > 0) == s.positivelyBetter;
            int dw = delta.isEmpty() ? 0 : font.width(delta) + 3;
            int vw = font.width(value);
            int left = x + 8, right = x + STATS_W - 8;
            fit(g, font, label, left, ry + 3, right - left - vw - dw - 6, 0xFFFFFFFF);
            g.drawString(font, value, right - dw - vw, ry + 3, 0xFFFFFFFF, true);
            if (!delta.isEmpty()) {
                g.drawString(font, delta, right - font.width(delta), ry + 3, good ? GREEN : RED, true);
            }
            int bw = right - left;
            int by = ry + 16;
            g.fill(left, by, right, by + 4, 0xFF000000);
            double base = Math.max(0, Math.min(1, s.defaultPercent));
            double mod = Math.max(0, Math.min(1, Math.abs(s.modifierPercent)));
            int whiteEnd = (int) Math.round((s.modifier < 0 ? Math.max(0, base - mod) : base) * bw);
            g.fill(left, by, left + whiteEnd, by + 4, 0xFFE6E6E6);
            if (s.modifier != 0.0) {
                int segStart = s.modifier > 0 ? (int) Math.round(base * bw) : whiteEnd;
                int segEnd = Math.min(bw, segStart + (int) Math.round(mod * bw));
                g.fill(left + segStart, by, left + segEnd, by + 4, good ? GREEN : RED);
            }
            for (int px = left + 3; px < right; px += 4) {
                g.fill(px, by, px + 1, by + 4, 0xFF000000);
            }
            ry += 25;
        }
        // hide button
        int hy = y + STATS_H - 18;
        g.fill(x, hy, x + STATS_W, hy + 1, LINE);
        boolean overHide = mx >= x && mx < x + STATS_W && my >= hy && my < hy + 18;
        String hide = Component.translatable("gui.tacz.gun_refit.property_diagrams.hide").getString();
        g.drawString(font, hide, x + (STATS_W - font.width(hide)) / 2, hy + 5, overHide ? HOVER : MUTED, true);
    }

    private static int iconIndex(AttachmentType t) {
        switch (t) {
            case GRIP:
                return 0;
            case LASER:
                return 1;
            case MUZZLE:
                return 2;
            case SCOPE:
                return 3;
            case STOCK:
                return 4;
            case EXTENDED_MAG:
                return 5;
            default:
                return 6;
        }
    }

    private static String categoryName(AttachmentType t) {
        return Component.translatable("tooltip.tacz.attachment." + t.name().toLowerCase()).getString();
    }

    private static String fireModeName(FireMode mode) {
        String key = mode == FireMode.AUTO ? "auto" : mode == FireMode.SEMI ? "semi" : mode == FireMode.BURST ? "burst" : "unknown";
        return Component.translatable("gui.tacz.gun_refit.property_diagrams." + key).getString();
    }

    // ------------------------------------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mxd, double myd, int button) {
        int mx = (int) mxd, my = (int) myd;
        if (button != 0) {
            return super.mouseClicked(mxd, myd, button);
        }
        ItemStack gun = gun();
        IGun igun = IGun.getIGunOrNull(gun);
        if (igun == null) {
            return false;
        }
        // statistics panel and its button
        if (!statsOpen) {
            if (inside(mx, my, MARGIN, MARGIN, 150, 20)) {
                statsOpen = true;
                return true;
            }
        } else {
            int x = MARGIN, y = MARGIN;
            if (inside(mx, my, x, y + STATS_H - 18, STATS_W, 18)) {
                statsOpen = false;
                preview = null;
                previewCandidate = ItemStack.EMPTY;
                return true;
            }
            if (inside(mx, my, x + 6, y + 5, 14, 14)) {
                EfwNetwork.CHANNEL.sendToServer(new EfwNetwork.GunFireSelect(gunSlot));
                return true;
            }
            int tw = STATS_W / TAB_NAMES.length;
            for (int i = 0; i < TAB_NAMES.length; i++) {
                if (inside(mx, my, x + i * tw, y + 24, tw, 18)) {
                    tab = i;
                    return true;
                }
            }
        }
        // categories
        for (int i = 0; i < COLUMN.length; i++) {
            if (inside(mx, my, slotX(), MARGIN + i * SLOT_STEP, SLOT, SLOT)) {
                AttachmentType type = COLUMN[i];
                if (igun.allowAttachmentType(gun, type)) {
                    RefitTransform.changeRefitScreenView(selected() == type ? AttachmentType.NONE : type);
                    listScroll = 0;
                    syncSliders();
                }
                return true;
            }
        }
        // attachments
        AttachmentType sel = selected();
        if (sel != AttachmentType.NONE) {
            List<Entry> list = entries(sel);
            int rows = Math.min(list.size(), maxRows());
            for (int r = 0; r < rows; r++) {
                if (inside(mx, my, flyX(), MARGIN + 21 + r * ROW_H, FLY_W, ROW_H)) {
                    click(list.get(r + listScroll), sel);
                    return true;
                }
            }
        }
        return super.mouseClicked(mxd, myd, button);
    }

    private void click(Entry e, AttachmentType type) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        com.voltyx.efw.EFW.LOGGER.info("Modification screen: {} {} on gun slot {} ({})", e.installed ? "unload" : "install", e.stack.getItem(), gunSlot, type);
        if (e.installed) {
            if (player.getInventory().getFreeSlot() < 0) {
                player.displayClientMessage(Component.translatable("gui.tacz.gun_refit.unload.no_space"), false);
                return;
            }
            SoundPlayManager.playerRefitSound(e.stack, player, SoundManager.UNINSTALL_SOUND);
            NetworkHandler.CHANNEL.sendToServer(new ClientMessageUnloadAttachment(gunSlot, type));
        } else {
            SoundPlayManager.playerRefitSound(e.stack, player, SoundManager.INSTALL_SOUND);
            NetworkHandler.CHANNEL.sendToServer(new ClientMessageRefitGun(e.invIndex, gunSlot, type));
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (selected() != AttachmentType.NONE && mx >= flyX() && mx < flyX() + FLY_W) {
            listScroll = Math.max(0, listScroll - (int) Math.signum(delta));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
