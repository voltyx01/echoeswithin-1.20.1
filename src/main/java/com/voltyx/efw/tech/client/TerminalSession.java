package com.voltyx.efw.tech.client;

import com.voltyx.efw.tech.BodycamEntry;
import com.voltyx.efw.tech.TechNetwork;
import com.voltyx.efw.tech.TerminalBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The console of the wall terminal (port of TerminalSession): login, boot, shell, chat menu, bodycam list and view. */
public final class TerminalSession {
    private static final TerminalSession INSTANCE = new TerminalSession();

    public static TerminalSession get() {
        return INSTANCE;
    }

    public enum Stage {
        LOGIN_USER, LOGIN_PASS, LOGIN_ERROR, BOOT_SEQUENCE, SHELL, CHAT_MENU, APP_CHAT, BODYCAM_LIST, BODYCAM_VIEW
    }

    public enum ChatSubMenu {
        MAIN, PRIVATE, GROUPS
    }

    public static final String[] CHAT_MENU_ITEMS = {"Global Chat", "Private Chats", "Groups"};
    public static final String[] GROUPS_MENU_ITEMS = {"Create Group", "Invites", "  Alpha Recon", "  Delta Squad", "  Sector-7 Outpost"};

    public record ConsoleLine(String tag, int tagColor, String text, int textColor) {
        public ConsoleLine(String text, int textColor) {
            this(null, 0, text, textColor);
        }
    }

    public record TerminalCommand(String name, String syntax, String description, String detailedHelp) {
    }

    public static final List<TerminalCommand> COMMANDS = List.of(
            new TerminalCommand("help", "help [cmd]", "Show command manual or list",
                    "Displays available commands list or detailed info for a specific command."),
            new TerminalCommand("status", "status", "Show system & link status",
                    "Displays CPU load, system power, and network interface status."),
            new TerminalCommand("chat", "chat", "Open interactive chat menu",
                    "Opens the network chat menu (Global, Private, Groups). Requires eth0 UP."),
            new TerminalCommand("ifup", "ifup [eth0]", "Bring network interface UP",
                    "Activates interface eth0 and initiates network link connection."),
            new TerminalCommand("ifdown", "ifdown [eth0]", "Take network interface DOWN",
                    "Deactivates interface eth0 and disconnects network link."),
            new TerminalCommand("whoami", "whoami", "Show user identity & clearance",
                    "Displays current username, system UID/GID, and security clearance level."),
            new TerminalCommand("clear", "clear", "Clear screen buffer",
                    "Clears all history lines from the terminal screen."),
            new TerminalCommand("bodycam", "bodycam", "Launch bodycam interface",
                    "Opens bodycam surveillance interface. Requires bodycam-driver installed."),
            new TerminalCommand("apt", "apt install <pkg>", "Package installation tool",
                    "Installs drivers and packages (e.g. 'apt install bodycam-driver')."),
            new TerminalCommand("exit", "exit", "Log out and exit terminal",
                    "Closes the terminal session and steps away."));

    public static final ConsoleLine[] BOOT_LINES = {
            new ConsoleLine("OK", 0xFF00FF66, "Mounted /dev/nvme0n1 on /", 0xFF44FFAA),
            new ConsoleLine("OK", 0xFF00FF66, "Cryptographic engine active", 0xFF44FFAA),
            new ConsoleLine("DOWN", 0xFFFF4444, "Network interface eth0: link DOWN", 0xFFFF7777),
            new ConsoleLine("OK", 0xFF00FF66, "Terminal security node active", 0xFF44FFAA),
            new ConsoleLine("OK", 0xFF00FF66, "Core subsystems initialized. Ready.", 0xFF44FFAA),
            new ConsoleLine("Security Clearance: LEVEL 5 (ROOT)", 0xFF33CC88)
    };

