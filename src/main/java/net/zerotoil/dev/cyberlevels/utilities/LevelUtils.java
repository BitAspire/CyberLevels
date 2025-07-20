package net.zerotoil.dev.cyberlevels.utilities;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import net.zerotoil.dev.cyberlevels.CyberLevels;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.configuration.Configuration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.math.RoundingMode;
import java.text.DecimalFormat;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class LevelUtils {

    final CyberLevels main;
    DecimalFormat decimalFormat;
    @Getter int decimals;
    boolean useSpecificFormula;

    String bar;
    String startBar;
    String middleBar;
    String endBar;

    public LevelUtils(CyberLevels main) {
        this.main = main;
        loadUtility();
        useSpecificFormula = levelsYML().isConfigurationSection("levels.experience.level");
    }

    private void loadUtility() {
        if (main.getFiles().getConfig("config").isConfigurationSection("config.round-evaluation") &&
                main.getFiles().getConfig("config").getBoolean("config.round-evaluation.enabled")) {

            StringBuilder decimalFormat = new StringBuilder("#");
            int roundDigits = main.getFiles().getConfig("config").getInt("config.round-evaluation.digits", 2);
            if (roundDigits > 0) decimalFormat.append(".");
            for (int i = 0; i < roundDigits; i++)
                decimalFormat.append("#");

            this.decimals = main.getFiles().getConfig("config").getInt("config.round-evaluation.digits");
            this.decimalFormat = new DecimalFormat(decimalFormat.toString());
            this.decimalFormat.setRoundingMode(RoundingMode.CEILING);
            this.decimalFormat.setMinimumFractionDigits(roundDigits);

        }
        else decimalFormat = null;

        bar = langYML().getString("messages.progress.bar");
        startBar = langYML().getString("messages.progress.complete-color", "");
        middleBar = langYML().getString("messages.progress.incomplete-color", "");
        endBar = langYML().getString("messages.progress.end-color", "");
    }

    public Configuration levelsYML() {
        return main.getFiles().getConfig("levels");
    }

    public Configuration langYML() {
        return main.getFiles().getConfig("lang");
    }

    public String generalFormula() {
        return levelsYML().getString("levels.experience.general-formula");
    }

    @Nullable
    public String levelFormula(long level) {
        if (!useSpecificFormula) return generalFormula();
        return levelsYML().getString("levels.experience.level." + level);
    }

    public double roundDecimal(double value) {
        if (decimalFormat == null) return value;
        return Double.parseDouble(decimalFormat.format(value).replace(",", "."));
    }

    public String roundStringDecimal(double value) {
        if (decimalFormat == null) return value + "";
        //if (decimalFormat.toPattern().equals("#")) return (int) value + "";
        return decimalFormat.format(value).replace(",", ".");
    }

    public String progressBar(Double exp, Double requiredExp) {
        if (requiredExp == 0) return startBar + bar + middleBar + endBar;
        int completion = Math.min((int) ((exp / requiredExp) * bar.length()), bar.length());
        if (completion == 0) return startBar + middleBar + bar + endBar;
        return startBar + bar.substring(0, completion) + middleBar + bar.substring(completion) + endBar;

    }

    public String getPlaceholders(String string, Player player, boolean playerPlaceholder) {
        return getPlaceholders(string, player, playerPlaceholder, false);
    }

    public String getPlaceholders(String string, Player player, boolean playerPlaceholder, boolean expRequirement) {
        String[] keys = {"{level}", "{playerEXP}", "{nextLevel}",
                "{maxLevel}", "{minLevel}", "{minEXP}"};
        String[] values = {
                main.getLevelCache().playerLevels().get(player).getLevel() + "",
                roundStringDecimal(main.getLevelCache().playerLevels().get(player).getExp()),
                (main.getLevelCache().playerLevels().get(player).getLevel() + 1) + "",
                main.getLevelCache().maxLevel() + "", main.getLevelCache().startLevel() + "",
                main.getLevelCache().startExp() + "",
        };
        string = StringUtils.replaceEach(string, keys, values);

        if (!expRequirement) {
            String[] keys1 = {"{requiredEXP}", "{percent}", "{progressBar}"};
            String[] values1 = {
                    roundStringDecimal(main.getLevelCache().playerLevels().get(player).nextExpRequirement()),
                    getPercent(
                            main.getLevelCache().playerLevels().get(player).getExp(),
                            main.getLevelCache().playerLevels().get(player).nextExpRequirement()
                    ),
                    progressBar(
                            main.getLevelCache().playerLevels().get(player).getExp(),
                            main.getLevelCache().playerLevels().get(player).nextExpRequirement()
                    )
            };
            string = StringUtils.replaceEach(string, keys1, values1);
        }

        if (playerPlaceholder) {
            String[] keys1 = {"{player}", "{playerDisplayName}", "{playerUUID}"};
            String[] values1 = {
                    player.getName(), player.getDisplayName(),
                    player.getUniqueId().toString()
            };
            string = StringUtils.replaceEach(string, keys1, values1);
        }
        return string;
    }

    public String getPercent(Double exp, Double requiredExp) {
        if (requiredExp.equals(exp)) return "100";
        return (int) (100 * (exp / requiredExp)) + "";
    }

}
