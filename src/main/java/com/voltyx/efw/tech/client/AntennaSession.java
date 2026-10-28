package com.voltyx.efw.tech.client;

import com.voltyx.efw.notes.client.InspectScreen;
import com.voltyx.efw.sins.SinContent;
import com.voltyx.efw.sins.client.ScreenTransition;
import com.voltyx.efw.tech.AntennaBlockEntity;
import com.voltyx.efw.tech.TechContent;
import com.voltyx.efw.tech.TechNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/** The keypad of the safe (port of AntennaSession): four digits, C clears, K/Enter checks the code. */
public final class AntennaSession {
    private static final AntennaSession INSTANCE = new AntennaSession();

    private String inputBuffer = "";
    private float screenFade;
    private String statusMessage = "";
    private float statusTimer;
    private boolean cursorVisible = true;
    private float cursorBlinkTimer;

    private AntennaSession() {
    }

    public static AntennaSession get() {
        return INSTANCE;
    }

    void reset() {
        inputBuffer = "";
        statusMessage = "";
        statusTimer = 0.0f;
        cursorBlinkTimer = 0.0f;
        cursorVisible = true;
    }

    void update(float dt, boolean active) {
        screenFade = active ? Math.min(1.0f, screenFade + dt * 3.5f) : Math.max(0.0f, screenFade - dt * 4.0f);
        cursorBlinkTimer += dt;
        if (cursorBlinkTimer >= 0.45f) {
            cursorBlinkTimer = 0.0f;
            cursorVisible = !cursorVisible;
        }
        if (statusTimer > 0.0f) {
            statusTimer -= dt;
            if (statusTimer <= 0.0f) {
                if ("ACCESS".equals(statusMessage)) {
                    // the door is open: step back and look at the module
                    AntennaCamera.close();
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.player.playSound(SinContent.ITEM_SOUND.get(), 1.5f, 1.0f);
                    }
                    ScreenTransition.to(new InspectScreen(new ItemStack(TechContent.INTERNET_MODULE.get()), null));
                }
                statusMessage = "";
            }
        }
    }

    private static void play(SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.playSound(sound, volume, pitch);
        }
    }

    private void typeDigit(char c, AntennaBlockEntity antenna) {
        if (statusTimer > 0.0f && "ERROR".equals(statusMessage)) {
            statusMessage = "";
            inputBuffer = "";
        }
        if (inputBuffer.length() < 4) {
            inputBuffer += c;
            String btn = buttonNameForChar(c);
            if (antenna != null && btn != null) {
                antenna.triggerButtonPress(btn);
            }
            play(SoundEvents.UI_BUTTON_CLICK.value(), 0.45F, 1.8F);
        }
    }

    private void clear(AntennaBlockEntity antenna) {
        if (antenna != null) {
            antenna.triggerButtonPress("clear");
        }
        play(SoundEvents.UI_BUTTON_CLICK.value(), 0.45F, 1.4F);
        if (!inputBuffer.isEmpty()) {
            inputBuffer = inputBuffer.substring(0, inputBuffer.length() - 1);
        }
        statusMessage = "";
    }

    private void okay(AntennaBlockEntity antenna, BlockPos pos) {
        if (antenna != null) {
            antenna.triggerButtonPress("okay");
        }
        play(SoundEvents.UI_BUTTON_CLICK.value(), 0.55F, 1.6F);
        validatePin(antenna, pos);
    }

    void charTyped(char c, AntennaBlockEntity antenna, BlockPos pos) {
        if (c >= '0' && c <= '9') {
            typeDigit(c, antenna);
        } else if (c == 'c' || c == 'C' || c == 'с' || c == 'С') {
            clear(antenna);
        } else if (c == 'k' || c == 'K' || c == 'л' || c == 'Л') {
            okay(antenna, pos);
        }
    }

    void keyPressed(int key, AntennaBlockEntity antenna, BlockPos pos) {
        if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
            clear(antenna);
        } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) {
            okay(antenna, pos);
        }
    }

    private void validatePin(AntennaBlockEntity antenna, BlockPos pos) {
        if (pos != null) {
            TechNetwork.sendToServer(new TechNetwork.AntennaPin(pos, inputBuffer));
        }
        if (antenna == null) {
            return;
        }
        if (antenna.getPinCode().equals(inputBuffer)) {
            statusMessage = "ACCESS";
            statusTimer = 2.4f;
            antenna.setUnlocked(true);
            antenna.setOpen(true);
            play(SoundEvents.NOTE_BLOCK_BELL.value(), 1.0F, 2.0F);
        } else {
            statusMessage = "ERROR";
            statusTimer = 1.0f;
            inputBuffer = "";
            play(SoundEvents.NOTE_BLOCK_BASS.value(), 1.0F, 0.6F);
        }
    }

    public static String buttonNameForChar(char c) {
        return switch (c) {
            case '1' -> "one";
            case '2' -> "two";
            case '3' -> "three";
            case '4' -> "four";
            case '5' -> "five";
            case '6' -> "six";
            case '7' -> "seven";
            case '8' -> "eight";
            case '9' -> "nine";
            case '0' -> "zero";
            default -> null;
        };
    }

    public String getInputBuffer() {
        return inputBuffer;
    }

    public float getScreenFade() {
        return screenFade;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public boolean isCursorVisible() {
        return cursorVisible;
    }
}