    private Stage stage = Stage.LOGIN_USER;
    private final StringBuilder inputBuffer = new StringBuilder();
    private String enteredUser = "";
    private float screenFade;
    private float bootTimer;
    private int bootStep;
    private float errorTimer;
    private float cursorTimer;
    private boolean cursorVisible = true;
    private boolean eth0Up;
    private boolean hasModule;
    private boolean hasBodycamDriver;
    private BlockPos terminalPos;
    private final List<BodycamEntry> availableCameras = new ArrayList<>();
    private int bodycamIndex;
    private float connectTimer;
    private float bodycamPollTimer;
    private int chatMenuIndex;
    private ChatSubMenu subMenu = ChatSubMenu.MAIN;
    private int privateMenuIndex;
    private int groupsMenuIndex;
    private String currentChatRoom = "Global Chat";
    private final List<ConsoleLine> chatMessages = new ArrayList<>();
    private final List<ConsoleLine> shellOutput = new ArrayList<>();
    private final List<String> moduleUsers = new ArrayList<>();

    private TerminalSession() {
        reset();
    }

    public void reset() {
        stage = Stage.LOGIN_USER;
        inputBuffer.setLength(0);
        enteredUser = "";
        screenFade = 0.0f;
        bootTimer = 0.0f;
        bootStep = 0;
        errorTimer = 0.0f;
        cursorTimer = 0.0f;
        cursorVisible = true;
        eth0Up = false;
        hasModule = false;
        hasBodycamDriver = false;
        terminalPos = null;
        availableCameras.clear();
        bodycamIndex = 0;
        connectTimer = 0.0f;
        bodycamPollTimer = 0.0f;
        chatMenuIndex = 0;
        subMenu = ChatSubMenu.MAIN;
        privateMenuIndex = 0;
        groupsMenuIndex = 0;
        currentChatRoom = "Global Chat";
        chatMessages.clear();
        shellOutput.clear();
    }

    /** Fresh session for a terminal (TerminalCameraController.open). */
    void begin(BlockPos pos, TerminalBlockEntity terminal) {
        reset();
        terminalPos = pos;
        hasModule = terminal.hasInternetModule();
        if (hasModule) {
            eth0Up = true;
        }
        hasBodycamDriver = terminal.hasBodycamDriver();
        moduleUsers.clear();
        moduleUsers.addAll(terminal.getModuleUsers());
    }

    public BlockPos getTerminalPos() {
        return terminalPos;
    }

    public void setAvailableCameras(List<BodycamEntry> list) {
        if (list == null) {
            list = Collections.emptyList();
        }
        if (stage == Stage.BODYCAM_VIEW) {
            String currentCamId = null;
            if (bodycamIndex >= 0 && bodycamIndex < availableCameras.size()) {
                currentCamId = availableCameras.get(bodycamIndex).camId;
            }
            availableCameras.clear();
            availableCameras.addAll(list);
            int found = -1;
            if (currentCamId != null) {
                for (int i = 0; i < availableCameras.size(); i++) {
                    if (currentCamId.equals(availableCameras.get(i).camId)) {
                        found = i;
                        break;
                    }
                }
            }
            if (found != -1) {
                bodycamIndex = found;
            } else if (bodycamIndex >= availableCameras.size()) {
                bodycamIndex = Math.max(0, availableCameras.size() - 1);
            }
        } else {
            availableCameras.clear();
            availableCameras.addAll(list);
            if (bodycamIndex >= availableCameras.size()) {
                bodycamIndex = Math.max(0, availableCameras.size() - 1);
            }
            connectTimer = 0.0f;
        }
    }

    public List<BodycamEntry> getAvailableCameras() {
        return availableCameras;
    }

    public int getBodycamIndex() {
        return bodycamIndex;
    }

    public float getConnectTimer() {
        return connectTimer;
    }

    public BodycamEntry getCurrentViewingCamera() {
        if (bodycamIndex < 0 || bodycamIndex >= availableCameras.size()) {
            return null;
        }
        return availableCameras.get(bodycamIndex);
    }

