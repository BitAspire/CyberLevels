package net.zerotoil.dev.cyberlevels.objects.exp;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import net.objecthunter.exp4j.ExpressionBuilder;
import net.zerotoil.dev.cyberlevels.CyberLevels;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EXPEarnEvent {

    @Getter(AccessLevel.NONE)
    final CyberLevels main;
    Boolean enabled = false;
    String category;
    String name;
    double minEXP;
    double maxEXP;
    boolean includedEnabled = false;
    boolean whitelist = false;
    List<String> list = new ArrayList<>();

    boolean specificEnabled = false;
    HashMap<String, Double> specificMin = new HashMap<>();
    HashMap<String, Double> specificMax = new HashMap<>();

    static Random random = new Random();

    protected EXPEarnEvent(CyberLevels main) {
        this.main = main;
    }

    public EXPEarnEvent(CyberLevels main, String category, String name) {
        this.main = main;
        this.category = category;
        this.name = name;
        ConfigurationSection config = main.getConfig("earn-exp").getConfigurationSection("earn-exp." + category);
        loadGeneral(config);
        loadSpecific(config);
    }

    protected void loadGeneral(ConfigurationSection config) {
        if (config == null) return;
        config = config.getConfigurationSection("general");     // general section
        if (config == null) return;                             // if no general section

        try { // since it can make a null pointer exception
            enabled = config.getBoolean("enabled");
            if (!enabled) return;
        } catch (Exception e) {
            return;
        }

        String exp = config.getString("exp");
        if (exp == null) {            // if exp isn't set, disable it
            enabled = false;
            return;
        }

        // correctly parses the exp values
        if (exp.contains(",")) {
            String[] string = exp.replace(" ", "").split(",", 2);
            double tempMin = Math.min(Double.parseDouble(string[0]), Double.parseDouble(string[1]));
            double tempMax = Math.max(Double.parseDouble(string[0]), Double.parseDouble(string[1]));
            minEXP = tempMin;
            maxEXP = tempMax;
        } else maxEXP = minEXP = Double.parseDouble(exp);

        config = config.getConfigurationSection("includes");
        if (config == null) return;                             // if no section with includes, just stop

        try { // since it can make a null pointer exception
            includedEnabled = config.getBoolean("enabled");
            if (!includedEnabled) return;
        } catch (Exception e) {
            return;                                             // return if not enabled, the rest isn't important
        }

        try {
            whitelist = config.getBoolean("whitelist");   // if whitelist isn't in config, set it to false
        } catch (Exception e) {
            whitelist = false;
        }

        for (String s : config.getStringList("list"))     // if gets to the end, get the whitelist/blacklist
            list.add(s.toUpperCase());
    }

    protected void loadSpecific(ConfigurationSection config) {
        if (config == null) return;
        config = config.getConfigurationSection("specific-" + name);
        if (config == null) return;
        try { // since it can make a null pointer exception
            specificEnabled = config.getBoolean("enabled");
            if (!specificEnabled) return;
        } catch (Exception e) {
            return;
        }

        if (config.get(name) == null) return;

        for (String s : main.getLangUtils().convertList(main.getConfig("earn-exp"), "earn-exp." + category + ".specific-" + name + "." + name)) {
            s = s.replace(" ", "");
            String val = s.split(":", 2)[1];
            s = s.split(":", 2)[0];
            if (val.contains(",")) {
                String[] string = val.split(",", 2);
                double tempMin = Math.min(Double.parseDouble(string[0]), Double.parseDouble(string[1]));
                double tempMax = Math.max(Double.parseDouble(string[0]), Double.parseDouble(string[1]));
                specificMin.put(s.toUpperCase(), tempMin);
                specificMax.put(s.toUpperCase(), tempMax);
            } else {
                specificMin.put(s.toUpperCase(), Double.parseDouble(val));
                specificMax.put(s.toUpperCase(), Double.parseDouble(val));
            }
        }

    }

    private double doFormula(Player player, String val) {
        return (new ExpressionBuilder(main.getLevelUtils().getPlaceholders(val, player, false, true))).build().evaluate();
    }

    public double getGeneralExp() {
        double tempExp = minEXP + (maxEXP - minEXP) * random.nextDouble();
        if (main.getExpCache().roundExp()) tempExp = main.getLevelUtils().roundDecimal(tempExp);
        if (main.getExpCache().useDouble()) tempExp = Math.round(tempExp);
        return tempExp;
    }

    public double getSpecificExp(String string) {
        if (!isInSpecificList(string)) return 0.0;
        double tempExp = specificMin.get(string.toUpperCase()) + (specificMax.get(string.toUpperCase()) - specificMin.get(string.toUpperCase())) * random.nextDouble();
        if (main.getExpCache().roundExp()) tempExp = main.getLevelUtils().roundDecimal(tempExp);
        if (main.getExpCache().useDouble()) tempExp = Math.round(tempExp);
        return tempExp;

    }

    public boolean isInGeneralList(String string) {
        if (includedEnabled) {
            if (whitelist) return list.contains(string.toUpperCase());
            else return !list.contains(string.toUpperCase());
        } else return true;
    }

    public boolean isInSpecificList(String string) {
        if (!specificEnabled) return false;
        return specificMin.containsKey(string);
    }

    public boolean hasPartialMatches(String string, boolean generalList) {
        if (generalList) {
            if (!includedEnabled) return true;
            else if (whitelist) for (String s : list) if (string.contains(s.toUpperCase())) return true;
            for (String s : list) if (!string.contains(s.toUpperCase())) return true;
        } else {
            if (!specificEnabled) return true;
            for (String s : specificMin.keySet()) if (string.contains(s.toUpperCase())) return true;
        }
        return false;
    }

    public boolean hasGeneralPermission(Player player) {
        if (includedEnabled) {
            boolean giveEXP = false;
            for (String s : list) {
                if (whitelist && player.hasPermission(s)) {
                    giveEXP = true;
                    break;
                }
                if (!whitelist && player.hasPermission(s)) break;
            }
            return giveEXP;
        } return true;
    }

    public boolean hasPermission(Player player) {
        if (!isSpecificEnabled()) return false;
        for (String s : getSpecificMin().keySet())
            if (player.hasPermission(s)) return true;
        return false;
    }

    public double getPartialMatchesExp(String string) {
        double amount = 0.0;
        string = string.toUpperCase(Locale.ROOT);

        if (specificEnabled)
            for (String s : specificMin.keySet())
                if (string.contains(s.toUpperCase(Locale.ROOT))) amount += getSpecificExp(s);

        if (enabled) {
            if (!includedEnabled) amount += getGeneralExp();
            else {
                boolean giveExp = true;
                for (String s : list) {
                    if (whitelist && string.contains(s.toUpperCase(Locale.ROOT))) {
                        amount += getGeneralExp();
                        break;
                    } else if (string.contains(s.toUpperCase(Locale.ROOT))) {
                        giveExp = false;
                        break;
                    }
                }
                if (!whitelist && giveExp) amount += getGeneralExp();
            }
        }

        return amount;
    }

    protected void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
    protected void setCategory(String category) {
        this.category = category;
    }
    protected void setName(String name) {
        this.name = name;
    }
    protected void setMinEXP(double minEXP) {
        this.minEXP = minEXP;
    }
    protected void setMaxEXP(double maxEXP) {
        this.maxEXP = maxEXP;
    }
    protected void setIncludedEnabled(boolean includedEnabled) {
        this.includedEnabled = includedEnabled;
    }
    protected void setWhitelist(boolean whitelist) {
        this.whitelist = whitelist;
    }
    protected void setList(List<String> list) {
        this.list = list;
    }
    protected void setSpecificEnabled(boolean specificEnabled) {
        this.specificEnabled = specificEnabled;
    }
    protected void setSpecificMin(HashMap<String, Double> specificMin) {
        this.specificMin = specificMin;
    }
    protected void setSpecificMax(HashMap<String, Double> specificMax) {
        this.specificMax = specificMax;
    }
    protected static void setRandom(Random random) {
        EXPEarnEvent.random = random;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
