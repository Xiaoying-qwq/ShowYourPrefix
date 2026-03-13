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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
    private int maxDailyChange;
    private final HashMap<UUID, Integer> playerChangeCount = new HashMap<>();
    private final HashMap<UUID, String> playerLastChangeDate = new HashMap<>();
    private File dataConfigFile;
    private FileConfiguration dataConfig;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public void onEnable() {
        createCustomConfig();
        createDataConfig();
        loadConfigValues();
        loadDisabledPlayers();
        loadPlayerChangeData();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("§a==================================");
        getLogger().info("§a  ShowYourPrefix 插件已启动！");
        getLogger().info("§a==================================");
    }

    @Override
    public void onDisable() {
        savePlayerChangeData();
        getLogger().info("§c  ShowYourPrefix 插件已关闭！");
    }
    private void createCustomConfig() {
        File showYourPrefixFolder = new File(getDataFolder().getParentFile(), "ShowYourPrefix");
        if (!showYourPrefixFolder.exists()) {
            showYourPrefixFolder.mkdirs();
        }
        customConfigFile = new File(showYourPrefixFolder, "config.yml");
        if (!customConfigFile.exists()) {
            try {
                customConfigFile.createNewFile();
                String defaultConfigContent = "# ======================================\n" +
                        "# ShowYourPrefix 插件配置文件\n" +
                        "# ======================================\n" +
                        "\n" +
                        "# ======================================\n" +
                        "# 每日更换次数限制\n" +
                        "# 玩家每天最多可以更换多少次头衔\n" +
                        "# 设置为 -1 表示不限制次数\n" +
                        "# ======================================\n" +
                        "daily-max-change: 5\n" +
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
    private void loadConfigValues() {
        maxDailyChange = getCustomConfig().getInt("daily-max-change", 5);
        getLogger().info("已设置每日最大更换次数为：" + (maxDailyChange == -1 ? "不限制" : maxDailyChange));
    }
    private void createDataConfig() {
        File showYourPrefixFolder = new File(getDataFolder().getParentFile(), "ShowYourPrefix");
        if (!showYourPrefixFolder.exists()) {
            showYourPrefixFolder.mkdirs();
        }

        dataConfigFile = new File(showYourPrefixFolder, "data.yml");
        if (!dataConfigFile.exists()) {
            try {
                dataConfigFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("创建玩家数据文件失败！" + e.getMessage());
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataConfigFile);
    }
    private void loadPlayerChangeData() {
        playerChangeCount.clear();
        playerLastChangeDate.clear();

        if (dataConfig.contains("player-data")) {
            for (String uuidStr : dataConfig.getConfigurationSection("player-data").getKeys(false)) {
                try {
                    UUID playerUUID = UUID.fromString(uuidStr);
                    String lastDate = dataConfig.getString("player-data." + uuidStr + ".last-change-date");
                    int count = dataConfig.getInt("player-data." + uuidStr + ".change-count", 0);

                    playerLastChangeDate.put(playerUUID, lastDate);
                    playerChangeCount.put(playerUUID, count);
                } catch (IllegalArgumentException e) {
                    getLogger().warning("无效的玩家UUID：" + uuidStr + "，已跳过");
                }
            }
        }
        getLogger().info("已加载 " + playerChangeCount.size() + " 个玩家的更换记录");
    }

    private void savePlayerChangeData() {
        dataConfig.set("player-data", null);
        for (UUID playerUUID : playerChangeCount.keySet()) {
            String path = "player-data." + playerUUID.toString();
            dataConfig.set(path + ".last-change-date", playerLastChangeDate.get(playerUUID));
            dataConfig.set(path + ".change-count", playerChangeCount.get(playerUUID));
        }

        try {
            dataConfig.save(dataConfigFile);
        } catch (IOException e) {
            getLogger().severe("保存玩家数据文件失败！" + e.getMessage());
        }
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

    private String getToday() {
        return LocalDate.now().format(dateFormatter);
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
                loadConfigValues();
                loadDisabledPlayers();
                dataConfig = YamlConfiguration.loadConfiguration(dataConfigFile);
                loadPlayerChangeData();

                sender.sendMessage(ChatColor.GREEN + "✅ ShowYourPrefix 配置&数据重载成功！");
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
                // 非管理员显示剩余次数（如果配置了限制）
                if (!player.hasPermission("cplt.admin") && maxDailyChange != -1) {
                    String today = getToday();
                    String lastDate = playerLastChangeDate.getOrDefault(playerUUID, "");
                    int currentCount = lastDate.equals(today) ? playerChangeCount.getOrDefault(playerUUID, 0) : 0;
                    int remainCount = maxDailyChange - currentCount;
                    player.sendMessage(ChatColor.GRAY + "今日剩余可更换次数：" + remainCount);
                }
                isTypingPrefix.put(playerUUID, true);
            }
            return true;
        }
        sender.sendMessage(ChatColor.RED + "指令格式错误！正确用法：");
        if (isAdmin) {
            sender.sendMessage(ChatColor.YELLOW + "管理员指令：");
            sender.sendMessage(ChatColor.GRAY + "- /cplt reload      重载配置&数据");
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
            String plainText = ChatColor.stripColor(prefixInput);
            if (plainText.length() > 8) {
                player.sendMessage(ChatColor.RED + "你的头衔太长了！最多只能设置 8 个字符（颜色代码不算）。");
                player.sendMessage(ChatColor.GRAY + "当前长度：" + plainText.length());
                isTypingPrefix.remove(playerUUID);
                return;
            }
            List<String> blacklist = getCustomConfig().getStringList("blacklist");
            for (String word : blacklist) {
                if (prefixInput.toLowerCase().contains(word.toLowerCase())) {
                    player.sendMessage(ChatColor.RED + "你的头衔包含违禁词「" + word + "」，请修改后重新申请！");
                    isTypingPrefix.remove(playerUUID);
                    return;
                }
            }
            boolean isAdmin = player.hasPermission("cplt.admin");
            if (!isAdmin && maxDailyChange != -1) {
                String today = getToday();
                String lastDate = playerLastChangeDate.getOrDefault(playerUUID, "");
                int currentCount = playerChangeCount.getOrDefault(playerUUID, 0);
                if (!lastDate.equals(today)) {
                    currentCount = 0;
                }
                if (currentCount >= maxDailyChange) {
                    player.sendMessage(ChatColor.RED + "❌ 你今日已经更换了" + maxDailyChange + "次头衔，明日0点后可再次更换！");
                    isTypingPrefix.remove(playerUUID);
                    return;
                }
                final int finalCount = currentCount + 1;
                final String finalPrefix = ChatColor.translateAlternateColorCodes('&', prefixInput);
                Bukkit.getScheduler().runTask(this, () -> {
                    String playerName = player.getName();
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + playerName + " meta setprefix 100 " + finalPrefix);
                    player.sendMessage(ChatColor.GREEN + "✅ 设置成功！你的头衔已更改为：" + finalPrefix);
                    player.sendMessage(ChatColor.GRAY + "今日剩余可更换次数：" + (maxDailyChange - finalCount));
                    playerChangeCount.put(playerUUID, finalCount);
                    playerLastChangeDate.put(playerUUID, today);
                    savePlayerChangeData();
                });

            } else {
                final String finalPrefix = ChatColor.translateAlternateColorCodes('&', prefixInput);
                Bukkit.getScheduler().runTask(this, () -> {
                    String playerName = player.getName();
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + playerName + " meta setprefix 100 " + finalPrefix);
                    player.sendMessage(ChatColor.GREEN + "✅ 设置成功！你的头衔已更改为：" + finalPrefix);
                    if (isAdmin) {
                        player.sendMessage(ChatColor.GRAY + "管理员权限：不受每日更换次数限制");
                    } else {
                        player.sendMessage(ChatColor.GRAY + "服务器设置：不限制每日更换次数");
                    }
                });
            }

            isTypingPrefix.remove(playerUUID);
        }
    }
}