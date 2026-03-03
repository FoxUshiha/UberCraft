package com.foxsrv.ubercraft;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import com.foxsrv.coincard.CoinCardPlugin;
import com.foxsrv.coincard.CoinCardPlugin.CoinCardAPI;
import com.foxsrv.coincard.CoinCardPlugin.TransferCallback;
import com.foxsrv.coincard.CoinCardPlugin.BalanceCallback;
import com.foxsrv.coincard.CoinCardPlugin.DecimalUtil;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class UberCraft extends JavaPlugin implements Listener {
    
    private static UberCraft instance;
    private File dataFile;
    private FileConfiguration dataConfig;
    private String prefix;
    private String serverCard;
    private BigDecimal distanceWorth;
    
    // Warps storage
    private final Map<String, Warp> warps = new ConcurrentHashMap<>();
    
    // CoinCard API
    private CoinCardAPI coinAPI;
    
    // Thread-safe maps
    private final Map<UUID, UberRequest> activeRequests = new ConcurrentHashMap<>();
    private final Map<UUID, UberRide> activeRides = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> expirationTasks = new ConcurrentHashMap<>();
    private final Map<UUID, CompassTarget> compassTargets = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> compassTrackers = new ConcurrentHashMap<>();
    
    // Payment queue
    private final ConcurrentLinkedQueue<PaymentTask> paymentQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean processingQueue = new AtomicBoolean(false);
    
    // Cache de mundos
    private final Map<String, World> worldCache = new ConcurrentHashMap<>();
    
    // ================== CLASSES INTERNAS ==================
    
    public enum UberStatus {
        WAITING, ACTIVE, PICKUP, EN_ROUTE, COMPLETED, CANCELLED, EXPIRED
    }
    
    public enum CancelType {
        PLAYER_CANCELLED, UBER_CANCELLED
    }
    
    public class Warp {
        private final String name;
        private final Location location;
        private final ItemStack icon;
        private final String worldName;
        
        public Warp(String name, Location location, ItemStack icon) {
            this.name = name;
            this.location = location.clone();
            this.icon = icon.clone();
            this.worldName = location.getWorld().getName();
        }
        
        public String getName() { return name; }
        public Location getLocation() { return location.clone(); }
        public ItemStack getIcon() { return icon.clone(); }
        public String getWorldName() { return worldName; }
    }
    
    public class UberRequest {
        private final UUID playerUUID;
        private final String playerName;
        private final Location pickupLocation;
        private final Location destinationLocation;
        private final String destinationName;
        private final long timestamp;
        private final BigDecimal estimatedPrice;
        private UberStatus status;
        private UUID uberUUID;
        
        public UberRequest(UUID playerUUID, String playerName, Location pickupLocation, 
                          Location destinationLocation, String destinationName, BigDecimal estimatedPrice) {
            this.playerUUID = playerUUID;
            this.playerName = playerName;
            this.pickupLocation = pickupLocation.clone();
            this.destinationLocation = destinationLocation.clone();
            this.destinationName = destinationName;
            this.timestamp = System.currentTimeMillis();
            this.estimatedPrice = estimatedPrice;
            this.status = UberStatus.WAITING;
        }
        
        public UUID getPlayerUUID() { return playerUUID; }
        public String getPlayerName() { return playerName; }
        public Location getPickupLocation() { return pickupLocation.clone(); }
        public Location getDestinationLocation() { return destinationLocation.clone(); }
        public String getDestinationName() { return destinationName; }
        public long getTimestamp() { return timestamp; }
        public BigDecimal getEstimatedPrice() { return estimatedPrice; }
        public UberStatus getStatus() { return status; }
        public void setStatus(UberStatus status) { this.status = status; }
        public UUID getUberUUID() { return uberUUID; }
        public void setUberUUID(UUID uberUUID) { this.uberUUID = uberUUID; }
    }
    
    public class UberRide {
        private final UUID playerUUID;
        private final String playerName;
        private final UUID uberUUID;
        private final String uberName;
        private final Location pickupLocation;
        private final Location destinationLocation;
        private final String destinationName;
        private final BigDecimal price;
        private long startTime;
        private UberStatus status;
        private boolean playerBoarded = false;
        
        public UberRide(UUID playerUUID, String playerName, UUID uberUUID, String uberName,
                       Location pickupLocation, Location destinationLocation, 
                       String destinationName, BigDecimal price) {
            this.playerUUID = playerUUID;
            this.playerName = playerName;
            this.uberUUID = uberUUID;
            this.uberName = uberName;
            this.pickupLocation = pickupLocation.clone();
            this.destinationLocation = destinationLocation.clone();
            this.destinationName = destinationName;
            this.price = price;
            this.startTime = System.currentTimeMillis();
            this.status = UberStatus.ACTIVE;
            this.playerBoarded = false;
        }
        
        public UUID getPlayerUUID() { return playerUUID; }
        public String getPlayerName() { return playerName; }
        public UUID getUberUUID() { return uberUUID; }
        public String getUberName() { return uberName; }
        public Location getPickupLocation() { return pickupLocation.clone(); }
        public Location getDestinationLocation() { return destinationLocation.clone(); }
        public String getDestinationName() { return destinationName; }
        public BigDecimal getPrice() { return price; }
        public long getStartTime() { return startTime; }
        public UberStatus getStatus() { return status; }
        public void setStatus(UberStatus status) { this.status = status; }
        public boolean hasPlayerBoarded() { return playerBoarded; }
        public void setPlayerBoarded(boolean boarded) { this.playerBoarded = boarded; }
    }
    
    public class CompassTarget {
        private final Location location;
        private final String targetName;
        private final UUID rideId;
        private final TargetType type;
        
        public enum TargetType {
            PICKUP, DESTINATION
        }
        
        public CompassTarget(Location location, String targetName, UUID rideId, TargetType type) {
            this.location = location.clone();
            this.targetName = targetName;
            this.rideId = rideId;
            this.type = type;
        }
        
        public Location getLocation() { return location.clone(); }
        public String getTargetName() { return targetName; }
        public UUID getRideId() { return rideId; }
        public TargetType getType() { return type; }
    }
    
    public class PaymentTask {
        private final String fromCard;
        private final String toCard;
        private final BigDecimal amount;
        private final String reason;
        private final PaymentCallback callback;
        
        public PaymentTask(String fromCard, String toCard, BigDecimal amount, String reason, PaymentCallback callback) {
            this.fromCard = fromCard;
            this.toCard = toCard;
            this.amount = amount;
            this.reason = reason;
            this.callback = callback;
        }
        
        public String getFromCard() { return fromCard; }
        public String getToCard() { return toCard; }
        public BigDecimal getAmount() { return amount; }
        public String getReason() { return reason; }
        public PaymentCallback getCallback() { return callback; }
    }
    
    public interface PaymentCallback {
        void onSuccess(String txId);
        void onFailure(String error);
    }
    
    // ================== COLOR UTILS ==================
    
    public String colorize(String message) {
        if (message == null) return "";
        return message.replace("&", "§");
    }
    
    // ================== MENU MANAGER ==================
    
    public class MenuManager {
        
        public void openUberMenu(Player player) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    Inventory inv = Bukkit.createInventory(null, 54, 
                        colorize(getConfig().getString("messages.uber-gui-title", "&6&lUber Requests")));
                    
                    if (dataConfig.contains("requests")) {
                        for (String key : dataConfig.getConfigurationSection("requests").getKeys(false)) {
                            String path = "requests." + key;
                            String playerName = dataConfig.getString(path + ".player");
                            String destination = dataConfig.getString(path + ".destination");
                            String world = dataConfig.getString(path + ".world");
                            double x = dataConfig.getDouble(path + ".x");
                            double y = dataConfig.getDouble(path + ".y");
                            double z = dataConfig.getDouble(path + ".z");
                            String price = dataConfig.getString(path + ".price");
                            long timestamp = dataConfig.getLong(path + ".timestamp");
                            
                            if (playerName == null || world == null) continue;
                            
                            // Check if request expired (1 minute)
                            if (System.currentTimeMillis() - timestamp > 60000) {
                                handleExpiredRequest(key, playerName, new BigDecimal(price));
                                continue;
                            }
                            
                            World bukkitWorld = getCachedWorld(world);
                            if (bukkitWorld == null) continue;
                            
                            ItemStack head = createPlayerHead(playerName, destination, world, x, y, z, price);
                            if (head != null) {
                                inv.addItem(head);
                            }
                        }
                    }
                    
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            player.openInventory(inv);
                        }
                    }.runTask(UberCraft.this);
                }
            }.runTaskAsynchronously(UberCraft.this);
        }
        
        public void openWarpMenu(Player player) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    Inventory inv = Bukkit.createInventory(null, 54, 
                        colorize(getConfig().getString("messages.warp-gui-title", "&6&lSelect Destination Warp")));
                    
                    String currentWorld = player.getWorld().getName();
                    
                    for (Warp warp : warps.values()) {
                        // Only show warps from the same world
                        if (!warp.getWorldName().equals(currentWorld)) {
                            continue;
                        }
                        
                        ItemStack icon = warp.getIcon().clone();
                        ItemMeta meta = icon.getItemMeta();
                        
                        meta.setDisplayName(colorize("&6&l" + warp.getName()));
                        
                        List<String> lore = new ArrayList<>();
                        lore.add(colorize("&7World: &f" + warp.getWorldName()));
                        lore.add(colorize("&7Location: &f" + 
                            (int)warp.getLocation().getX() + " " + 
                            (int)warp.getLocation().getY() + " " + 
                            (int)warp.getLocation().getZ()));
                        
                        // Calculate price
                        double distance = player.getLocation().distance(warp.getLocation());
                        BigDecimal price = BigDecimal.valueOf(distance).multiply(distanceWorth)
                            .setScale(8, RoundingMode.DOWN);
                        if (price.compareTo(BigDecimal.ZERO) <= 0) {
                            price = new BigDecimal("0.00000001");
                        }
                        
                        lore.add(colorize("&7Price: &a" + formatCoin(price)));
                        lore.add("");
                        lore.add(colorize("&aClick to request Uber to this warp"));
                        
                        meta.setLore(lore);
                        icon.setItemMeta(meta);
                        
                        inv.addItem(icon);
                    }
                    
                    // If no warps in this world, add a placeholder
                    if (inv.firstEmpty() == 0) {
                        ItemStack noWarps = new ItemStack(Material.BARRIER);
                        ItemMeta meta = noWarps.getItemMeta();
                        meta.setDisplayName(colorize("&c&lNo Warps Available"));
                        List<String> lore = new ArrayList<>();
                        lore.add(colorize("&7There are no warps configured"));
                        lore.add(colorize("&7for this world."));
                        meta.setLore(lore);
                        noWarps.setItemMeta(meta);
                        inv.setItem(22, noWarps);
                    }
                    
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            player.openInventory(inv);
                        }
                    }.runTask(UberCraft.this);
                }
            }.runTaskAsynchronously(UberCraft.this);
        }
        
        private ItemStack createPlayerHead(String playerName, String destination, String world, 
                                          double x, double y, double z, String price) {
            try {
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(playerName));
                meta.setDisplayName(colorize("&6&l" + playerName));
                
                List<String> lore = new ArrayList<>();
                lore.add(colorize("&7Destination: &f" + destination));
                lore.add(colorize("&7Location: &f" + world + " " + (int)x + " " + (int)y + " " + (int)z));
                lore.add(colorize("&7Price: &a" + price));
                lore.add("");
                lore.add(colorize("&aClick to accept this Uber ride"));
                meta.setLore(lore);
                
                head.setItemMeta(meta);
                return head;
            } catch (Exception e) {
                return null;
            }
        }
        
        private void handleExpiredRequest(String key, String playerName, BigDecimal price) {
            dataConfig.set("requests." + key, null);
            saveData();
            
            // Refund player (90% of price)
            BigDecimal refund = price.multiply(new BigDecimal("0.9")).setScale(8, RoundingMode.DOWN);
            
            Player player = Bukkit.getPlayerExact(playerName);
            if (player != null && player.isOnline()) {
                player.sendMessage(colorize(getPrefix() + getConfig().getString("messages.request-expired", 
                    "&cYour Uber request expired! &a" + formatCoin(refund) + " &chas been refunded.")));
            }
            
            // Add refund to payment queue
            addToPaymentQueue(serverCard, getPlayerCard(playerName), refund, "REFUND_EXPIRED", 
                new PaymentCallback() {
                    @Override
                    public void onSuccess(String txId) {
                        getLogger().info("Expired request refunded to " + playerName + ": " + refund + " tx=" + txId);
                    }
                    
                    @Override
                    public void onFailure(String error) {
                        getLogger().warning("Failed to refund expired request to " + playerName + ": " + error);
                    }
                });
        }
    }
    
    // ================== COMMAND EXECUTORS ==================
    
    public class UberCommand implements CommandExecutor, TabCompleter {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Command only for players!");
                return true;
            }
            
            Player player = (Player) sender;
            
            if (args.length == 0) {
                showHelp(player);
                return true;
            }
            
            String sub = args[0].toLowerCase();
            
            switch (sub) {
                case "x":
                case "xyz":
                    if (args.length < 4) {
                        player.sendMessage(colorize(getPrefix() + " &cUse: /uber x y z [world]"));
                        return true;
                    }
                    handleXYZRequest(player, args);
                    break;
                    
                case "warp":
                    if (!player.hasPermission("uber.player")) {
                        player.sendMessage(colorize(getPrefix() + getConfig().getString("messages.no-permission")));
                        return true;
                    }
                    // Always open warp selection menu
                    new MenuManager().openWarpMenu(player);
                    break;
                    
                case "admin":
                    if (!player.hasPermission("uber.admin")) {
                        player.sendMessage(colorize(getPrefix() + getConfig().getString("messages.no-permission")));
                        return true;
                    }
                    if (args.length < 2) {
                        showAdminHelp(player);
                        return true;
                    }
                    handleAdminCommand(player, args);
                    break;
                    
                case "cancel":
                    handleCancel(player);
                    break;
                    
                default:
                    showHelp(player);
                    break;
            }
            
            return true;
        }
        
        private void showHelp(Player player) {
            player.sendMessage(colorize("&6=== UberCraft v1.0 ==="));
            player.sendMessage(colorize("&7/uber x y z [world] &f- Request Uber to coordinates"));
            player.sendMessage(colorize("&7/uber warp &f- Open warp selection menu"));
            player.sendMessage(colorize("&7/uber cancel &f- Cancel your current Uber"));
            if (player.hasPermission("uber.admin")) {
                player.sendMessage(colorize("&7/uber admin set <name> &f- Create a warp with item in hand"));
                player.sendMessage(colorize("&7/uber admin unset <name> &f- Remove a warp"));
            }
            player.sendMessage(colorize("&7/ubergui &f- Open Uber requests menu (Ubers only)"));
        }
        
        private void showAdminHelp(Player player) {
            player.sendMessage(colorize("&6=== UberCraft Admin ==="));
            player.sendMessage(colorize("&7/uber admin set <name> &f- Create a warp with item in hand"));
            player.sendMessage(colorize("&7/uber admin unset <name> &f- Remove a warp"));
        }
        
        private void handleAdminCommand(Player player, String[] args) {
            String adminSub = args[1].toLowerCase();
            
            switch (adminSub) {
                case "set":
                    if (args.length < 3) {
                        player.sendMessage(colorize(getPrefix() + " &cUse: /uber admin set <name>"));
                        return;
                    }
                    handleSetWarp(player, args[2]);
                    break;
                    
                case "unset":
                    if (args.length < 3) {
                        player.sendMessage(colorize(getPrefix() + " &cUse: /uber admin unset <name>"));
                        return;
                    }
                    handleUnsetWarp(player, args[2]);
                    break;
                    
                default:
                    showAdminHelp(player);
                    break;
            }
        }
        
        private void handleSetWarp(Player player, String name) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item == null || item.getType() == Material.AIR) {
                player.sendMessage(colorize(getPrefix() + " &cYou must hold an item to use as warp icon!"));
                return;
            }
            
            String warpName = name.toLowerCase();
            Warp warp = new Warp(warpName, player.getLocation(), item.clone());
            warps.put(warpName, warp);
            
            // Save to config
            String path = "warps." + warpName;
            dataConfig.set(path + ".name", warpName);
            dataConfig.set(path + ".world", player.getWorld().getName());
            dataConfig.set(path + ".x", player.getLocation().getX());
            dataConfig.set(path + ".y", player.getLocation().getY());
            dataConfig.set(path + ".z", player.getLocation().getZ());
            dataConfig.set(path + ".icon", item.getType().toString());
            dataConfig.set(path + ".icon-data", item.getDurability());
            saveData();
            
            player.sendMessage(colorize(getPrefix() + " &aWarp &6" + name + " &acreated!"));
        }
        
        private void handleUnsetWarp(Player player, String name) {
            String warpName = name.toLowerCase();
            if (warps.remove(warpName) == null) {
                player.sendMessage(colorize(getPrefix() + " &cWarp not found!"));
                return;
            }
            
            dataConfig.set("warps." + warpName, null);
            saveData();
            
            player.sendMessage(colorize(getPrefix() + " &aWarp &6" + name + " &aremoved!"));
        }
        
        private void handleXYZRequest(Player player, String[] args) {
            try {
                double x = Double.parseDouble(args[1]);
                double y = Double.parseDouble(args[2]);
                double z = Double.parseDouble(args[3]);
                
                World world = player.getWorld();
                if (args.length >= 5) {
                    World targetWorld = Bukkit.getWorld(args[4]);
                    if (targetWorld != null) {
                        world = targetWorld;
                    }
                }
                
                Location dest = new Location(world, x, y, z);
                createUberRequest(player, dest, "X:" + (int)x + " Y:" + (int)y + " Z:" + (int)z);
                
            } catch (NumberFormatException e) {
                player.sendMessage(colorize(getPrefix() + " &cInvalid coordinates!"));
            }
        }
        
        public void handleWarpRequest(Player player, Warp warp) {
            createUberRequest(player, warp.getLocation(), "Warp: " + warp.getName());
        }
        
        private void handleCancel(Player player) {
            // Check if player has an active request
            for (Map.Entry<UUID, UberRequest> entry : activeRequests.entrySet()) {
                if (entry.getValue().getPlayerUUID().equals(player.getUniqueId())) {
                    cancelRequest(player, entry.getKey(), entry.getValue());
                    return;
                }
            }
            
            // Check if player is in an active ride
            for (Map.Entry<UUID, UberRide> entry : activeRides.entrySet()) {
                UberRide ride = entry.getValue();
                if (ride.getPlayerUUID().equals(player.getUniqueId()) || 
                    ride.getUberUUID().equals(player.getUniqueId())) {
                    
                    // Determine who is cancelling
                    if (player.getUniqueId().equals(ride.getPlayerUUID())) {
                        // Player is cancelling the ride
                        cancelRide(player, entry.getKey(), ride, CancelType.PLAYER_CANCELLED);
                    } else {
                        // Uber is cancelling the ride
                        cancelRide(player, entry.getKey(), ride, CancelType.UBER_CANCELLED);
                    }
                    return;
                }
            }
            
            player.sendMessage(colorize(getPrefix() + " &cYou don't have an active Uber!"));
        }
        
        @Override
        public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
            List<String> completions = new ArrayList<>();
            
            if (args.length == 1) {
                completions.add("x");
                completions.add("xyz");
                completions.add("warp");
                if (sender.hasPermission("uber.admin")) {
                    completions.add("admin");
                }
                completions.add("cancel");
            } else if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
                if (sender.hasPermission("uber.admin")) {
                    completions.add("set");
                    completions.add("unset");
                }
            } else if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
                if (args[1].equalsIgnoreCase("unset")) {
                    completions.addAll(warps.keySet());
                }
            }
            
            return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        private void createUberRequest(Player player, Location destination, String destName) {
            // Check if player already has an active request
            for (UberRequest req : activeRequests.values()) {
                if (req.getPlayerUUID().equals(player.getUniqueId())) {
                    player.sendMessage(colorize(getPrefix() + " &cYou already have an active Uber request!"));
                    return;
                }
            }
            
            // Check if player is in an active ride
            for (UberRide ride : activeRides.values()) {
                if (ride.getPlayerUUID().equals(player.getUniqueId())) {
                    player.sendMessage(colorize(getPrefix() + " &cYou are already in an Uber ride!"));
                    return;
                }
            }
            
            // Calculate distance and price
            double distance = player.getLocation().distance(destination);
            BigDecimal price = BigDecimal.valueOf(distance).multiply(distanceWorth)
                .setScale(8, RoundingMode.DOWN);
            
            // Check minimum price
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                price = new BigDecimal("0.00000001");
            }
            
            // Check if player has enough balance
            String playerCard = getPlayerCard(player.getUniqueId());
            if (playerCard == null) {
                player.sendMessage(colorize(getPrefix() + " &cYou don't have a card set! Use /coin card <card>"));
                return;
            }
            
            final BigDecimal finalPrice = price;
            coinAPI.getBalance(playerCard, new BalanceCallback() {
                @Override
                public void onResult(double balance, String error) {
                    if (error != null) {
                        player.sendMessage(colorize(getPrefix() + " &cFailed to check balance: " + error));
                        return;
                    }
                    
                    BigDecimal playerBalance = BigDecimal.valueOf(balance);
                    if (playerBalance.compareTo(finalPrice) < 0) {
                        player.sendMessage(colorize(getPrefix() + " &cInsufficient balance! Need: " + 
                            formatCoin(finalPrice) + " Have: " + formatCoin(playerBalance)));
                        return;
                    }
                    
                    // Process payment to server
                    addToPaymentQueue(playerCard, serverCard, finalPrice, "UBER_REQUEST", 
                        new PaymentCallback() {
                            @Override
                            public void onSuccess(String txId) {
                                // Create request
                                UUID requestId = UUID.randomUUID();
                                UberRequest request = new UberRequest(
                                    player.getUniqueId(), player.getName(),
                                    player.getLocation(), destination, destName, finalPrice
                                );
                                
                                activeRequests.put(requestId, request);
                                
                                // Save to data.dat
                                String path = "requests." + requestId.toString();
                                dataConfig.set(path + ".player", player.getName());
                                dataConfig.set(path + ".destination", destName);
                                dataConfig.set(path + ".world", destination.getWorld().getName());
                                dataConfig.set(path + ".x", destination.getX());
                                dataConfig.set(path + ".y", destination.getY());
                                dataConfig.set(path + ".z", destination.getZ());
                                dataConfig.set(path + ".price", finalPrice.toString());
                                dataConfig.set(path + ".timestamp", System.currentTimeMillis());
                                saveData();
                                
                                // Schedule expiration
                                scheduleExpiration(requestId, player, finalPrice);
                                
                                // Notify player
                                player.sendMessage(colorize(getPrefix() + " &aUber requested! Price: &6" + 
                                    formatCoin(finalPrice) + " &aTransaction: &b" + txId));
                                player.sendMessage(colorize(getPrefix() + " &7Waiting for an Uber to accept..."));
                                
                                // Alert online Ubers
                                alertUbers(request);
                            }
                            
                            @Override
                            public void onFailure(String error) {
                                player.sendMessage(colorize(getPrefix() + " &cPayment failed: " + error));
                            }
                        });
                }
            });
        }
        
        private void cancelRequest(Player player, UUID requestId, UberRequest request) {
            // Remove request
            activeRequests.remove(requestId);
            dataConfig.set("requests." + requestId.toString(), null);
            saveData();
            
            // Cancel expiration task
            if (expirationTasks.containsKey(requestId)) {
                expirationTasks.get(requestId).cancel();
                expirationTasks.remove(requestId);
            }
            
            // Calculate refund (90%)
            BigDecimal refund = request.getEstimatedPrice().multiply(new BigDecimal("0.9"))
                .setScale(8, RoundingMode.DOWN);
            
            // Process refund
            addToPaymentQueue(serverCard, getPlayerCard(player.getUniqueId()), refund, "CANCEL_REFUND", 
                new PaymentCallback() {
                    @Override
                    public void onSuccess(String txId) {
                        player.sendMessage(colorize(getPrefix() + " &aRequest cancelled! Refunded: &6" + 
                            formatCoin(refund) + " &aTransaction: &b" + txId));
                    }
                    
                    @Override
                    public void onFailure(String error) {
                        player.sendMessage(colorize(getPrefix() + " &cRefund failed: " + error));
                    }
                });
        }
        
        private void cancelRide(Player player, UUID rideId, UberRide ride, CancelType cancelType) {
            // Remove ride
            activeRides.remove(rideId);
            
            // Remove compass from Uber
            Player uber = Bukkit.getPlayer(ride.getUberUUID());
            if (uber != null && uber.isOnline()) {
                removeCompass(uber);
            }
            
            // Calculate payments based on who cancelled (make them final for inner class)
            final BigDecimal uberPayment;
            final BigDecimal playerRefund;
            
            if (cancelType == CancelType.PLAYER_CANCELLED) {
                // Player cancelled - Uber gets 50%, Player gets 40% refund, Server keeps 10%
                uberPayment = ride.getPrice().multiply(new BigDecimal("0.5"))
                    .setScale(8, RoundingMode.DOWN);
                playerRefund = ride.getPrice().multiply(new BigDecimal("0.4"))
                    .setScale(8, RoundingMode.DOWN);
                
                getLogger().info(String.format("Ride cancelled by PLAYER: %s cancelled. Uber %s gets 50%% (%s), Player %s gets 40%% refund (%s), Server keeps 10%%",
                    ride.getPlayerName(), ride.getUberName(), formatCoin(uberPayment),
                    ride.getPlayerName(), formatCoin(playerRefund)));
                
            } else {
                // Uber cancelled - Player gets 90% refund, Uber gets 0%, Server keeps 10%
                uberPayment = BigDecimal.ZERO;
                playerRefund = ride.getPrice().multiply(new BigDecimal("0.9"))
                    .setScale(8, RoundingMode.DOWN);
                
                getLogger().info(String.format("Ride cancelled by UBER: %s cancelled. Player %s gets 90%% refund (%s), Uber gets 0%%, Server keeps 10%%",
                    ride.getUberName(), ride.getPlayerName(), formatCoin(playerRefund)));
            }
            
            // Store values needed for messages as final
            final String uberName = ride.getUberName();
            final String playerName = ride.getPlayerName();
            final BigDecimal ridePrice = ride.getPrice();
            final UUID uberUUID = ride.getUberUUID();
            final UUID playerUUID = ride.getPlayerUUID();
            
            // Pay Uber (if any)
            if (uberPayment.compareTo(BigDecimal.ZERO) > 0 && uber != null && uber.isOnline()) {
                final BigDecimal finalUberPayment = uberPayment;
                addToPaymentQueue(serverCard, getPlayerCard(uberUUID), finalUberPayment, "CANCEL_UBER_PAY", 
                    new PaymentCallback() {
                        @Override
                        public void onSuccess(String txId) {
                            if (uber.isOnline()) {
                                uber.sendMessage(colorize(getPrefix() + " &aRide cancelled! You received: &6" + 
                                    formatCoin(finalUberPayment) + " &aTransaction: &b" + txId));
                            }
                        }
                        
                        @Override
                        public void onFailure(String error) {
                            if (uber.isOnline()) {
                                uber.sendMessage(colorize(getPrefix() + " &cPayment failed: " + error));
                            }
                        }
                    });
            } else if (uber != null && uber.isOnline() && cancelType == CancelType.UBER_CANCELLED) {
                uber.sendMessage(colorize(getPrefix() + " &cYou cancelled the ride. No payment issued."));
            }
            
            // Refund player
            Player ridePlayer = Bukkit.getPlayer(playerUUID);
            if (ridePlayer != null && ridePlayer.isOnline()) {
                final BigDecimal finalPlayerRefund = playerRefund;
                final CancelType finalCancelType = cancelType;
                
                String refundMessage;
                if (cancelType == CancelType.PLAYER_CANCELLED) {
                    refundMessage = " &aRide cancelled! Refunded (40%): &6" + formatCoin(finalPlayerRefund);
                } else {
                    refundMessage = " &aRide cancelled by Uber! Refunded (90%): &6" + formatCoin(finalPlayerRefund);
                }
                final String finalRefundMessage = refundMessage;
                
                addToPaymentQueue(serverCard, getPlayerCard(playerUUID), finalPlayerRefund, "CANCEL_PLAYER_REFUND", 
                    new PaymentCallback() {
                        @Override
                        public void onSuccess(String txId) {
                            if (ridePlayer.isOnline()) {
                                ridePlayer.sendMessage(colorize(getPrefix() + finalRefundMessage + " &aTransaction: &b" + txId));
                            }
                        }
                        
                        @Override
                        public void onFailure(String error) {
                            if (ridePlayer.isOnline()) {
                                ridePlayer.sendMessage(colorize(getPrefix() + " &cRefund failed: " + error));
                            }
                        }
                    });
            }
        }
    }
    
    public class UberGuiCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Command only for players!");
                return true;
            }
            
            Player player = (Player) sender;
            
            if (!player.hasPermission("uber.uber")) {
                player.sendMessage(colorize(getPrefix() + getConfig().getString("messages.no-permission", 
                    "&cYou don't have permission!")));
                return true;
            }
            
            new MenuManager().openUberMenu(player);
            return true;
        }
    }
    
    // ================== LISTENER ==================
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();
        String uberGuiTitle = colorize(getConfig().getString("messages.uber-gui-title", "&6&lUber Requests"));
        String warpGuiTitle = colorize(getConfig().getString("messages.warp-gui-title", "&6&lSelect Destination Warp"));
        
        if (title.equals(uberGuiTitle)) {
            event.setCancelled(true);
            
            if (!player.hasPermission("uber.uber")) {
                player.sendMessage(colorize(getPrefix() + getConfig().getString("messages.no-permission")));
                return;
            }
            
            if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.PLAYER_HEAD) {
                ItemStack head = event.getCurrentItem();
                if (!head.hasItemMeta() || head.getItemMeta().getDisplayName() == null) return;
                
                String displayName = head.getItemMeta().getDisplayName();
                String playerName = displayName.replace("§6§l", "").trim();
                
                // Process click async
                String finalPlayerName = playerName;
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        processUberAccept(player, finalPlayerName);
                    }
                }.runTaskAsynchronously(UberCraft.this);
            }
        } else if (title.equals(warpGuiTitle)) {
            event.setCancelled(true);
            
            if (event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.AIR) {
                ItemStack item = event.getCurrentItem();
                
                // Check if it's the "No Warps" placeholder
                if (item.getType() == Material.BARRIER) {
                    player.closeInventory();
                    player.sendMessage(colorize(getPrefix() + " &cNo warps available in this world!"));
                    return;
                }
                
                if (!item.hasItemMeta() || item.getItemMeta().getDisplayName() == null) return;
                
                String displayName = item.getItemMeta().getDisplayName();
                String warpName = displayName.replace("§6§l", "").trim().toLowerCase();
                
                Warp warp = warps.get(warpName);
                if (warp != null) {
                    // Check if warp is in same world
                    if (!warp.getWorldName().equals(player.getWorld().getName())) {
                        player.sendMessage(colorize(getPrefix() + " &cThis warp is in a different world!"));
                        player.closeInventory();
                        return;
                    }
                    
                    // Process warp selection
                    player.closeInventory();
                    new UberCommand().handleWarpRequest(player, warp);
                }
            }
        }
    }
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        
        // Quick check if player has compass
        if (!compassTargets.containsKey(player.getUniqueId())) {
            return;
        }
        
        CompassTarget target = compassTargets.get(player.getUniqueId());
        if (target == null) {
            removeCompass(player);
            return;
        }
        
        UberRide ride = activeRides.get(target.getRideId());
        if (ride == null) {
            // Ride no longer exists, remove compass
            removeCompass(player);
            player.sendMessage(colorize(getPrefix() + " &cThis ride no longer exists!"));
            return;
        }
        
        // Check if Uber is in same world as target
        if (!player.getWorld().equals(target.getLocation().getWorld())) {
            player.sendMessage(colorize(getPrefix() + " &cYou must be in the same world as the target!"));
            return;
        }
        
        double distance = player.getLocation().distance(target.getLocation());
        
        // Handle different stages
        if (target.getType() == CompassTarget.TargetType.PICKUP) {
            // Going to pickup the player
            if (distance < 5) {
                // Arrived at pickup location
                Player ridePlayer = Bukkit.getPlayer(ride.getPlayerUUID());
                if (ridePlayer != null && ridePlayer.isOnline()) {
                    // Update compass to destination
                    updateCompassTarget(player, ride.getDestinationLocation(), 
                        ride.getPlayerName(), ride, CompassTarget.TargetType.DESTINATION);
                    
                    // Mark player as boarded
                    ride.setPlayerBoarded(true);
                    
                    // Send messages
                    player.sendMessage(colorize(getPrefix() + " &aYou have arrived! Tell the player to board."));
                    player.sendMessage(colorize(getPrefix() + " &7The compass now points to the destination."));
                    
                    ridePlayer.sendMessage(colorize(getPrefix() + " &aThe Uber has arrived! Right-click to board."));
                    ridePlayer.sendMessage(colorize(getPrefix() + " &7After boarding, they will take you to your destination."));
                } else {
                    // Player offline, cancel ride
                    activeRides.remove(target.getRideId());
                    removeCompass(player);
                    player.sendMessage(colorize(getPrefix() + " &cThe player is no longer online. Ride cancelled."));
                }
            }
        } else if (target.getType() == CompassTarget.TargetType.DESTINATION) {
            // Going to destination
            if (ride.hasPlayerBoarded() && distance < 5) {
                // Arrived at destination - complete ride
                completeRide(player, ride, target.getRideId());
            }
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        removeCompass(player);
        
        // Handle player quit during ride
        for (Map.Entry<UUID, UberRide> entry : activeRides.entrySet()) {
            UberRide ride = entry.getValue();
            if (ride.getPlayerUUID().equals(player.getUniqueId())) {
                // Player quit - cancel ride (treat as player cancelled)
                UberCommand command = new UberCommand();
                command.cancelRide(null, entry.getKey(), ride, CancelType.PLAYER_CANCELLED);
            } else if (ride.getUberUUID().equals(player.getUniqueId())) {
                // Uber quit - cancel ride and refund player (treat as uber cancelled)
                UberCommand command = new UberCommand();
                command.cancelRide(null, entry.getKey(), ride, CancelType.UBER_CANCELLED);
            }
        }
    }
    
    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (compassTargets.containsKey(player.getUniqueId())) {
            CompassTarget target = compassTargets.get(player.getUniqueId());
            if (target != null) {
                player.sendMessage(colorize(getPrefix() + " &cYou changed world! The compass will only work in the target world."));
            }
        }
    }
    
    private void processUberAccept(Player uber, String playerName) {
        UUID playerUUID = null;
        UberRequest acceptedRequest = null;
        UUID requestId = null;
        
        // Find request by player name
        for (Map.Entry<UUID, UberRequest> entry : activeRequests.entrySet()) {
            if (entry.getValue().getPlayerName().equals(playerName)) {
                playerUUID = entry.getValue().getPlayerUUID();
                acceptedRequest = entry.getValue();
                requestId = entry.getKey();
                break;
            }
        }
        
        if (acceptedRequest == null) {
            uber.sendMessage(colorize(getPrefix() + " &cThis request no longer exists!"));
            return;
        }
        
        // Check if Uber is in same world as pickup
        if (!uber.getWorld().equals(acceptedRequest.getPickupLocation().getWorld())) {
            uber.sendMessage(colorize(getPrefix() + " &cYou must be in the same world as the player to accept this ride!"));
            return;
        }
        
        // Check if Uber is already in a ride
        for (UberRide ride : activeRides.values()) {
            if (ride.getUberUUID().equals(uber.getUniqueId())) {
                uber.sendMessage(colorize(getPrefix() + " &cYou are already in an Uber ride!"));
                return;
            }
        }
        
        Player player = Bukkit.getPlayer(playerUUID);
        if (player == null || !player.isOnline()) {
            uber.sendMessage(colorize(getPrefix() + " &cThe player is no longer online!"));
            return;
        }
        
        // Create ride
        UUID rideId = UUID.randomUUID();
        UberRide ride = new UberRide(
            playerUUID, playerName,
            uber.getUniqueId(), uber.getName(),
            acceptedRequest.getPickupLocation(),
            acceptedRequest.getDestinationLocation(),
            acceptedRequest.getDestinationName(),
            acceptedRequest.getEstimatedPrice()
        );
        
        activeRides.put(rideId, ride);
        
        // Remove request
        activeRequests.remove(requestId);
        dataConfig.set("requests." + requestId.toString(), null);
        saveData();
        
        // Cancel expiration task
        if (expirationTasks.containsKey(requestId)) {
            expirationTasks.get(requestId).cancel();
            expirationTasks.remove(requestId);
        }
        
        // Give compass pointing to pickup location first
        new BukkitRunnable() {
            @Override
            public void run() {
                giveCompass(uber, playerName, ride.getPickupLocation(), rideId, CompassTarget.TargetType.PICKUP);
                
                uber.sendMessage(colorize(getPrefix() + " &aYou accepted " + playerName + "'s ride!"));
                uber.sendMessage(colorize(getPrefix() + " &7Go to the pickup location first."));
                uber.sendMessage(colorize(getPrefix() + " &7Destination after pickup: &f" + ride.getDestinationName()));
                uber.sendMessage(colorize(getPrefix() + " &7Price: &a" + formatCoin(ride.getPrice())));
                
                player.sendMessage(colorize(getPrefix() + " &a" + uber.getName() + " accepted your ride!"));
                player.sendMessage(colorize(getPrefix() + " &7They are on their way to pick you up."));
            }
        }.runTask(this);
    }
    
    /**
     * Complete a ride and process payment to Uber (90% of price)
     * Player already paid 100% upfront, server keeps 10% fee
     */
    private void completeRide(Player uber, UberRide ride, UUID rideId) {
        if (uber == null || ride == null || rideId == null) {
            getLogger().warning("completeRide called with null parameters");
            return;
        }
        
        try {
            // Remove compass first
            removeCompass(uber);
            
            // Remove ride from active rides
            activeRides.remove(rideId);
            
            // Pay Uber (90% of price) - server pays the uber
            BigDecimal uberPayment = ride.getPrice().multiply(new BigDecimal("0.9"))
                .setScale(8, RoundingMode.DOWN);
            
            // Check if uber has a card
            String uberCard = getPlayerCard(ride.getUberUUID());
            if (uberCard == null) {
                uber.sendMessage(colorize(getPrefix() + " &cYou don't have a card set! Contact an admin."));
                getLogger().warning("Uber " + ride.getUberName() + " has no card set for payment!");
                return;
            }
            
            // Check if server has a card
            if (serverCard == null || serverCard.isEmpty()) {
                uber.sendMessage(colorize(getPrefix() + " &cServer card not configured! Contact an admin."));
                getLogger().severe("Server card not configured in config.yml!");
                return;
            }
            
            // Process payment to Uber
            addToPaymentQueue(serverCard, uberCard, uberPayment, "UBER_COMPLETE_PAY", 
                new PaymentCallback() {
                    @Override
                    public void onSuccess(String txId) {
                        if (uber.isOnline()) {
                            uber.sendMessage(colorize(getPrefix() + " &aRide completed! You received: &6" + 
                                formatCoin(uberPayment) + " &aTransaction: &b" + txId));
                        }
                        
                        // Notify player (no refund, they already paid)
                        Player player = Bukkit.getPlayer(ride.getPlayerUUID());
                        if (player != null && player.isOnline()) {
                            player.sendMessage(colorize(getPrefix() + " &aRide completed! Thank you for using UberCraft!"));
                            player.sendMessage(colorize(getPrefix() + " &7You paid: &6" + formatCoin(ride.getPrice())));
                        }
                        
                        getLogger().info(String.format("Ride completed: %s paid %s, %s received %s (10%% fee)",
                            ride.getPlayerName(), formatCoin(ride.getPrice()),
                            ride.getUberName(), formatCoin(uberPayment)));
                    }
                    
                    @Override
                    public void onFailure(String error) {
                        if (uber.isOnline()) {
                            uber.sendMessage(colorize(getPrefix() + " &cPayment failed: " + error));
                        }
                        getLogger().warning("Failed to pay uber " + ride.getUberName() + " for ride: " + error);
                        
                        // Log the error but don't retry automatically to avoid double payments
                        getLogger().warning("Ride completed but payment failed. Manual intervention may be needed.");
                    }
                });
                
        } catch (Exception e) {
            getLogger().severe("Error in completeRide: " + e.getMessage());
            e.printStackTrace();
            
            // Try to clean up
            removeCompass(uber);
            activeRides.remove(rideId);
            
            if (uber.isOnline()) {
                uber.sendMessage(colorize(getPrefix() + " &cAn error occurred completing the ride. Contact an admin."));
            }
        }
    }
    
    private void scheduleExpiration(UUID requestId, Player player, BigDecimal price) {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                UberRequest request = activeRequests.get(requestId);
                if (request != null && request.getStatus() == UberStatus.WAITING) {
                    activeRequests.remove(requestId);
                    dataConfig.set("requests." + requestId.toString(), null);
                    saveData();
                    
                    // Refund player (90%)
                    BigDecimal refund = price.multiply(new BigDecimal("0.9"))
                        .setScale(8, RoundingMode.DOWN);
                    
                    addToPaymentQueue(serverCard, getPlayerCard(player.getUniqueId()), refund, "EXPIRED_REFUND", 
                        new PaymentCallback() {
                            @Override
                            public void onSuccess(String txId) {
                                if (player.isOnline()) {
                                    player.sendMessage(colorize(getPrefix() + 
                                        getConfig().getString("messages.request-expired", 
                                            "&cYour Uber request expired! &a" + formatCoin(refund) + " &chas been refunded.")));
                                }
                            }
                            
                            @Override
                            public void onFailure(String error) {
                                getLogger().warning("Failed to refund expired request: " + error);
                            }
                        });
                    
                    expirationTasks.remove(requestId);
                }
            }
        }.runTaskLater(this, 1200L); // 60 seconds = 1200 ticks (corrigido de 6000L para 1200L)
        
        expirationTasks.put(requestId, task);
    }
    
    private void alertUbers(UberRequest request) {
        for (Player uber : Bukkit.getOnlinePlayers()) {
            if (uber.hasPermission("uber.uber")) {
                uber.sendMessage(colorize(getPrefix() + " &6&lNEW UBER REQUEST!"));
                uber.sendMessage(colorize(" &7Player: &f" + request.getPlayerName()));
                uber.sendMessage(colorize(" &7Destination: &f" + request.getDestinationName()));
                uber.sendMessage(colorize(" &7Price: &a" + formatCoin(request.getEstimatedPrice())));
                uber.sendMessage(colorize(" &aUse /ubergui to accept!"));
            }
        }
    }
    
    private void giveCompass(Player player, String targetName, Location targetLoc, UUID rideId, CompassTarget.TargetType type) {
        removeCompass(player);
        
        ItemStack compass = new ItemStack(Material.COMPASS);
        CompassMeta meta = (CompassMeta) compass.getItemMeta();
        
        String displayName;
        if (type == CompassTarget.TargetType.PICKUP) {
            displayName = colorize(getConfig().getString("messages.uber-compass-pickup", "&6&lUber Compass &7- Pickup %player%")
                .replace("%player%", targetName));
        } else {
            displayName = colorize(getConfig().getString("messages.uber-compass-destination", "&6&lUber Compass &7- %player%'s Destination")
                .replace("%player%", targetName));
        }
        
        meta.setDisplayName(displayName);
        meta.setLore(java.util.Arrays.asList(
            colorize(getConfig().getString("messages.uber-compass-lore", "&7Follow this compass"))
        ));
        
        compass.setItemMeta(meta);
        player.getInventory().addItem(compass);
        
        player.setCompassTarget(targetLoc);
        compassTargets.put(player.getUniqueId(), new CompassTarget(targetLoc, targetName, rideId, type));
    }
    
    private void updateCompassTarget(Player player, Location newTarget, String targetName, UberRide ride, CompassTarget.TargetType newType) {
        if (player == null || !player.isOnline()) return;
        
        CompassTarget existing = compassTargets.get(player.getUniqueId());
        if (existing != null) {
            // Update existing compass
            player.setCompassTarget(newTarget);
            compassTargets.put(player.getUniqueId(), new CompassTarget(newTarget, targetName, existing.getRideId(), newType));
            
            // Update compass item lore
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() == Material.COMPASS && item.hasItemMeta()) {
                    CompassMeta meta = (CompassMeta) item.getItemMeta();
                    String newName;
                    if (newType == CompassTarget.TargetType.PICKUP) {
                        newName = colorize(getConfig().getString("messages.uber-compass-pickup", "&6&lUber Compass &7- Pickup %player%")
                            .replace("%player%", targetName));
                    } else {
                        newName = colorize(getConfig().getString("messages.uber-compass-destination", "&6&lUber Compass &7- %player%'s Destination")
                            .replace("%player%", targetName));
                    }
                    meta.setDisplayName(newName);
                    item.setItemMeta(meta);
                    break;
                }
            }
        }
    }
    
    private void removeCompass(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == Material.COMPASS) {
                player.getInventory().setItem(i, null);
            }
        }
        
        // Reset compass target
        if (player.getBedSpawnLocation() != null) {
            player.setCompassTarget(player.getBedSpawnLocation());
        } else {
            player.setCompassTarget(player.getWorld().getSpawnLocation());
        }
        
        compassTargets.remove(player.getUniqueId());
        
        if (compassTrackers.containsKey(player.getUniqueId())) {
            compassTrackers.get(player.getUniqueId()).cancel();
            compassTrackers.remove(player.getUniqueId());
        }
    }
    
    // ================== PAYMENT QUEUE SYSTEM ==================
    
    private void addToPaymentQueue(String fromCard, String toCard, BigDecimal amount, 
                                   String reason, PaymentCallback callback) {
        if (fromCard == null || fromCard.isEmpty()) {
            getLogger().severe("Cannot add payment to queue: fromCard is null or empty");
            callback.onFailure("Invalid source card");
            return;
        }
        if (toCard == null || toCard.isEmpty()) {
            getLogger().severe("Cannot add payment to queue: toCard is null or empty");
            callback.onFailure("Invalid destination card");
            return;
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            getLogger().severe("Cannot add payment to queue: invalid amount " + amount);
            callback.onFailure("Invalid amount");
            return;
        }
        
        paymentQueue.offer(new PaymentTask(fromCard, toCard, amount, reason, callback));
        processPaymentQueue();
    }
    
    private void processPaymentQueue() {
        if (processingQueue.compareAndSet(false, true)) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    processNextPayment();
                }
            }.runTaskAsynchronously(this);
        }
    }
    
    private void processNextPayment() {
        PaymentTask task = paymentQueue.poll();
        
        if (task == null) {
            processingQueue.set(false);
            return;
        }
        
        // Process payment
        coinAPI.transfer(task.getFromCard(), task.getToCard(), task.getAmount().doubleValue(), 
            new TransferCallback() {
                @Override
                public void onSuccess(String txId, double amount) {
                    task.getCallback().onSuccess(txId);
                    
                    // Schedule next payment with delay
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            processNextPayment();
                        }
                    }.runTaskLaterAsynchronously(UberCraft.this, 20L); // 1 second delay
                }
                
                @Override
                public void onFailure(String error) {
                    task.getCallback().onFailure(error);
                    
                    // Schedule next payment
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            processNextPayment();
                        }
                    }.runTaskLaterAsynchronously(UberCraft.this, 20L);
                }
            });
    }
    
    // ================== UTILITY METHODS ==================
    
    private String getPlayerCard(UUID uuid) {
        if (uuid == null) return null;
        return coinAPI.getPlayerCard(uuid);
    }
    
    private String getPlayerCard(String playerName) {
        if (playerName == null || playerName.isEmpty()) return null;
        return coinAPI.getPlayerCardByNick(playerName);
    }
    
    private World getCachedWorld(String worldName) {
        if (worldName == null || worldName.isEmpty()) return null;
        return worldCache.computeIfAbsent(worldName, Bukkit::getWorld);
    }
    
    private String formatCoin(BigDecimal value) {
        if (value == null) return "0";
        return DecimalUtil.formatFull(value.doubleValue());
    }
    
    private void loadWarps() {
        warps.clear();
        if (dataConfig.contains("warps")) {
            for (String key : dataConfig.getConfigurationSection("warps").getKeys(false)) {
                String path = "warps." + key;
                String worldName = dataConfig.getString(path + ".world");
                World world = Bukkit.getWorld(worldName);
                if (world == null) continue;
                
                double x = dataConfig.getDouble(path + ".x");
                double y = dataConfig.getDouble(path + ".y");
                double z = dataConfig.getDouble(path + ".z");
                Location loc = new Location(world, x, y, z);
                
                String iconType = dataConfig.getString(path + ".icon", "COMPASS");
                short iconData = (short) dataConfig.getInt(path + ".icon-data", 0);
                ItemStack icon;
                try {
                    icon = new ItemStack(Material.valueOf(iconType), 1, iconData);
                } catch (IllegalArgumentException e) {
                    icon = new ItemStack(Material.COMPASS);
                }
                
                Warp warp = new Warp(key, loc, icon);
                warps.put(key, warp);
            }
        }
    }
    
    // ================== MAIN METHODS ==================
    
    @Override
    public void onEnable() {
        instance = this;
        
        // Create data folder
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        
        // Save default config
        saveDefaultConfig();
        loadConfig();
        
        // Initialize data file
        dataFile = new File(getDataFolder(), "data.dat");
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        
        // Load warps
        loadWarps();
        
        saveData();
        
        // Get CoinCard API
        if (Bukkit.getPluginManager().getPlugin("CoinCard") == null) {
            getLogger().severe("CoinCard not found! Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        
        coinAPI = CoinCardPlugin.getAPI();
        if (coinAPI == null) {
            getLogger().severe("CoinCard API not available! Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        
        // Register commands
        UberCommand uberCommand = new UberCommand();
        getCommand("uber").setExecutor(uberCommand);
        getCommand("uber").setTabCompleter(uberCommand);
        getCommand("ubergui").setExecutor(new UberGuiCommand());
        
        // Register events
        getServer().getPluginManager().registerEvents(this, this);
        
        // Start cleanup task (every minute)
        new BukkitRunnable() {
            @Override
            public void run() {
                worldCache.clear();
            }
        }.runTaskTimer(this, 6000L, 6000L);
        
        getLogger().info("UberCraft enabled successfully with " + warps.size() + " warps!");
    }
    
    @Override
    public void onDisable() {
        // Cancel all tasks
        for (BukkitTask task : expirationTasks.values()) {
            task.cancel();
        }
        expirationTasks.clear();
        
        for (BukkitTask tracker : compassTrackers.values()) {
            tracker.cancel();
        }
        compassTrackers.clear();
        
        activeRequests.clear();
        activeRides.clear();
        compassTargets.clear();
        worldCache.clear();
        
        saveData();
        getLogger().info("UberCraft disabled!");
    }
    
    public void loadConfig() {
        reloadConfig();
        FileConfiguration config = getConfig();
        
        prefix = colorize(config.getString("messages.prefix", "&8[&5UberCraft&8]"));
        serverCard = config.getString("Server_Card", "");
        distanceWorth = BigDecimal.valueOf(config.getDouble("Distance_Worth", 0.00000001))
            .setScale(8, RoundingMode.DOWN);
    }
    
    public void saveData() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void reloadData() {
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        loadWarps();
    }
    
    public String getPrefix() {
        return prefix;
    }
    
    public static UberCraft getInstance() {
        return instance;
    }
}
