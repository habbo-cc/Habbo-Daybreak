package com.eu.habbo.habbohotel.items.chests;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pure July contract rule evaluation shared by session validation and the SQL commit boundary. */
public final class ChestContractEvaluator {
  private ChestContractEvaluator() {}

  public static Evaluation evaluate(ChestContractPlan plan, List<HabboItem> offeredItems) {
    if (plan == null) {
      return Evaluation.invalid();
    }
    List<HabboItem> offered =
        offeredItems == null
            ? List.of()
            : offeredItems.stream()
                .filter(item -> item != null && item.getBaseItem() != null)
                .toList();
    int multiplier;
    if (plan.giveRules() == null) {
      if (plan.contractType()
              != com.eu.habbo.habbohotel.items.interactions.InteractionChestContract.TYPE_REWARD
          && offered.isEmpty()) {
        return Evaluation.invalid();
      }
      multiplier =
          plan.contractType()
                  == com.eu.habbo.habbohotel.items.interactions.InteractionChestContract.TYPE_REWARD
              ? configuredMultiplier(plan)
              : 1;
    } else {
      if (offered.isEmpty() || !allOfferedItemsPermitted(plan.giveRules(), offered)) {
        return Evaluation.invalid();
      }
      multiplier =
          plan.multiplierMode() == ChestContractPlan.MULTIPLIER_AUTO
              ? maximumSatisfiedMultiplier(plan.giveRules(), offered, plan.multiplier())
              : configuredMultiplier(plan);
      if (multiplier < 1 || !anyRuleSatisfied(plan.giveRules(), offered, multiplier)) {
        return Evaluation.invalid();
      }
    }

    long depositedCoins = 0;
    int depositedFurni = 0;
    for (HabboItem item : offered) {
      int value = ChestManager.creditValue(item);
      if (value > 0) {
        depositedCoins += value;
      } else {
        depositedFurni++;
      }
    }
    Counts withdrawals = counts(plan.getRule(), multiplier);
    if (depositedCoins > Integer.MAX_VALUE || withdrawals == null) {
      return Evaluation.invalid();
    }
    return new Evaluation(
        true,
        multiplier,
        depositedFurni,
        (int) depositedCoins,
        withdrawals.furni,
        withdrawals.coins);
  }

