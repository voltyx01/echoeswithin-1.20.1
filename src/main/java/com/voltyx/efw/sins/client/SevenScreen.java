package com.voltyx.efw.sins.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.voltyx.efw.EFW;
import com.voltyx.efw.gender.main.Breasts;
import com.voltyx.efw.gender.main.GenderPlayer;
import com.voltyx.efw.gender.main.WildfireGender;
import com.voltyx.efw.gender.main.config.ClientConfiguration;
import com.voltyx.efw.sins.SevenConfig;
import com.voltyx.efw.sins.stat.SinDefs;
import com.voltyx.efw.sins.stat.SinEffect;
import com.voltyx.efw.sins.SinContent;
import com.voltyx.efw.sins.SinData;
import com.voltyx.efw.sins.SinNetwork;
import com.voltyx.efw.sins.SinType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * SEVEN (port of the 1.12 GuiSevenScreen): sin selection, then the Grievance (level and effects), Skills (lycoris flower and
 * skill tree), Manuals and Form tabs, and the level-up card flow. Everything is laid out on a fixed 512x274 virtual canvas
 * stretched to the window.
 */
public class SevenScreen extends Screen {
    private static final float VIRTUAL_W = 512f;
    private static final float VIRTUAL_H = 274f;
    private float uiScaleX = 1f;
    private float uiScaleY = 1f;

    public static final int STATE_CLASS_SELECT = 0;
    public static final int STATE_MAIN_HUD = 1;
    public static final int STATE_LEVEL_UP = 2;

    public static final int TAB_GRIEVANCE = 0;
    public static final int TAB_SKILLS = 1;
    public static final int TAB_MANUALS = 2;
    public static final int TAB_APPEARANCE = 3;

    // palette: cold dark slate, or warm umber/blood/gold
    private static int COLOR_BG_TOP, COLOR_BG_BOTTOM, COLOR_BG, COLOR_PANEL_TOP, COLOR_PANEL_BOTTOM, COLOR_PAPER, COLOR_PAPER_DIM,
            COLOR_INK, COLOR_BLOOD, COLOR_BLOOD_BRIGHT, COLOR_GOLD, COLOR_GOLD_BRIGHT, COLOR_LINE, COLOR_PERIMETER_GLOW,
            COLOR_ARROW_HOVER, COLOR_CARD_BG_TOP, COLOR_CARD_BG_BOTTOM;

    private static void applyTheme(boolean warm) {
        if (warm) {
            COLOR_BG_TOP = 0xFF181310;
            COLOR_BG_BOTTOM = 0xFF0E0B09;
            COLOR_BG = 0xFF14100D;
            COLOR_PANEL_TOP = 0xF41E1814;
            COLOR_PANEL_BOTTOM = 0xF7120E0C;
            COLOR_PAPER = 0xFFC9C3B6;
            COLOR_PAPER_DIM = 0xFF8B8578;
            COLOR_INK = 0xFF3A3630;
            COLOR_BLOOD = 0xFF7A2418;
            COLOR_BLOOD_BRIGHT = 0xFFA8341F;
            COLOR_GOLD = 0xFFC98A2E;
            COLOR_GOLD_BRIGHT = 0xFFE8A53D;
            COLOR_LINE = 0xFF221F1C;
            COLOR_PERIMETER_GLOW = 0xFFFFCB6B;
            COLOR_ARROW_HOVER = 0x33C98A2E;
            COLOR_CARD_BG_TOP = 0xF2141210;
            COLOR_CARD_BG_BOTTOM = 0xF20A0908;
        } else {
            COLOR_BG_TOP = 0xFF14171C;
            COLOR_BG_BOTTOM = 0xFF0B0D10;
            COLOR_BG = 0xFF101317;
            COLOR_PANEL_TOP = 0xF4181C22;
            COLOR_PANEL_BOTTOM = 0xF70E1014;
            COLOR_PAPER = 0xFFD2D8E0;
            COLOR_PAPER_DIM = 0xFF7D8794;
            COLOR_INK = 0xFF2B323D;
            COLOR_BLOOD = 0xFF2A394D;
            COLOR_BLOOD_BRIGHT = 0xFF4A688F;
            COLOR_GOLD = 0xFF587299;
            COLOR_GOLD_BRIGHT = 0xFF8BAAC9;
            COLOR_LINE = 0xFF1C222B;
            COLOR_PERIMETER_GLOW = 0xFF8BAAC9;
            COLOR_ARROW_HOVER = 0x33587299;
            COLOR_CARD_BG_TOP = 0xF214171C;
            COLOR_CARD_BG_BOTTOM = 0xF20B0D10;
        }
    }

    static {
        applyTheme(false);
    }

    private int currentState = STATE_CLASS_SELECT;
    public int currentTab = TAB_GRIEVANCE;

    // flower placement, tuned in 1.12
    private final FlowerRenderer flowerModel = new FlowerRenderer();
    private final int flowerPosX = 134;
    private final int flowerPosY = 321;
    private final float flowerScreenScale = 303.18015f;
    private final float flowerOffsetX = -42.174496f;
    private final float flowerOffsetY = -7.199999f;
    private final float flowerYaw = 20.95074f;
    private final float flowerPitch = 60.949986f;
    private final float flowerRoll = 20.45f;
    private final float flowerAnimSpeed = 2.4f;
    private int debugQueueSlotSelect = 0;
    private float flowerSlideAnim = 0f;

    private final Screen parentScreen;
    private final DustParticles dust = new DustParticles();
    private boolean dustInitialised;
    private long lastFrameTime = System.nanoTime();

    private final float[] sinCardHoverLift = new float[7];
    private SinType lastKnownChosenSin = null;
    private float hudHeaderFade = 0.0f;

    // the class select flow mirrors the level-up SELECT/EXIT/RETURN flow
    private static final int CLASS_PHASE_LISTING = 0;
    private static final int CLASS_PHASE_SELECT = 1;
    private static final int CLASS_PHASE_EXIT = 2;
    private static final int CLASS_PHASE_RETURN = 3;
    private int classSelectPhase = CLASS_PHASE_LISTING;
    private long classSelectPhaseStartTime = 0L;
    private int selectedSinIndex = -1;
    private final float[] levelUpCardHoverLift = new float[3];

    private float currentDeltaSec = 0f;
    private float grievanceScrollAnim = 0f;
    private float manualsScrollAnim = 0f;
    private float skillsScrollAnim = 0f;

    private List<String> offeredCards = new ArrayList<>();
    /** The rank each offered card will have if taken, fixed when the offer came (the sync after the choice must not change it). */
    private final Map<String, Integer> offeredRanks = new HashMap<>();
    private long levelUpAnimStartTime = 0;

    private static final int LVLUP_PHASE_ENTER = 0;
    private static final int LVLUP_PHASE_CARDS = 1;
    private static final int LVLUP_PHASE_SELECT = 2;
    private static final int LVLUP_PHASE_EXIT = 3;
    private static final int LVLUP_PHASE_RETURN = 4;
    private static final long LVLUP_ENTER_DURATION_MS = 650L;
    private static final long LVLUP_SELECT_DURATION_MS = 1000L;
    private static final long LVLUP_EXIT_DURATION_MS = 434L;
    private static final long LVLUP_RETURN_DURATION_MS = 650L;
    private static final float LVLUP_SELECTED_SCALE = 1.08f;
    private static final long LVLUP_CARD_FLIGHT_MS = 434L;
    private static final long LVLUP_TITLE_FADE_MS = 300L;
    private int levelUpPhase = LVLUP_PHASE_ENTER;
    private long levelUpPhaseStartTime = 0L;
    private int selectedCardIndex = -1;

    /** The tooltip of this frame: set while drawing, drawn last, on top of everything. */
    private String tipTitle;
    private List<TipLine> tipLines = new ArrayList<>();
    private int tipX, tipY, tipBorder;
    private boolean tipLeft;

    private record TipLine(String text, int color) {
    }

    private int grievanceScrollY, maxGrievanceScroll, manualsScrollY, maxManualsScroll, skillsScrollY, maxSkillsScroll;

    // ===== skill tree =====
    private static final int SKILL_BRANCHES = 3;
    private static final int SKILLS_PER_BRANCH = 4;
    private static final int SKILL_COUNT = 1 + SKILL_BRANCHES * SKILLS_PER_BRANCH;
    private static final int SKILL_NODE_SIZE = 22;
    private static final int SKILL_ROW_GAP = 30;

    private record SkillNode(int id, int branch, int depth, String name, String description) {
    }

    // placeholder Wrath-flavoured set from 1.12 (the real per-sin data was never written)
    private static final SkillNode[] SKILL_NODES = {
            new SkillNode(0, -1, 0, "Притупление", "Пока вы под угрозой: тряска экрана и сбивание прицела от полученного урона ощутимо слабее."),
            new SkillNode(1, 0, 1, "Загнанный зверь", "Пока здоровье ниже 50%, скорость бега увеличена."),
            new SkillNode(2, 0, 2, "Инерция боли", "Чем меньше остаётся здоровья, тем быстрее перезаряжается оружие."),
            new SkillNode(3, 0, 3, "Шрамы", "Атаки заражённых врагов с шансом не вызывают кровотечение."),
            new SkillNode(4, 0, 4, "Мёртвый нерв", "Раз в 10 минут смертельный урон игнорируется: здоровье остаётся на 1, и вы неуязвимы следующие 2 секунды."),
            new SkillNode(5, 1, 1, "Холодная ярость", "Расход выносливости в бою снижен."),
            new SkillNode(6, 1, 2, "Стойкость плоти", "Пока рядом заражённый враг, регенерация здоровья не прекращается."),
            new SkillNode(7, 1, 3, "Хладнокровие", "После добивания врага следующий выстрел не имеет разброса и отдачи."),
            new SkillNode(8, 1, 4, "Второе дыхание", "При падении здоровья ниже 20% вы на 3 секунды получаете иммунитет к оглушению и замедлению."),
            new SkillNode(9, 2, 1, "Верный прицел", "Первый выстрел по новой цели всегда критический."),
            new SkillNode(10, 2, 2, "Стальные нервы", "Получение урона больше не сбивает прицеливание."),
            new SkillNode(11, 2, 3, "Голод хищника", "Добивание врага восстанавливает часть выносливости."),
            new SkillNode(12, 2, 4, "Последний вдох", "Если удар должен был убить вас, здоровье не может опуститься ниже 1 в течение следующих 4 секунд."),
    };

    /** Icon of each node: intermediate skills 1..9, capstones 10f..12f. */
    private static final int[] SKILL_ASSIGNED_ICON = {0, 1, 2, 3, 10, 4, 5, 6, 11, 7, 8, 9, 12};

    // the picture of the tree: follows SinData (synced from the server); a click burns the seal at once and asks the server
    private static final boolean[] skillUnlocked = new boolean[SKILL_COUNT];
    private static final long[] skillUnlockAnimStart = new long[SKILL_COUNT];
    public static final List<Integer> skillUnlockOrder = new ArrayList<>();

    static {
        skillUnlocked[0] = true; // the root is always unlocked
        Arrays.fill(skillUnlockAnimStart, -1L);
    }

    private static final ResourceLocation TEX_SKILL_UNUPGRADED = new ResourceLocation(EFW.MOD_ID, "textures/gui/skills/skill_unupgraded.png");
    private static final ResourceLocation[] TEX_SKILLS = new ResourceLocation[13];

    static {
        TEX_SKILLS[0] = new ResourceLocation(EFW.MOD_ID, "textures/gui/skills/skill0.png");
        for (int i = 1; i <= 9; i++) {
            TEX_SKILLS[i] = new ResourceLocation(EFW.MOD_ID, "textures/gui/skills/skill" + i + ".png");
        }
        TEX_SKILLS[10] = new ResourceLocation(EFW.MOD_ID, "textures/gui/skills/skill10f.png");
        TEX_SKILLS[11] = new ResourceLocation(EFW.MOD_ID, "textures/gui/skills/skill11f.png");
        TEX_SKILLS[12] = new ResourceLocation(EFW.MOD_ID, "textures/gui/skills/skill12f.png");
    }

    private static int[] rawTexSkillUnupgraded;
    private static final int[][] rawTexSkills = new int[13][];
    private static boolean skillRawTexturesLoaded;

    private final DynamicTexture[] skillBurnTextures = new DynamicTexture[SKILL_COUNT];
    private final ResourceLocation[] skillBurnTextureLocations = new ResourceLocation[SKILL_COUNT];
    private static final long SKILL_BURN_DURATION_MS = 1000L;
    private static final long SKILL_FLASH_DURATION_MS = 350L;
    private boolean flowerMenuSoundPlayed;

    private static final class SkillSpark2D {
        float x, y, vx, vy, age, maxAge, size;

        SkillSpark2D(float cx, float cy, Random rand) {
            x = cx + (rand.nextFloat() - 0.5f) * 12.0f;
            y = cy + (rand.nextFloat() - 0.5f) * 12.0f;
            float angle = rand.nextFloat() * 6.283185f;
            float speed = 14.0f + rand.nextFloat() * 30.0f;
            vx = (float) Math.cos(angle) * speed;
            vy = (float) Math.sin(angle) * speed - 16.0f; // drift upwards
            maxAge = 0.40f + rand.nextFloat() * 0.40f;
            size = 1.8f + rand.nextFloat() * 2.2f;
        }

        boolean update(float deltaSec) {
            age += deltaSec;
            if (age >= maxAge) {
                return false;
            }
            x += vx * deltaSec;
            y += vy * deltaSec;
            vy += 20.0f * deltaSec;
            vx *= (1.0f - 0.8f * deltaSec);
            return true;
        }
    }

    private final List<SkillSpark2D> skillSparks2D = new ArrayList<>();
    private final Random skillRand = new Random();

    private SkillNode hoveredSkillNodeForTooltip;
    private int hoveredSkillTooltipX, hoveredSkillTooltipY;

