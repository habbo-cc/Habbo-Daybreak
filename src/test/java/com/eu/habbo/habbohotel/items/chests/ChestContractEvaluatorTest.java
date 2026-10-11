package com.eu.habbo.habbohotel.items.chests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionChestContract;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChestContractEvaluatorTest {
  @Test
  void evaluatesFixedMixedPaymentAndReward() throws Exception {
    ChestContractPlan plan =
        plan(
            InteractionChestContract.TYPE_TRADE,
            List.of(
                new ChestContractPlan.Rule(
                    List.of(
                        new ChestContractPlan.Node(0, 10, null),
                        new ChestContractPlan.Node(
                            1,
                            2,
                            new ChestContractPlan.ItemType(false, 900, ""))))),
            new ChestContractPlan.Rule(
                List.of(
                    new ChestContractPlan.Node(0, 3, null),
                    new ChestContractPlan.Node(
                        1, 1, new ChestContractPlan.ItemType(true, 901, "poster-7")))),
            ChestContractPlan.MULTIPLIER_FIXED,
            2);
    List<HabboItem> offered = new ArrayList<>();
    offered.add(item(1, "CF_20_test", FurnitureType.FLOOR, 50, ""));
    for (int id = 2; id <= 5; id++) {
      offered.add(item(id, "chair", FurnitureType.FLOOR, 900, ""));
    }

    ChestContractEvaluator.Evaluation result =
        ChestContractEvaluator.evaluate(plan, offered);

    assertTrue(result.valid());
    assertEquals(2, result.multiplier());
    assertEquals(4, result.depositedFurni());
    assertEquals(20, result.depositedCoins());
    assertEquals(2, result.withdrawnFurni());
    assertEquals(6, result.withdrawnCoins());
  }

  @Test
  void autoMultiplierUsesLargestCompletePayment() throws Exception {
    ChestContractPlan plan =
        plan(
            InteractionChestContract.TYPE_PAYMENT,
            List.of(
                new ChestContractPlan.Rule(
                    List.of(
                        new ChestContractPlan.Node(
                            1,
                            2,
                            new ChestContractPlan.ItemType(false, 900, ""))))),
            null,
            ChestContractPlan.MULTIPLIER_AUTO,
            5);
    List<HabboItem> offered = new ArrayList<>();
    for (int id = 1; id <= 5; id++) {
      offered.add(item(id, "chair", FurnitureType.FLOOR, 900, ""));
    }

    ChestContractEvaluator.Evaluation result =
        ChestContractEvaluator.evaluate(plan, offered);

    assertTrue(result.valid());
    assertEquals(2, result.multiplier());
    assertEquals(5, result.depositedFurni());
  }

  @Test
  void rejectsAnItemOutsideAllAlternativeRules() throws Exception {
    ChestContractPlan plan =
        plan(
            InteractionChestContract.TYPE_PAYMENT,
            List.of(
                new ChestContractPlan.Rule(
                    List.of(
                        new ChestContractPlan.Node(
                            1,
                            1,
                            new ChestContractPlan.ItemType(false, 900, ""))))),
            null,
            ChestContractPlan.MULTIPLIER_NONE,
            1);

    assertFalse(
        ChestContractEvaluator.evaluate(
                plan,
                List.of(
                    item(1, "chair", FurnitureType.FLOOR, 900, ""),
                    item(2, "table", FurnitureType.FLOOR, 999, "")))
            .valid());
  }

  @Test
  void anythingPaymentAcceptsOneTradeableFurnitureAtMultiplierOne() throws Exception {
    ChestContractPlan plan =
        plan(
            InteractionChestContract.TYPE_PAYMENT,
            null,
            null,
            ChestContractPlan.MULTIPLIER_AUTO,
            500);

    ChestContractEvaluator.Evaluation result =
        ChestContractEvaluator.evaluate(
            plan, List.of(item(1, "table", FurnitureType.FLOOR, 999, "")));

    assertTrue(result.valid());
    assertEquals(1, result.multiplier());
  }

  @Test
  void multiUserRewardPreflightRequiresEnoughOfEveryNodeForEveryone() throws Exception {
    ChestContractPlan plan =
        plan(
            InteractionChestContract.TYPE_REWARD,
            null,
            new ChestContractPlan.Rule(
                List.of(
                    new ChestContractPlan.Node(0, 4, null),
                    new ChestContractPlan.Node(
                        1,
                        2,
                        new ChestContractPlan.ItemType(false, 900, "")))),
            ChestContractPlan.MULTIPLIER_FIXED,
            2);
    List<HabboItem> furniture = new ArrayList<>();
    for (int id = 1; id <= 12; id++) {
      furniture.add(item(id, "chair", FurnitureType.FLOOR, 900, ""));
    }

    assertTrue(ChestContractEvaluator.canFulfillRewards(plan, furniture, 24, 3));
    assertFalse(ChestContractEvaluator.canFulfillRewards(plan, furniture, 23, 3));
    assertFalse(
        ChestContractEvaluator.canFulfillRewards(
            plan, furniture.subList(0, 11), 24, 3));
  }

  private static HabboItem item(
      int id, String name, FurnitureType type, int spriteId, String extraData) {
    HabboItem item = mock(HabboItem.class);
    Item base = mock(Item.class);
    when(item.getId()).thenReturn(id);
    when(item.getBaseItem()).thenReturn(base);
    when(item.getExtradata()).thenReturn(extraData);
    when(base.getName()).thenReturn(name);
    when(base.getType()).thenReturn(type);
    when(base.getSpriteId()).thenReturn(spriteId);
    return item;
  }

  @SuppressWarnings("unchecked")
  private static ChestContractPlan plan(
      int type,
      List<ChestContractPlan.Rule> give,
      ChestContractPlan.Rule get,
      int multiplierMode,
      int multiplier)
      throws Exception {
    Constructor<ChestContractPlan> constructor =
        ChestContractPlan.class.getDeclaredConstructor(
            int.class,
            int.class,
            int.class,
            List.class,
            ChestContractPlan.Rule.class,
            String.class,
            String.class,
            int.class,
            boolean.class,
            String.class,
            int.class,
            int.class);
    constructor.setAccessible(true);
    return constructor.newInstance(
        100,
        200,
        type,
        give,
        get,
        "receive",
        "generic",
        11,
        true,
        "reward",
        multiplierMode,
        multiplier);
  }
}
