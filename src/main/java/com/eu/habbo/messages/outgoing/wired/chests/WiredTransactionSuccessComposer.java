package com.eu.habbo.messages.outgoing.wired.chests;

import com.eu.habbo.habbohotel.items.chests.ChestContractPlan;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** Exact July S2C 2677 reward-success payload on the collision-free local header 7133. */
public final class WiredTransactionSuccessComposer extends MessageComposer {
  private static final int REWARD_CONTENTS = 2;

  private final ChestContractPlan.Rule rewardRule;
  private final String rewardText;
  private final boolean showDialog;
  private final int multiplier;

  public WiredTransactionSuccessComposer(ChestContractPlan contract, int multiplier) {
    if (contract == null || contract.getRule() == null || multiplier < 1) {
      throw new IllegalArgumentException("reward contract");
    }
    this.rewardRule = contract.getRule();
    this.rewardText = contract.rewardText();
    this.showDialog = contract.showDialog();
    this.multiplier = multiplier;
  }

  public WiredTransactionSuccessComposer(
      ChestContractPlan.Rule rewardRule, String rewardText, boolean showDialog) {
    if (rewardRule == null) {
      throw new IllegalArgumentException("reward rule");
    }
    this.rewardRule = rewardRule;
    this.rewardText = rewardText == null ? "" : rewardText;
    this.showDialog = showDialog;
    this.multiplier = 1;
  }

  @Override
  protected ServerMessage composeInternal() {
    this.response.init(Outgoing.WiredTransactionSuccessComposer);
    this.response.appendInt(REWARD_CONTENTS);
    appendRule(this.rewardRule);
    this.response.appendString(this.rewardText);
    this.response.appendBoolean(this.showDialog);
    return this.response;
  }

  private void appendRule(ChestContractPlan.Rule rule) {
    this.response.appendInt(rule.nodes().size());
    for (ChestContractPlan.Node node : rule.nodes()) {
      long scaled = (long) node.amount() * this.multiplier;
      if (scaled < 1 || scaled > Integer.MAX_VALUE) {
        throw new IllegalArgumentException("scaled reward amount");
      }
      this.response.appendByte(node.type());
      this.response.appendInt((int) scaled);
      if (node.type() == 1) {
        this.response.appendBoolean(node.itemType().wall());
        this.response.appendInt(node.itemType().typeId());
        this.response.appendString(node.itemType().poster());
      }
    }
  }
}
