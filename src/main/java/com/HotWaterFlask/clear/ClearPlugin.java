package com.HotWaterFlask.clear;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class ClearPlugin extends JavaPlugin implements Listener {

    private FileConfiguration config;
    private FileConfiguration langConfig;
    private boolean isClearing = false;
    
    // 实体类型列表，包含从1.7到1.21.10的所有生物
    private final List<EntityType> ALL_ENTITY_TYPES;
    
    // TPS监控相关变量
    private int tpsTaskId = -1;
    private String currentTpsStatus = "unknown";
    private long lastBroadcastTime = 0;
    private final List<Double> recentTps = new ArrayList<>();
    
    // 液体限制相关变量
    private int liquidTaskId = -1;
    private int liquidEventCount = 0;
    private final Set<Material> liquidBlocks = new HashSet<>();
    private final Set<Material> liquidItems = new HashSet<>();
    
    // 红石限制相关变量
    private int redstoneTaskId = -1;
    private int redstoneEventCount = 0;
    private final Set<Material> redstoneBlocks = new HashSet<>();
    private final Set<String> redstoneIgnoreWorlds = new HashSet<>();
    private final Map<String, Long> redstoneMessageCooldowns = new HashMap<>(); // 红石消息冷却映射，避免重复消息
    private long lastRedstoneTipTime = 0; // 上次发送普通红石提示的时间
    private long lastRedstoneCleanupTipTime = 0; // 上次发送红石清理提示的时间
    
    // 作物限制相关变量
    private int cropTaskId = -1;
    private int cropEventCount = 0;
    private long lastCropTipTime = 0;
    private final Set<String> cropIgnoreWorlds = new HashSet<>();
    
    // 自动清理相关变量
    private int autoClearTaskId = -1;
    
    // 初始化实体类型列表，在运行时检查实体类型是否存在
    public ClearPlugin() {
        List<EntityType> entityTypes = new ArrayList<>();
        
        // 基本生物实体
        addIfExists(entityTypes, EntityType.BAT, EntityType.BLAZE, EntityType.CAVE_SPIDER, EntityType.CHICKEN,
                EntityType.COW, EntityType.CREEPER, EntityType.ENDER_DRAGON, EntityType.ENDERMAN,
                EntityType.ENDERMITE, EntityType.GHAST, EntityType.GUARDIAN, EntityType.HORSE,
                EntityType.IRON_GOLEM, EntityType.MAGMA_CUBE, EntityType.MOOSHROOM, EntityType.OCELOT,
                EntityType.PIG, EntityType.RABBIT, EntityType.SHEEP, EntityType.SILVERFISH,
                EntityType.SKELETON, EntityType.SKELETON_HORSE, EntityType.SLIME, EntityType.SPIDER,
                EntityType.SQUID, EntityType.STRAY, EntityType.VILLAGER, EntityType.WITCH,
                EntityType.WITHER, EntityType.WITHER_SKELETON, EntityType.WOLF, EntityType.ZOMBIE,
                EntityType.ZOMBIE_HORSE, EntityType.ZOMBIE_VILLAGER,
                EntityType.DONKEY, EntityType.LLAMA, EntityType.MULE, EntityType.POLAR_BEAR,
                EntityType.SHULKER, EntityType.ELDER_GUARDIAN, EntityType.EVOKER,
                EntityType.VEX, EntityType.VINDICATOR, EntityType.HUSK, EntityType.DOLPHIN,
                EntityType.ILLUSIONER, EntityType.PARROT, EntityType.TURTLE, EntityType.COD,
                EntityType.DROWNED, EntityType.PUFFERFISH, EntityType.SALMON, EntityType.TROPICAL_FISH,
                EntityType.BEE, EntityType.CAT, EntityType.FOX, EntityType.PANDA, EntityType.PILLAGER,
                EntityType.RAVAGER, EntityType.TRADER_LLAMA, EntityType.WANDERING_TRADER,
                EntityType.HOGLIN, EntityType.PIGLIN, EntityType.STRIDER, EntityType.ZOGLIN,
                EntityType.ZOMBIFIED_PIGLIN, EntityType.GLOW_SQUID, EntityType.GOAT, EntityType.WARDEN,
                EntityType.FROG, EntityType.TADPOLE, EntityType.SNIFFER, EntityType.BREEZE, EntityType.BOGGED,
                EntityType.ALLAY, EntityType.ARMADILLO, EntityType.AXOLOTL, EntityType.CAMEL,
                EntityType.GIANT, EntityType.SNOW_GOLEM);
        
        // 尝试添加可能不存在的实体类型
        try {
            // 使用反射获取可能不存在的实体类型
            Class<?> entityTypeClass = EntityType.class;
            
            // 添加CREAKING实体类型
            try {
                EntityType breaking = EntityType.valueOf("CREAKING");
                entityTypes.add(breaking);
            } catch (IllegalArgumentException e) {
                // 忽略不存在的实体类型
            }
            
            // 添加HAPPY_GHAST实体类型
            try {
                EntityType happyGhast = EntityType.valueOf("HAPPY_GHAST");
                entityTypes.add(happyGhast);
            } catch (IllegalArgumentException e) {
                // 忽略不存在的实体类型
            }
            
        } catch (Exception e) {
            // 忽略任何反射异常
        }
        
        this.ALL_ENTITY_TYPES = Collections.unmodifiableList(entityTypes);
    }
    
    // 批量添加实体类型，如果它们存在的话
    private void addIfExists(List<EntityType> entityTypes, EntityType... types) {
        for (EntityType type : types) {
            if (type != null) {
                entityTypes.add(type);
            }
        }
    }

    @Override
    public void onEnable() {
        // 加载配置文件
        saveDefaultConfig();
        config = getConfig();
        
        // 加载语言文件
        loadLanguageFile();
        
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(this, this);
        
        // 使用格式化的插件启动消息
        String pluginName = getDescription().getName();
        String pluginVersion = getDescription().getVersion();
        String enableMessage = ChatColor.translateAlternateColorCodes('&', getLang("format_plugin_enabled", pluginName, pluginVersion));
        getLogger().info(ChatColor.stripColor(enableMessage));
        Bukkit.getConsoleSender().sendMessage(enableMessage);
        
        // 打印配置信息
        getLogger().info("Loading configuration...");
        
        // 初始化TPS监控
        initTPSMonitor();
        
        // 初始化液体限制
        initLiquidLimit();
        
        // 初始化红石限制
        initRedstoneLimit();
        
        // 初始化作物限制
        initCropLimit();
        
        // 初始化自动清理
        initAutoClear();
    }
    
    /**
     * 加载语言文件
     */
    private void loadLanguageFile() {
        // 检查语言文件是否存在
        File langFile = new File(getDataFolder(), "language.yml");
        if (!langFile.exists()) {
            // 只有当文件不存在时，才保存默认语言文件
            saveResource("language.yml", false);
            getLogger().info("语言文件不存在，已创建默认语言文件");
        }
        
        // 加载语言文件
        langConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(langFile);
    }
    
    /**
     * 获取语言字符串并替换占位符
     * @param key 语言键名
     * @param args 占位符参数
     * @return 语言字符串
     */
    private String getLang(String key, Object... args) {
        String text = langConfig.getString(key, key);
        
        // 替换占位符 {0}, {1}, {2} 等
        for (int i = 0; i < args.length; i++) {
            text = text.replace("{" + i + "}", args[i].toString());
        }
        
        return text;
    }

    @Override
    public void onDisable() {
        // 使用格式化的插件停止消息
        String pluginName = getDescription().getName();
        String pluginVersion = getDescription().getVersion();
        String disableMessage = ChatColor.translateAlternateColorCodes('&', getLang("format_plugin_disabled", pluginName, pluginVersion));
        getLogger().info(ChatColor.stripColor(disableMessage));
        Bukkit.getConsoleSender().sendMessage(disableMessage);
        
        // 停止TPS监控任务
        if (tpsTaskId != -1) {
            Bukkit.getScheduler().cancelTask(tpsTaskId);
            tpsTaskId = -1;
        }
        
        // 停止液体限制任务
        if (liquidTaskId != -1) {
            Bukkit.getScheduler().cancelTask(liquidTaskId);
            liquidTaskId = -1;
        }
        
        // 停止红石限制任务
        if (redstoneTaskId != -1) {
            Bukkit.getScheduler().cancelTask(redstoneTaskId);
            redstoneTaskId = -1;
        }
        
        // 停止作物限制任务
        if (cropTaskId != -1) {
            Bukkit.getScheduler().cancelTask(cropTaskId);
            cropTaskId = -1;
        }
        
        // 停止自动清理任务
        if (autoClearTaskId != -1) {
            Bukkit.getScheduler().cancelTask(autoClearTaskId);
            autoClearTaskId = -1;
        }
    }
    
    /**
     * 初始化红石限制
     */
    private void initRedstoneLimit() {
        // 加载红石方块和忽略世界
        loadRedstoneBlocksAndIgnoreWorlds();
        
        // 清除旧任务
        if (redstoneTaskId != -1) {
            Bukkit.getScheduler().cancelTask(redstoneTaskId);
        }
        
        // 启动红石事件计数重置任务
        if (config.getBoolean("redstone.enable", true)) {
            redstoneTaskId = Bukkit.getScheduler().runTaskTimer(this, () -> {
                if (config.getBoolean("redstone.reset", false)) {
                    redstoneEventCount = 0;
                }
            }, config.getInt("redstone.checkInterval", 100) * 20L, 
              config.getInt("redstone.checkInterval", 100) * 20L).getTaskId();
        }
    }
    
    /**
     * 加载红石方块和忽略世界
     */
    private void loadRedstoneBlocksAndIgnoreWorlds() {
        redstoneBlocks.clear();
        redstoneIgnoreWorlds.clear();
        
        // 加载红石方块
        for (String materialName : config.getStringList("redstone.removeBlocks")) {
            try {
                Material material = Material.valueOf(materialName.toUpperCase());
                redstoneBlocks.add(material);
            } catch (IllegalArgumentException e) {
                getLogger().warning("无效的红石方块类型: " + materialName);
            }
        }
        
        // 加载忽略世界
        redstoneIgnoreWorlds.addAll(config.getStringList("redstone.ignoreWorlds"));
    }
    
    /**
     * 初始化作物限制
     */
    private void initCropLimit() {
        // 加载作物忽略世界
        loadCropIgnoreWorlds();
        
        // 清除旧任务
        if (cropTaskId != -1) {
            Bukkit.getScheduler().cancelTask(cropTaskId);
        }
        
        // 启动作物事件计数重置任务
        if (config.getBoolean("crop.enable", false)) {
            cropTaskId = Bukkit.getScheduler().runTaskTimer(this, () -> {
                if (config.getBoolean("crop.reset", false)) {
                    cropEventCount = 0;
                }
            }, config.getInt("crop.checkInterval", 20) * 20L, 
              config.getInt("crop.checkInterval", 20) * 20L).getTaskId();
        }
    }
    
    /**
     * 初始化自动清理
     */
    private void initAutoClear() {
        // 清除旧任务
        if (autoClearTaskId != -1) {
            Bukkit.getScheduler().cancelTask(autoClearTaskId);
        }
        
        // 启动自动清理任务
        int checkInterval = config.getInt("clear.checkInterval", 300);
        autoClearTaskId = Bukkit.getScheduler().runTaskTimer(this, this::autoClear, 
                checkInterval * 20L, checkInterval * 20L).getTaskId();
    }
    
    /**
     * 执行自动清理
     */
    private void autoClear() {
        // 计算服务器实体总数
        int totalEntities = calculateTotalEntities();
        
        // 获取配置的自动清理参数
        int startClearEntitys = config.getInt("clear.startClearEntitys", 1000);
        int mustClearAmount = config.getInt("clear.mustClear.amount", 5000);
        int mustClearLevel = config.getInt("clear.mustClear.level", 3);
        
        // 检查是否需要执行清理
        int clearLevel = 0;
        boolean shouldClear = false;
        
        if (totalEntities >= mustClearAmount) {
            // 实体数量达到必须清理阈值
            clearLevel = mustClearLevel;
            shouldClear = true;
        } else if (totalEntities >= startClearEntitys) {
            // 实体数量达到开始清理阈值，根据服务器状态决定清理等级
            clearLevel = getAutoClearLevel();
            shouldClear = clearLevel > 0;
        }
        
        // 执行清理
        if (shouldClear) {
            performAutoClear(clearLevel, totalEntities);
        }
    }
    
    /**
     * 计算服务器实体总数
     * @return 实体总数
     */
    private int calculateTotalEntities() {
        int total = 0;
        List<String> ignoreWorldsList = config.getStringList("clear.ignoreWorlds");
        Set<String> ignoreWorlds = new HashSet<>(ignoreWorldsList);
        
        for (World world : Bukkit.getWorlds()) {
            // 跳过忽略的世界
            if (ignoreWorlds.contains(world.getName())) {
                continue;
            }
            
            // 统计实体数量
            total += world.getEntities().size();
        }
        
        return total;
    }
    
    /**
     * 获取自动清理等级
     * @return 清理等级
     */
    private int getAutoClearLevel() {
        // 获取当前TPS状态
        String tpsStatus = currentTpsStatus;
        
        // 根据TPS状态获取清理等级
        if ("good".equals(tpsStatus)) {
            return 1;
        } else if ("fine".equals(tpsStatus)) {
            return 2;
        } else if ("bad".equals(tpsStatus)) {
            return 3;
        } else {
            return 0;
        }
    }
    
    /**
     * 执行自动清理
     * @param clearLevel 清理等级
     * @param totalEntities 总实体数量
     */
    private void performAutoClear(int clearLevel, int totalEntities) {
        // 根据清理等级获取需要清理的实体类型
        Set<EntityType> entitiesToClear = getEntitiesToClearByLevel(clearLevel);
        
        if (entitiesToClear.isEmpty()) {
            return;
        }
        
        // 获取配置的tip属性
        boolean tip = config.getBoolean("clear.tip", true);
        
        // 广播清理开始消息 - 使用语言文件中的配置
        if (tip) {
            String startMessage = ChatColor.translateAlternateColorCodes('&', getLang("format_success", getLang("auto_clear_start")));
            Bukkit.broadcastMessage(startMessage);
        }
        
        // 显示清理等级 - 使用语言文件中的配置
        String configNode = "unknown";
        switch (clearLevel) {
            case 1:
                configNode = "good";
                break;
            case 2:
                configNode = "fine";
                break;
            case 3:
                configNode = "bad";
                break;
            default:
                configNode = "unknown";
        }
        String clearLevelShow = config.getString("clear.clear." + configNode + ".show", "高级");
        String levelMessage = ChatColor.translateAlternateColorCodes('&', getLang("format_clear_level", clearLevelShow));
        if (tip) {
            Bukkit.broadcastMessage(levelMessage);
        }
        
        // 执行清理
        long startTime = System.currentTimeMillis();
        Map<EntityType, Integer> clearedEntities = new HashMap<>();
        int totalCleared = 0;
        
        List<String> ignoreWorldsList = config.getStringList("clear.ignoreWorlds");
        Set<String> ignoreWorlds = new HashSet<>(ignoreWorldsList);
        
        // 遍历所有世界
        for (World world : Bukkit.getWorlds()) {
            // 跳过忽略的世界
            if (ignoreWorlds.contains(world.getName())) {
                continue;
            }
            
            // 获取世界中的实体
            List<Entity> entities = world.getEntities();
            
            // 执行清理
            for (Entity entity : new ArrayList<>(entities)) {
                EntityType type = entity.getType();
                
                // 跳过玩家和命令方块矿车
                if (type == EntityType.PLAYER || type == EntityType.COMMAND_BLOCK_MINECART) {
                    continue;
                }
                
                // 跳过正在使用的实体
                if (entity.isValid() && entity.getPassengers().size() > 0) {
                    continue;
                }
                
                // 特殊处理：跳过Citizens插件的NPC
                if (isCitizensNPC(entity)) {
                    continue;
                }
                
                // 检查是否需要清理该实体类型
                if (entitiesToClear.contains(type)) {
                    // 执行密集清理 - 只清理超出每个网格最大允许数量的实体
                    if (shouldClearEntity(entity, type)) {
                        entity.remove();
                        clearedEntities.put(type, clearedEntities.getOrDefault(type, 0) + 1);
                        totalCleared++;
                    }
                }
            }
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        // 广播清理结果 - 使用语言文件中的配置
        if (tip) {
            // 清理完成消息
            String completeMessage = ChatColor.translateAlternateColorCodes('&', getLang("format_success", getLang("clear_result")));
            Bukkit.broadcastMessage(completeMessage);
            
            // 计算清理前后的实体总数变化
            int totalAfter = calculateTotalEntities();
            int totalBefore = totalEntities;
            
            // 显示总数量变化 - 使用语言文件中的配置
            String totalChangeMessage = ChatColor.translateAlternateColorCodes('&', getLang("format_total_count_change", totalBefore, totalAfter));
            Bukkit.broadcastMessage(totalChangeMessage);
        }
    }
    
    /**
     * 根据清理等级获取需要清理的实体类型
     * @param clearLevel 清理等级
     * @return 需要清理的实体类型集合
     */
    private Set<EntityType> getEntitiesToClearByLevel(int clearLevel) {
        Set<EntityType> entitiesToClear = new HashSet<>();
        
        // 根据清理等级获取对应的配置节点
        String configNode = "unknown";
        switch (clearLevel) {
            case 1:
                configNode = "good";
                break;
            case 2:
                configNode = "fine";
                break;
            case 3:
                configNode = "bad";
                break;
            default:
                return entitiesToClear;
        }
        
        // 检查是否需要清理怪物
        if (config.getBoolean("clear.clear." + configNode + ".monsters", false)) {
            entitiesToClear.addAll(loadEntityTypesFromConfig("entity-categories.monsters.types"));
        }
        
        // 检查是否需要清理动物
        if (config.getBoolean("clear.clear." + configNode + ".animals", false)) {
            entitiesToClear.addAll(loadEntityTypesFromConfig("entity-categories.animals.types"));
        }
        
        // 检查是否需要清理物品
        if (config.getBoolean("clear.clear." + configNode + ".items", false)) {
            entitiesToClear.addAll(loadEntityTypesFromConfig("entity-categories.items.types"));
        }
        
        // 检查是否需要清理载具
        if (config.getBoolean("clear.clear." + configNode + ".vehicles", false)) {
            entitiesToClear.addAll(loadEntityTypesFromConfig("entity-categories.vehicles.types"));
        }
        
        // 检查是否需要清理弹射物
        if (config.getBoolean("clear.clear." + configNode + ".projectiles", false)) {
            entitiesToClear.addAll(loadEntityTypesFromConfig("entity-categories.projectiles.types"));
        }
        
        // 检查是否需要清理特殊实体
        if (config.getBoolean("clear.clear." + configNode + ".special", false)) {
            entitiesToClear.addAll(loadEntityTypesFromConfig("entity-categories.special.types"));
        }
        
        return entitiesToClear;
    }
    
    /**
     * 加载作物忽略世界
     */
    private void loadCropIgnoreWorlds() {
        cropIgnoreWorlds.clear();
        cropIgnoreWorlds.addAll(config.getStringList("crop.ignoreWorlds"));
    }
    
    /**
     * 获取当前TPS状态对应的配置键
     * @return 配置键
     */
    private String getCurrentTPSConfigKey() {
        return currentTpsStatus.equals("unknown") ? "unknown" : currentTpsStatus;
    }
    
    /**
     * 初始化液体限制
     */
    private void initLiquidLimit() {
        // 加载液体方块和物品
        loadLiquidBlocksAndItems();
        
        // 清除旧任务
        if (liquidTaskId != -1) {
            Bukkit.getScheduler().cancelTask(liquidTaskId);
        }
        
        // 启动液体事件计数重置任务
        if (config.getBoolean("liquid.enable", true)) {
            liquidTaskId = Bukkit.getScheduler().runTaskTimer(this, () -> {
                liquidEventCount = 0;
            }, config.getInt("liquid.checkInterval", 15) * 20L, 
              config.getInt("liquid.checkInterval", 15) * 20L).getTaskId();
        }
    }
    
    /**
     * 加载液体方块和物品
     */
    private void loadLiquidBlocksAndItems() {
        liquidBlocks.clear();
        liquidItems.clear();
        
        // 加载液体方块
            for (String materialName : config.getStringList("liquid.liquidBlocks")) {
                try {
                    Material material = Material.valueOf(materialName.toUpperCase());
                    liquidBlocks.add(material);
                } catch (IllegalArgumentException e) {
                    getLogger().warning("无效的液体方块类型: " + materialName);
                }
            }
            
            // 加载液体物品
            for (String materialName : config.getStringList("liquid.items")) {
                try {
                    Material material = Material.valueOf(materialName.toUpperCase());
                    liquidItems.add(material);
                } catch (IllegalArgumentException e) {
                    getLogger().warning("无效的液体物品类型: " + materialName);
                }
            }
    }
    
    /**
     * 初始化TPS监控
     */
    private void initTPSMonitor() {
        // 清除旧任务
        if (tpsTaskId != -1) {
            Bukkit.getScheduler().cancelTask(tpsTaskId);
        }
        
        // 初始化最近TPS列表
        recentTps.clear();
        for (int i = 0; i < 10; i++) {
            recentTps.add(20.0);
        }
        
        // 启动TPS监控任务
        tpsTaskId = Bukkit.getScheduler().runTaskTimer(this, this::updateTPS, 20L, 20L).getTaskId();
        
        // 启动TPS状态检查任务
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::checkTPSStatus, 
                config.getInt("tps.checkInterval", 80) * 20L, 
                config.getInt("tps.checkInterval", 80) * 20L).getTaskId();
    }
    
    /**
     * 更新TPS值
     */
    private void updateTPS() {
        // 在新版本API中，Bukkit.getTPS()方法已移除，使用默认值20.0
        double tps = 20.0;
        
        // 添加到最近TPS列表
        synchronized (recentTps) {
            recentTps.add(0, Math.min(20.0, tps));
            if (recentTps.size() > 10) {
                recentTps.remove(recentTps.size() - 1);
            }
        }
    }
    
    /**
     * 获取平均TPS
     * @return 平均TPS
     */
    private double getAverageTPS() {
        synchronized (recentTps) {
            return recentTps.stream().mapToDouble(Double::doubleValue).average().orElse(20.0);
        }
    }
    
    /**
     * 检查TPS状态
     */
    private void checkTPSStatus() {
        double averageTps = getAverageTPS();
        String newStatus = getTPSStatus(averageTps);
        
        // 如果TPS状态改变
        if (!currentTpsStatus.equals(newStatus)) {
            currentTpsStatus = newStatus;
            broadcastTPSStatus();
        }
    }
    
    /**
     * 获取TPS状态
     * @param tps TPS值
     * @return 状态名称
     */
    private String getTPSStatus(double tps) {
        double goodThreshold = config.getDouble("tps.levels.good.threshold", 16.0);
        double fineThreshold = config.getDouble("tps.levels.fine.threshold", 10.0);
        
        if (tps >= goodThreshold) {
            return "good";
        } else if (tps >= fineThreshold) {
            return "fine";
        } else {
            return "bad";
        }
    }
    
    /**
     * 广播TPS状态
     */
    private void broadcastTPSStatus() {
        if (!config.getBoolean("tps.broadcast", true)) {
            return;
        }
        
        long now = System.currentTimeMillis();
        // 防止频繁广播
        if (now - lastBroadcastTime < 60000) { // 1分钟内不重复广播
            return;
        }
        lastBroadcastTime = now;
        
        // 构建广播消息
        String status = config.getString("tps.levels." + currentTpsStatus + ".status", "&7未知");
        String show = config.getString("tps.levels." + currentTpsStatus + ".show", "");
        
        String message = ChatColor.translateAlternateColorCodes('&', 
                "&6[Clear插件] &f当前服务器TPS状态: " + status + " &a(" + String.format("%.1f", getAverageTPS()) + "/20.0)" + show);
        
        // 广播消息
        Bukkit.broadcastMessage(message);
        getLogger().info(ChatColor.stripColor(message));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("clear")) {
            // 检查权限 - 原插件只检查OP权限
            Player p = null;
            if (sender instanceof Player) {
                p = (Player)sender;
                if (!p.isOp()) {
                    String noPermMessage = ChatColor.translateAlternateColorCodes('&', 
                        getLang("format_no_permission", "clear.use"));
                    sender.sendMessage(noPermMessage);
                    return true;
                }
            }
            
            // 处理命令参数
            int length = args.length;
            try {
                if (length != 1 || !args[0].equalsIgnoreCase("?")) {
                    if (length == 1) {
                        if (args[0].equalsIgnoreCase("reload")) {
                            reloadConfig(sender);
                            return true;
                        }  if (args[0].equalsIgnoreCase("info")) {
                            showInfo(sender);
                            return true;
                        }  if (args[0].equalsIgnoreCase("start")) {
                            startClear(sender, "all");
                            return true;
                        }
                    } else if (length == 2 && 
                        args[0].equalsIgnoreCase("start")) {
                        // 原插件支持数字参数作为清理等级
                        String clearLevel = args[1];
                        // 传递清理等级给startClear方法
                        startClear(sender, clearLevel);
                        return true;
                    }
                }
                
                // 显示帮助 - 使用正确的键名
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', getLang("format_cmd_help_header", getLang("clear_command_tip"))));
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', getLang("format_cmd_help_item", getLang("command_reload"), getLang("reload_config"))));
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', getLang("format_cmd_help_item", getLang("command_info"), getLang("info_description"))));
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', getLang("format_cmd_help_item", getLang("command_start"), getLang("start_description"))));
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', getLang("format_fail", getLang("invalid_number"))));
            }
            return true;
        }
        return false;
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', getLang("format_cmd_help_header", getLang("clear_command_tip"))));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', getLang("format_cmd_help_item", getLang("command_reload"), getLang("reload_config"))));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', getLang("format_cmd_help_item", getLang("command_info"), getLang("info_description"))));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', getLang("format_cmd_help_item", getLang("command_start"), getLang("start_description"))));
    }

    private void reloadConfig(CommandSender sender) {
        reloadConfig();
        config = getConfig();
        loadLanguageFile();
        sender.sendMessage(ChatColor.GREEN + getLang("reload_success"));
    }

    private void showInfo(CommandSender sender) {
        Map<EntityType, Integer> entityCount = new HashMap<>();
        int totalEntities = 0;
        
        // 统计所有世界的实体
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                EntityType type = entity.getType();
                entityCount.put(type, entityCount.getOrDefault(type, 0) + 1);
                totalEntities++;
            }
        }
        
        sender.sendMessage(ChatColor.GOLD + "=== Entity Information ===");
        
        // 使用格式化的总数量统计
        String totalCountMessage = ChatColor.translateAlternateColorCodes('&', 
            getLang("format_total_count", totalEntities));
        sender.sendMessage(totalCountMessage);
        
        // 按实体类型分组显示
        Map<String, Integer> categoryCount = new HashMap<>();
        categoryCount.put("Monsters", 0);
        categoryCount.put("Animals", 0);
        categoryCount.put("Items", 0);
        categoryCount.put("Others", 0);
        
        for (Map.Entry<EntityType, Integer> entry : entityCount.entrySet()) {
            EntityType type = entry.getKey();
            int count = entry.getValue();
            
            if (isMonsterType(type)) {
                categoryCount.put("Monsters", categoryCount.get("Monsters") + count);
            } else if (type.isAlive() && !isMonsterType(type) && type != EntityType.PLAYER) {
                categoryCount.put("Animals", categoryCount.get("Animals") + count);
            } else if (type == EntityType.ITEM) {
                categoryCount.put("Items", categoryCount.get("Items") + count);
            } else {
                categoryCount.put("Others", categoryCount.get("Others") + count);
            }
        }
        
        // 使用格式化的列表显示各分类实体数量
        for (Map.Entry<String, Integer> entry : categoryCount.entrySet()) {
            String broadcastInfo = ChatColor.translateAlternateColorCodes('&', 
                getLang("format_broadcast_info", entry.getKey(), entry.getValue()));
            sender.sendMessage(broadcastInfo);
        }
    }

    private void startClear(CommandSender sender, String clearType) {
        if (isClearing) {
            String message = ChatColor.translateAlternateColorCodes('&', getLang("format_fail", "清理操作正在进行中！"));
            sender.sendMessage(message);
            return;
        }
        
        isClearing = true;
        
        // 获取配置的tip属性
        boolean tip = config.getBoolean("clear.tip", true);
        
        // 清理开始消息 - 使用语言文件中的配置
        String startMessage = ChatColor.translateAlternateColorCodes('&', getLang("format_success", getLang("auto_clear_start")));
        if (tip) {
            Bukkit.broadcastMessage(startMessage);
        } else {
            Bukkit.getLogger().info(ChatColor.stripColor(startMessage));
        }
        
        // 获取清理类型，默认为all
        final String finalClearType = clearType == null || clearType.isEmpty() ? "all" : clearType;
        
        // 显示清理等级 - 根据输入的清理等级获取对应的配置
        String clearLevelShow = "高级"; // 默认值
        try {
            // 尝试将清理类型转换为清理等级
            int level = Integer.parseInt(finalClearType);
            // 根据清理等级获取对应的配置节点
            String configNode;
            switch (level) {
                case 0:
                    configNode = "unknown";
                    break;
                case 1:
                    configNode = "good";
                    break;
                case 2:
                    configNode = "fine";
                    break;
                case 3:
                    configNode = "bad";
                    break;
                default:
                    configNode = "bad";
            }
            // 获取配置的清理等级显示名称
            clearLevelShow = config.getString("clear.clear." + configNode + ".show", "高级");
        } catch (NumberFormatException e) {
            // 如果清理类型不是数字，使用默认值
        }
        
        // 显示清理等级 - 使用语言文件中的配置
        String levelMessage = ChatColor.translateAlternateColorCodes('&', getLang("format_clear_level", clearLevelShow));
        if (tip) {
            Bukkit.broadcastMessage(levelMessage);
        } else {
            Bukkit.getLogger().info(ChatColor.stripColor(levelMessage));
        }
        
        // 计算清理前的实体总数
        final int[] totalBefore = {calculateTotalEntities()}; 
        
        // 在异步线程中执行清理操作，避免阻塞主线程
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            long startTime = System.currentTimeMillis();
            Map<EntityType, Integer> clearedEntities = new HashMap<>();
            final int[] totalCleared = {0};
            
            // 加载配置的实体类型列表
            Set<EntityType> allowedTypes = new HashSet<>();
            
            // 只支持清理等级（数字）
            int clearLevel;
            try {
                // 尝试将清理类型转换为清理等级
                clearLevel = Integer.parseInt(finalClearType);
            } catch (NumberFormatException e) {
                // 如果不是数字，显示帮助信息
                showHelp(sender);
                isClearing = false;
                return;
            }
            
            // 根据清理等级获取对应的配置节点
            String configNode;
            switch (clearLevel) {
                case 0:
                    configNode = "unknown";
                    break;
                case 1:
                    configNode = "good";
                    break;
                case 2:
                    configNode = "fine";
                    break;
                case 3:
                    configNode = "bad";
                    break;
                default:
                    configNode = "bad";
            }
            
            // 根据清理等级配置，决定清理哪些实体类型
            if (config.getBoolean("clear.clear." + configNode + ".monsters", false)) {
                allowedTypes.addAll(loadEntityTypesFromConfig("entity-categories.monsters.types"));
            }
            if (config.getBoolean("clear.clear." + configNode + ".animals", false)) {
                allowedTypes.addAll(loadEntityTypesFromConfig("entity-categories.animals.types"));
            }
            if (config.getBoolean("clear.clear." + configNode + ".items", false)) {
                allowedTypes.addAll(loadEntityTypesFromConfig("entity-categories.items.types"));
            }
            if (config.getBoolean("clear.clear." + configNode + ".vehicles", false)) {
                allowedTypes.addAll(loadEntityTypesFromConfig("entity-categories.vehicles.types"));
            }
            if (config.getBoolean("clear.clear." + configNode + ".projectiles", false)) {
                allowedTypes.addAll(loadEntityTypesFromConfig("entity-categories.projectiles.types"));
            }
            if (config.getBoolean("clear.clear." + configNode + ".special", false)) {
                allowedTypes.addAll(loadEntityTypesFromConfig("entity-categories.special.types"));
            }
            
            // 如果没有找到任何要清理的实体类型，记录日志，但继续执行，显示清理结果和总数量
            if (allowedTypes.isEmpty()) {
                getLogger().info("没有找到要清理的实体类型，将只显示清理结果和总数量。");
            }
            
            // 清理所有世界的实体
            for (World world : Bukkit.getWorlds()) {
                // 使用主线程执行实体操作
                Bukkit.getScheduler().runTask(this, () -> {
                    List<Entity> entities = world.getEntities();
                    int maxBatchSize = 1000;
                    
                    // 分批处理实体，避免服务器过载
                    for (int i = 0; i < entities.size(); i += maxBatchSize) {
                        List<Entity> batch = entities.subList(i, Math.min(i + maxBatchSize, entities.size()));
                        
                        for (Entity entity : new ArrayList<>(batch)) {
                            EntityType type = entity.getType();
                            
                            // 跳过玩家和命令方块矿车
                            if (type == EntityType.PLAYER || type == EntityType.COMMAND_BLOCK_MINECART) {
                                continue;
                            }
                            
                            // 跳过正在使用的实体（例如被骑乘的动物）
                            if (entity.isValid() && entity.getPassengers().size() > 0) {
                                continue;
                            }
                            
                            // 检查实体类型是否在允许列表中
                            if (allowedTypes.contains(type)) {
                                // 特殊处理：跳过Citizens插件的NPC
                                if (isCitizensNPC(entity)) {
                                    continue;
                                }
                                
                                // 特殊处理物品实体，检查是否为珍贵物品
                                if (type == EntityType.ITEM) {
                                    org.bukkit.entity.Item itemEntity = (org.bukkit.entity.Item) entity;
                                    org.bukkit.inventory.ItemStack itemStack = itemEntity.getItemStack();
                                    String materialName = itemStack.getType().name();
                                    
                                    // 加载珍贵物品列表
                                    List<String> whitelist = config.getStringList("entity-categories.items.whitelist");
                                    
                                    // 检查物品是否在珍贵物品列表中
                                    boolean isWhitelisted = whitelist.contains(materialName);
                                    
                                    // 如果是珍贵物品，则跳过清理
                                    if (isWhitelisted) {
                                        continue;
                                    }
                                }
                                
                                // 执行密集清理 - 只清理超出每个网格最大允许数量的实体
                                if (shouldClearEntity(entity, type)) {
                                    // 保存实体位置和类型，以便异步处理回收
                                    Location entityLocation = entity.getLocation().clone();
                                    EntityType entityType = entity.getType();
                                    
                                    // 移除实体
                                    entity.remove();
                                    clearedEntities.put(type, clearedEntities.getOrDefault(type, 0) + 1);
                                    totalCleared[0]++;
                                    
                                    // 动物回收系统 - 按照概率将清理的实体变成刷怪蛋
                                    if (config.getBoolean("animal-recycling.enabled", true)) {
                                        // 将实体回收任务提交到异步线程处理
                                        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                                            // 由于实体已经被移除，我们创建一个新的实体来获取位置信息
                                            // 注意：这里不能直接使用entity，因为它已经被移除
                                            // 所以我们需要重新创建一个临时实体来处理回收
                                            // 但是由于实体已经被移除，我们直接使用保存的位置和类型
                                            // 来处理回收逻辑
                                            
                                            // 获取实体所属的类别
                                            String category = getEntityCategory(entityType);
                                            
                                            // 获取该实体类型的变成蛋的概率
                                            int chance = getRecycleChance(entityType);
                                            
                                            // 随机判定是否变成刷怪蛋
                                            if (chance > 0 && new Random().nextInt(1000) < chance) {
                                                // 获取刷怪蛋
                                                ItemStack spawnEgg = getSpawnEggForEntity(entityType);
                                                if (spawnEgg == null) return;
                                                
                                                // 查找附近的箱子
                                                Block chestBlock = findNearbyChest(entityLocation);
                                                
                                                // 如果没有找到箱子，生成一个新箱子
                                                if (chestBlock == null) {
                                                    chestBlock = generateNewChest(entityLocation);
                                                }
                                                
                                                // 如果找到或生成了箱子，将刷怪蛋放入箱子
                                                if (chestBlock != null) {
                                                    // 复制变量到final变量，以便在lambda表达式中使用
                                                    final Block finalChestBlock = chestBlock;
                                                    final ItemStack finalSpawnEgg = spawnEgg;
                                                    final EntityType finalEntityType = entityType;
                                                    
                                                    // 在主线程中操作方块
                                                    Bukkit.getScheduler().runTask(this, () -> {
                                                        // 获取箱子的物品容器
                                                        org.bukkit.block.Container container = (org.bukkit.block.Container) finalChestBlock.getState();
                                                        if (container != null) {
                                                            // 将刷怪蛋添加到箱子中
                                                            container.getInventory().addItem(finalSpawnEgg);
                                                            container.update();
                                                            
                                                            // 记录日志
                                                            getLogger().info("动物回收系统：已将 " + finalEntityType.name() + " 转化为刷怪蛋并放入箱子");
                                                        }
                                                    });
                                                }
                                            }
                                        });
                                    }
                                }
                            }
                        }
                        
                        // 短暂延迟，避免服务器过载
                        try {
                            TimeUnit.MILLISECONDS.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                });
                
                // 短暂延迟，避免服务器过载
                try {
                    TimeUnit.MILLISECONDS.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            long endTime = System.currentTimeMillis();
            int finalTotalCleared = totalCleared[0];
            
            // 计算清理后的实体总数
            final int totalAfter = totalBefore[0] - finalTotalCleared;
            
            // 发送清理结果
            Bukkit.getScheduler().runTask(this, () -> {
                // 清理完成消息 - 使用语言文件中的配置
                String completeMessage = ChatColor.translateAlternateColorCodes('&', getLang("format_success", getLang("clear_result")));
                if (tip) {
                    Bukkit.broadcastMessage(completeMessage);
                } else {
                    Bukkit.getLogger().info(ChatColor.stripColor(completeMessage));
                }
                
                // 显示总数量变化 - 使用语言文件中的配置
                String totalChangeMessage = ChatColor.translateAlternateColorCodes('&', getLang("format_total_count_change", totalBefore[0], totalAfter));
                if (tip) {
                    Bukkit.broadcastMessage(totalChangeMessage);
                } else {
                    Bukkit.getLogger().info(ChatColor.stripColor(totalChangeMessage));
                }
                
                isClearing = false;
            });
        });
    }
    
    /**
     * 从配置文件加载实体类型列表
     * @param path 配置文件路径
     * @return 实体类型集合
     */
    private Set<EntityType> loadEntityTypesFromConfig(String path) {
        Set<EntityType> types = new HashSet<>();
        List<String> typeNames = config.getStringList(path);
        
        for (String typeName : typeNames) {
            try {
                EntityType type = EntityType.valueOf(typeName);
                types.add(type);
            } catch (IllegalArgumentException e) {
                getLogger().warning("Invalid entity type in config: " + typeName);
            }
        }
        
        return types;
    }
    
    /**
     * 判断实体类型是否为怪物
     * @param type 实体类型
     * @return 是否为怪物
     */
    private boolean isMonsterType(EntityType type) {
        switch (type) {
            case BLAZE:
            case CAVE_SPIDER:
            case CREEPER:
            case DROWNED:
            case ENDERMAN:
            case ENDERMITE:
            case EVOKER:
            case GHAST:
            case GUARDIAN:
            case HOGLIN:
            case HUSK:
            case MAGMA_CUBE:
            case PHANTOM:
            case PIGLIN:
            case PIGLIN_BRUTE:
            case PILLAGER:
            case RAVAGER:
            case SHULKER:
            case SILVERFISH:
            case SKELETON:
            case SLIME:
            case SPIDER:
            case STRAY:
            case VEX:
            case VINDICATOR:
            case WITCH:
            case WITHER_SKELETON:
            case ZOGLIN:
            case ZOMBIE:
            case ZOMBIE_VILLAGER:
            case BREEZE:
            case BOGGED:
            case ELDER_GUARDIAN:
            case GIANT:
            case ILLUSIONER:
            case WARDEN:
            case ZOMBIE_HORSE:
            case ZOMBIFIED_PIGLIN:
            case ENDER_DRAGON:
            case WITHER:
            case CREAKING:
            case HAPPY_GHAST:
            case OMINOUS_ITEM_SPAWNER:
                return true;
            default:
                return false;
        }
    }
    
    /**
     * 获取实体所属的类别
     * @param type 实体类型
     * @return 实体类别
     */
    private String getEntityCategory(EntityType type) {
        if (isMonsterType(type)) {
            return "monsters";
        } else if (type.isAlive() && !isMonsterType(type) && type != EntityType.PLAYER) {
            return "animals";
        } else if (type == EntityType.ITEM) {
            return "items";
        } else {
            // 检查是否是投射物
            Set<EntityType> projectiles = loadEntityTypesFromConfig("entity-categories.projectiles.types");
            if (projectiles.contains(type)) {
                return "projectiles";
            }
            return "other";
        }
    }
    
    /**
     * 检查实体是否是Citizens插件的NPC
     * @param entity 要检查的实体
     * @return 是否是Citizens NPC
     */
    private boolean isCitizensNPC(Entity entity) {
        try {
            // 检查实体是否有Citizens相关的元数据 - 这是Citizens NPC的主要标记
            if (entity.hasMetadata("NPC")) {
                return true;
            }
            
            // 检查实体是否有Citizens相关的标签
            if (entity.getScoreboardTags().contains("citizens_npc")) {
                return true;
            }
        } catch (Exception e) {
            // 如果出现任何异常，假设不是NPC，继续清理
        }
        
        return false;
    }
    
    /**
     * 查找附近的箱子
     * @param location 查找位置
     * @return 找到的箱子方块，如果没有找到则返回null
     */
    private Block findNearbyChest(Location location) {
        World world = location.getWorld();
        if (world == null) return null;
        
        boolean firstAll = config.getBoolean("animal-recycling.firstAll", true);
        int heightMax = config.getInt("animal-recycling.heightMax", 2);
        int heightMin = config.getInt("animal-recycling.heightMin", 1);
        
        // 搜索半径
        int radius = 10;
        
        // 如果firstAll为true，在更大范围内查找
        if (firstAll) {
            int y = location.getBlockY();
            int minY = Math.max(0, y - heightMin);
            int maxY = Math.min(world.getMaxHeight(), y + heightMax);
            
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    for (int currentY = minY; currentY <= maxY; currentY++) {
                        Block block = world.getBlockAt(location.getBlockX() + x, currentY, location.getBlockZ() + z);
                        if (isChestBlock(block)) {
                            return block;
                        }
                    }
                }
            }
        } else {
            // 否则在小范围内查找
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    for (int y = -2; y <= 2; y++) {
                        Block block = world.getBlockAt(location.getBlockX() + x, location.getBlockY() + y, location.getBlockZ() + z);
                        if (isChestBlock(block)) {
                            return block;
                        }
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * 检查方块是否是箱子
     * @param block 要检查的方块
     * @return 是否是箱子
     */
    private boolean isChestBlock(Block block) {
        Material type = block.getType();
        return type == Material.CHEST || type == Material.TRAPPED_CHEST || 
               type.name().endsWith("_CHEST") || type.name().endsWith("_BARREL");
    }
    
    /**
     * 生成新箱子
     * @param location 生成位置
     * @return 生成的箱子方块，如果生成失败则返回null
     */
    private Block generateNewChest(Location location) {
        World world = location.getWorld();
        if (world == null) return null;
        
        // 加载配置的空气方块列表
        List<String> airBlockNames = config.getStringList("animal-recycling.airBlocks");
        Set<Material> airBlocks = new HashSet<>();
        
        // 将配置的方块名称转换为Material对象
        for (String blockName : airBlockNames) {
            try {
                Material material = Material.valueOf(blockName);
                airBlocks.add(material);
            } catch (IllegalArgumentException e) {
                // 忽略无效的方块名称
                getLogger().warning("无效的方块名称在airBlocks配置中: " + blockName);
            }
        }
        
        // 寻找合适的生成位置
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                for (int y = location.getBlockY() - 1; y >= Math.max(0, location.getBlockY() - 10); y--) {
                    Block groundBlock = world.getBlockAt(location.getBlockX() + x, y, location.getBlockZ() + z);
                    Block chestBlock = world.getBlockAt(location.getBlockX() + x, y + 1, location.getBlockZ() + z);
                    
                    // 检查地面方块是否是固体方块，且箱子位置是否是空气或配置的空气方块
                    if (groundBlock.getType().isSolid() && 
                        (chestBlock.isEmpty() || airBlocks.contains(chestBlock.getType()))) {
                        
                        // 生成箱子
                        chestBlock.setType(Material.CHEST);
                        return chestBlock;
                    }
                }
            }
        }
        
        // 如果没有找到合适的位置，尝试在实体位置上方生成
        Block currentBlock = location.getBlock();
        while (currentBlock.getY() < world.getMaxHeight() && 
               (currentBlock.isEmpty() || airBlocks.contains(currentBlock.getType()))) {
            currentBlock = world.getBlockAt(currentBlock.getX(), currentBlock.getY() + 1, currentBlock.getZ());
        }
        
        if (currentBlock.getY() < world.getMaxHeight()) {
            Block aboveBlock = world.getBlockAt(currentBlock.getX(), currentBlock.getY() + 1, currentBlock.getZ());
            if (aboveBlock.isEmpty() || airBlocks.contains(aboveBlock.getType())) {
                aboveBlock.setType(Material.CHEST);
                return aboveBlock;
            }
        }
        
        return null;
    }
    
    /**
     * 获取对应实体的刷怪蛋
     * @param type 实体类型
     * @return 刷怪蛋物品栈，如果无法获取则返回null
     */
    private ItemStack getSpawnEggForEntity(EntityType type) {
        try {
            // 在1.21版本中，刷怪蛋的Material命名方式是ENTITY_NAME_SPAWN_EGG
            String eggName = type.name() + "_SPAWN_EGG";
            Material material = Material.valueOf(eggName);
            return new ItemStack(material, 1);
        } catch (IllegalArgumentException e) {
            // 如果找不到对应的刷怪蛋，记录日志
            getLogger().warning("无法找到实体类型 " + type.name() + " 对应的刷怪蛋");
            return null;
        }
    }
    
    /**
     * 获取实体回收为刷怪蛋的概率
     * @param type 实体类型
     * @return 回收概率（0-1000），返回0表示不回收
     */
    private int getRecycleChance(EntityType type) {
        // 获取实体所属类别
        String category = getEntityCategory(type);
        
        // 只处理怪物和动物类别
        if (!category.equals("monsters") && !category.equals("animals")) {
            return 0;
        }
        
        // 检查该类别是否启用
        if (!config.getBoolean("animal-recycling.clearTypes." + category + ".enabled", false)) {
            return 0;
        }
        
        // 获取该类别的实体类型概率列表
        List<String> typeList = config.getStringList("animal-recycling.clearTypes." + category + ".types");
        
        // 遍历列表，查找匹配的实体类型
        for (String entry : typeList) {
            // 分割实体类型和概率
            String[] parts = entry.split(" ");
            if (parts.length != 2) continue;
            
            String entityTypeName = parts[0];
            int chance;
            
            try {
                chance = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                continue;
            }
            
            // 检查实体类型是否匹配
            if (entityTypeName.equalsIgnoreCase(type.name())) {
                return chance;
            }
        }
        
        // 未找到匹配的实体类型，返回0
        return 0;
    }
    
    /**
     * 回收实体，将其转化为刷怪蛋
     * @param entity 要回收的实体
     */
    private void recycleEntity(Entity entity) {
        // 检查回收系统是否启用
        if (!config.getBoolean("animal-recycling.enabled", true)) return;
        
        // 获取实体类型
        EntityType type = entity.getType();
        Location location = entity.getLocation();
        
        // 获取实体回收为刷怪蛋的概率
        int chance = getRecycleChance(type);
        
        // 随机判定是否变成刷怪蛋
        if (chance > 0 && new Random().nextInt(1000) < chance) {
            // 获取刷怪蛋
            ItemStack spawnEgg = getSpawnEggForEntity(type);
            if (spawnEgg == null) return;
            
            // 查找附近的箱子
            Block chestBlock = findNearbyChest(location);
            
            // 如果没有找到箱子，生成一个新箱子
            if (chestBlock == null) {
                chestBlock = generateNewChest(location);
            }
            
            // 如果找到或生成了箱子，将刷怪蛋放入箱子
            if (chestBlock != null) {
                // 获取箱子的物品容器
                org.bukkit.block.Container container = (org.bukkit.block.Container) chestBlock.getState();
                if (container != null) {
                    // 将刷怪蛋添加到箱子中
                    container.getInventory().addItem(spawnEgg);
                    container.update();
                    
                    // 记录日志
                    getLogger().info("动物回收系统：已将 " + type.name() + " 转化为刷怪蛋并放入箱子");
                }
            }
        }
    }
    
    /**
     * 检查是否应该清理该实体（密集清理逻辑）
     * @param entity 要检查的实体
     * @param type 实体类型
     * @return 是否应该清理该实体
     */
    private boolean shouldClearEntity(Entity entity, EntityType type) {
        // 智能清理限制：保留有特殊状态的实体
        if (shouldKeepEntity(entity)) {
            return false;
        }
        
        // 获取实体所在位置
        Location location = entity.getLocation();
        int x = location.getBlockX();
        int z = location.getBlockZ();
        
        // 确定实体所属的类别
        String category = getEntityCategory(type);
        
        // 检查是否启用密集检测（默认启用）
        boolean enableDensityCheck = config.getBoolean("entity-categories." + category + ".enableDensityCheck", true);
        
        // 如果未启用密集检测，直接返回true，清理所有该类型实体
        if (!enableDensityCheck) {
            return true;
        }
        
        // 获取该类别的网格大小和每个网格最大允许数量
        int gridSize = config.getInt("entity-categories." + category + ".gridSize", 10);
        int maxPerGrid = config.getInt("entity-categories." + category + ".maxPerGrid", 5);
        
        // 计算实体所在的网格坐标
        int gridX = x / gridSize;
        int gridZ = z / gridSize;
        
        // 创建网格键
        String gridKey = gridX + "," + gridZ;
        
        // 使用ThreadLocal存储当前线程的网格计数，避免并发问题
        ThreadLocal<Map<String, Map<EntityType, Integer>>> threadLocalGridCount = ThreadLocal.withInitial(HashMap::new);
        Map<String, Map<EntityType, Integer>> gridCount = threadLocalGridCount.get();
        
        // 获取或创建当前网格的实体计数
        Map<EntityType, Integer> entityCountInGrid = gridCount.computeIfAbsent(gridKey, k -> new HashMap<>());
        
        // 统计当前网格内的实体数量
        int currentCount = entityCountInGrid.getOrDefault(type, 0);
        
        // 如果当前网格内的实体数量已经超过每个网格最大允许数量，应该清理该实体
        if (currentCount >= maxPerGrid) {
            return true;
        }
        
        // 否则，增加当前网格内该实体类型的计数
        entityCountInGrid.put(type, currentCount + 1);
        
        // 返回false，表示不应该清理该实体
        return false;
    }
    
    /**
     * 检查是否应该保留该实体
     * @param entity 要检查的实体
     * @return 是否应该保留该实体
     */
    private boolean shouldKeepEntity(Entity entity) {
        // 1. 检查实体是否被命名
        if (entity.getCustomName() != null) {
            return true;
        }
        
        // 2. 检查实体是否穿戴了装备（对于可穿戴装备的实体）
        if (hasEquipment(entity)) {
            return true;
        }
        
        // 3. 检查实体是否有仇恨目标（敌对生物）或被吸引
        if (hasTarget(entity)) {
            return true;
        }
        
        // 4. 检查实体是否在繁殖冷却期或有宝宝
        if (isBreedingOrHasBaby(entity)) {
            return true;
        }
        
        // 5. 检查实体是否是玩家的宠物或被驯服
        if (isTamed(entity)) {
            return true;
        }
        
        // 6. 检查实体是否是交易过的村民（有交易经验）
        if (isTradedVillager(entity)) {
            return true;
        }
        
        // 7. 检查实体是否被其他实体乘坐（如船、矿车）或正在乘坐其他实体
        if (isRiddenOrRiding(entity)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 检查实体是否是交易过的村民
     * @param entity 要检查的实体
     * @return 是否是交易过的村民
     */
    private boolean isTradedVillager(Entity entity) {
        try {
            if (entity instanceof org.bukkit.entity.Villager) {
                org.bukkit.entity.Villager villager = (org.bukkit.entity.Villager) entity;
                // 检查村民是否有交易经验（已交易过）
                if (villager.getVillagerExperience() > 0) {
                    return true;
                }
            }
        } catch (Exception e) {
            // 忽略任何异常，避免插件崩溃
        }
        return false;
    }
    
    /**
     * 检查实体是否被其他实体乘坐或正在乘坐其他实体
     * @param entity 要检查的实体
     * @return 是否被乘坐或正在乘坐
     */
    private boolean isRiddenOrRiding(Entity entity) {
        try {
            // 检查实体是否正在乘坐其他实体
            if (entity.getVehicle() != null) {
                return true;
            }
            
            // 检查实体是否被其他实体乘坐
            if (entity.getPassengers().size() > 0) {
                return true;
            }
        } catch (Exception e) {
            // 忽略任何异常，避免插件崩溃
        }
        return false;
    }
    
    /**
     * 检查实体是否穿戴了玩家给予的装备（不包括自然生成的战利品装备）
     * @param entity 要检查的实体
     * @return 是否穿戴了玩家给予的装备
     */
    private boolean hasEquipment(Entity entity) {
        try {
            // 对于不同类型的实体，检查不同的装备槽
            if (entity instanceof org.bukkit.entity.LivingEntity) {
                org.bukkit.entity.LivingEntity livingEntity = (org.bukkit.entity.LivingEntity) entity;
                
                // 获取装备
                org.bukkit.inventory.EntityEquipment equipment = livingEntity.getEquipment();
                if (equipment == null) {
                    return false;
                }
                
                // 检查各个装备槽
                return checkEquipmentItem(equipment.getHelmet()) || 
                       checkEquipmentItem(equipment.getChestplate()) || 
                       checkEquipmentItem(equipment.getLeggings()) || 
                       checkEquipmentItem(equipment.getBoots()) || 
                       checkEquipmentItem(equipment.getItemInMainHand()) || 
                       checkEquipmentItem(equipment.getItemInOffHand());
            }
        } catch (Exception e) {
            // 忽略任何异常，避免插件崩溃
        }
        return false;
    }
    
    /**
     * 检查装备物品是否是玩家给予的（不是自然生成的战利品）
     * @param item 装备物品
     * @return 是否是玩家给予的装备
     */
    private boolean checkEquipmentItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        
        try {
            // 检查物品是否有自定义名称（玩家命名的装备）
            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                return true;
            }
            
            // 检查物品是否有附魔（玩家附魔或使用了附魔书）
            if (item.hasItemMeta() && item.getItemMeta().hasEnchants()) {
                return true;
            }
            
            // 检查物品是否有修复次数（玩家修复过的装备）
            if (item.getDurability() < item.getType().getMaxDurability() && item.getDurability() > 0) {
                return true;
            }
            
            // 检查物品是否有特定的NBT标签，表明它不是自然生成的
            // 使用反射获取物品的NBT数据
            try {
                // 获取CraftItemStack实例
                Class<?> craftItemStackClass = Class.forName("org.bukkit.craftbukkit.v1_21_R1.inventory.CraftItemStack");
                Method asNMSCopyMethod = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class);
                Object nmsItemStack = asNMSCopyMethod.invoke(null, item);
                
                // 获取NBT标签
                Class<?> nmsItemStackClass = Class.forName("net.minecraft.world.item.ItemStack");
                Method getTagMethod = nmsItemStackClass.getMethod("getTag");
                Object nbtTag = getTagMethod.invoke(nmsItemStack);
                
                // 检查NBT标签中是否有表示玩家放置的标记
                if (nbtTag != null) {
                    Class<?> nbtTagClass = Class.forName("net.minecraft.nbt.Tag");
                    Class<?> compoundTagClass = Class.forName("net.minecraft.nbt.CompoundTag");
                    
                    if (compoundTagClass.isInstance(nbtTag)) {
                        Method containsMethod = compoundTagClass.getMethod("contains", String.class);
                        
                        // 检查是否有特定标签表示不是自然生成的
                        return (boolean) containsMethod.invoke(nbtTag, "Enchantments") ||
                               (boolean) containsMethod.invoke(nbtTag, "display") ||
                               (boolean) containsMethod.invoke(nbtTag, "RepairCost");
                    }
                }
            } catch (Exception e) {
                // 忽略反射异常，使用其他方法判断
            }
            
            // 对于某些特殊物品，如钻石盔甲、金盔甲等，玩家通常不会让它们自然生成
            // 这里我们可以添加一些逻辑来判断，但需要小心处理
            
            // 检查物品是否是稀有物品
            Set<Material> rareMaterials = new HashSet<>();
            rareMaterials.add(Material.DIAMOND_HELMET);
            rareMaterials.add(Material.DIAMOND_CHESTPLATE);
            rareMaterials.add(Material.DIAMOND_LEGGINGS);
            rareMaterials.add(Material.DIAMOND_BOOTS);
            rareMaterials.add(Material.NETHERITE_HELMET);
            rareMaterials.add(Material.NETHERITE_CHESTPLATE);
            rareMaterials.add(Material.NETHERITE_LEGGINGS);
            rareMaterials.add(Material.NETHERITE_BOOTS);
            rareMaterials.add(Material.ELYTRA);
            
            if (rareMaterials.contains(item.getType())) {
                return true;
            }
            
        } catch (Exception e) {
            // 忽略任何异常，避免插件崩溃
        }
        
        // 默认返回false，认为是自然生成的装备
        return false;
    }
    
    /**
     * 检查实体是否有仇恨目标或被吸引
     * @param entity 要检查的实体
     * @return 是否有仇恨目标或被吸引
     */
    private boolean hasTarget(Entity entity) {
        try {
            // 检查实体是否有目标
            if (entity instanceof org.bukkit.entity.Monster) {
                org.bukkit.entity.Monster monster = (org.bukkit.entity.Monster) entity;
                if (monster.getTarget() != null) {
                    return true;
                }
            }
            
            // 检查猪灵是否被金锭吸引（猪灵会追踪金锭）
            if (entity instanceof org.bukkit.entity.Piglin) {
                org.bukkit.entity.Piglin piglin = (org.bukkit.entity.Piglin) entity;
                if (piglin.getTarget() != null) {
                    return true;
                }
            }
            
            // 检查猪是否被胡萝卜吸引（使用反射检查是否有目标）
            if (entity instanceof org.bukkit.entity.Pig) {
                try {
                    java.lang.reflect.Method getTargetMethod = entity.getClass().getMethod("getTarget");
                    Object target = getTargetMethod.invoke(entity);
                    if (target != null) {
                        return true;
                    }
                } catch (Exception e) {
                    // 忽略反射异常
                }
            }
            
            // 检查其他可能有目标的实体类型
            if (entity instanceof org.bukkit.entity.Rabbit) {
                try {
                    java.lang.reflect.Method getTargetMethod = entity.getClass().getMethod("getTarget");
                    Object target = getTargetMethod.invoke(entity);
                    if (target != null) {
                        return true;
                    }
                } catch (Exception e) {
                    // 忽略反射异常
                }
            }
            
            // 检查是否有其他追踪行为（如使用AI目标选择器）
            try {
                // 获取实体的AI目标选择器
                java.lang.reflect.Method getGoalSelectorMethod = entity.getClass().getMethod("getGoalSelector");
                Object goalSelector = getGoalSelectorMethod.invoke(entity);
                
                // 检查是否有目标追踪的目标
                if (goalSelector != null) {
                    java.lang.reflect.Method getGoalsMethod = goalSelector.getClass().getMethod("getGoals");
                    Object goals = getGoalsMethod.invoke(goalSelector);
                    
                    // 如果目标列表不为空，说明实体可能有活动的目标
                    if (goals instanceof java.util.Collection && !((java.util.Collection<?>) goals).isEmpty()) {
                        return true;
                    }
                }
            } catch (Exception e) {
                // 忽略反射异常
            }
        } catch (Exception e) {
            // 忽略任何异常，避免插件崩溃
        }
        return false;
    }
    
    /**
     * 检查实体是否在繁殖冷却期或有宝宝
     * @param entity 要检查的实体
     * @return 是否在繁殖冷却期或有宝宝
     */
    private boolean isBreedingOrHasBaby(Entity entity) {
        try {
            // 检查实体是否是可繁殖的动物
            if (entity instanceof org.bukkit.entity.Animals) {
                org.bukkit.entity.Animals animal = (org.bukkit.entity.Animals) entity;
                
                // 检查是否在繁殖冷却期（使用反射调用getLoveModeTicks方法）
                try {
                    java.lang.reflect.Method getLoveModeTicksMethod = animal.getClass().getMethod("getLoveModeTicks");
                    int loveModeTicks = (int) getLoveModeTicksMethod.invoke(animal);
                    if (loveModeTicks > 0) {
                        return true;
                    }
                } catch (Exception e) {
                    // 忽略反射异常，使用其他方法
                }
                
                // 检查附近是否有宝宝
                List<Entity> nearbyEntities = entity.getNearbyEntities(5, 5, 5);
                for (Entity nearby : nearbyEntities) {
                    if (nearby.getType() == entity.getType() && nearby instanceof org.bukkit.entity.Animals) {
                        org.bukkit.entity.Animals nearbyAnimal = (org.bukkit.entity.Animals) nearby;
                        if (!nearbyAnimal.isAdult()) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 忽略任何异常，避免插件崩溃
        }
        return false;
    }
    
    /**
     * 检查实体是否是玩家的宠物或被驯服
     * @param entity 要检查的实体
     * @return 是否是玩家的宠物或被驯服
     */
    private boolean isTamed(Entity entity) {
        try {
            // 检查狼、猫、马等是否被驯服
            if (entity instanceof org.bukkit.entity.Tameable) {
                org.bukkit.entity.Tameable tameable = (org.bukkit.entity.Tameable) entity;
                if (tameable.isTamed()) {
                    return true;
                }
            }
        } catch (Exception e) {
            // 忽略任何异常，避免插件崩溃
        }
        return false;
    }
    

    
    // -------------------------- 事件处理方法 --------------------------
    
    /**
     * 处理液体流动事件
     */
    @EventHandler
    public void onBlockFromTo(BlockFromToEvent event) {
        if (!config.getBoolean("liquid.enable", true)) {
            return;
        }
        
        Block block = event.getBlock();
        if (liquidBlocks.contains(block.getType())) {
            liquidEventCount++;
            
            // 获取当前TPS状态对应的液体限制
            String tpsKey = getCurrentTPSConfigKey();
            int cancelLimit = config.getInt("liquid.times." + tpsKey + ".cancel", 8000);
            
            // 检查是否达到取消液体流动的限制
            if (liquidEventCount >= cancelLimit) {
                event.setCancelled(true);
            }
        }
    }
    
    /**
     * 处理玩家倒液体事件
     */
    @EventHandler
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!config.getBoolean("liquid.enable", true)) {
            return;
        }
        
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item != null && liquidItems.contains(item.getType())) {
            // 获取当前TPS状态对应的液体限制
            String tpsKey = getCurrentTPSConfigKey();
            int limit = config.getInt("liquid.times." + tpsKey + ".limit", 5000);
            
            // 检查是否达到限制
            if (liquidEventCount >= limit) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + getLang("server_busy"));
            }
        }
    }
    
    // 红石事件监听 - 使用多种事件来全面检测高频红石
    
    // 监听红石部件状态变化（红石线路、中继器、比较器等）
    @EventHandler
    public void onBlockRedstone(BlockRedstoneEvent event) {
        Block block = event.getBlock();
        // 只在红石能量变化时处理，避免重复计数
        if (event.getNewCurrent() != event.getOldCurrent()) {
            handleRedstoneActivity(block);
        }
    }
    
    // 监听活塞事件
    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent event) {
        handleRedstoneActivity(event.getBlock());
    }
    
    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent event) {
        handleRedstoneActivity(event.getBlock());
    }
    
    /**
     * 处理红石活动
     * @param block 触发事件的方块
     */
    private void handleRedstoneActivity(Block block) {
        if (!config.getBoolean("redstone.enable", true)) {
            return;
        }
        
        World world = block.getWorld();
        
        // 检查是否在忽略世界中
        if (redstoneIgnoreWorlds.contains(world.getName())) {
            return;
        }
        
        // 增加红石事件计数
        redstoneEventCount++;
        
        // 获取当前TPS状态对应的红石限制
        String tpsKey = getCurrentTPSConfigKey();
        int tipTimes = config.getInt("redstone.times." + tpsKey + ".tipTimes", 100);
        int removeTimes = config.getInt("redstone.times." + tpsKey + ".removeTimes", 140);
        
        // 创建唯一消息键，避免相同坐标短时间内重复发送相同消息
        String messageKey = block.getX() + "," + block.getY() + "," + block.getZ() + "," + block.getWorld().getName();
        long now = System.currentTimeMillis();
        
        // 检查是否需要提示
        if (redstoneEventCount >= tipTimes && redstoneEventCount < removeTimes) {
            // 提示冷却时间（毫秒）
            long coolDown = config.getLong("redstone.tip.consoleTipMinInterval", 500);
            
            // 检查是否在冷却期内
            if (now - redstoneMessageCooldowns.getOrDefault(messageKey, 0L) > coolDown) {
                // 提示逻辑
                String message = getLang("format_redstone_tip", block.getX(), block.getY(), block.getZ(), block.getWorld().getName());
                String coloredMessage = ChatColor.translateAlternateColorCodes('&', message);
                
                // 控制台提示
                if (config.getBoolean("redstone.tip.console", true)) {
                    Bukkit.getConsoleSender().sendMessage(coloredMessage);
                }
                
                // 游戏内提示给OP
                if (config.getBoolean("redstone.tip.ingame", true)) {
                    long ingameCoolDown = config.getLong("redstone.tip.ingameTipMinInterval", 5000);
                    if (now - lastRedstoneTipTime > ingameCoolDown) {
                        // 发送给所有在线OP玩家
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            if (player.isOp()) {
                                player.sendMessage(coloredMessage);
                            }
                        }
                        lastRedstoneTipTime = now;
                    }
                }
                
                // 更新冷却时间
                redstoneMessageCooldowns.put(messageKey, now);
            }
        }
        
        // 检查是否需要移除红石方块
        if (redstoneEventCount >= removeTimes) {
            // 清理冷却时间（毫秒）
            long coolDown = config.getLong("redstone.tip.consoleTipMinInterval", 500);
            
            // 检查是否在冷却期内
            if (now - redstoneMessageCooldowns.getOrDefault(messageKey + "_cleanup", 0L) > coolDown) {
                // 移除红石方块逻辑
                String message = getLang("format_redstone_tip3", block.getX(), block.getY(), block.getZ(), block.getWorld().getName(), config.getBoolean("redstone.drop", false) ? "掉落" : "清除");
                String coloredMessage = ChatColor.translateAlternateColorCodes('&', message);
                
                // 控制台提示
                if (config.getBoolean("redstone.tip.console", true)) {
                    Bukkit.getConsoleSender().sendMessage(coloredMessage);
                }
                
                // 游戏内提示给OP
                if (config.getBoolean("redstone.tip.ingame", true)) {
                    long ingameCoolDown = config.getLong("redstone.tip.ingameTipMinInterval", 5000);
                    if (now - lastRedstoneCleanupTipTime > ingameCoolDown) {
                        // 发送给所有在线OP玩家
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            if (player.isOp()) {
                                player.sendMessage(coloredMessage);
                            }
                        }
                        lastRedstoneCleanupTipTime = now;
                    }
                }
                
                // 更新清理冷却时间
                redstoneMessageCooldowns.put(messageKey + "_cleanup", now);
                
                // 清理红石方块
                cleanupRedstoneBlocks(block);
                
                // 如果需要重置计数器
                if (config.getBoolean("redstone.reset", false)) {
                    redstoneEventCount = 0;
                }
            }
        }
    }
    
    /**
     * 清理红石方块
     * @param centerBlock 中心方块
     */
    private void cleanupRedstoneBlocks(Block centerBlock) {
        World world = centerBlock.getWorld();
        int gridSize = config.getInt("redstone.gridSize", 20);
        
        // 计算清理范围
        int minX = centerBlock.getX() - gridSize / 2;
        int maxX = centerBlock.getX() + gridSize / 2;
        int minY = Math.max(0, centerBlock.getY() - 5);
        int maxY = Math.min(world.getMaxHeight() - 1, centerBlock.getY() + 5);
        int minZ = centerBlock.getZ() - gridSize / 2;
        int maxZ = centerBlock.getZ() + gridSize / 2;
        
        // 遍历范围内的方块
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    Material type = block.getType();
                    
                    // 检查是否需要清理该方块
                    if (shouldRemoveRedstoneBlock(type)) {
                        // 根据配置决定是掉落还是清除
                        if (config.getBoolean("redstone.drop", false)) {
                            block.breakNaturally();
                        } else {
                            block.setType(Material.AIR);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 检查是否应该移除该红石方块
     * @param type 方块类型
     * @return 是否应该移除
     */
    private boolean shouldRemoveRedstoneBlock(Material type) {
        // 如果配置为清理所有方块
        if (config.getBoolean("redstone.allBlocks", false)) {
            return true;
        }
        
        // 否则检查是否在配置的移除列表中
        return redstoneBlocks.contains(type);
    }
    
    /**
     * 处理作物生长事件
     */
    @EventHandler
    public void onBlockGrow(BlockGrowEvent event) {
        handleCropEvent(event.getBlock());
    }
    
    /**
     * 处理方块蔓延事件（如蘑菇蔓延）
     */
    @EventHandler
    public void onBlockSpread(BlockSpreadEvent event) {
        handleCropEvent(event.getBlock());
    }
    
    /**
     * 处理作物事件
     */
    private void handleCropEvent(Block block) {
        if (!config.getBoolean("crop.enable", false)) {
            return;
        }
        
        World world = block.getWorld();
        if (cropIgnoreWorlds.contains(world.getName())) {
            return;
        }
        
        cropEventCount++;
        
        long now = System.currentTimeMillis();
        
        // 检查是否达到作物事件上限
        if (cropEventCount >= config.getInt("crop.max", 30)) {
            // 提示
            if (now - lastCropTipTime > config.getLong("crop.tip.ingameTipMinInterval", 5000)) {
                lastCropTipTime = now;
                
                // 控制台提示
                if (config.getBoolean("crop.tip.console", true)) {
                    getLogger().warning("作物生长事件过多！当前计数: " + cropEventCount + "/" + config.getInt("crop.max", 30));
                }
                
                // 游戏内提示
                if (config.getBoolean("crop.tip.ingame", false)) {
                    Bukkit.broadcastMessage(ChatColor.RED + "[Clear插件] 警告：作物生长事件过多，可能导致服务器卡顿！");
                }
            }
            
            // 重置计数器
            if (config.getBoolean("crop.reset", false)) {
                cropEventCount = 0;
            }
        }
    }
}