  public static boolean itemPermitted(ChestContractPlan plan, HabboItem item) {
    if (plan == null || item == null || item.getBaseItem() == null) {
      return false;
    }
    if (plan.giveRules() == null) {
      return true;
    }
    for (ChestContractPlan.Rule rule : plan.giveRules()) {
      for (ChestContractPlan.Node node : rule.nodes()) {
        if (matches(node, item)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * July action 47 must reject a multi-user offer before giving anything when the selected chests
   * cannot cover every reward. This is a deterministic preflight; the SQL commit still repeats the
   * checks under row locks.
   */
  public static boolean canFulfillRewards(
      ChestContractPlan plan,
      List<HabboItem> availableFurniture,
      long availableCoins,
      int recipientCount) {
    if (plan == null || recipientCount < 1 || availableCoins < 0) {
      return false;
    }
    if (plan.getRule() == null) {
      return true;
    }
    int multiplier = configuredMultiplier(plan);
    if (multiplier < 1) {
      return false;
    }
    Map<NodeKey, Long> required = new HashMap<>();
    for (ChestContractPlan.Node node : plan.getRule().nodes()) {
      long amount = (long) node.amount() * multiplier * recipientCount;
      if (amount <= 0 || amount > Integer.MAX_VALUE) {
        return false;
      }
      required.merge(NodeKey.from(node), amount, Long::sum);
    }
    List<HabboItem> furniture =
        availableFurniture == null
            ? List.of()
            : availableFurniture.stream()
                .filter(item -> item != null && item.getBaseItem() != null)
                .toList();
    for (Map.Entry<NodeKey, Long> requirement : required.entrySet()) {
      long available =
          requirement.getKey().coins
              ? availableCoins
              : furniture.stream().filter(requirement.getKey()::matches).count();
      if (available < requirement.getValue()) {
        return false;
      }
    }
    return true;
  }

  private static int configuredMultiplier(ChestContractPlan plan) {
    return plan.multiplierMode() == ChestContractPlan.MULTIPLIER_NONE ? 1 : plan.multiplier();
  }

  private static int maximumSatisfiedMultiplier(
      List<ChestContractPlan.Rule> rules, List<HabboItem> offered, int limit) {
    for (int multiplier = Math.max(1, limit); multiplier >= 1; multiplier--) {
      if (anyRuleSatisfied(rules, offered, multiplier)) {
        return multiplier;
      }
    }
    return 0;
  }

  private static boolean anyRuleSatisfied(
      List<ChestContractPlan.Rule> rules, List<HabboItem> offered, int multiplier) {
    if (rules == null || rules.isEmpty() || multiplier < 1) {
      return false;
    }
    for (ChestContractPlan.Rule rule : rules) {
      if (ruleSatisfied(rule, offered, multiplier)) {
        return true;
      }
    }
    return false;
  }

  private static boolean ruleSatisfied(
      ChestContractPlan.Rule rule, List<HabboItem> offered, int multiplier) {
    Map<NodeKey, Long> required = new HashMap<>();
    for (ChestContractPlan.Node node : rule.nodes()) {
      long amount = (long) node.amount() * multiplier;
      if (amount <= 0 || amount > Integer.MAX_VALUE) {
        return false;
      }
      required.merge(NodeKey.from(node), amount, Long::sum);
    }
    for (Map.Entry<NodeKey, Long> requirement : required.entrySet()) {
      long available =
          requirement.getKey().coins
              ? offered.stream().mapToLong(ChestManager::creditValue).sum()
              : offered.stream()
                  .filter(item -> requirement.getKey().matches(item))
                  .count();
      if (available < requirement.getValue()) {
        return false;
      }
    }
    return true;
  }

  private static boolean allOfferedItemsPermitted(
      List<ChestContractPlan.Rule> rules, List<HabboItem> offered) {
    for (HabboItem item : offered) {
      boolean permitted = false;
      for (ChestContractPlan.Rule rule : rules) {
        for (ChestContractPlan.Node node : rule.nodes()) {
          if (matches(node, item)) {
            permitted = true;
            break;
          }
        }
        if (permitted) {
          break;
        }
      }
      if (!permitted) {
        return false;
      }
    }
    return true;
  }

  private static Counts counts(ChestContractPlan.Rule rule, int multiplier) {
    if (rule == null) {
      return new Counts(0, 0);
    }
    long furni = 0;
    long coins = 0;
    for (ChestContractPlan.Node node : rule.nodes()) {
      long amount = (long) node.amount() * multiplier;
      if (node.type() == 0) {
        coins += amount;
      } else {
        furni += amount;
      }
      if (coins > Integer.MAX_VALUE || furni > Integer.MAX_VALUE) {
        return null;
      }
    }
    return new Counts((int) furni, (int) coins);
  }

  private static boolean matches(ChestContractPlan.Node node, HabboItem item) {
    if (node.type() == 0) {
      return ChestManager.creditValue(item) > 0;
    }
    return NodeKey.from(node).matches(item);
  }

  public record Evaluation(
      boolean valid,
      int multiplier,
      int depositedFurni,
      int depositedCoins,
      int withdrawnFurni,
      int withdrawnCoins) {
    static Evaluation invalid() {
      return new Evaluation(false, 0, 0, 0, 0, 0);
    }
  }

  private record Counts(int furni, int coins) {}

  private record NodeKey(boolean coins, boolean wall, int typeId, String poster) {
    static NodeKey from(ChestContractPlan.Node node) {
      if (node.type() == 0) {
        return new NodeKey(true, false, 0, "");
      }
      ChestContractPlan.ItemType type = node.itemType();
      return new NodeKey(false, type.wall(), type.typeId(), normalized(type.poster()));
    }

    boolean matches(HabboItem item) {
      if (this.coins) {
        return ChestManager.creditValue(item) > 0;
      }
      if (item == null || item.getBaseItem() == null) {
        return false;
      }
      boolean itemWall = item.getBaseItem().getType() == FurnitureType.WALL;
      if (itemWall != this.wall || item.getBaseItem().getSpriteId() != this.typeId) {
        return false;
      }
      return this.poster.isEmpty()
          || this.poster.equals(normalized(item.getExtradata()));
    }

    private static String normalized(String value) {
      return value == null ? "" : value.trim();
    }
  }
}
