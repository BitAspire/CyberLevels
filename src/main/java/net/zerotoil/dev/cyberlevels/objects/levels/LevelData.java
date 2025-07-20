package net.zerotoil.dev.cyberlevels.objects.levels;

import lombok.Getter;
import lombok.Setter;
import net.objecthunter.exp4j.ExpressionBuilder;
import net.zerotoil.dev.cyberlevels.CyberLevels;
import net.zerotoil.dev.cyberlevels.objects.RewardObject;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class LevelData {

    private final CyberLevels main;
    private Long level;
    private String expFormula;

    @Getter
    @Setter
    private List<RewardObject> rewards;

    public LevelData(CyberLevels main, Long level) {
        this.main = main;
        setLevel(level);
    }

    public void setLevel(Long level) {
        this.level = level;
        String formula = main.getLevelUtils().levelFormula(level);
        if (formula == null) formula = main.getLevelUtils().generalFormula();
        expFormula = formula;
        clearRewards();
    }

    public void addReward(RewardObject reward) {
        rewards.add(reward);
    }

    public void clearRewards() {
        rewards = new ArrayList<>();
    }

    public Double getRequiredExp(Player player) {
        String formula = expFormula;
        formula = main.getLevelUtils().getPlaceholders(formula, player, false, true);
        return (new ExpressionBuilder(formula).build().evaluate());
    }

}