    public List<String> getModuleUsers() {
        List<String> list = new ArrayList<>(moduleUsers);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !list.contains(mc.player.getName().getString())) {
            list.add(mc.player.getName().getString());
        }
        if (list.isEmpty()) {
            list.add("root");
        }
        return list;
    }

    public boolean hasModule() {
        return hasModule;
    }

    void update(float dt, boolean active) {
        if (active) {
            // fade in only once the camera starts moving to the terminal
            screenFade = TerminalCamera.getTransitionProgress() > 0.08f ? Math.min(1.0f, screenFade + dt * 2.0f) : 0.0f;
        } else if (stage == Stage.BODYCAM_VIEW) {
            screenFade = 1.0f;
            // the terminal block was removed: drop the session
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && terminalPos != null && !(mc.level.getBlockEntity(terminalPos) instanceof TerminalBlockEntity)) {
                reset();
                return;
            }
        } else {
            screenFade = Math.max(0.0f, screenFade - dt * 4.0f);
            if (screenFade <= 0.0f) {
                reset();
            }
        }

        cursorTimer += dt;
        if (cursorTimer >= 0.5f) {
            cursorTimer -= 0.5f;
            cursorVisible = !cursorVisible;
        }
        if (stage == Stage.BODYCAM_VIEW) {
            connectTimer += dt;
        }
        // refresh camera states and battery once a second
        if (stage == Stage.BODYCAM_VIEW || stage == Stage.BODYCAM_LIST) {
            bodycamPollTimer += dt;
            if (bodycamPollTimer >= 1.0f) {
                bodycamPollTimer = 0.0f;
                TechNetwork.sendToServer(new TechNetwork.RequestBodycamList());
            }
        } else {
            bodycamPollTimer = 0.0f;
        }
        if (stage == Stage.LOGIN_ERROR) {
            errorTimer -= dt;
            if (errorTimer <= 0.0f) {
                stage = Stage.LOGIN_USER;
                enteredUser = "";
                inputBuffer.setLength(0);
            }
        }
        if (stage == Stage.BOOT_SEQUENCE) {
            bootTimer += dt;
            if (bootStep < BOOT_LINES.length) {
                if (bootTimer >= (bootStep + 1) * 0.38f) {
                    bootStep++;
                    playKeySound(1.4f + bootStep * 0.05f);
                }
            } else if (bootTimer >= BOOT_LINES.length * 0.38f + 0.45f) {
                stage = Stage.SHELL;
                inputBuffer.setLength(0);
                shellOutput.clear();
                shellOutput.add(new ConsoleLine("Type 'help' to see available commands.", 0xFF55FFBB));
                playKeySound(1.8f);
            }
        }
    }

    private static boolean isTypeable(char c) {
        return (c >= 32 && c <= 126) || (c >= 1040 && c <= 1103) || c == 'ё' || c == 'Ё';
    }

    private static boolean isEnter(int key) {
        return key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
    }

    /** A typed character (letters, digits, Cyrillic). */
    public void charTyped(char c) {
        if (stage == Stage.BOOT_SEQUENCE || stage == Stage.BODYCAM_LIST || stage == Stage.BODYCAM_VIEW || stage == Stage.CHAT_MENU) {
            return; // these stages read keys only
        }
        if (isTypeable(c) && inputBuffer.length() < 30) {
            inputBuffer.append(c);
            playKeySound(1.7f);
        }
    }

    /** A pressed key (control keys and the bodycam/menu navigation). */
    public void keyPressed(int key) {
        if (stage == Stage.BOOT_SEQUENCE) {
            return;
        }
        // Ctrl+C interrupts
        if (Screen.hasControlDown() && key == GLFW.GLFW_KEY_C) {
            if (stage == Stage.APP_CHAT) {
                stage = Stage.SHELL;
                inputBuffer.setLength(0);
                shellOutput.add(new ConsoleLine("^C", 0xFFFF5555));
                shellOutput.add(new ConsoleLine("Process [chat: " + currentChatRoom + "] terminated (SIGINT).", 0xFF888888));
                trimHistory();
                playKeySound(1.1f);
            } else if (stage == Stage.CHAT_MENU) {
                stage = Stage.SHELL;
                inputBuffer.setLength(0);
                shellOutput.add(new ConsoleLine("^C", 0xFFFF5555));
                trimHistory();
                playKeySound(1.1f);
            } else if (stage == Stage.SHELL && inputBuffer.length() > 0) {
                shellOutput.add(new ConsoleLine("root@mw-terminal:~# " + inputBuffer + "^C", 0xFF44FFAA));
                inputBuffer.setLength(0);
                trimHistory();
                playKeySound(1.1f);
            }
            return;
        }

        if (stage == Stage.APP_CHAT) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                stage = Stage.CHAT_MENU;
                inputBuffer.setLength(0);
                playKeySound(1.3f);
            } else if (isEnter(key)) {
                String msg = inputBuffer.toString().trim();
                inputBuffer.setLength(0);
                if (!msg.isEmpty()) {
                    Minecraft mc = Minecraft.getInstance();
                    String sender = mc.player != null ? mc.player.getName().getString() : "root";
                    chatMessages.add(new ConsoleLine("<" + sender + "> " + msg, 0xFFFFFFFF));
                    if (chatMessages.size() > 7) {
                        chatMessages.remove(0);
                    }
                }
                playKeySound(1.2f);
            } else if (key == GLFW.GLFW_KEY_BACKSPACE && inputBuffer.length() > 0) {
                inputBuffer.deleteCharAt(inputBuffer.length() - 1);
                playKeySound(1.5f);
            }
            return;
        }

        if (stage == Stage.BODYCAM_LIST) {
            if (key == GLFW.GLFW_KEY_UP) {
                if (!availableCameras.isEmpty()) {
                    bodycamIndex = (bodycamIndex - 1 + availableCameras.size()) % availableCameras.size();
                    playKeySound(1.4f);
                }
            } else if (key == GLFW.GLFW_KEY_DOWN) {
                if (!availableCameras.isEmpty()) {
                    bodycamIndex = (bodycamIndex + 1) % availableCameras.size();
                    playKeySound(1.4f);
                }
            } else if (key == GLFW.GLFW_KEY_TAB) {
                BodycamEntry entry = getCurrentViewingCamera();
                if (entry != null) {
                    entry.isChecked = !entry.isChecked;
                    playKeySound(1.6f);
                }
            } else if (isEnter(key)) {
                if (getCurrentViewingCamera() != null) {
                    playKeySound(1.2f);
                    stage = Stage.BODYCAM_VIEW;
                    connectTimer = 0.0f;
                }
            } else if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_Q) {
                stage = Stage.SHELL;
                shellOutput.add(new ConsoleLine("Bodycam interface closed.", 0xFF888888));
                trimHistory();
                playKeySound(1.3f);
            }
            return;
        }

        if (stage == Stage.BODYCAM_VIEW) {
            Minecraft mc = Minecraft.getInstance();
            if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_A) {
                if (!availableCameras.isEmpty()) {
                    bodycamIndex = (bodycamIndex - 1 + availableCameras.size()) % availableCameras.size();
                    connectTimer = 0.0f;
                    playKeySound(1.4f);
                }
            } else if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_D) {
                if (!availableCameras.isEmpty()) {
                    bodycamIndex = (bodycamIndex + 1) % availableCameras.size();
                    connectTimer = 0.0f;
                    playKeySound(1.4f);
                }
            } else if (key == GLFW.GLFW_KEY_E || mc.options.keyInventory.getKey().getValue() == key) {
                TerminalCamera.close();
                playKeySound(1.2f);
            } else if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_Q || key == GLFW.GLFW_KEY_BACKSPACE) {
                stage = Stage.BODYCAM_LIST;
                playKeySound(1.3f);
            }
            return;
        }

        if (stage == Stage.CHAT_MENU) {
            if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
                int d = key == GLFW.GLFW_KEY_UP ? -1 : 1;
                if (subMenu == ChatSubMenu.GROUPS) {
                    groupsMenuIndex = Math.floorMod(groupsMenuIndex + d, GROUPS_MENU_ITEMS.length);
                } else if (subMenu == ChatSubMenu.PRIVATE) {
                    List<String> users = getModuleUsers();
                    if (!users.isEmpty()) {
                        privateMenuIndex = Math.floorMod(privateMenuIndex + d, users.size());
                    }
                } else {
                    chatMenuIndex = Math.floorMod(chatMenuIndex + d, CHAT_MENU_ITEMS.length);
                }
                playKeySound(1.4f);
            } else if (isEnter(key)) {
                handleChatMenuSelect();
                playKeySound(1.2f);
            } else if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE) {
                if (subMenu != ChatSubMenu.MAIN) {
                    subMenu = ChatSubMenu.MAIN;
                    privateMenuIndex = 0;
                    groupsMenuIndex = 0;
                } else {
                    stage = Stage.SHELL;
                    shellOutput.add(new ConsoleLine("Chat session closed.", 0xFF888888));
                    trimHistory();
                }
                playKeySound(1.3f);
            }
            return;
        }

        if (isEnter(key)) {
            handleEnter();
            playKeySound(1.2f);
        } else if (key == GLFW.GLFW_KEY_BACKSPACE && inputBuffer.length() > 0) {
            inputBuffer.deleteCharAt(inputBuffer.length() - 1);
            playKeySound(1.5f);
        }
    }

    private void handleChatMenuSelect() {
        if (subMenu == ChatSubMenu.GROUPS) {
            String selected = GROUPS_MENU_ITEMS[groupsMenuIndex].trim();
            currentChatRoom = groupsMenuIndex == 0 ? "Group: #Alpha" : groupsMenuIndex == 1 ? "Group: #Invites" : "Group: #" + selected;
            chatMessages.clear();
            chatMessages.add(new ConsoleLine("GRP", 0xFF00FF66, "Joined room: " + currentChatRoom, 0xFF88DDAA));
            chatMessages.add(new ConsoleLine("GRP", 0xFF00FF66, "Multicast routing ready. 3 operators in group.", 0xFF88DDAA));
            inputBuffer.setLength(0);
            stage = Stage.APP_CHAT;
        } else if (subMenu == ChatSubMenu.PRIVATE) {
            List<String> users = getModuleUsers();
            String target = privateMenuIndex < users.size() ? users.get(privateMenuIndex) : "root";
            currentChatRoom = "@" + target;
            chatMessages.clear();
            chatMessages.add(new ConsoleLine("P2P", 0xFF00FF66, "Secure direct link established with " + target, 0xFF88DDAA));
            chatMessages.add(new ConsoleLine("P2P", 0xFF00FF66, "Diffie-Hellman Key Exchange OK. AES-256 active.", 0xFF88DDAA));
            inputBuffer.setLength(0);
            stage = Stage.APP_CHAT;
        } else {
            switch (chatMenuIndex) {
                case 0 -> {
                    currentChatRoom = "Global Chat";
                    chatMessages.clear();
                    chatMessages.add(new ConsoleLine("SYS", 0xFF00FF66, "Broadcast channel #global opened (eth0).", 0xFF88DDAA));
                    chatMessages.add(new ConsoleLine("SYS", 0xFF00FF66, "Connected to network frequency 144.200 MHz.", 0xFF88DDAA));
                    inputBuffer.setLength(0);
                    stage = Stage.APP_CHAT;
                }
                case 1 -> {
                    subMenu = ChatSubMenu.PRIVATE;
                    privateMenuIndex = 0;
                }
                case 2 -> {
                    subMenu = ChatSubMenu.GROUPS;
                    groupsMenuIndex = 0;
                }
                default -> {
                }
            }
        }
    }

    private void handleEnter() {
        String input = inputBuffer.toString().trim();
        inputBuffer.setLength(0);
        if (stage == Stage.LOGIN_USER) {
            enteredUser = input;
            stage = Stage.LOGIN_PASS;
        } else if (stage == Stage.LOGIN_PASS) {
            if ("root".equalsIgnoreCase(enteredUser) && "root".equals(input)) {
                stage = Stage.BOOT_SEQUENCE;
                bootTimer = 0.0f;
                bootStep = 0;
            } else {
                stage = Stage.LOGIN_ERROR;
                errorTimer = 1.2f;
            }
        } else if (stage == Stage.SHELL) {
            executeCommand(input);
        }
    }

    private void executeCommand(String rawCmd) {
        String cmd = rawCmd.trim();
        shellOutput.add(new ConsoleLine("root@mw-terminal:~# " + cmd, 0xFF44FFAA));
        if (cmd.isEmpty()) {
            trimHistory();
            return;
        }
        String[] parts = cmd.split("\\s+");
        String mainCmd = parts[0].toLowerCase();
        String arg = parts.length > 1 ? parts[1].toLowerCase() : null;

        if ("help".equals(mainCmd)) {
            if (arg != null) {
                TerminalCommand found = null;
                for (TerminalCommand c : COMMANDS) {
                    if (c.name().equalsIgnoreCase(arg)) {
                        found = c;
                        break;
                    }
                }
                if (found != null) {
                    shellOutput.add(new ConsoleLine("Command: " + found.syntax(), 0xFF55FFBB));
                    shellOutput.add(new ConsoleLine(found.detailedHelp(), 0xFF44FFAA));
                } else {
                    shellOutput.add(new ConsoleLine("help: unknown command '" + arg + "'", 0xFFFF6666));
                }
            } else {
                shellOutput.add(new ConsoleLine("=== Available Commands ===", 0xFF55FFBB));
                for (TerminalCommand c : COMMANDS) {
                    shellOutput.add(new ConsoleLine(c.syntax() + " - " + c.description(), 0xFF44FFAA));
                }
                shellOutput.add(new ConsoleLine("Type 'help <cmd>' for details.", 0xFF88DDAA));
            }
        } else if ("status".equals(mainCmd)) {
            shellOutput.add(new ConsoleLine("OK", 0xFF00FF66, "Core: ONLINE | Load: 0.08 | Power: NOMINAL", 0xFF55FFBB));
            String ethStatus = !hasModule ? "NO CARRIER (module not installed)" : (eth0Up ? "UP (1000Mbps)" : "DOWN (No carrier)");
            shellOutput.add(new ConsoleLine("eth0 status: " + ethStatus, !hasModule || !eth0Up ? 0xFFFF4444 : 0xFF00FF66));
        } else if ("chat".equals(mainCmd)) {
            if (!hasModule) {
                shellOutput.add(new ConsoleLine("ERROR", 0xFFFF4444, "eth0: No internet module installed.", 0xFFFF7777));
                shellOutput.add(new ConsoleLine("Install an Internet Module into this terminal first.", 0xFFFF9944));
            } else if (!eth0Up) {
                shellOutput.add(new ConsoleLine("ERROR", 0xFFFF4444, "eth0: Interface is DOWN. Run 'ifup eth0' first.", 0xFFFF7777));
            } else {
                stage = Stage.CHAT_MENU;
                chatMenuIndex = 0;
                subMenu = ChatSubMenu.MAIN;
            }
        } else if ("ifup".equals(mainCmd) || "eth0 up".equals(cmd.toLowerCase())) {
            if (!hasModule) {
                shellOutput.add(new ConsoleLine("ERROR", 0xFFFF4444, "eth0: No hardware module detected.", 0xFFFF7777));
                shellOutput.add(new ConsoleLine("Install an Internet Module to enable networking.", 0xFFFF9944));
            } else {
                eth0Up = true;
                shellOutput.add(new ConsoleLine("OK", 0xFF00FF66, "Interface eth0: link UP (1000Mbps)", 0xFF00FF66));
                shellOutput.add(new ConsoleLine("eth0: IPv4 10.0.0.42/24 DHCP ACK", 0xFF44FFAA));
            }
        } else if ("ifdown".equals(mainCmd) || "eth0 down".equals(cmd.toLowerCase())) {
            eth0Up = false;
            shellOutput.add(new ConsoleLine("DOWN", 0xFFFF4444, "Interface eth0: link DOWN", 0xFFFF4444));
            shellOutput.add(new ConsoleLine("eth0: link carrier lost.", 0xFFFF7777));
        } else if ("whoami".equals(mainCmd) || "id".equals(mainCmd)) {
            shellOutput.add(new ConsoleLine("root (UID 0, GID 0) - Clearance: LEVEL 5", 0xFF33CC88));
        } else if ("clear".equals(mainCmd)) {
            shellOutput.clear();
            shellOutput.add(new ConsoleLine("Type 'help' to see available commands.", 0xFF55FFBB));
        } else if ("apt".equals(mainCmd) || "apt-get".equals(mainCmd)) {
            if (parts.length >= 3 && "install".equalsIgnoreCase(parts[1]) && "bodycam-driver".equalsIgnoreCase(parts[2])) {
                if (!hasModule) {
                    shellOutput.add(new ConsoleLine("E: Could not resolve 'archive.mwccf.net'", 0xFFFF4444));
                    shellOutput.add(new ConsoleLine("Install an Internet Module to connect to repositories.", 0xFFFF9944));
                } else if (!eth0Up) {
                    shellOutput.add(new ConsoleLine("E: Network interface eth0 is DOWN. Run 'ifup eth0' first.", 0xFFFF4444));
                } else {
                    hasBodycamDriver = true;
                    if (terminalPos != null) {
                        TechNetwork.sendToServer(new TechNetwork.InstallBodycamDriver(terminalPos));
                    }
                    shellOutput.add(new ConsoleLine("Reading package lists... Done", 0xFF55FFBB));
                    shellOutput.add(new ConsoleLine("Downloading bodycam-driver (1.2.0)... [ 100% ]", 0xFF44FFAA));
                    shellOutput.add(new ConsoleLine("Unpacking kernel module: mw_bodycam.ko...", 0xFF44FFAA));
                    shellOutput.add(new ConsoleLine("OK", 0xFF00FF66, "Setting up video capture subsystem", 0xFF44FFAA));
                    shellOutput.add(new ConsoleLine("Driver installed. Type 'bodycam' to launch.", 0xFF00FF66));
                }
            } else {
                shellOutput.add(new ConsoleLine("Usage: apt install <package-name>", 0xFFFF9944));
            }
        } else if ("bodycam".equals(mainCmd)) {
            if (!hasBodycamDriver) {
                shellOutput.add(new ConsoleLine("bash: bodycam: command not found. Try 'apt install bodycam-driver'.", 0xFFFF6666));
            } else {
                TechNetwork.sendToServer(new TechNetwork.RequestBodycamList());
                stage = Stage.BODYCAM_LIST;
                bodycamIndex = 0;
            }
        } else if ("exit".equals(mainCmd) || "quit".equals(mainCmd) || "logout".equals(mainCmd)) {
            TerminalCamera.close();
        } else {
            shellOutput.add(new ConsoleLine("bash: " + cmd + ": command not found. Try 'help'.", 0xFFFF6666));
        }
        trimHistory();
    }

    private void trimHistory() {
        while (shellOutput.size() > 10) {
            shellOutput.remove(0);
        }
    }

    private static void playKeySound(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
    }

    public Stage getStage() {
        return stage;
    }

    public String getInputBuffer() {
        return inputBuffer.toString();
    }

    public String getEnteredUser() {
        return enteredUser;
    }

    public float getScreenFade() {
        return screenFade;
    }

    public int getBootStep() {
        return bootStep;
    }

    public boolean isCursorVisible() {
        return cursorVisible;
    }

    public boolean isEth0Up() {
        return eth0Up;
    }

    public int getChatMenuIndex() {
        return chatMenuIndex;
    }

    public ChatSubMenu getSubMenu() {
        return subMenu;
    }

    public int getPrivateMenuIndex() {
        return privateMenuIndex;
    }

    public int getGroupsMenuIndex() {
        return groupsMenuIndex;
    }

    public List<ConsoleLine> getShellOutput() {
        return shellOutput;
    }

    public String getCurrentChatRoom() {
        return currentChatRoom;
    }

    public List<ConsoleLine> getChatMessages() {
        return chatMessages;
    }
}
