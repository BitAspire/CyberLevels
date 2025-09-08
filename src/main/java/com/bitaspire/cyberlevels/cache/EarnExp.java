package com.bitaspire.cyberlevels.cache;

import com.bitaspire.cyberlevels.CyberLevels;
import com.bitaspire.cyberlevels.level.ExpSource;
import com.bitaspire.cyberlevels.user.LevelUser;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.croabeast.file.ConfigurableFile;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public class EarnExp {

    private static final Random random = new Random();

    private final CyberLevels main;
    private final Map<String, SourceImpl> events = new HashMap<>();

    private ConfigurableFile file;

    EarnExp(CyberLevels main) {
        this.main = main;

        try {
            long start = System.currentTimeMillis();
            main.logger("&dLoading earn-exp events...");

            file = new CLVFile(main, "earn-exp");

            for (String key : file.getKeys("earn-exp")) {
                String specific = "";

                for (String temp : file.getKeys("earn-exp." + key))
                    if (temp.contains("specific-")) {
                        specific = temp.split("-", 2)[1];
                        break;
                    }

                new SourceImpl(key, specific);
            }

            main.logger("&7Loaded &e" + (events.size()) + "&7 earn-exp events in &a" + (System.currentTimeMillis() - start) + "ms&7.", "");
        }
        catch (IOException ignored) {}

        setDefaultEvents();
    }

    void setDefaultEvents() {
        final SourceImpl source = events.get("timed-giving");
        source.setRegistrable(new ExpSource.Registrable() {
            private BukkitTask task = null;

            @Override
            public void register() {
                if (!source.isEnabled() && !source.useSpecifics())
                    return;

                task = Bukkit.getScheduler().runTaskTimer(
                        main,
                        () -> {
                            for (Player p : Bukkit.getOnlinePlayers())
                                sendPermissionExp(p, source);
                        }, 0L,
                        20L * Math.max(1, source.getInterval()));
            }

            @Override
            public void unregister() {
                if (task == null) return;

                task.cancel();
                task = null;
            }
        });

        events.get("dying").setListener(s -> new Listener() {
            @EventHandler(priority = EventPriority.HIGH)
            void onPlayerDeath(PlayerDeathEvent event) {
                if (event.getEntity().getLastDamageCause() != null)
                    sendPermissionExp(event.getEntity(), s);
            }
        });

        events.get("damaging-players").setListener(s -> createDamageListener(e -> e instanceof Player, s));
        events.get("damaging-animals").setListener(s -> createDamageListener(e -> e instanceof Animals, s));

        events.get("damaging-monsters").setListener(s -> createDamageListener(
                e -> e instanceof Monster || (main.serverVersion() > 12 && e instanceof Mob) || e instanceof WaterMob, s));

        events.get("killing-players").setListener(s -> createDeathListener(e -> e instanceof Player, s));
        events.get("killing-animals").setListener(s -> createDeathListener(e -> e instanceof Animals, s));

        events.get("killing-monsters").setListener(s -> createDeathListener(
                e -> e instanceof Monster || (main.serverVersion() > 12 && e instanceof Mob) || e instanceof WaterMob, s));

        events.get("placing").setListener(s -> new Listener() {
            @EventHandler (priority = EventPriority.HIGHEST)
            private void onPlacing(BlockPlaceEvent event) {
                if (event.isCancelled()) return;

                if (main.cache().antiAbuse().onlyNaturalBlocks())
                    event.getBlock().setMetadata("CLV_PLACED", new FixedMetadataValue(main, true));

                sendExp(event.getPlayer(), s, event.getBlock().getType().toString());
            }
        });

        events.get("breaking").setListener(s -> new Listener() {
            @EventHandler (priority = EventPriority.HIGHEST)
            @SuppressWarnings("deprecation")
            private void onBreaking(BlockBreakEvent event) {
                if (event.isCancelled()) return;

                double version = main.serverVersion();

                AntiAbuse antiAbuse = main.cache().antiAbuse();
                Player player = event.getPlayer();

                if (antiAbuse.isSilkTouchEnabled() &&
                        (main.serverVersion() > 8
                                ? player.getInventory().getItemInMainHand()
                                : player.getItemInHand())
                                .containsEnchantment(Enchantment.SILK_TOUCH))
                    return;

                Block block = event.getBlock();
                if (antiAbuse.onlyNaturalBlocks() && block.hasMetadata("CLV_PLACED")) {
                    if ((version <= 12 ?
                            !(block.getState().getData() instanceof Ageable) :
                            !(block.getBlockData() instanceof Ageable)) ||
                            antiAbuse.includeNaturalCrops())
                        return;

                    else {
                        Ageable ageable = (Ageable) (version > 12 ?
                                block.getBlockData() :
                                block.getState().getData());

                        if (ageable.getAge() != ageable.getMaximumAge()) return;
                    }
                }

                sendExp(event.getPlayer(), s, block.getType().toString());
            }
        });

        events.get("consuming").setListener(s -> new Listener() {
            @EventHandler (priority = EventPriority.HIGHEST)
            private void onConsumption(PlayerItemConsumeEvent event) {
                if (!event.isCancelled())
                    sendExp(event.getPlayer(), s, event.getItem().getType().toString());
            }
        });

        events.get("moving").setListener(s -> new Listener() {
            @EventHandler (priority = EventPriority.HIGHEST)
            private void onMovement(PlayerMoveEvent event) {
                if (event.isCancelled()) return;

                Location from = event.getFrom();

                Location to = event.getTo();
                if (to == null) return;

                if (from.getBlockX() == to.getBlockX() &&
                        from.getBlockY() == to.getBlockY() &&
                        from.getBlockZ() == to.getBlockZ())
                    return;

                sendPermissionExp(event.getPlayer(), s);
            }
        });

        events.get("crafting").setListener(s -> new Listener() {
            @EventHandler (priority = EventPriority.HIGHEST)
            private void onCrafting(CraftItemEvent event) {
                if (event.isCancelled() ||
                        !(event.getWhoClicked() instanceof Player) ||
                        event.getCurrentItem() == null)
                    return;

                sendExp(
                        (Player) event.getWhoClicked(), s,
                        event.getCurrentItem().getType().toString()
                );
            }
        });

        events.get("brewing").setListener(s -> new Listener() {
            @EventHandler (priority = EventPriority.HIGHEST)
            private void onBrewing(BrewEvent event) {
                if (event.isCancelled() || event.getContents().getViewers().isEmpty())
                    return;

                HumanEntity humanEntity = event.getContents().getViewers().get(0);
                if (!(humanEntity instanceof Player)) return;

                final Player player = (Player) humanEntity;
                PotionType[] prePotion = new PotionType[3];

                for (int i = 0; i <= 2 ; i++) {
                    ItemStack stack = event.getContents().getItem(i);
                    if (stack == null) continue;

                    PotionMeta meta = (PotionMeta) stack.getItemMeta();
                    if (meta == null) continue;

                    prePotion[i] = meta.getBasePotionData().getType();
                }

                (new BukkitRunnable() {
                    @Override
                    public void run() {
                        double counter = 0;

                        for (int i = 0; i <= 2 ; i++) {
                            ItemStack stack = event.getContents().getItem(i);
                            if (stack == null) continue;

                            PotionMeta meta = (PotionMeta) stack.getItemMeta();
                            if (meta == null) continue;

                            String data = "";

                            PotionType type = meta.getBasePotionData().getType();
                            if (prePotion[i] == null || type != prePotion[i])
                                data = type.toString();

                            if (main.levelSystem().checkAntiAbuse(player, s)) return;
                            if (s.isEnabled() || s.useSpecifics())
                                counter += s.getPartialMatchesExp(data);
                        }

                        LevelUser<?> user = main.userManager().getUser(player);

                        if (counter > 0) {
                            user.addExp(counter + "", main.cache().config().isMultiplierEvents());
                            return;
                        }

                        if (counter < 0) user.removeExp(Math.abs(counter) + "");
                    }
                }).runTaskLater(main, 1L);
            }
        });

        events.get("enchanting").setListener(s -> new Listener() {
            @EventHandler (priority = EventPriority.HIGHEST)
            private void onEnchant(EnchantItemEvent event) {
                if (event.isCancelled()) return;

                StringBuilder data = new StringBuilder();
                for (Enchantment enchantment : event.getEnchantsToAdd().keySet())
                    data.append(enchantment.getKey().getKey())
                            .append("-")
                            .append(event.getEnchantsToAdd().get(enchantment))
                            .append(" ");

                if (main.levelSystem().checkAntiAbuse(event.getEnchanter(), s)) return;

                double counter = 0;
                if (s.isEnabled() || s.useSpecifics())
                    counter += s.getPartialMatchesExp(data.toString());

                LevelUser<?> user = main.userManager().getUser(event.getEnchanter());

                if (counter > 0) {
                    user.addExp(counter + "", main.cache().config().isMultiplierEvents());
                    return;
                }

                if (counter < 0) user.removeExp(Math.abs(counter) + "");
            }
        });

        events.get("fishing").setListener(s -> new Listener() {
            @EventHandler (priority = EventPriority.HIGHEST)
            private void onFishing(PlayerFishEvent event) {
                Entity caught = event.getCaught();
                if (!event.isCancelled() && caught != null || caught instanceof Item)
                    sendExp(event.getPlayer(), s, ((Item) caught).getItemStack().getType().toString());
            }
        });

        if (main.serverVersion() >= 10)
            events.get("breeding").setListener(s -> new Listener() {
                @EventHandler(priority = EventPriority.HIGHEST)
                private void onBreeding(EntityBreedEvent event) {
                    if (!event.isCancelled() && event.getBreeder() instanceof Player)
                        sendExp((Player) event.getBreeder(), s, event.getEntity().getType().toString());
                }
            });

        events.get("chatting").setListener(s -> new Listener() {
            @EventHandler (priority = EventPriority.HIGHEST)
            private void onChat(AsyncPlayerChatEvent event) {
                if (event.isCancelled()) return;

                Player player = event.getPlayer();
                String item = event.getMessage().toUpperCase();
                double counter = 0;

                if (main.levelSystem().checkAntiAbuse(player, s))
                    return;

                if (s.isEnabled() || s.useSpecifics())
                    counter += s.getPartialMatchesExp(item);

                final double finalCounter = counter;
                Bukkit.getScheduler().runTask(main, () -> {
                    LevelUser<?> user = main.userManager().getUser(player);

                    if (finalCounter > 0) {
                        user.addExp(finalCounter + "", main.cache().config().isMultiplierEvents());
                        return;
                    }

                    if (finalCounter < 0) user.removeExp(Math.abs(finalCounter) + "");
                });
            }
        });

        events.get("vanilla-exp-gain").setListener(s -> new Listener() {
            @EventHandler (priority = EventPriority.HIGHEST)
            private void onExperience(PlayerExpChangeEvent event) {
                if (event.getAmount() > 0)
                    sendExp(event.getPlayer(), s, event.getAmount() + "");
            }
        });
    }

    void doDamageEvent(EntityDamageByEntityEvent event, ExpSource s) {
        if (event.isCancelled()) return;

        Entity attacker = event.getDamager();
        if ((attacker instanceof Projectile) && (((Projectile) attacker).getShooter() instanceof Player))
            attacker = (Entity) ((Projectile) attacker).getShooter();

        else if ((attacker instanceof TNTPrimed) && (((TNTPrimed) attacker).getSource() instanceof Player))
            attacker = ((TNTPrimed) attacker).getSource();

        if (!(attacker instanceof Player)) return;

        Entity target = event.getEntity();
        sendExp(((Player) attacker), s, (target instanceof Player) ?
                target.getName() :
                target.getType().toString());
    }

    void doDeathEvent(EntityDeathEvent event, ExpSource s) {
        if (event.getEntity().getLastDamageCause() == null) return;

        EntityDamageEvent damageEvent = event.getEntity().getLastDamageCause();
        if (damageEvent instanceof EntityDamageByEntityEvent)
            doDamageEvent((EntityDamageByEntityEvent) damageEvent, s);
    }

    private Listener createDamageListener(Predicate<Entity> filter, ExpSource source) {
        return new Listener() {
            @EventHandler(priority = EventPriority.HIGHEST)
            public void onDamage(EntityDamageByEntityEvent event) {
                if (event.isCancelled()) return;
                if (filter.test(event.getEntity())) {
                    doDamageEvent(event, source);
                }
            }
        };
    }

    private Listener createDeathListener(Predicate<Entity> filter, ExpSource source) {
        return new Listener() {
            @EventHandler(priority = EventPriority.HIGHEST)
            public void onDeath(EntityDeathEvent event) {
                if (filter.test(event.getEntity())) doDeathEvent(event, source);
            }
        };
    }

    void sendExp(Player player, ExpSource source, String value) {
        if (main.levelSystem().checkAntiAbuse(player, source)) return;
        double counter = 0;

        if (source.isEnabled() && source.isInList(value))
            counter += source.getRange().getRandom();
        if (source.useSpecifics() && source.isInList(value, true))
            counter += source.getSpecificRange(value).getRandom();

        if (counter == 0) return;

        LevelUser<?> user = main.userManager().getUser(player);
        if (counter > 0) {
            user.addExp(counter + "", main.cache().config().isMultiplierEvents());
            return;
        }

        user.removeExp(Math.abs(counter) + "");
    }

    void sendPermissionExp(Player player, ExpSource source) {
        if (main.levelSystem().checkAntiAbuse(player, source))
            return;

        double counter = 0;

        if (source.isEnabled() && source.hasPermission(player))
            counter += source.getRange().getRandom();

        if (source.useSpecifics() && source.hasPermission(player, true))
            for (String s : source.getSpecificList())
                if (player.hasPermission(s))
                    counter += source.getSpecificRange(s).getRandom();

        LevelUser<?> user = main.userManager().getUser(player);

        if (counter > 0) {
            user.addExp(counter + "", main.cache().config().isMultiplierEvents());
            return;
        }

        if (counter < 0) user.removeExp(Math.abs(counter) + "");
    }

    @NotNull
    public Map<String, ExpSource> getExpSources() {
        return new HashMap<>(events);
    }

    public void register() {
        events.values().forEach(e -> e.getRegistrable().register());
    }

    public void unregister() {
        events.values().forEach(e -> e.getRegistrable().unregister());
    }

    @Getter
    class SourceImpl implements ExpSource {

        private final String category, name;

        private boolean enabled;
        private final int interval;

        private final Range range;

        @Accessors(fluent = true)
        private final boolean includes;
        private final boolean whitelist;

        private final List<String> list;

        private final boolean specific;
        @Getter(AccessLevel.NONE)
        private final Map<String, Range> specifics = new HashMap<>();

        @Setter
        private Registrable registrable;
        private Listener listener = null;

        SourceImpl(String category, String specificName) {
            this.category = category;
            this.name = specificName;

            enabled = get("general.enabled", false);
            interval = get("general.interval", 0);
            range = new RangeImpl(this, get("general.exp", (Object) "").toString());

            includes = get("general.includes.enabled", false);
            whitelist = get("general.includes.whitelist", false);
            list = getList("general.includes.list");

            specific = get("specific-" + specificName + ".enabled", false);

            for (String key : getList("specific-" + specificName + "." + specificName)) {
                String[] array = key.split(":", 2);

                key = array[0].trim();
                String value = array[1].trim();

                specifics.put(key, new RangeImpl(null, value));
            }

            registrable = new Registrable() {
                @Override
                public void register() {
                    if (listener != null)
                        main.getServer().getPluginManager().registerEvents(listener, main);
                }

                @Override
                public void unregister() {
                    if (listener != null)
                        HandlerList.unregisterAll(listener);
                }
            };

            events.put(category, this);
        }

        <T> T get(String path, T def) {
            return file.get("earn-exp." + category + "." + path, def);
        }

        List<String> getList(String path) {
            return file.toStringList("earn-exp." + category + "." + path);
        }

        void setListener(Function<SourceImpl, Listener> function) {
            listener = function.apply(this);
        }

        @NotNull
        public List<String> getIncludeList() {
            return list;
        }

        @Override
        public boolean useSpecifics() {
            return specific;
        }

        @NotNull
        public List<String> getSpecificList() {
            return new ArrayList<>(specifics.keySet());
        }

        @Override
        public boolean isInList(String value, boolean specific) {
            return !specific ?
                    (!includes || whitelist == list.contains(value.toUpperCase())) :
                    (this.specific && specifics.containsKey(value));
        }

        @Override
        public boolean hasPermission(Player player, boolean specific) {
            if (specific)
                return this.specific && specifics.keySet().stream().anyMatch(player::hasPermission);

            if (!includes) return true;

            for (String s : list)
                if (player.hasPermission(s)) return whitelist;

            return !whitelist;
        }

        @NotNull
        public Range getSpecificRange(String value) {
            return specifics.get(value);
        }

        public double getPartialMatchesExp(String string) {
            double amount = 0.0;
            String upper = string.toUpperCase(Locale.ENGLISH);

            if (specific) {
                for (String s : specifics.keySet())
                    if (upper.contains(s.toUpperCase(Locale.ENGLISH)))
                        amount += getSpecificRange(s).getRandom();
            }

            if (enabled) {
                if (!includes) {
                    amount += getRange().getRandom();
                }
                else {
                    boolean giveExp = true;
                    for (String s : list) {
                        if (!upper.contains(s.toUpperCase(Locale.ENGLISH)))
                            continue;

                        if (whitelist) {
                            amount += getRange().getRandom();
                            return amount;
                        }

                        giveExp = false;
                        break;
                    }

                    if (!whitelist && giveExp)
                        amount += getRange().getRandom();
                }
            }

            return amount;
        }

        @Override
        public String toString() {
            return "SourceImpl{" +
                    "category='" + category + '\'' +
                    ", name='" + name + '\'' +
                    ", enabled=" + enabled +
                    ", interval=" + interval +
                    ", range=" + range +
                    ", includes=" + includes +
                    ", whitelist=" + whitelist +
                    ", list=" + list +
                    ", specific=" + specific +
                    ", specifics=" + specifics +
                    '}';
        }

        @Getter
        class RangeImpl implements Range {

            private double min = 0, max = 0;

            RangeImpl(SourceImpl parent, String exp) {
                if (StringUtils.isBlank(exp)) {
                    if (parent != null) parent.enabled = false;
                    return;
                }

                if (exp.contains(",")) {
                    String[] string = exp.replace(" ", "").split(",", 2);
                    min = Math.min(Double.parseDouble(string[0]), Double.parseDouble(string[1]));
                    max = Math.max(Double.parseDouble(string[0]), Double.parseDouble(string[1]));
                    return;
                }

                max = min = Double.parseDouble(exp);
            }

            public double getRandom() {
                double tempExp = min + (max - min) * random.nextDouble();
                if (main.cache().config().isExpIntegerOnly())
                    tempExp = Math.round(tempExp);
                return tempExp;
            }

            @Override
            public String toString() {
                return "RangeImpl{" + "min=" + min + ", max=" + max + '}';
            }
        }
    }
}
