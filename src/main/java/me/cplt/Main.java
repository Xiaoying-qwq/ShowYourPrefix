package me.cplt;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class Main extends JavaPlugin implements Listener, TabCompleter {
    private final HashMap<UUID, Boolean> isTypingPrefix = new HashMap<>();
    private Set<String> disabledPlayers;
    private File customConfigFile;
    private FileConfiguration customConfig;

    @Override
    public void onEnable() {
        createCustomConfig();
        loadDisabledPlayers();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("§a  ShowYourPrefix 插件已启动！");
    }
    private void createCustomConfig() {
        // 1. 创建 plugins/ShowYourPrefix 文件夹
        File showYourPrefixFolder = new File(getDataFolder().getParentFile(), "ShowYourPrefix");
        if (!showYourPrefixFolder.exists()) {
            showYourPrefixFolder.mkdirs();
        }

        // 2. 创建 plugins/ShowYourPrefix/config.yml 文件
        customConfigFile = new File(showYourPrefixFolder, "config.yml");
        if (!customConfigFile.exists()) {
            try {
                customConfigFile.createNewFile();

                // ========== 核心：直接写入带注释的配置模板 ==========
                String defaultConfigContent = "# ======================================\n" +
                        "# ShowYourPrefix 插件配置文件\n" +
                        "# ======================================\n" +
                        "\n" +
                        "# ======================================\n" +
                        "# 禁用玩家列表\n" +
                        "# 在此列表中的玩家将无法使用 /cplt 申请头衔\n" +
                        "# 格式：\n" +
                        "# disabled-players:\n" +
                        "#   - 玩家名1\n" +
                        "#   - 玩家名2\n" +
                        "# ======================================\n" +
                        "disabled-players: []\n" +
                        "\n" +
                        "# ======================================\n" +
                        "# 违禁词列表\n" +
                        "# 玩家申请的头衔中包含以下词汇时将被拒绝\n" +
                        "# 支持中文和英文，不区分大小写\n" +
                        "# ======================================\n" +
                        "blacklist:\n" +
                        "  # 冒充管理类\n" +
                        "  - 服主\n" +
                        "  - 腐竹\n" +
                        "  - 管理员\n" +
                        "  - 管理\n" +
                        "  - GM\n" +
                        "  - OP\n" +
                        "  - admin\n" +
                        "  - 超级管理员\n" +
                        "  # 违规行为类\n" +
                        "  - 外挂\n" +
                        "  - 作弊\n" +
                        "  - 透视\n" +
                        "  - 飞天\n" +
                        "  - 骗子\n" +
                        "  - 开挂\n" +
                        "  - 脚本\n" +
                        "  # 不文明类\n" +
                        "  - 傻\n" +
                        "  - 逼\n" +
                        "  - 操\n" +
                        "  - 草\n" +
                        "  - 垃圾\n" +
                        "  - 废物\n" +
                        "  - 脑瘫\n" +
                        "  # 误导性类\n" +
                        "  - 充值\n" +
                        "  - 氪金\n" +
                        "  - VIP\n" +
                        "  - 至尊\n" +
                        "  - 内部\n" +
                        "  - 福利";
                FileWriter writer = new FileWriter(customConfigFile);
                writer.write(defaultConfigContent);
                writer.close();

            } catch (IOException e) {
                getLogger().severe("创建外置配置文件失败！" + e.getMessage());
            }
        }
        customConfig = YamlConfiguration.loadConfiguration(customConfigFile);
    }
    private void loadDisabledPlayers() {
        disabledPlayers = new HashSet<>();
        if (getCustomConfig().contains("disabled-players")) {
            disabledPlayers.addAll(getCustomConfig().getStringList("disabled-players"));
        }
        getLogger().info("已加载 " + disabledPlayers.size() + " 个被禁用的玩家");
    }
    private void saveDisabledPlayers() {
        getCustomConfig().set("disabled-players", new HashSet<>(disabledPlayers));
        try {
            getCustomConfig().save(customConfigFile);
        } catch (IOException e) {
            getLogger().severe("保存禁用玩家列表失败！" + e.getMessage());
        }
    }
    public FileConfiguration getCustomConfig() {
        if (customConfig == null) {
            createCustomConfig();
        }
        return customConfig;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("cplt")) return false;
        boolean isAdmin = sender.hasPermission("cplt.admin");
        boolean canUse = sender.hasPermission("cplt.use");
        if (!isAdmin && !canUse) {
            sender.sendMessage(ChatColor.RED + "你没有权限使用该指令！");
            return true;
        }
        if (isAdmin) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                customConfig = YamlConfiguration.loadConfiguration(customConfigFile);
                loadDisabledPlayers();
                sender.sendMessage(ChatColor.GREEN + "✅ ShowYourPrefix 配置文件重载成功！");
                return true;
            }
            if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
                sender.sendMessage(ChatColor.YELLOW + "===== 被禁用头衔申请的玩家列表 =====");
                if (disabledPlayers.isEmpty()) {
                    sender.sendMessage(ChatColor.GRAY + "当前没有被禁用的玩家");
                } else {
                    for (String playerName : disabledPlayers) {
                        sender.sendMessage(ChatColor.RED + "- " + playerName);
                    }
                }
                return true;
            }
            if (args.length == 2) {
                String action = args[0].toLowerCase();
                String targetPlayerName = args[1];
                if (action.equals("off")) {
                    if (disabledPlayers.contains(targetPlayerName)) {
                        sender.sendMessage(ChatColor.RED + "玩家 " + targetPlayerName + " 已经被禁用申请功能了！");
                        return true;
                    }
                    disabledPlayers.add(targetPlayerName);
                    saveDisabledPlayers();
                    sender.sendMessage(ChatColor.GREEN + "已成功禁用玩家 " + targetPlayerName + " 的头衔申请功能！");
                    Player target = Bukkit.getPlayerExact(targetPlayerName);
                    if (target != null && target.isOnline()) {
                        target.sendMessage(ChatColor.RED + "⚠️ 你的头衔申请功能已被管理员禁用！");
                    }
                    return true;
                }
                if (action.equals("on")) {
                    if (!disabledPlayers.contains(targetPlayerName)) {
                        sender.sendMessage(ChatColor.RED + "玩家 " + targetPlayerName + " 未被禁用申请功能！");
                        return true;
                    }
                    disabledPlayers.remove(targetPlayerName);
                    saveDisabledPlayers();
                    sender.sendMessage(ChatColor.GREEN + "已成功启用玩家 " + targetPlayerName + " 的头衔申请功能！");
                    Player target = Bukkit.getPlayerExact(targetPlayerName);
                    if (target != null && target.isOnline()) {
                        target.sendMessage(ChatColor.GREEN + "✅ 你的头衔申请功能已被管理员启用！");
                    }
                    return true;
                }
            }
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "只有玩家可以使用头衔申请功能！");
            if (isAdmin) {
                sender.sendMessage(ChatColor.YELLOW + "管理员指令：/cplt off/on 玩家ID | /cplt list | /cplt reload");
            }
            return true;
        }
        Player player = (Player) sender;
        UUID playerUUID = player.getUniqueId();
        if (disabledPlayers.contains(player.getName())) {
            player.sendMessage(ChatColor.RED + "❌ 你已被禁用头衔申请功能，请联系服务器管理员！");
            return true;
        }
        if (args.length == 0) {
            if (isTypingPrefix.containsKey(playerUUID)) {
                isTypingPrefix.remove(playerUUID);
                player.sendMessage(ChatColor.YELLOW + "❎ 已取消头衔申请。");
            } else {
                player.sendMessage(ChatColor.GREEN + "请在聊天栏输入你想要的头衔（支持颜色代码 &）：");
                player.sendMessage(ChatColor.GRAY + "示例：&b&l大佬");
                player.sendMessage(ChatColor.GRAY + "再次输入 /cplt 可取消申请");
                isTypingPrefix.put(playerUUID, true);
            }
            return true;
        }
        sender.sendMessage(ChatColor.RED + "指令格式错误！正确用法：");
        if (isAdmin) {
            sender.sendMessage(ChatColor.YELLOW + "管理员指令：");
            sender.sendMessage(ChatColor.GRAY + "- /cplt reload      重载配置文件");
            sender.sendMessage(ChatColor.GRAY + "- /cplt off 玩家ID  禁用玩家的头衔申请");
            sender.sendMessage(ChatColor.GRAY + "- /cplt on 玩家ID   启用玩家的头衔申请");
            sender.sendMessage(ChatColor.GRAY + "- /cplt list        查看所有被禁用的玩家");
        }
        sender.sendMessage(ChatColor.YELLOW + "玩家指令：");
        sender.sendMessage(ChatColor.GRAY + "- /cplt  申请或取消自定义头衔");
        return true;
    }
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("cplt")) return null;

        List<String> completions = new ArrayList<>();
        if (args.length == 1 && sender.hasPermission("cplt.admin")) {
            completions.add("on");
            completions.add("off");
            completions.add("list");
            completions.add("reload");
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && sender.hasPermission("cplt.admin")) {
            String firstArg = args[0].toLowerCase();
            if (firstArg.equals("on") || firstArg.equals("off")) {
                completions = Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .collect(Collectors.toList());
                return completions.stream()
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return completions;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        if (isTypingPrefix.containsKey(playerUUID)) {
            event.setCancelled(true);
            String prefixInput = event.getMessage();
            List<String> blacklist = getCustomConfig().getStringList("blacklist");
            for (String word : blacklist) {
                if (prefixInput.toLowerCase().contains(word.toLowerCase())) {
                    player.sendMessage(ChatColor.RED + "你的头衔包含违禁词「" + word + "」，请修改后重新申请！");
                    isTypingPrefix.remove(playerUUID);
                    return;
                }
            }
            final String finalPrefix = ChatColor.translateAlternateColorCodes('&', prefixInput);
            Bukkit.getScheduler().runTask(this, () -> {
                String playerName = player.getName();
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + playerName + " meta setprefix 100 " + finalPrefix);
                player.sendMessage(ChatColor.GREEN + "✅ 设置成功！你的头衔已更改为：" + finalPrefix);
            });

            isTypingPrefix.remove(playerUUID);
        }
    }
}