    public SevenScreen(Screen parent) {
        super(Component.literal("SEVEN"));
        this.parentScreen = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private SinData getSinData() {
        return minecraft != null && minecraft.player != null ? SinData.get(minecraft.player) : null;
    }

    @Override
    protected void init() {
        applyTheme(SevenConfig.WARM_THEME.get());
        if (!openSoundPlayed) {
            playUi(SinContent.FLOWER_MENU_ENTER.get(), 0.8f);
            openSoundPlayed = true;
        }
        if (!dustInitialised) {
            dust.init((int) VIRTUAL_W, (int) VIRTUAL_H);
            dustInitialised = true;
        }
        lastFrameTime = System.nanoTime();
        syncSkillsFromData();
        SinData data = getSinData();
        if (data != null && data.getChosenSin() != null) {
            currentState = currentState == STATE_LEVEL_UP ? STATE_LEVEL_UP : STATE_MAIN_HUD;
        } else if (classSelectPhase == CLASS_PHASE_LISTING) {
            currentState = STATE_CLASS_SELECT;
        }
        if (currentState == STATE_MAIN_HUD && currentTab == TAB_SKILLS) {
            flowerModel.playOpen();
            if (!flowerMenuSoundPlayed) {
                playFlowerMenuSound();
                flowerMenuSoundPlayed = true;
            }
        }
    }

    public void setTab(int newTab) {
        if (currentTab == newTab) {
            return;
        }
        int oldTab = currentTab;
        currentTab = newTab;
        if (newTab == TAB_SKILLS) {
            flowerModel.playOpen();
            playFlowerMenuSound();
            flowerMenuSoundPlayed = true;
        } else {
            playUi(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
            if (oldTab == TAB_SKILLS) {
                flowerModel.playClose(1.8f);
                flowerMenuSoundPlayed = false;
            }
        }
    }

    private void playUi(SoundEvent sound, float volume) {
        playUi(sound, 1.0f, volume);
    }

    private void playUi(SoundEvent sound, float pitch, float volume) {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
        }
    }

    /** What the mouse is over this frame and was over the last one: a sound when it comes onto something new. */
    private Set<String> hoverNow = new HashSet<>();
    private Set<String> hoverPrev = new HashSet<>();
    private boolean openSoundPlayed;
    private int lastSliderStep = -1;

    private void hoverSound(String key, boolean hovered, SoundEvent sound, float pitch, float volume) {
        if (hovered) {
            hoverNow.add(key);
            if (!hoverPrev.contains(key)) {
                playUi(sound, pitch, volume);
            }
        }
    }

    /** A chosen card (the sin, a level-up card): paper laid down and a soft chime. */
    private void playCardChosen() {
        playUi(SoundEvents.BOOK_PUT, 0.9f, 1.0f);
        playUi(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 0.7f);
    }

    /** Not possible right now (no points, the skill is locked, nothing to level up with). */
    private void playDenied() {
        playUi(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6f, 0.6f);
    }

    private void playFlowerMenuSound() {
        // the louder layer grows with the unlocked skills (0 -> silent, 12 -> full)
        int upgraded = 0;
        for (int i = 1; i < skillUnlocked.length && i < 13; i++) {
            if (skillUnlocked[i]) {
                upgraded++;
            }
        }
        float volFlower = Math.max(0.0f, Math.min(1.0f, upgraded / 12.0f));
        if (volFlower > 0.001f) {
            playUi(SinContent.FLOWER_MENU.get(), volFlower);
        }
        playUi(SinContent.FLOWER_MENU_0.get(), 0.60f);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (currentTab == TAB_SKILLS && currentState == STATE_MAIN_HUD) {
            if (keyCode == GLFW.GLFW_KEY_P) {
                // debug from 1.12: ignite the next petal of the queue
                int qLen = FlowerRenderer.PETAL_BURN_QUEUE.length;
                flowerModel.triggerPetalBurn(FlowerRenderer.PETAL_BURN_QUEUE[debugQueueSlotSelect % qLen]);
                debugQueueSlotSelect = (debugQueueSlotSelect + 1) % qLen;
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (currentState == STATE_LEVEL_UP && (levelUpPhase == LVLUP_PHASE_ENTER || levelUpPhase == LVLUP_PHASE_CARDS)) {
                selectedCardIndex = -1;
                levelUpPhase = LVLUP_PHASE_RETURN;
                levelUpPhaseStartTime = System.nanoTime();
                return true;
            }
            Screen target = parentScreen != null ? parentScreen : new InventoryScreen(minecraft.player);
            ScreenTransition.to(target);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (currentState == STATE_MAIN_HUD && delta != 0) {
            int step = delta > 0 ? -22 : 22;
            switch (currentTab) {
                case TAB_GRIEVANCE -> grievanceScrollY = clampScroll(grievanceScrollY + step, maxGrievanceScroll);
                case TAB_MANUALS -> manualsScrollY = clampScroll(manualsScrollY + step, maxManualsScroll);
                case TAB_SKILLS -> skillsScrollY = clampScroll(skillsScrollY + step, maxSkillsScroll);
                case TAB_APPEARANCE -> appearanceScrollY = clampScroll(appearanceScrollY + step, maxAppearanceScroll);
                default -> {
                }
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private static int clampScroll(int value, int max) {
        return Math.max(0, Math.min(max, value));
    }

    /** Free skill points of the player now. */
    private int skillPoints() {
        SinData data = getSinData();
        return data == null ? 0 : data.skillPoints(SinDefs.get());
    }

    /** The server sent the data: the tree shows what it says (seals burnt elsewhere appear burnt). */
    public void onDataSynced() {
        syncSkillsFromData();
    }

    private void syncSkillsFromData() {
        SinData data = getSinData();
        if (data == null) {
            return;
        }
        boolean changed = false;
        for (int i = 0; i < SKILL_COUNT; i++) {
            boolean has = i == 0 || data.hasSkill(i); // the root is the sin itself
            if (has != skillUnlocked[i]) {
                skillUnlocked[i] = has;
                if (!has) {
                    skillUnlockAnimStart[i] = -1L;
                }
                changed = true;
            }
        }
        List<Integer> order = data.getUnlockOrder();
        if (changed || !skillUnlockOrder.equals(order)) {
            if (skillUnlockOrder.stream().anyMatch(n -> !order.contains(n))) {
                flowerModel.resetPetalBurns(); // a reset: the petals grow back
            }
            skillUnlockOrder.clear();
            skillUnlockOrder.addAll(order);
        }
    }

    public void receiveLevelUpCards(List<String> cards) {
        offeredCards = cards;
        offeredRanks.clear();
        SinData data = getSinData();
        for (String id : cards) {
            offeredRanks.put(id, (data == null ? 0 : data.rank(id)) + 1);
        }
        currentState = STATE_LEVEL_UP;
        selectedCardIndex = -1;
        levelUpPhase = LVLUP_PHASE_ENTER;
        levelUpPhaseStartTime = System.nanoTime();
        levelUpAnimStartTime = 0L;
        Arrays.fill(levelUpCardHoverLift, 0f);
    }

    private int getDporCount() {
        if (minecraft == null || minecraft.player == null) {
            return 0;
        }
        int count = 0;
        for (ItemStack stack : minecraft.player.getInventory().items) {
            if (!stack.isEmpty() && stack.getItem() == SinContent.DPOR.get()) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static float approachScroll(float current, float target, float deltaSec) {
        float next = current + (target - current) * Math.min(1.0f, deltaSec * 12.0f);
        return Math.abs(next - target) < 0.5f ? target : next;
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        // the screen paints its own background
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        uiScaleX = width / VIRTUAL_W;
        uiScaleY = height / VIRTUAL_H;
        int vMouseX = (int) (mouseX / uiScaleX);
        int vMouseY = (int) (mouseY / uiScaleY);
        tipTitle = null;
        Set<String> lastFrame = hoverPrev;
        hoverPrev = hoverNow;
        hoverNow = lastFrame;
        hoverNow.clear();

        long now = System.nanoTime();
        float deltaSec = Math.min(0.1f, (now - lastFrameTime) / 1_000_000_000.0f);
        lastFrameTime = now;
        currentDeltaSec = deltaSec;
        grievanceScrollAnim = approachScroll(grievanceScrollAnim, grievanceScrollY, deltaSec);
        manualsScrollAnim = approachScroll(manualsScrollAnim, manualsScrollY, deltaSec);
        skillsScrollAnim = approachScroll(skillsScrollAnim, skillsScrollY, deltaSec);
        appearanceScrollAnim = approachScroll(appearanceScrollAnim, appearanceScrollY, deltaSec);

        PoseStack pose = g.pose();
        pose.pushPose();
        pose.scale(uiScaleX, uiScaleY, 1.0f);

        g.fillGradient(0, 0, (int) VIRTUAL_W, (int) VIRTUAL_H, COLOR_BG_TOP, COLOR_BG_BOTTOM);
        dust.updateAndRender(g, (int) VIRTUAL_W, (int) VIRTUAL_H);

        SinType chosenSin = null;
        SinData data = getSinData();
        if (data != null && data.getChosenSin() != null) {
            chosenSin = data.getChosenSin();
            lastKnownChosenSin = chosenSin;
        } else if (lastKnownChosenSin != null) {
            chosenSin = lastKnownChosenSin;
        } else if (selectedSinIndex >= 0 && selectedSinIndex < SinType.values().length) {
            chosenSin = SinType.values()[selectedSinIndex];
        }

        if (currentState == STATE_MAIN_HUD || currentState == STATE_LEVEL_UP) {
            hudHeaderFade = Math.min(1.0f, hudHeaderFade + deltaSec * 3.5f);
        } else if (classSelectPhase == CLASS_PHASE_RETURN) {
            long phaseElapsed = (now - classSelectPhaseStartTime) / 1_000_000L;
            hudHeaderFade = easeOutCubic(clamp01(phaseElapsed / (float) LVLUP_RETURN_DURATION_MS));
        } else {
            hudHeaderFade = 0.0f;
        }
        if (chosenSin != null && hudHeaderFade > 0.005f) {
            drawChosenSinHeader(g, chosenSin, hudHeaderFade);
        }

        if (currentState == STATE_CLASS_SELECT) {
            drawClassSelectFlow(g, vMouseX, vMouseY, deltaSec, partialTicks);
        } else if (currentState == STATE_MAIN_HUD) {
            drawMainHudScreen(g, vMouseX, vMouseY, partialTicks, 0.0f, 0.0f);
        } else if (currentState == STATE_LEVEL_UP) {
            drawLevelUpFlow(g, vMouseX, vMouseY, partialTicks);
        }

        if (tipTitle != null) {
            drawTooltip(g, tipX, tipY, tipTitle, tipLines, tipBorder, tipLeft);
        }
        pose.popPose();
    }

    // ===================================================================== 1. class select
    private static final int SIN_CARD_W = 88;
    private static final int SIN_CARD_H = 110;
    private static final int SIN_GAP_X = 10;
    private static final int SIN_GAP_Y = 8;

    private int sinCardBaseX(int i) {
        int row1StartX = ((int) VIRTUAL_W - (4 * SIN_CARD_W + 3 * SIN_GAP_X)) / 2;
        int row2StartX = ((int) VIRTUAL_W - (3 * SIN_CARD_W + 2 * SIN_GAP_X)) / 2;
        return i < 4 ? row1StartX + i * (SIN_CARD_W + SIN_GAP_X) : row2StartX + (i - 4) * (SIN_CARD_W + SIN_GAP_X);
    }

    private int sinCardBaseY(int i) {
        int topHeaderY = Math.max(8, (int) VIRTUAL_H / 2 - 130);
        int row1StartY = topHeaderY + 26;
        return i < 4 ? row1StartY : row1StartY + SIN_CARD_H + SIN_GAP_Y;
    }

    private void drawClassSelectScreen(GuiGraphics g, int mouseX, int mouseY, float deltaSec) {
        String title = "ВЫБЕРИ СВОЙ ГРЕХ";
        String subtitle = "ПЕРВОНАЧАЛЬНЫЙ ВЫБОР ОПРЕДЕЛИТ ВАШИ СЛАБОСТИ И СИЛЫ";
        int topHeaderY = Math.max(8, (int) VIRTUAL_H / 2 - 130);
        text(g, title, ((int) VIRTUAL_W - font.width(title)) / 2, topHeaderY, COLOR_PAPER);
        text(g, subtitle, ((int) VIRTUAL_W - font.width(subtitle)) / 2, topHeaderY + 11, COLOR_PAPER_DIM);

        SinType[] sins = SinType.values();
        for (int i = 0; i < sins.length; i++) {
            int cx = sinCardBaseX(i);
            int cy = sinCardBaseY(i);
            boolean isHovered = mouseX >= cx && mouseX <= cx + SIN_CARD_W && mouseY >= cy && mouseY <= cy + SIN_CARD_H;
            hoverSound("sin" + i, isHovered, SoundEvents.BOOK_PAGE_TURN, 1.2f, 0.5f);
            float targetLift = isHovered ? 4.0f : 0.0f;
            sinCardHoverLift[i] += (targetLift - sinCardHoverLift[i]) * Math.min(1.0f, deltaSec * 15.0f);
            if (Math.abs(sinCardHoverLift[i] - targetLift) < 0.05f) {
                sinCardHoverLift[i] = targetLift;
            }
            drawSinCard(g, sins[i], cx, Math.round(cy - sinCardHoverLift[i]), mouseX, mouseY, isHovered, -1.0f);
        }
    }

    /** One sin card; pass borderGlowT >= 0 for the travelling perimeter glow of the confirmed pick. */
    private void drawSinCard(GuiGraphics g, SinType sin, int cx, int cy, int mouseX, int mouseY, boolean isHovered, float borderGlowT) {
        g.fillGradient(cx, cy, cx + SIN_CARD_W, cy + SIN_CARD_H, COLOR_PANEL_TOP, COLOR_PANEL_BOTTOM);
        drawBoxOutline(g, cx, cy, SIN_CARD_W, SIN_CARD_H, isHovered ? COLOR_BLOOD_BRIGHT : COLOR_LINE);
        if (borderGlowT >= 0.0f) {
            drawPerimeterGlow(g, cx, cy, SIN_CARD_W, SIN_CARD_H, borderGlowT);
        }
        // torn top-left corner
        g.fill(cx, cy, cx + 4, cy + 4, COLOR_BG);
        drawLine(g, cx, cy + 4, cx + 4, cy, isHovered ? COLOR_BLOOD_BRIGHT : COLOR_LINE);

        text(g, sin.getNumber(), cx + SIN_CARD_W - 14, cy + 5, COLOR_INK);

        int infoX = cx + 5;
        int infoY = cy + 5;
        boolean infoHover = mouseX >= infoX && mouseX <= infoX + 11 && mouseY >= infoY && mouseY <= infoY + 11;
        drawBoxOutline(g, infoX, infoY, 11, 11, infoHover ? COLOR_GOLD_BRIGHT : COLOR_PAPER_DIM);
        text(g, "i", infoX + 4, infoY + 2, infoHover ? COLOR_GOLD_BRIGHT : COLOR_PAPER_DIM);
        if (infoHover) {
            tip(infoX + 11, infoY, sin.getNameRu(), COLOR_BLOOD,
                    new TipLine("В чём сила: " + sin.getFocusRu(), COLOR_GOLD_BRIGHT),
                    new TipLine("Чем платит: " + sin.getPriceRu(), COLOR_BLOOD_BRIGHT));
        }

        int iconSize = 32;
        drawSinIcon(g, sin, cx + (SIN_CARD_W - iconSize) / 2, cy + 23, iconSize, 1.0f);
        text(g, sin.getNameRu(), cx + (SIN_CARD_W - font.width(sin.getNameRu())) / 2, cy + SIN_CARD_H - 14,
                isHovered ? COLOR_GOLD_BRIGHT : COLOR_PAPER);
    }

    private void drawClassSelectFlow(GuiGraphics g, int mouseX, int mouseY, float deltaSec, float partialTicks) {
        long now = System.nanoTime();
        long phaseElapsed = (now - classSelectPhaseStartTime) / 1_000_000L;
        switch (classSelectPhase) {
            case CLASS_PHASE_LISTING -> drawClassSelectScreen(g, mouseX, mouseY, deltaSec);
            case CLASS_PHASE_SELECT -> {
                drawClassSelectHighlight(g, clamp01(phaseElapsed / (float) LVLUP_SELECT_DURATION_MS));
                if (phaseElapsed >= LVLUP_SELECT_DURATION_MS) {
                    classSelectPhase = CLASS_PHASE_EXIT;
                    classSelectPhaseStartTime = now;
                    playUi(SinContent.MENU_SWOOSH_INVERT.get(), 1.0f);
                }
            }
            case CLASS_PHASE_EXIT -> {
                drawClassSelectExit(g, easeInCubic(phaseElapsed / (float) LVLUP_EXIT_DURATION_MS));
                if (phaseElapsed >= LVLUP_EXIT_DURATION_MS) {
                    classSelectPhase = CLASS_PHASE_RETURN;
                    classSelectPhaseStartTime = now;
                }
            }
            case CLASS_PHASE_RETURN -> {
                // the HUD model and panel slide in from the sides
                float t = easeOutCubic(phaseElapsed / (float) LVLUP_RETURN_DURATION_MS);
                drawMainHudScreen(g, mouseX, mouseY, partialTicks, -VIRTUAL_W * (1.0f - t), VIRTUAL_W * (1.0f - t));
                if (phaseElapsed >= LVLUP_RETURN_DURATION_MS) {
                    currentState = STATE_MAIN_HUD;
                    classSelectPhase = CLASS_PHASE_LISTING;
                    selectedSinIndex = -1;
                }
            }
            default -> {
            }
        }
    }

    /** The chosen sin centres, grows and glows; the other six fly off the top. */
    private void drawClassSelectHighlight(GuiGraphics g, float t) {
        float ease = easeOutCubic(t);
        SinType[] sins = SinType.values();
        for (int i = 0; i < sins.length; i++) {
            if (i == selectedSinIndex) {
                continue;
            }
            int baseY = sinCardBaseY(i);
            drawSinCard(g, sins[i], sinCardBaseX(i), baseY - (int) ((SIN_CARD_H + baseY + 60) * ease), -9999, -9999, false, -1.0f);
        }
        if (selectedSinIndex >= 0 && selectedSinIndex < sins.length) {
            int baseX = sinCardBaseX(selectedSinIndex);
            int baseY = sinCardBaseY(selectedSinIndex);
            float scale = 1.0f + (LVLUP_SELECTED_SCALE - 1.0f) * ease;
            float centerX = baseX + SIN_CARD_W / 2.0f + (VIRTUAL_W / 2.0f - (baseX + SIN_CARD_W / 2.0f)) * ease;
            float centerY = baseY + SIN_CARD_H / 2.0f + (VIRTUAL_H / 2.0f - (baseY + SIN_CARD_H / 2.0f)) * ease;
            float glowT = (t * 1.5f) % 1.0f; // ~1.5 laps around the card
            PoseStack pose = g.pose();
            pose.pushPose();
            pose.translate(centerX, centerY, 0.0f);
            pose.scale(scale, scale, 1.0f);
            pose.translate(-SIN_CARD_W / 2.0f, -SIN_CARD_H / 2.0f, 0.0f);
            drawSinCard(g, sins[selectedSinIndex], 0, 0, -9999, -9999, false, glowT);
            pose.popPose();
        }
    }

    private void drawClassSelectExit(GuiGraphics g, float t) {
        SinType[] sins = SinType.values();
        if (selectedSinIndex < 0 || selectedSinIndex >= sins.length) {
            return;
        }
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(VIRTUAL_W / 2.0f, VIRTUAL_H / 2.0f + t * (VIRTUAL_H * 1.3f), 0.0f);
        pose.scale(LVLUP_SELECTED_SCALE, LVLUP_SELECTED_SCALE, 1.0f);
        pose.translate(-SIN_CARD_W / 2.0f, -SIN_CARD_H / 2.0f, 0.0f);
        drawSinCard(g, sins[selectedSinIndex], 0, 0, -9999, -9999, false, -1.0f);
        pose.popPose();
    }

    // ===================================================================== 2. main HUD
    private void drawMainHudScreen(GuiGraphics g, int mouseX, int mouseY, float partialTicks, float modelOffsetX, float panelOffsetX) {
        int panelW = 236;
        int rightX = (int) VIRTUAL_W - panelW - 10;
        PoseStack pose = g.pose();

        float targetSlide = currentTab == TAB_SKILLS ? 1.0f : 0.0f;
        flowerSlideAnim += (targetSlide - flowerSlideAnim) * Math.min(1.0f, currentDeltaSec * 8.0f);
        if (Math.abs(targetSlide - flowerSlideAnim) < 0.001f) {
            flowerSlideAnim = targetSlide;
        }

        // 3D layer: the flower below, the player model above it
        pose.pushPose();
        pose.translate(modelOffsetX, 0.0f, 0.0f);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        if (flowerSlideAnim > 0.001f || !flowerModel.isFullyClosed()) {
            drawFlowerDisplay(g, currentDeltaSec, flowerSlideAnim);
        }
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX); // the player always draws over the flower
        if (flowerSlideAnim < 0.999f) {
            drawPlayerModel(g);
        }
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX); // panels draw over both
        pose.popPose();

        pose.pushPose();
        pose.translate(panelOffsetX, 0.0f, 0.0f);
        int tabGap = 3;
        int tabW = (panelW - tabGap * 3) / 4;
        int tabH = 18;
        int tabY = 16;
        drawTab(g, rightX, tabY, tabW, tabH, "Grievance", currentTab == TAB_GRIEVANCE, mouseX, mouseY);
        drawTab(g, rightX + (tabW + tabGap), tabY, tabW, tabH, "Skills", currentTab == TAB_SKILLS, mouseX, mouseY);
        drawTab(g, rightX + (tabW + tabGap) * 2, tabY, tabW, tabH, "Manuals", currentTab == TAB_MANUALS, mouseX, mouseY);
        drawTab(g, rightX + (tabW + tabGap) * 3, tabY, tabW, tabH, "Form", currentTab == TAB_APPEARANCE, mouseX, mouseY);

        // clip the panel area (scissor takes GUI coordinates)
        int clipX = (int) (rightX - 2 + panelOffsetX);
        int clipY = 38;
        int clipW = panelW + 4;
        int clipH = (int) VIRTUAL_H - clipY - 8;
        g.enableScissor((int) (clipX * uiScaleX), (int) (clipY * uiScaleY), (int) ((clipX + clipW) * uiScaleX),
                (int) ((clipY + clipH) * uiScaleY));
        switch (currentTab) {
            case TAB_GRIEVANCE -> drawGrievanceTab(g, mouseX, mouseY);
            case TAB_SKILLS -> drawSkillsTab(g, mouseX, mouseY);
            case TAB_MANUALS -> drawManualsTab(g, mouseX, mouseY);
            default -> drawAppearanceTab(g, mouseX, mouseY);
        }
        g.disableScissor();

        if (currentTab == TAB_SKILLS && hoveredSkillNodeForTooltip != null) {
            drawSkillTooltip(g, hoveredSkillNodeForTooltip, hoveredSkillTooltipX, hoveredSkillTooltipY);
        }
        pose.popPose();
    }

    private void drawPlayerModel(GuiGraphics g) {
        Player ent = minecraft.player;
        if (ent == null) {
            return;
        }
        int scale = 190;
        int posX = Math.max(70, ((int) VIRTUAL_W - 260) / 2);
        int posY = (int) VIRTUAL_H / 2 + 260 + (int) (flowerSlideAnim * 320f);
        // the model faces 20 degrees aside, as the fixed "mouse" offset of the 1.12 call; the eye editor turns its face to the screen
        boolean eyes = currentTab == TAB_APPEARANCE && blinkEditorOpen;
        faceYaw += ((eyes ? 0.0f : -20.0f) - faceYaw) * 0.15f;
        Runnable restoreHat = null;
        if (eyes && (Object) minecraft.getEntityRenderDispatcher().getRenderer(ent) instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer renderer) {
            // the hat layer would cover the face being edited: its front face is left out for this drawing
            restoreHat = com.voltyx.efw.gender.render.HatFront.hide(renderer.getModel().hat);
        }
        com.voltyx.efw.gender.render.BlinkLayer.editing = eyes;
        try {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, posX, posY, scale, faceYaw, faceYaw, ent);
            g.flush();
        } finally {
            com.voltyx.efw.gender.render.BlinkLayer.editing = false;
            if (restoreHat != null) {
                restoreHat.run();
            }
        }
    }

    private void drawFlowerDisplay(GuiGraphics g, float deltaSec, float slideProgress) {
        float slideOffsetY = (1.0f - easeOutCubic(slideProgress)) * 160f;
        float curX = flowerPosX + flowerOffsetX;
        float curY = flowerPosY + flowerOffsetY + slideOffsetY;
        float curZ = 20f;

        flowerModel.update(deltaSec, flowerAnimSpeed, skillUnlocked, skillUnlockOrder);
        if (slideProgress <= 0.15f) {
            return; // disappears a little before it has fully slid down
        }
        float fadeAlpha = clamp01((slideProgress - 0.15f) / 0.35f);

        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(curX, curY, curZ);
        pose.scale(flowerScreenScale, -flowerScreenScale, flowerScreenScale); // -Y: 3D model up in GUI space
        pose.mulPose(Axis.ZP.rotationDegrees(flowerRoll));
        pose.mulPose(Axis.YP.rotationDegrees(flowerYaw));
        pose.mulPose(Axis.XP.rotationDegrees(flowerPitch));
        flowerModel.render(pose, 1.0f / 16.0f);
        flowerModel.renderParticles(pose, fadeAlpha);
        pose.popPose();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private float getSkillNodeCenterX(SkillNode node) {
        int panelW = 236;
        int contentX = (int) VIRTUAL_W - panelW - 10 + 12;
        int contentW = panelW - 24;
        if (node.depth == 0) {
            return contentX + contentW / 2.0f;
        }
        int colW = contentW / SKILL_BRANCHES;
        return contentX + colW * node.branch + colW / 2.0f;
    }

    private float getSkillNodeCenterY(SkillNode node) {
        int treeTop = 40 + 10 - Math.round(skillsScrollAnim) + 11 + 10;
        return treeTop + node.depth * SKILL_ROW_GAP + SKILL_NODE_SIZE / 2.0f;
    }

    private void drawSkillsTab(GuiGraphics g, int mouseX, int mouseY) {
        ensureSkillRawTexturesLoaded();
        int panelW = 236;
        int rightX = (int) VIRTUAL_W - panelW - 10;
        int panelH = (int) VIRTUAL_H - 50;
        int panelY = 40;
        g.fillGradient(rightX, panelY, rightX + panelW, panelY + panelH, COLOR_PANEL_TOP, COLOR_PANEL_BOTTOM);
        drawBoxOutline(g, rightX, panelY, panelW, panelH, COLOR_LINE);

        int contentX = rightX + 12;
        int contentW = panelW - 24;
        int treeAreaH = SKILLS_PER_BRANCH * SKILL_ROW_GAP + SKILL_NODE_SIZE + 14;
        maxSkillsScroll = Math.max(0, 10 + 11 + 10 + treeAreaH - (panelH - 10));

        int baseY = panelY + 10 - Math.round(skillsScrollAnim);
        text(g, ChatFormatting.BOLD + "ДРЕВО НАВЫКОВ", contentX, baseY, COLOR_GOLD_BRIGHT);
        int points = skillPoints();
        String pointsStr = points + " ◆";
        text(g, pointsStr, contentX + contentW - font.width(pointsStr), baseY, points > 0 ? COLOR_GOLD_BRIGHT : COLOR_PAPER_DIM);
        int sepY = baseY + 11;
        g.fill(contentX, sepY, contentX + contentW, sepY + 1, COLOR_LINE);

        int treeTop = sepY + 10;
        int colW = contentW / SKILL_BRANCHES;
        int rootX = contentX + contentW / 2;
        int rootY = treeTop + SKILL_NODE_SIZE / 2;
        hoveredSkillNodeForTooltip = null;

        // connectors first, so the nodes draw over the lines
        for (int b = 0; b < SKILL_BRANCHES; b++) {
            int colX = contentX + colW * b + colW / 2;
            int prevX = rootX;
            int prevY = rootY;
            for (int d = 1; d <= SKILLS_PER_BRANCH; d++) {
                int nodeY = treeTop + d * SKILL_ROW_GAP + SKILL_NODE_SIZE / 2;
                drawLine(g, prevX, prevY, colX, nodeY, COLOR_LINE);
                prevX = colX;
                prevY = nodeY;
            }
        }

        boolean rootHover = isPointInSkillNode(mouseX, mouseY, rootX, rootY, SKILL_NODES[0]);
        drawSkillNode(g, SKILL_NODES[0], rootX, rootY, rootHover);
        hoverSound("skill0", rootHover, SoundEvents.NOTE_BLOCK_HAT.value(), 1.5f, 0.3f);
        if (rootHover) {
            hoveredSkillNodeForTooltip = SKILL_NODES[0];
            hoveredSkillTooltipX = rootX;
            hoveredSkillTooltipY = rootY;
        }
        for (int b = 0; b < SKILL_BRANCHES; b++) {
            int colX = contentX + colW * b + colW / 2;
            for (int d = 1; d <= SKILLS_PER_BRANCH; d++) {
                SkillNode node = findSkillNode(b, d);
                int nodeY = treeTop + d * SKILL_ROW_GAP + SKILL_NODE_SIZE / 2;
                boolean hover = isPointInSkillNode(mouseX, mouseY, colX, nodeY, node);
                drawSkillNode(g, node, colX, nodeY, hover);
                hoverSound("skill" + node.id, hover, SoundEvents.NOTE_BLOCK_HAT.value(), 1.5f, 0.3f);
                if (hover) {
                    hoveredSkillNodeForTooltip = node;
                    hoveredSkillTooltipX = colX;
                    hoveredSkillTooltipY = nodeY;
                }
            }
        }
        updateAndRenderSkillSparks(g, currentDeltaSec);
    }

    private int getSkillNodeSize(SkillNode node) {
        return node.depth == SKILLS_PER_BRANCH ? 26 : SKILL_NODE_SIZE;
    }

    private SkillNode findSkillNode(int branch, int depth) {
        for (SkillNode n : SKILL_NODES) {
            if (n.branch == branch && n.depth == depth) {
                return n;
            }
        }
        return null;
    }

    private boolean isPointInSkillNode(int mouseX, int mouseY, int cx, int cy, SkillNode node) {
        int half = getSkillNodeSize(node) / 2;
        return mouseX >= cx - half && mouseX <= cx + half && mouseY >= cy - half && mouseY <= cy + half;
    }

    private boolean isSkillAvailable(SkillNode node) {
        SinData data = getSinData();
        if (node.depth == 0 || skillUnlocked[node.id] || data == null || skillPoints() <= 0) {
            return false;
        }
        int prereqId = node.depth <= 1 ? 0 : findSkillNode(node.branch, node.depth - 1).id;
        return skillUnlocked[prereqId];
    }

    /** Locked: grey dim icon; available: blood outline, gold on hover; unlocked: clean icon. Unlocking burns the icon in. */
    private void drawSkillNode(GuiGraphics g, SkillNode node, int cx, int cy, boolean isHovered) {
        int nodeSize = getSkillNodeSize(node);
        int half = nodeSize / 2;
        int x = cx - half;
        int y = cy - half;
        boolean unlocked = skillUnlocked[node.id];
        boolean available = isSkillAvailable(node);
        int fillColor;
        int borderColor;
        if (unlocked) {
            fillColor = 0;
            borderColor = COLOR_LINE;
        } else if (available) {
            fillColor = isHovered ? 0x22000000 : 0x33000000;
            borderColor = isHovered ? COLOR_GOLD_BRIGHT : COLOR_BLOOD;
        } else {
            fillColor = 0x55000000;
            borderColor = COLOR_LINE;
        }
        drawSkillIcon(g, node, x, y, nodeSize, unlocked, isHovered, available);
        if (fillColor != 0) {
            g.fill(x, y, x + nodeSize, y + nodeSize, fillColor);
        }
        drawBoxOutline(g, x, y, nodeSize, nodeSize, borderColor);

        long animStart = skillUnlockAnimStart[node.id];
        if (animStart > 0) {
            long elapsed = (System.nanoTime() - animStart) / 1_000_000L;
            // gold flash right after the second of burning
            if (elapsed >= SKILL_BURN_DURATION_MS && elapsed < SKILL_BURN_DURATION_MS + SKILL_FLASH_DURATION_MS) {
                float flashProgress = (elapsed - SKILL_BURN_DURATION_MS) / (float) SKILL_FLASH_DURATION_MS;
                int flashAlpha = Math.max(0, Math.min(255, (int) ((float) Math.sin(flashProgress * Math.PI) * 175)));
                additiveRect(g, x, y, x + nodeSize, y + nodeSize, (flashAlpha << 24) | 0xFFE066);
                drawBoxOutline(g, x, y, nodeSize, nodeSize, (Math.min(255, flashAlpha + 60) << 24) | 0xFFF099);
            }
        }
    }

    private void drawSkillTooltip(GuiGraphics g, SkillNode node, int anchorX, int anchorY) {
        SinData data = getSinData();
        SinDefs.SkillDef def = data == null ? null : SinDefs.get().skill(data.getChosenSin(), node.id);
        List<TipLine> lines = new ArrayList<>();
        if (def == null) {
            lines.add(new TipLine(node.description, COLOR_PAPER));
        } else {
            lines.add(new TipLine(def.text(knowsDoll()), COLOR_PAPER_DIM));
            lines.add(new TipLine("", 0));
            for (SinEffect e : def.effects()) {
                lines.add(new TipLine("• " + effectText(e), e.isGood() ? COLOR_GOLD : COLOR_BLOOD_BRIGHT));
            }
        }
        if (!skillUnlocked[node.id] && (isSkillAvailable(node) || skillPoints() > 0)) {
            lines.add(new TipLine("", 0));
            lines.add(new TipLine(isSkillAvailable(node) ? "Нажмите, чтобы сжечь печать (1 ◆). Навсегда." : "Сначала сожгите печать выше по ветке.",
                    isSkillAvailable(node) ? COLOR_GOLD_BRIGHT : COLOR_INK));
        }
        drawTooltip(g, anchorX, anchorY, def != null ? def.name() : node.name, lines, skillUnlocked[node.id] ? COLOR_GOLD : COLOR_BLOOD, true);
    }

    private void tryUnlockSkill(SkillNode node) {
        if (!isSkillAvailable(node)) {
            if (!skillUnlocked[node.id]) {
                playDenied();
            }
            return;
        }
        skillUnlocked[node.id] = true;
        if (!skillUnlockOrder.contains(node.id)) {
            skillUnlockOrder.add(node.id);
        }
        SinNetwork.sendToServer(new SinNetwork.UnlockSkill(node.id));
        skillUnlockAnimStart[node.id] = System.nanoTime();
        spawnSkillSparks(getSkillNodeCenterX(node), getSkillNodeCenterY(node));
        playUi(SinContent.SKILL_UPGRADE.get(), 1.0f);
    }

    private void spawnSkillSparks(float cx, float cy) {
        int count = 12 + skillRand.nextInt(6);
        for (int i = 0; i < count; i++) {
            skillSparks2D.add(new SkillSpark2D(cx, cy, skillRand));
        }
    }

    private void updateAndRenderSkillSparks(GuiGraphics g, float deltaSec) {
        skillSparks2D.removeIf(s -> !s.update(deltaSec));
        if (skillSparks2D.isEmpty()) {
            return;
        }
        BufferBuilder buffer = beginAdditive();
        Matrix4f mat = g.pose().last().pose();
        for (SkillSpark2D s : skillSparks2D) {
            float progress = s.age / s.maxAge;
            int a = (int) ((float) Math.sin(progress * Math.PI) * 240);
            int gr = (int) (225 - progress * 95); // bright gold to warm ember
            int b = (int) (120 - progress * 100);
            float sz = s.size * (1.0f - progress * 0.35f);
            float x0 = s.x - sz * 0.5f, y0 = s.y - sz * 0.5f, x1 = s.x + sz * 0.5f, y1 = s.y + sz * 0.5f;
            buffer.vertex(mat, x0, y1, 0).color(255, gr, b, a).endVertex();
            buffer.vertex(mat, x1, y1, 0).color(255, gr, b, a).endVertex();
            buffer.vertex(mat, x1, y0, 0).color(255, gr, b, a).endVertex();
            buffer.vertex(mat, x0, y0, 0).color(255, gr, b, a).endVertex();
        }
        endAdditive(buffer);
    }

    private void drawTab(GuiGraphics g, int x, int y, int w, int h, String title, boolean active, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        hoverSound("tab" + title, hover && !active, SoundEvents.NOTE_BLOCK_HAT.value(), 1.8f, 0.25f);
        g.fillGradient(x, y, x + w, y + h, COLOR_PANEL_TOP, COLOR_PANEL_BOTTOM);
        drawBoxOutline(g, x, y, w, h, active ? COLOR_BLOOD_BRIGHT : (hover ? COLOR_PAPER_DIM : COLOR_LINE));
        text(g, title, x + (w - font.width(title)) / 2, y + (h - 8) / 2, active ? COLOR_PAPER : COLOR_PAPER_DIM);
    }

    private void drawGrievanceTab(GuiGraphics g, int mouseX, int mouseY) {
        SinData data = getSinData();
        if (data == null) {
            return;
        }
        int panelW = 236;
        int rightX = (int) VIRTUAL_W - panelW - 10;
        List<CardLine> cardLines = cardLines(data, panelW - 20);
        int lvlH = 44;
        int effH = Math.max(50, 24 + cardLines.size() * 11);
        maxGrievanceScroll = Math.max(0, lvlH + 8 + effH - ((int) VIRTUAL_H - 52));

        int lvlY = 40 - Math.round(grievanceScrollAnim);
        g.fillGradient(rightX, lvlY, rightX + panelW, lvlY + lvlH, COLOR_PANEL_TOP, COLOR_PANEL_BOTTOM);
        drawBoxOutline(g, rightX, lvlY, panelW, lvlH, COLOR_LINE);

        int arrowW = 32;
        int dporCount = getDporCount();
        boolean full = data.getPages() >= SinDefs.get().maxPages;
        boolean hasDpor = dporCount > 0 && !full;
        boolean arrowHover = mouseX >= rightX && mouseX <= rightX + arrowW && mouseY >= lvlY && mouseY <= lvlY + lvlH;
        if (arrowHover && hasDpor) {
            g.fill(rightX, lvlY, rightX + arrowW, lvlY + lvlH, COLOR_ARROW_HOVER);
        }
        drawLine(g, rightX + arrowW, lvlY, rightX + arrowW, lvlY + lvlH, COLOR_LINE);
        text(g, "▲", rightX + 12, lvlY + 18, hasDpor ? (arrowHover ? 0xFFFFFFFF : COLOR_GOLD_BRIGHT) : COLOR_INK);

        SinType sin = data.getChosenSin() != null ? data.getChosenSin() : SinType.WRATH;
        text(g, sin.getNameRu().toUpperCase(), rightX + arrowW + 8, lvlY + 9, COLOR_BLOOD_BRIGHT);
        text(g, "УР. " + data.getSinLevel(), rightX + panelW - 48, lvlY + 9, COLOR_GOLD_BRIGHT);
        text(g, full ? "● Прочитано всё" : hasDpor ? "● Можно прочесть страницу" : "○ Нужна страница", rightX + arrowW + 8, lvlY + 25,
                hasDpor ? COLOR_GOLD_BRIGHT : COLOR_PAPER_DIM);

        int iconSize = 16;
        int iconX = rightX + panelW - iconSize - 6;
        int iconY = lvlY + 20;
        RenderSystem.enableBlend();
        if (hasDpor) {
            g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        } else {
            g.setColor(0.45f, 0.45f, 0.45f, 0.45f);
        }
        g.blit(DPOR_ICON_TEX, iconX, iconY, iconSize, iconSize, 0, 0, 16, 16, 16, 16);
        g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        if (mouseX >= iconX && mouseX <= iconX + iconSize && mouseY >= iconY && mouseY <= iconY + iconSize) {
            tip(iconX, iconY, new ItemStack(SinContent.DPOR.get()).getHoverName().getString(), COLOR_BLOOD,
                    new TipLine("В инвентаре: " + dporCount, COLOR_GOLD_BRIGHT));
            tipLeft = true;
        }

        int effY = lvlY + lvlH + 8;
        g.fillGradient(rightX, effY, rightX + panelW, effY + effH, COLOR_PANEL_TOP, COLOR_PANEL_BOTTOM);
        drawBoxOutline(g, rightX, effY, panelW, effH, COLOR_LINE);
        text(g, "КАРТЫ", rightX + 10, effY + 8, COLOR_PAPER_DIM);
        int curY = effY + 22;
        for (CardLine line : cardLines) {
            g.drawString(font, line.text, rightX + 10 + line.indent, curY, line.color, false);
            curY += 11;
        }
    }

    private record CardLine(FormattedCharSequence text, int indent, int color) {
    }

    /** Every card taken: its name and rank, then what it gives (gold) and what it costs (blood), at its rank. */
    private List<CardLine> cardLines(SinData data, int width) {
        List<CardLine> out = new ArrayList<>();
        SinDefs defs = SinDefs.get();
        for (Map.Entry<String, Integer> e : data.getCards().entrySet()) {
            SinDefs.CardDef card = defs.card(e.getKey());
            if (card == null) {
                continue;
            }
            int rank = e.getValue();
            String title = card.name() + (defs.maxRank > 1 ? " " + roman(rank) : "");
            out.add(new CardLine(Component.literal(title).getVisualOrderText(), 0, card.grace() ? COLOR_GOLD_BRIGHT : COLOR_PAPER));
            for (SinEffect b : card.buffs()) {
                addWrapped(out, "▲ " + effectText(b.scaled(defs.buffScale(rank))), width - 6, 6, COLOR_GOLD);
            }
            for (SinEffect c : card.costs()) {
                addWrapped(out, "▼ " + effectText(c.scaled(defs.costScale(rank))), width - 6, 6, COLOR_BLOOD_BRIGHT);
            }
        }
        if (out.isEmpty()) {
            out.add(new CardLine(Component.literal("Пока ни одной.").getVisualOrderText(), 0, COLOR_PAPER_DIM));
        }
        return out;
    }

    private void addWrapped(List<CardLine> out, String text, int width, int indent, int color) {
        for (FormattedCharSequence l : font.split(Component.literal(text), width)) {
            out.add(new CardLine(l, indent, color));
        }
    }

    private static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
    }

    /** The manual opened in the Manuals tab (its volumes listed under it), -1 for none. */
    private int openManual = -1;
    /** Where the manual headers were drawn last frame (virtual y, with the scroll), for the clicks. */
    private final int[] manualHeaderY = new int[SinDefs.MANUALS];
    private static final int MANUAL_HEADER_H = 32;

    /**
     * The manuals: a header each (icon, name, volumes read, the last one's line); a click opens one in place with all its volumes, each
     * with what it gives: the read ones bright, the next one to read marked, the rest dim. Replaces the big tooltip of every volume.
     */
    private void drawManualsTab(GuiGraphics g, int mouseX, int mouseY) {
        SinData data = getSinData();
        if (data == null) {
            return;
        }
        int panelW = 236;
        int rightX = (int) VIRTUAL_W - panelW - 10;
        String[] bookNames = {"Холодное оружие", "Огнестрел", "Медикаменты", "Взлом", "Инструменты"};
        int[] progress = data.getLoreBooksProgress();
        int textX = rightX + 10 + 16 + 8;
        int textW = panelW - (textX - rightX) - 10;

        // heights first: the open manual's volumes
        List<List<CardLine>> volumes = new ArrayList<>();
        int openH = 0;
        if (openManual >= 0 && openManual < bookNames.length) {
            int read = openManual < progress.length ? progress[openManual] : 0;
            SinDefs.RankDef[] ranks = SinDefs.get().manuals[openManual];
            for (int r = 0; r < SinDefs.MANUAL_RANKS; r++) {
                List<CardLine> lines = new ArrayList<>();
                if (ranks[r] != null) {
                    boolean known = r < read;
                    boolean next = r == read;
                    int textColor = known ? COLOR_PAPER : next ? COLOR_PAPER_DIM : COLOR_INK;
                    int effColor = known ? COLOR_GOLD : next ? nextVolumeColor() : COLOR_INK;
                    addWrapped(lines, ranks[r].text(), textW - 14, 14, textColor);
                    for (SinEffect e : ranks[r].effects()) {
                        addWrapped(lines, "• " + effectText(e), textW - 20, 20, effColor);
                    }
                }
                volumes.add(lines);
                openH += Math.max(1, lines.size()) * 10 + 5;
            }
            openH += 4;
        }
        int bookH = 26 + bookNames.length * MANUAL_HEADER_H + openH;
        maxManualsScroll = Math.max(0, bookH - ((int) VIRTUAL_H - 52));

        int bookY = 40 - Math.round(manualsScrollAnim);
        g.fillGradient(rightX, bookY, rightX + panelW, bookY + bookH, COLOR_PANEL_TOP, COLOR_PANEL_BOTTOM);
        drawBoxOutline(g, rightX, bookY, panelW, bookH, COLOR_LINE);
        text(g, "ИЗУЧЕННЫЕ РУКОВОДСТВА", rightX + 10, bookY + 10, COLOR_PAPER_DIM);

        int y = bookY + 26;
        int iconSize = 16;
        for (int i = 0; i < bookNames.length; i++) {
            manualHeaderY[i] = y;
            int prog = i < progress.length ? progress[i] : 0;
            boolean open = i == openManual;
            boolean hover = mouseX >= rightX && mouseX <= rightX + panelW && mouseY >= y - 2 && mouseY < y + MANUAL_HEADER_H - 2;
            if (hover || open) {
                g.fill(rightX + 1, y - 2, rightX + panelW - 1, y + MANUAL_HEADER_H - 2, open ? 0x22FFFFFF : 0x14FFFFFF);
            }
            int iconX = rightX + 10;
            drawDashedOutline(g, iconX - 1, y - 1, iconSize + 2, iconSize + 2, prog > 0 ? COLOR_GOLD_BRIGHT : COLOR_PAPER_DIM);
            Item icon = SinContent.manualForCategory(i);
            if (icon != null) {
                g.renderItem(new ItemStack(icon), iconX, y);
                if (prog <= 0) {
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 200); // over the item, which renders at z+150
                    g.fill(iconX, y, iconX + iconSize, y + iconSize, 0xB00A0A0A);
                    g.pose().popPose();
                }
            }
            text(g, (open ? "▾ " : "▸ ") + bookNames[i], textX, y + 2, COLOR_PAPER);
            String progStr = prog + " / " + SinDefs.MANUAL_RANKS;
            text(g, progStr, rightX + panelW - 10 - font.width(progStr), y + 2, prog > 0 ? COLOR_GOLD_BRIGHT : COLOR_PAPER_DIM);
            SinDefs.RankDef now = prog > 0 ? SinDefs.get().manuals[i][prog - 1] : null;
            String line = now != null ? now.text() : "Не прочитано ни одного тома.";
            List<FormattedCharSequence> wrapped = font.split(Component.literal(line), textW);
            if (!wrapped.isEmpty()) {
                g.drawString(font, wrapped.get(0), textX, y + 14, now != null ? COLOR_GOLD : COLOR_INK, false);
            }
            y += MANUAL_HEADER_H;

            if (open) {
                int read = prog;
                g.fill(rightX + 10, y - 3, rightX + panelW - 10, y - 2, COLOR_LINE);
                for (int r = 0; r < volumes.size(); r++) {
                    boolean known = r < read;
                    boolean next = r == read;
                    String num = roman(r + 1);
                    int numColor = known ? COLOR_GOLD_BRIGHT : next ? COLOR_PAPER_DIM : COLOR_INK;
                    text(g, num, textX - 2 + (10 - font.width(num)) / 2, y, numColor);
                    if (next) {
                        text(g, "›", textX - 10, y, COLOR_GOLD_BRIGHT); // the one to read next
                    }
                    List<CardLine> lines = volumes.get(r);
                    int ly = y;
                    for (CardLine l : lines) {
                        g.drawString(font, l.text(), textX + l.indent(), ly, l.color(), false);
                        ly += 10;
                    }
                    y += Math.max(1, lines.size()) * 10 + 5;
                }
                y += 4;
            }
        }
    }

    /** The effects of the next volume to read: between the read ones' gold and the far ones' ink. */
    private int nextVolumeColor() {
        return (COLOR_GOLD & 0x00FFFFFF) | 0x99000000;
    }

    /** A click on a manual's header opens it in place (or closes it). */
    private boolean handleManualsClick(int mouseX, int mouseY) {
        int panelW = 236;
        int rightX = (int) VIRTUAL_W - panelW - 10;
        if (mouseX < rightX || mouseX > rightX + panelW) {
            return false;
        }
        for (int i = 0; i < manualHeaderY.length; i++) {
            int y = manualHeaderY[i];
            if (y != 0 && mouseY >= y - 2 && mouseY < y + MANUAL_HEADER_H - 2) {
                openManual = openManual == i ? -1 : i;
                playUi(SoundEvents.BOOK_PAGE_TURN, 0.8f, 1.0f);
                return true;
            }
        }
        return false;
    }

    // Form tab (port of the 1.12 Appearance tab): theme, gender and the breast / physics settings of the built-in gender system
    private static final int FORM_BTN_H = 18;
    private static final int FORM_BTN_STEP = 22;

    /** One line of the Form tab: a button, or a slider when {@code max > min}. */
    private static final class FormRow {
        final java.util.function.Supplier<String> label;
        final Runnable onClick;
        final float min, max;
        final java.util.function.Supplier<Float> get;
        final java.util.function.Consumer<Float> set;
        final Runnable onSave;

        FormRow(java.util.function.Supplier<String> label, Runnable onClick) {
            this(label, onClick, 0, 0, null, null, null);
        }

        FormRow(java.util.function.Supplier<String> label, Runnable onClick, float min, float max, java.util.function.Supplier<Float> get,
                java.util.function.Consumer<Float> set, Runnable onSave) {
            this.label = label;
            this.onClick = onClick;
            this.min = min;
            this.max = max;
            this.get = get;
            this.set = set;
            this.onSave = onSave;
        }

        boolean isSlider() {
            return max > min;
        }

        float fraction() {
            return Math.max(0f, Math.min(1f, (get.get() - min) / (max - min)));
        }
    }

    private int appearanceScrollY, maxAppearanceScroll;
    private float appearanceScrollAnim;
    private int draggingFormRow = -1;
    private boolean formChanged;
    /** The eye editor (blinking) is open: the model turns its face to the screen and shows where the eyes and lids are. */
    private boolean blinkEditorOpen;
    private int[] blinkAccepted;
    private float faceYaw = -20.0f;

    private int formButtonY(int index) {
        return 46 + index * FORM_BTN_STEP - Math.round(appearanceScrollAnim);
    }

    private static String onOff(boolean on) {
        return on ? ChatFormatting.GREEN + "ВКЛ" : ChatFormatting.RED + "ВЫКЛ";
    }

    private static String percent(float v) {
        return Math.round(v * 100) + "%";
    }

    private List<FormRow> formRows() {
        List<FormRow> rows = new ArrayList<>();
        rows.add(new FormRow(() -> "Тема интерфейса: " + (SevenConfig.WARM_THEME.get() ? ChatFormatting.GOLD + "Оранжевая (Старая)" : ChatFormatting.AQUA + "Тёмно-серая"),
                () -> {
                    boolean warm = !SevenConfig.WARM_THEME.get();
                    SevenConfig.WARM_THEME.set(warm);
                    SevenConfig.WARM_THEME.save();
                    applyTheme(warm);
                }));
        GenderPlayer plr = minecraft != null && minecraft.player != null ? WildfireGender.getPlayerById(minecraft.player.getUUID()) : null;
        if (plr == null) {
            return rows;
        }
        Breasts breasts = plr.getBreasts();
        Runnable save = () -> GenderPlayer.saveGenderInfo(plr);
        rows.add(new FormRow(() -> "Пол: " + plr.getGender().getDisplayName().getString(), () -> {
            GenderPlayer.Gender next = switch (plr.getGender()) {
                case MALE -> GenderPlayer.Gender.FEMALE;
                case FEMALE -> GenderPlayer.Gender.OTHER;
                default -> GenderPlayer.Gender.MALE;
            };
            if (plr.updateGender(next)) {
                save.run();
            }
        }));
        rows.add(new FormRow(() -> "Размер бюста: " + percent(plr.getBustSize()), null, ClientConfiguration.BUST_SIZE.getMinInclusive(),
                ClientConfiguration.BUST_SIZE.getMaxInclusive(), plr::getBustSize, plr::updateBustSize, save));
        rows.add(new FormRow(() -> "Расстояние (X): " + String.format("%.1f", breasts.getXOffset() * 10), null, ClientConfiguration.BREASTS_OFFSET_X.getMinInclusive(),
                ClientConfiguration.BREASTS_OFFSET_X.getMaxInclusive(), breasts::getXOffset, breasts::updateXOffset, save));
        rows.add(new FormRow(() -> "Высота (Y): " + String.format("%.1f", breasts.getYOffset() * 10), null, ClientConfiguration.BREASTS_OFFSET_Y.getMinInclusive(),
                ClientConfiguration.BREASTS_OFFSET_Y.getMaxInclusive(), breasts::getYOffset, breasts::updateYOffset, save));
        rows.add(new FormRow(() -> "Глубина (Z): " + String.format("%.1f", breasts.getZOffset() * 10), null, ClientConfiguration.BREASTS_OFFSET_Z.getMinInclusive(),
                ClientConfiguration.BREASTS_OFFSET_Z.getMaxInclusive(), breasts::getZOffset, breasts::updateZOffset, save));
        rows.add(new FormRow(() -> "Поворот / Ложбинка: " + percent(breasts.getCleavage()), null, ClientConfiguration.BREASTS_CLEAVAGE.getMinInclusive(),
                ClientConfiguration.BREASTS_CLEAVAGE.getMaxInclusive(), breasts::getCleavage, breasts::updateCleavage, save));
        rows.add(new FormRow(() -> "Физика тела: " + onOff(plr.hasBreastPhysics()), () -> {
            if (plr.updateBreastPhysics(!plr.hasBreastPhysics())) {
                save.run();
            }
        }));
        rows.add(new FormRow(() -> "Физика брони: " + onOff(plr.getArmorPhysicsOverride()), () -> {
            if (plr.updateArmorPhysicsOverride(!plr.getArmorPhysicsOverride())) {
                save.run();
            }
        }));
        rows.add(new FormRow(() -> "Упругость: " + percent(plr.getBounceMultiplierRaw()), null, ClientConfiguration.BOUNCE_MULTIPLIER.getMinInclusive(),
                ClientConfiguration.BOUNCE_MULTIPLIER.getMaxInclusive(), plr::getBounceMultiplierRaw, plr::updateBounceMultiplier, save));
        rows.add(new FormRow(() -> "Инерция: " + percent(plr.getFloppiness()), null, ClientConfiguration.FLOPPY_MULTIPLIER.getMinInclusive(),
                ClientConfiguration.FLOPPY_MULTIPLIER.getMaxInclusive(), plr::getFloppiness, plr::updateFloppiness, save));
        rows.add(new FormRow(() -> "Спаренная физика: " + (breasts.isUniboob() ? ChatFormatting.RED + "НЕТ" : ChatFormatting.GREEN + "ДА"), () -> {
            if (breasts.updateUniboob(!breasts.isUniboob())) {
                save.run();
            }
        }));
        rows.add(new FormRow(() -> "Грудь в броне: " + onOff(plr.showBreastsInArmor()), () -> {
            if (plr.updateShowBreastsInArmor(!plr.showBreastsInArmor())) {
                save.run();
            }
        }));
        rows.add(new FormRow(() -> "Женские звуки урона: " + onOff(plr.hasHurtSounds()), () -> {
            if (plr.updateHurtSounds(!plr.hasHurtSounds())) {
                save.run();
            }
        }));
        blinkRows(rows, plr, save);
        return rows;
    }

    /** Blinking eyes (mwccf): a switch that opens the editor, the editor rows under it while it is open. */
    private void blinkRows(List<FormRow> rows, GenderPlayer plr, Runnable save) {
        if (blinkAccepted == null && plr.isBlinkEnabled()) {
            blinkAccepted = blinkSnapshot(plr);
        }
        rows.add(new FormRow(() -> "Моргание глаз: " + onOff(plr.isBlinkEnabled()) + ChatFormatting.RESET + (blinkEditorOpen ? "  \u25BE" : "  \u25B8"), () -> {
            if (blinkEditorOpen) {
                blinkEditorOpen = false;
                return;
            }
            blinkEditorOpen = true;
            if (!plr.isBlinkEnabled() && plr.updateBlinking(true)) {
                save.run();
            }
            blinkAccepted = blinkSnapshot(plr);
        }));
        if (!blinkEditorOpen) {
            return;
        }
        rows.add(new FormRow(() -> "  Размер глаз: " + sizeName(plr.getEyeSize()), null, 0, 2, () -> (float) plr.getEyeSize(), v -> {
            if (plr.updateEyeSize(Math.round(v)) && plr.getEyelidSize() > plr.getEyeSize()) {
                plr.updateEyelidSize(plr.getEyeSize());
            }
        }, save));
        rows.add(new FormRow(() -> "  Расстояние: " + plr.getEyeDistance() * 2 + " px", null, 1, 4, () -> (float) plr.getEyeDistance(),
                v -> plr.updateEyeDistance(Math.round(v)), save));
        rows.add(new FormRow(() -> "  Высота: " + plr.getEyeHeight(), null, 0, 8, () -> (float) plr.getEyeHeight(),
                v -> plr.updateEyeHeight(Math.round(v)), save));
        if (plr.getEyeSize() > 0) {
            rows.add(new FormRow(() -> "  Размер века: " + sizeName(plr.getEyelidSize()), null, 0, plr.getEyeSize(),
                    () -> (float) plr.getEyelidSize(), v -> plr.updateEyelidSize(Math.round(v)), save));
        } else {
            rows.add(new FormRow(() -> "  Размер века: " + sizeName(0), () -> {
            }));
        }
        rows.add(new FormRow(() -> "  Режим век: " + (plr.isDualEyelid() ? "2 участка" : "Инверсия (1)"), () -> {
            if (plr.updateDualEyelid(!plr.isDualEyelid())) {
                save.run();
            }
        }));
        rows.add(new FormRow(() -> "  Смещение века (X): " + plr.getEyelidOffsetX(), null, -8, 8, () -> (float) plr.getEyelidOffsetX(),
                v -> plr.updateEyelidOffsetX(Math.round(v)), save));
        rows.add(new FormRow(() -> "  Смещение века (Y): " + (plr.getEyelidOffsetY() > 0 ? "+" : "") + plr.getEyelidOffsetY(), null, -8, 8,
                () -> (float) plr.getEyelidOffsetY(), v -> plr.updateEyelidOffsetY(Math.round(v)), save));
        rows.add(new FormRow(() -> {
            int pct = Math.round((plr.getBlinkFrequency() - 1.0f) * 100);
            return "  Частота: " + (pct >= 0 ? "+" : "") + pct + "%";
        }, null, 0.8f, 1.35f, plr::getBlinkFrequency, plr::updateBlinkFrequency, save));
        // accept the changes, or switch blinking off when nothing was changed
        rows.add(new FormRow(() -> "  " + (blinkDirty(plr) ? "Принять" : "Выключить"), () -> {
            if (blinkDirty(plr)) {
                blinkAccepted = blinkSnapshot(plr);
            } else {
                plr.updateBlinking(false);
                save.run();
                blinkAccepted = null;
            }
            blinkEditorOpen = false;
        }));
    }

    private static String sizeName(int size) {
        return size == 0 ? "1x1" : size == 1 ? "2x1" : "2x2";
    }

    private static int[] blinkSnapshot(GenderPlayer plr) {
        return new int[] {plr.getEyeSize(), plr.getEyeDistance(), plr.getEyeHeight(), plr.getEyelidSize(), plr.isDualEyelid() ? 1 : 0,
                plr.getEyelidOffsetX(), plr.getEyelidOffsetY(), Math.round(plr.getBlinkFrequency() * 100)};
    }

    private boolean blinkDirty(GenderPlayer plr) {
        return blinkAccepted == null || !java.util.Arrays.equals(blinkSnapshot(plr), blinkAccepted);
    }

    private void drawAppearanceTab(GuiGraphics g, int mouseX, int mouseY) {
        int panelW = 236;
        int x = (int) VIRTUAL_W - panelW - 10 + 8;
        int w = panelW - 16;
        List<FormRow> rows = formRows();
        maxAppearanceScroll = Math.max(0, rows.size() * FORM_BTN_STEP - ((int) VIRTUAL_H - 46 - 10));
        appearanceScrollY = clampScroll(appearanceScrollY, maxAppearanceScroll);
        for (int i = 0; i < rows.size(); i++) {
            FormRow row = rows.get(i);
            int y = formButtonY(i);
            if (y + FORM_BTN_H < 38 || y > (int) VIRTUAL_H - 8) {
                continue;
            }
            if (row.isSlider()) {
                drawFormSlider(g, x, y, w, row, mouseX, mouseY, i == draggingFormRow);
            } else {
                drawFormButton(g, x, y, w, row.label.get(), mouseX, mouseY, true);
            }
        }
        if (rows.size() == 1) {
            text(g, "Настройки тела доступны в мире", x, formButtonY(1) + 4, COLOR_PAPER_DIM);
        }
    }

    private void drawFormButton(GuiGraphics g, int x, int y, int w, String label, int mouseX, int mouseY, boolean enabled) {
        boolean hover = enabled && mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + FORM_BTN_H && mouseY >= 38;
        g.fillGradient(x, y, x + w, y + FORM_BTN_H, COLOR_PANEL_TOP, COLOR_PANEL_BOTTOM);
        drawBoxOutline(g, x, y, w, FORM_BTN_H, hover ? COLOR_GOLD_BRIGHT : COLOR_LINE);
        text(g, label, x + (w - font.width(label)) / 2, y + 5, enabled ? (hover ? COLOR_GOLD_BRIGHT : COLOR_PAPER) : COLOR_INK);
    }

    private void drawFormSlider(GuiGraphics g, int x, int y, int w, FormRow row, int mouseX, int mouseY, boolean dragging) {
        boolean hover = dragging || (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + FORM_BTN_H && mouseY >= 38);
        g.fillGradient(x, y, x + w, y + FORM_BTN_H, COLOR_PANEL_TOP, COLOR_PANEL_BOTTOM);
        int fillTo = x + 2 + Math.round(row.fraction() * (w - 4));
        g.fill(x + 2, y + 2, fillTo, y + FORM_BTN_H - 2, (COLOR_GOLD & 0x00FFFFFF) | 0x66000000);
        g.fill(fillTo - 1, y + 1, fillTo + 1, y + FORM_BTN_H - 1, COLOR_GOLD_BRIGHT);
        drawBoxOutline(g, x, y, w, FORM_BTN_H, hover ? COLOR_GOLD_BRIGHT : COLOR_LINE);
        String label = row.label.get();
        text(g, label, x + (w - font.width(label)) / 2, y + 5, hover ? COLOR_GOLD_BRIGHT : COLOR_PAPER);
    }

    private void setSliderFromMouse(FormRow row, int mouseX) {
        int panelW = 236;
        int x = (int) VIRTUAL_W - panelW - 10 + 8;
        int w = panelW - 16;
        float t = Math.max(0f, Math.min(1f, (mouseX - (x + 4)) / (float) (w - 8)));
        float value = row.min + t * (row.max - row.min);
        if (Math.abs(value - row.get.get()) > 1e-4f) {
            row.set.accept(value);
            formChanged = true;
        }
        // a soft tick every twentieth of the slider, higher towards its right end
        int step = Math.round(t * 20);
        if (step != lastSliderStep) {
            lastSliderStep = step;
            playUi(SoundEvents.NOTE_BLOCK_HAT.value(), 0.8f + t * 0.8f, 0.2f);
        }
    }

    private void handleAppearanceClick(int mouseX, int mouseY) {
        int panelW = 236;
        int x = (int) VIRTUAL_W - panelW - 10 + 8;
        int w = panelW - 16;
        if (mouseX < x || mouseX > x + w || mouseY < 38) {
            return;
        }
        List<FormRow> rows = formRows();
        for (int i = 0; i < rows.size(); i++) {
            int y = formButtonY(i);
            if (mouseY < y || mouseY > y + FORM_BTN_H) {
                continue;
            }
            FormRow row = rows.get(i);
            if (row.isSlider()) {
                lastSliderStep = -1;
                draggingFormRow = i;
                formChanged = false;
                setSliderFromMouse(row, mouseX);
            } else {
                row.onClick.run();
                playUi(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f);
            }
            return;
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (draggingFormRow >= 0 && currentTab == TAB_APPEARANCE) {
            List<FormRow> rows = formRows();
            if (draggingFormRow < rows.size()) {
                setSliderFromMouse(rows.get(draggingFormRow), (int) (mouseX / uiScaleX));
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingFormRow >= 0) {
            List<FormRow> rows = formRows();
            if (formChanged && draggingFormRow < rows.size() && rows.get(draggingFormRow).onSave != null) {
                rows.get(draggingFormRow).onSave.run();
            }
            draggingFormRow = -1;
            formChanged = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // ===================================================================== 3. level-up flow
    private void drawLevelUpFlow(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        long now = System.nanoTime();
        long phaseElapsed = (now - levelUpPhaseStartTime) / 1_000_000L;
        switch (levelUpPhase) {
            case LVLUP_PHASE_ENTER -> {
                float t = easeOutCubic(phaseElapsed / (float) LVLUP_ENTER_DURATION_MS);
                drawMainHudScreen(g, mouseX, mouseY, partialTicks, -VIRTUAL_W * t, VIRTUAL_W * t);
                if (phaseElapsed >= LVLUP_ENTER_DURATION_MS) {
                    levelUpPhase = LVLUP_PHASE_CARDS;
                    levelUpAnimStartTime = now;
                    playUi(SinContent.MENU_SWOOSH.get(), 1.0f);
                }
            }
            case LVLUP_PHASE_CARDS -> drawLevelUpCardsListing(g, mouseX, mouseY, (now - levelUpAnimStartTime) / 1_000_000L);
            case LVLUP_PHASE_SELECT -> {
                drawLevelUpSelection(g, Math.min(phaseElapsed, 600L) / 600.0f);
                if (phaseElapsed >= LVLUP_SELECT_DURATION_MS) {
                    levelUpPhase = LVLUP_PHASE_EXIT;
                    levelUpPhaseStartTime = now;
                    playUi(SinContent.MENU_SWOOSH_INVERT.get(), 1.0f);
                }
            }
            case LVLUP_PHASE_EXIT -> {
                drawLevelUpExit(g, easeInCubic(phaseElapsed / (float) LVLUP_EXIT_DURATION_MS));
                if (phaseElapsed >= LVLUP_EXIT_DURATION_MS) {
                    levelUpPhase = LVLUP_PHASE_RETURN;
                    levelUpPhaseStartTime = now;
                }
            }
            case LVLUP_PHASE_RETURN -> {
                float t = easeOutCubic(phaseElapsed / (float) LVLUP_RETURN_DURATION_MS);
                drawMainHudScreen(g, mouseX, mouseY, partialTicks, -VIRTUAL_W * (1.0f - t), VIRTUAL_W * (1.0f - t));
                if (phaseElapsed >= LVLUP_RETURN_DURATION_MS) {
                    currentState = STATE_MAIN_HUD;
                    levelUpPhase = LVLUP_PHASE_ENTER;
                    offeredCards = new ArrayList<>();
                    selectedCardIndex = -1;
                }
            }
            default -> {
            }
        }
    }

    private static final int LVLUP_CARD_W = 150;
    private static final int LVLUP_CARD_H = 220;
    private static final int LVLUP_CARD_GAP = 20;

    private int lvlUpCardBaseX(int index) {
        return ((int) VIRTUAL_W - (3 * LVLUP_CARD_W + 2 * LVLUP_CARD_GAP)) / 2 + index * (LVLUP_CARD_W + LVLUP_CARD_GAP);
    }

    private int lvlUpCardBaseY() {
        return ((int) VIRTUAL_H - LVLUP_CARD_H) / 2 + 10;
    }

    private void drawLevelUpCardsListing(GuiGraphics g, int mouseX, int mouseY, long elapsed) {
        float titleFade = clamp01((elapsed - LVLUP_CARD_FLIGHT_MS) / (float) LVLUP_TITLE_FADE_MS);
        if (titleFade > 0.0f) {
            String title = "СТРАНИЦА ОТКРОВЕНИЯ " + (getSinData() == null ? "" : getSinData().getPages() + 1);
            int alphaByte = Math.max(4, (int) (titleFade * 255.0f));
            text(g, title, ((int) VIRTUAL_W - font.width(title)) / 2, 14, (alphaByte << 24) | (COLOR_GOLD_BRIGHT & 0x00FFFFFF));
        }
        int targetY = lvlUpCardBaseY();
        for (int i = 0; i < offeredCards.size() && i < levelUpCardHoverLift.length; i++) {
            int cx = lvlUpCardBaseX(i);
            float progress = clamp01(elapsed / (float) LVLUP_CARD_FLIGHT_MS);
            int flightY = (int) (-300 + (targetY + 300) * easeOutCubic(progress));
            boolean landed = progress >= 0.999f;
            boolean isHovered = landed && mouseX >= cx && mouseX <= cx + LVLUP_CARD_W && mouseY >= flightY && mouseY <= flightY + LVLUP_CARD_H;
            hoverSound("card" + i, isHovered, SoundEvents.BOOK_PAGE_TURN, 1.2f, 0.5f);
            float targetLift = isHovered ? 4.0f : 0.0f;
            levelUpCardHoverLift[i] += (targetLift - levelUpCardHoverLift[i]) * Math.min(1.0f, currentDeltaSec * 15.0f);
            if (Math.abs(levelUpCardHoverLift[i] - targetLift) < 0.05f) {
                levelUpCardHoverLift[i] = targetLift;
            }
            int cy = landed ? Math.round(targetY - levelUpCardHoverLift[i]) : flightY;
            drawSingleCard(g, i, offeredCards.get(i), cx, cy, isHovered, -1.0f);
        }
    }

    private void drawLevelUpSelection(GuiGraphics g, float t) {
        float ease = easeOutCubic(t);
        int targetY = lvlUpCardBaseY();
        for (int i = 0; i < offeredCards.size(); i++) {
            if (i != selectedCardIndex) {
                drawSingleCard(g, i, offeredCards.get(i), lvlUpCardBaseX(i), targetY - (int) ((LVLUP_CARD_H + targetY + 60) * ease), false, -1.0f);
            }
        }
        if (selectedCardIndex >= 0 && selectedCardIndex < offeredCards.size()) {
            int baseX = lvlUpCardBaseX(selectedCardIndex);
            float scale = 1.0f + (LVLUP_SELECTED_SCALE - 1.0f) * ease;
            float centerX = baseX + LVLUP_CARD_W / 2.0f + (VIRTUAL_W / 2.0f - (baseX + LVLUP_CARD_W / 2.0f)) * ease;
            float centerY = targetY + LVLUP_CARD_H / 2.0f + (VIRTUAL_H / 2.0f - (targetY + LVLUP_CARD_H / 2.0f)) * ease;
            PoseStack pose = g.pose();
            pose.pushPose();
            pose.translate(centerX, centerY, 0.0f);
            pose.scale(scale, scale, 1.0f);
            pose.translate(-LVLUP_CARD_W / 2.0f, -LVLUP_CARD_H / 2.0f, 0.0f);
            drawSingleCard(g, selectedCardIndex, offeredCards.get(selectedCardIndex), 0, 0, false, t);
            pose.popPose();
        }
    }

    private void drawLevelUpExit(GuiGraphics g, float t) {
        if (selectedCardIndex < 0 || selectedCardIndex >= offeredCards.size()) {
            return;
        }
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(VIRTUAL_W / 2.0f, VIRTUAL_H / 2.0f + t * (VIRTUAL_H * 1.3f), 0.0f);
        pose.scale(LVLUP_SELECTED_SCALE, LVLUP_SELECTED_SCALE, 1.0f);
        pose.translate(-LVLUP_CARD_W / 2.0f, -LVLUP_CARD_H / 2.0f, 0.0f);
        drawSingleCard(g, selectedCardIndex, offeredCards.get(selectedCardIndex), 0, 0, false, -1.0f);
        pose.popPose();
    }

    private void drawSingleCard(GuiGraphics g, int index, String cardId, int cx, int cy, boolean isHovered, float borderGlowT) {
        g.fillGradient(cx, cy, cx + LVLUP_CARD_W, cy + LVLUP_CARD_H, COLOR_CARD_BG_TOP, COLOR_CARD_BG_BOTTOM);
        drawBoxOutline(g, cx, cy, LVLUP_CARD_W, LVLUP_CARD_H, isHovered ? COLOR_GOLD_BRIGHT : COLOR_BLOOD);
        if (borderGlowT >= 0.0f && borderGlowT < 1.0f) {
            drawPerimeterGlow(g, cx, cy, LVLUP_CARD_W, LVLUP_CARD_H, borderGlowT);
        }
        SinDefs defs = SinDefs.get();
        SinDefs.CardDef card = defs.card(cardId);
        int rank = offeredRanks.getOrDefault(cardId, 1);
        String head = card != null && card.grace() ? "БЛАГОДАТЬ" : card != null && card.sin() == null ? "ОБЩАЯ" : "ГРЕХ";
        text(g, head, cx + 12, cy + 10, card != null && card.grace() ? COLOR_GOLD_BRIGHT : COLOR_PAPER_DIM);
        String rankStr = rank > 1 ? "РАНГ " + roman(rank) : "НОВАЯ";
        text(g, rankStr, cx + LVLUP_CARD_W - 12 - font.width(rankStr), cy + 10, rank > 1 ? COLOR_GOLD_BRIGHT : COLOR_PAPER_DIM);
        if (card == null) {
            text(g, cardId, cx + 12, cy + 30, COLOR_BLOOD_BRIGHT);
            return;
        }
        int w = LVLUP_CARD_W - 24;
        int y = cy + 26;
        for (FormattedCharSequence l : font.split(Component.literal(card.name()).withStyle(ChatFormatting.BOLD), w)) {
            g.drawString(font, l, cx + 12, y, COLOR_GOLD_BRIGHT, false);
            y += 10;
        }
        y += 6;
        int bottom = cy + LVLUP_CARD_H - 28;
        for (SinEffect b : card.buffs()) {
            y = drawCardLines(g, "▲ " + effectText(b.scaled(defs.buffScale(rank))), cx + 12, y, w, COLOR_GOLD, bottom);
        }
        if (!card.costs().isEmpty()) {
            y += 4;
        }
        for (SinEffect c : card.costs()) {
            y = drawCardLines(g, "▼ " + effectText(c.scaled(defs.costScale(rank))), cx + 12, y, w, COLOR_BLOOD_BRIGHT, bottom);
        }
        if (!card.text(knowsDoll()).isEmpty()) {
            y += 6;
            drawCardLines(g, card.text(knowsDoll()), cx + 12, y, w, COLOR_PAPER_DIM, bottom);
        }
        String prompt = isHovered ? ChatFormatting.YELLOW + "▶ ВЫБРАТЬ ◀" : "нажмите, чтобы принять";
        text(g, prompt, cx + (LVLUP_CARD_W - font.width(prompt)) / 2, cy + LVLUP_CARD_H - 20, isHovered ? COLOR_GOLD_BRIGHT : COLOR_PAPER_DIM);
    }

    /** Wrapped text inside a card, cut at the bottom; returns the next free y. */
    private int drawCardLines(GuiGraphics g, String text, int x, int y, int width, int color, int bottom) {
        for (FormattedCharSequence l : font.split(Component.literal(text), width)) {
            if (y > bottom) {
                return y;
            }
            g.drawString(font, l, x, y, color, false);
            y += 10;
        }
        return y + 2;
    }

    private void drawPerimeterGlow(GuiGraphics g, int x, int y, int w, int h, float t) {
        float loopT = t - (float) Math.floor(t);
        float dist = loopT * 2.0f * (w + h);
        int chip = 10;
        int thickness = 3;
        int c = COLOR_PERIMETER_GLOW;
        if (dist < w) {
            int px = x + (int) dist;
            g.fill(px, y - thickness / 2, Math.min(px + chip, x + w), y + thickness / 2 + 1, c);
        } else if (dist < w + h) {
            int py = y + (int) (dist - w);
            g.fill(x + w - thickness / 2, py, x + w + thickness / 2 + 1, Math.min(py + chip, y + h), c);
        } else if (dist < 2 * w + h) {
            int px = x + w - (int) (dist - w - h);
            g.fill(Math.max(px - chip, x), y + h - thickness / 2, px, y + h + thickness / 2 + 1, c);
        } else {
            int py = y + h - (int) (dist - 2 * w - h);
            g.fill(x - thickness / 2, Math.max(py - chip, y), x + thickness / 2 + 1, py, c);
        }
    }

    private static float easeOutCubic(float t) {
        float c = clamp01(t);
        return 1.0f - (float) Math.pow(1.0f - c, 3);
    }

    private static float easeInCubic(float t) {
        float c = clamp01(t);
        return c * c * c;
    }

    private static float clamp01(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
    }

    // ===================================================================== input
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int vX = (int) (mouseX / uiScaleX);
        int vY = (int) (mouseY / uiScaleY);
        if (button == 0) {
            if (currentState == STATE_CLASS_SELECT) {
                handleClassSelectClick(vX, vY);
            } else if (currentState == STATE_MAIN_HUD) {
                handleMainHudClick(vX, vY);
            } else if (currentState == STATE_LEVEL_UP) {
                handleLevelUpClick(vX, vY);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void handleClassSelectClick(int mouseX, int mouseY) {
        if (classSelectPhase != CLASS_PHASE_LISTING) {
            return; // only while the grid is up
        }
        SinType[] sins = SinType.values();
        for (int i = 0; i < sins.length; i++) {
            int cx = sinCardBaseX(i);
            // hit box at the card's rendered (hover-lifted) position
            int drawY = Math.round(sinCardBaseY(i) - sinCardHoverLift[i]);
            int infoX = cx + 5;
            int infoY = drawY + 5;
            if (mouseX >= infoX && mouseX <= infoX + 11 && mouseY >= infoY && mouseY <= infoY + 11) {
                return; // the info icon only shows the tooltip
            }
            if (mouseX >= cx && mouseX <= cx + SIN_CARD_W && mouseY >= drawY && mouseY <= drawY + SIN_CARD_H) {
                SinNetwork.sendToServer(new SinNetwork.SelectSin(sins[i].getId()));
                playCardChosen();
                lastKnownChosenSin = sins[i];
                selectedSinIndex = i;
                classSelectPhase = CLASS_PHASE_SELECT;
                classSelectPhaseStartTime = System.nanoTime();
                return;
            }
        }
    }

    private void handleMainHudClick(int mouseX, int mouseY) {
        int panelW = 236;
        int rightX = (int) VIRTUAL_W - panelW - 10;
        int tabGap = 3;
        int tabW = (panelW - tabGap * 3) / 4;
        int tabH = 18;
        int tabY = 16;
        if (mouseY >= tabY && mouseY <= tabY + tabH) {
            for (int t = 0; t < 4; t++) {
                int tx = rightX + (tabW + tabGap) * t;
                if (mouseX >= tx && mouseX <= tx + tabW) {
                    setTab(t);
                    return;
                }
            }
        }
        if (currentTab == TAB_GRIEVANCE) {
            int lvlY = 40 - Math.round(grievanceScrollAnim);
            if (mouseX >= rightX && mouseX <= rightX + 32 && mouseY >= lvlY && mouseY <= lvlY + 44) {
                if (getDporCount() > 0 || (minecraft.player != null && minecraft.player.isCreative())) {
                    SinNetwork.sendToServer(new SinNetwork.LevelUpRequest());
                    // a dark charge rising, a chime on top: the grievance grows (not the vanilla level ding)
                    playUi(SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.75f, 0.55f);
                    playUi(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6f, 0.45f);
                } else {
                    playDenied();
                }
            }
        } else if (currentTab == TAB_SKILLS) {
            handleSkillsClick(mouseX, mouseY);
        } else if (currentTab == TAB_APPEARANCE) {
            handleAppearanceClick(mouseX, mouseY);
        } else if (currentTab == TAB_MANUALS) {
            handleManualsClick(mouseX, mouseY);
        }
    }

    private void handleSkillsClick(int mouseX, int mouseY) {
        int panelW = 236;
        int contentX = (int) VIRTUAL_W - panelW - 10 + 12;
        int contentW = panelW - 24;
        int treeTop = 40 + 10 - Math.round(skillsScrollAnim) + 11 + 10;
        int colW = contentW / SKILL_BRANCHES;
        int rootX = contentX + contentW / 2;
        int rootY = treeTop + SKILL_NODE_SIZE / 2;
        if (isPointInSkillNode(mouseX, mouseY, rootX, rootY, SKILL_NODES[0])) {
            tryUnlockSkill(SKILL_NODES[0]);
            return;
        }
        for (int b = 0; b < SKILL_BRANCHES; b++) {
            int colX = contentX + colW * b + colW / 2;
            for (int d = 1; d <= SKILLS_PER_BRANCH; d++) {
                SkillNode node = findSkillNode(b, d);
                int nodeY = treeTop + d * SKILL_ROW_GAP + SKILL_NODE_SIZE / 2;
                if (isPointInSkillNode(mouseX, mouseY, colX, nodeY, node)) {
                    tryUnlockSkill(node);
                    return;
                }
            }
        }
    }

    private void handleLevelUpClick(int mouseX, int mouseY) {
        if (levelUpPhase != LVLUP_PHASE_CARDS) {
            return;
        }
        int cy = lvlUpCardBaseY();
        for (int i = 0; i < offeredCards.size(); i++) {
            int cx = lvlUpCardBaseX(i);
            if (mouseX >= cx && mouseX <= cx + LVLUP_CARD_W && mouseY >= cy && mouseY <= cy + LVLUP_CARD_H) {
                SinNetwork.sendToServer(new SinNetwork.AcceptCard(i));
                playCardChosen();
                selectedCardIndex = i;
                levelUpPhase = LVLUP_PHASE_SELECT;
                levelUpPhaseStartTime = System.nanoTime();
                return;
            }
        }
    }

    // ===================================================================== drawing helpers
    private void text(GuiGraphics g, String s, int x, int y, int color) {
        g.drawString(font, s, x, y, color, false);
    }

    private void tip(int anchorX, int anchorY, String title, int border, TipLine... lines) {
        tipTitle = title;
        tipLines = new ArrayList<>(List.of(lines));
        tipX = anchorX;
        tipY = anchorY;
        tipBorder = border;
        tipLeft = false;
    }

    /**
     * A tooltip next to the anchor: on its right when it fits, else on its left; never out of the screen, every line wrapped.
     * An empty line is a small gap.
     */
    private void drawTooltip(GuiGraphics g, int anchorX, int anchorY, String title, List<TipLine> lines, int border) {
        drawTooltip(g, anchorX, anchorY, title, lines, border, false);
    }

    /** {@code preferLeft}: opens on the left of the anchor unless there is no room there (for things on the right panel). */
    private void drawTooltip(GuiGraphics g, int anchorX, int anchorY, String title, List<TipLine> lines, int border, boolean preferLeft) {
        int maxText = 196;
        int pad = 6;
        int lineH = font.lineHeight + 1;
        List<FormattedCharSequence> titleLines = font.split(Component.literal(title).withStyle(ChatFormatting.BOLD), maxText);
        List<FormattedCharSequence> body = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        for (TipLine line : lines) {
            if (line.text().isEmpty()) {
                body.add(null);
                colors.add(0);
                continue;
            }
            for (FormattedCharSequence l : font.split(Component.literal(line.text()), maxText)) {
                body.add(l);
                colors.add(line.color());
            }
        }
        int textW = 0;
        for (FormattedCharSequence l : titleLines) {
            textW = Math.max(textW, font.width(l));
        }
        int bodyH = 0;
        for (FormattedCharSequence l : body) {
            textW = l == null ? textW : Math.max(textW, font.width(l));
            bodyH += l == null ? 4 : lineH;
        }
        int w = textW + pad * 2;
        int h = pad * 2 + titleLines.size() * lineH + (body.isEmpty() ? 0 : 4 + bodyH);
        int x = preferLeft ? anchorX - 12 - w : anchorX + 12;
        if (preferLeft ? x < 4 : x + w > VIRTUAL_W - 4) {
            x = preferLeft ? anchorX + 12 : anchorX - 12 - w;
        }
        x = Math.max(4, Math.min(x, (int) VIRTUAL_W - 4 - w));
        int y = Math.max(4, Math.min(anchorY - 4, (int) VIRTUAL_H - 4 - h));
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(0, 0, 300);
        g.fill(x, y, x + w, y + h, 0xF7050505);
        drawBoxOutline(g, x, y, w, h, border);
        int ty = y + pad;
        for (FormattedCharSequence l : titleLines) {
            g.drawString(font, l, x + pad, ty, COLOR_GOLD_BRIGHT, false);
            ty += lineH;
        }
        ty += 4;
        for (int i = 0; i < body.size(); i++) {
            if (body.get(i) == null) {
                ty += 4;
                continue;
            }
            g.drawString(font, body.get(i), x + pad, ty, colors.get(i), false);
            ty += lineH;
        }
        pose.popPose();
    }

    /** The doll is a secret until the player has held it. */
    private boolean knowsDoll() {
        SinData data = getSinData();
        return data != null && data.knowsDoll();
    }

    /** The sentence of an effect, or the veil over it while the doll is unknown. */
    private String effectText(SinEffect e) {
        return e.isAboutDoll() && !knowsDoll() ? SinEffect.VEILED : e.describe();
    }

    private void drawBoxOutline(GuiGraphics g, int x, int y, int w, int h, int color) {
        BufferBuilder b = beginLines();
        Matrix4f m = g.pose().last().pose();
        lineVerts(b, m, x, y, x + w, y, color);
        lineVerts(b, m, x + w, y, x + w, y + h, color);
        lineVerts(b, m, x + w, y + h, x, y + h, color);
        lineVerts(b, m, x, y + h, x, y, color);
        endLines(b);
    }

    /** A 1-pixel line, like the GL_LINES of the 1.12 screen, however much the canvas is stretched. */
    private void drawLine(GuiGraphics g, float x1, float y1, float x2, float y2, int color) {
        BufferBuilder b = beginLines();
        lineVerts(b, g.pose().last().pose(), x1, y1, x2, y2, color);
        endLines(b);
    }

    private void drawDashedOutline(GuiGraphics g, int x, int y, int w, int h, int color) {
        BufferBuilder b = beginLines();
        Matrix4f m = g.pose().last().pose();
        for (int cx = x; cx < x + w; cx += 8) {
            lineVerts(b, m, cx, y, Math.min(cx + 4, x + w), y, color);
            lineVerts(b, m, cx, y + h, Math.min(cx + 4, x + w), y + h, color);
        }
        for (int cy = y; cy < y + h; cy += 8) {
            lineVerts(b, m, x, cy, x, Math.min(cy + 4, y + h), color);
            lineVerts(b, m, x + w, cy, x + w, Math.min(cy + 4, y + h), color);
        }
        endLines(b);
    }

    private static BufferBuilder beginLines() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        return b;
    }

    private static void lineVerts(BufferBuilder b, Matrix4f m, float x1, float y1, float x2, float y2, int color) {
        b.vertex(m, x1, y1, 0).color(color).endVertex();
        b.vertex(m, x2, y2, 0).color(color).endVertex();
    }

    private static void endLines(BufferBuilder b) {
        BufferUploader.drawWithShader(b.end());
    }

    private static BufferBuilder beginAdditive() {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        return b;
    }

    private static void endAdditive(BufferBuilder b) {
        BufferUploader.drawWithShader(b.end());
        RenderSystem.defaultBlendFunc();
    }

    private static void additiveRect(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        BufferBuilder b = beginAdditive();
        Matrix4f m = g.pose().last().pose();
        b.vertex(m, x1, y2, 0).color(color).endVertex();
        b.vertex(m, x2, y2, 0).color(color).endVertex();
        b.vertex(m, x2, y1, 0).color(color).endVertex();
        b.vertex(m, x1, y1, 0).color(color).endVertex();
        endAdditive(b);
    }

    private static final Map<SinType, ResourceLocation> SIN_ICONS = new EnumMap<>(SinType.class);

    static {
        for (SinType s : SinType.values()) {
            SIN_ICONS.put(s, new ResourceLocation(EFW.MOD_ID, "textures/gui/sins/" + s.getId() + ".png"));
        }
    }

    private static final ResourceLocation DPOR_ICON_TEX = new ResourceLocation(EFW.MOD_ID, "textures/item/dpor.png");

    private void drawSinIcon(GuiGraphics g, SinType sin, int x, int y, int size, float alpha) {
        RenderSystem.enableBlend();
        g.setColor(1.0f, 1.0f, 1.0f, alpha);
        g.blit(SIN_ICONS.get(sin), x, y, size, size, 0, 0, 16, 16, 16, 16);
        g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void drawChosenSinHeader(GuiGraphics g, SinType sin, float alpha) {
        int x = 12;
        int y = 10;
        int iconSize = 16;
        drawSinIcon(g, sin, x, y, iconSize, alpha);
        int textAlpha = Math.max(4, Math.min(255, (int) (alpha * 255)));
        text(g, ChatFormatting.BOLD + sin.getNameRu().toUpperCase(), x + iconSize + 6, y + 4, (textAlpha << 24) | (COLOR_PAPER & 0x00FFFFFF));
    }

    // skill icons and their burn-in animation
    private static synchronized void ensureSkillRawTexturesLoaded() {
        if (skillRawTexturesLoaded) {
            return;
        }
        skillRawTexturesLoaded = true;
        rawTexSkillUnupgraded = loadArgbPixels(TEX_SKILL_UNUPGRADED);
        for (int i = 0; i < TEX_SKILLS.length; i++) {
            rawTexSkills[i] = loadArgbPixels(TEX_SKILLS[i]);
        }
    }

    /** Pixels of a 16x16 texture as ARGB ints. */
    private static int[] loadArgbPixels(ResourceLocation loc) {
        int[] pixels = new int[16 * 16];
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(loc);
        if (res.isEmpty()) {
            return pixels;
        }
        try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
            for (int v = 0; v < Math.min(16, img.getHeight()); v++) {
                for (int u = 0; u < Math.min(16, img.getWidth()); u++) {
                    pixels[v * 16 + u] = abgrToArgb(img.getPixelRGBA(u, v));
                }
            }
        } catch (Exception e) {
            EFW.LOGGER.warn("Could not read skill icon {}", loc, e);
        }
        return pixels;
    }

    private static int abgrToArgb(int c) {
        return (c & 0xFF00FF00) | ((c & 0xFF) << 16) | ((c >> 16) & 0xFF);
    }

    private void drawSkillIcon(GuiGraphics g, SkillNode node, int x, int y, int nodeSize, boolean unlocked, boolean isHovered, boolean available) {
        int assignedIndex = SKILL_ASSIGNED_ICON[node.id];
        long animStart = skillUnlockAnimStart[node.id];
        if (animStart > 0 && unlocked && node.id > 0) {
            long elapsed = (System.nanoTime() - animStart) / 1_000_000L;
            if (elapsed < SKILL_BURN_DURATION_MS) {
                updateSkillBurnTexture(node.id, assignedIndex, elapsed / (float) SKILL_BURN_DURATION_MS);
                RenderSystem.enableBlend();
                g.blit(skillBurnTextureLocations[node.id], x, y, nodeSize, nodeSize, 0, 0, 16, 16, 16, 16);
                return;
            }
        }
        if (unlocked) {
            g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        } else if (available) {
            if (isHovered) {
                g.setColor(1.0f, 0.95f, 0.8f, 1.0f);
            } else {
                g.setColor(0.85f, 0.85f, 0.85f, 0.9f);
            }
        } else {
            g.setColor(0.55f, 0.52f, 0.50f, 0.75f);
        }
        RenderSystem.enableBlend();
        g.blit(unlocked ? TEX_SKILLS[assignedIndex] : TEX_SKILL_UNUPGRADED, x, y, nodeSize, nodeSize, 0, 0, 16, 16, 16, 16);
        g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    /** Burns the locked icon away from the centre outwards, revealing the skill icon behind a thin fire line. */
    private void updateSkillBurnTexture(int nodeId, int assignedIndex, float progress) {
        if (skillBurnTextures[nodeId] == null) {
            skillBurnTextures[nodeId] = new DynamicTexture(16, 16, true);
            skillBurnTextureLocations[nodeId] = new ResourceLocation(EFW.MOD_ID, "dynamic/skill_burn_" + nodeId);
            Minecraft.getInstance().getTextureManager().register(skillBurnTextureLocations[nodeId], skillBurnTextures[nodeId]);
        }
        int[] unup = rawTexSkillUnupgraded;
        int[] target = rawTexSkills[assignedIndex];
        NativeImage out = skillBurnTextures[nodeId].getPixels();
        if (unup == null || target == null || out == null) {
            return;
        }
        float maxDist = (float) Math.sqrt(7.5 * 7.5 + 7.5 * 7.5);
        float waveCenter = progress * 1.25f; // to 1.25 so the corners burn too
        float waveHalfWidth = 0.022f;
        float ashAhead = 0.055f;
        float glowTimeSec = System.nanoTime() / 1_000_000_000.0f;
        for (int v = 0; v < 16; v++) {
            for (int u = 0; u < 16; u++) {
                int idx = v * 16 + u;
                int baseCol = unup[idx];
                int targetCol = target[idx];
                float dx = u - 7.5f;
                float dy = v - 7.5f;
                float noise = ((u * 37 + v * 53 + nodeId * 101) & 15) / 15.0f - 0.5f;
                float diff = (float) Math.sqrt(dx * dx + dy * dy) / maxDist + noise * 0.03f - waveCenter;
                int baseA = (baseCol >> 24) & 0xFF;
                int targetA = (targetCol >> 24) & 0xFF;
                int argb;
                if (baseA == 0 && targetA == 0) {
                    argb = 0;
                } else {
                    int origR = (baseCol >> 16) & 0xFF, origG = (baseCol >> 8) & 0xFF, origB = baseCol & 0xFF;
                    int targR = (targetCol >> 16) & 0xFF, targG = (targetCol >> 8) & 0xFF, targB = targetCol & 0xFF;
                    int r, gr, b, a = 255;
                    if (diff < -waveHalfWidth) {
                        // behind the wave: the skill icon, with a short glowing edge
                        float coolDist = Math.min(1.0f, (-diff - waveHalfWidth) * 8.0f);
                        a = targetA;
                        if (coolDist < 0.6f) {
                            float redHeat = 1.0f - coolDist / 0.6f;
                            r = Math.min(255, (int) (targR + (255 - targR) * redHeat * 0.5f));
                            gr = Math.min(255, (int) (targG + (70 - targG) * redHeat * 0.3f));
                            b = targB;
                        } else {
                            r = targR;
                            gr = targG;
                            b = targB;
                        }
                    } else if (Math.abs(diff) <= waveHalfWidth) {
                        // the burning front
                        float frontT = 1.0f - Math.abs(diff) / waveHalfWidth;
                        float flicker = 0.90f + 0.10f * (float) Math.sin(glowTimeSec * 22.0f + (u + v * 3) * 1.5f);
                        r = 255;
                        gr = Math.max(0, Math.min(255, (int) ((125 + 40 * frontT) * flicker)));
                        b = Math.max(0, Math.min(255, (int) ((10 + 20 * frontT) * flicker)));
                        a = Math.max(baseA, targetA);
                    } else if (diff <= ashAhead) {
                        // pale ash creeping ahead of the wave
                        float ashT = 1.0f - (diff - waveHalfWidth) / (ashAhead - waveHalfWidth);
                        float mix = ashT * 0.65f;
                        r = Math.max(0, Math.min(255, (int) (origR + (195 - origR) * mix)));
                        gr = Math.max(0, Math.min(255, (int) (origG + (190 - origG) * mix)));
                        b = Math.max(0, Math.min(255, (int) (origB + (180 - origB) * mix)));
                        a = baseA;
                        if (ashT > 0.8f) {
                            float warm = (ashT - 0.8f) / 0.2f;
                            r = Math.min(255, (int) (r + (255 - r) * warm * 0.4f));
                            gr = Math.min(255, (int) (gr + (110 - gr) * warm * 0.2f));
                        }
                    } else {
                        r = origR;
                        gr = origG;
                        b = origB;
                        a = baseA;
                    }
                    argb = (a << 24) | (r << 16) | (gr << 8) | b;
                }
                out.setPixelRGBA(u, v, abgrToArgb(argb)); // the swap is its own inverse
            }
        }
        skillBurnTextures[nodeId].upload();
    }

    @Override
    public void removed() {
        for (int i = 0; i < skillBurnTextures.length; i++) {
            if (skillBurnTextures[i] != null) {
                Minecraft.getInstance().getTextureManager().release(skillBurnTextureLocations[i]);
                skillBurnTextures[i] = null;
            }
        }
        super.removed();
    }
}
