package com.eu.habbo.habbohotel.items;

import com.eu.habbo.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One crackable furni's reward configuration, aggregated from one-or-more rows of items_crackable
 * (all sharing the same crackable_id).
 *
 * Model:
 *  - tier  : independent draw group. Each tier rolls exactly one outcome; the crackable's result is
 *            the union of every tier's winner. Chances only need to be sane WITHIN a tier.
 *  - type  : 's' simple  -> item_ids and chance are parallel CSVs; each item is its own outcome.
 *            'b' bundle  -> item_ids is one outcome dropped together; chance is a single number.
 *  - chance: decimals supported (e.g. 8.33). Scaled x100 to an integer weight so the weighted-range
 *            math stays integer and free of float drift.
 *
 * Per-furni meta (count, required_effect, achievements, subscription) is read from the first row seen
 * for a crackable_id (authors must keep it identical across that crackable's rows).
 */
public class CrackableReward {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrackableReward.class);

    public final int crackableId;

    public int count;
    public String achievementTick;
    public String achievementCracked;
    public int requiredEffect;
    public int subscriptionDuration;
    public RedeemableSubscriptionType subscriptionType;

    private final Map<Integer, Tier> tiers = new LinkedHashMap<>();

    public CrackableReward(ResultSet set) throws SQLException {
        this.crackableId = set.getInt("crackable_id");
        this.applyMeta(set);
        this.addRow(set);
    }

    /** Copy the per-furni metadata from a row. */
    public final void applyMeta(ResultSet set) throws SQLException {
        this.count = set.getInt("count");
        this.achievementTick = set.getString("achievement_tick");
        this.achievementCracked = set.getString("achievement_cracked");
        this.requiredEffect = set.getInt("required_effect");
        this.subscriptionDuration = set.getInt("subscription_duration");
        this.subscriptionType = RedeemableSubscriptionType.fromString(set.getString("subscription_type"));
    }

    /** Parse one items_crackable row into outcome(s) under its tier. Bad rows are logged and skipped. */
    public final void addRow(ResultSet set) throws SQLException {
        int tierId = set.getInt("tier");
        String type = set.getString("type");
        String itemIdsRaw = set.getString("item_ids");
        String chanceRaw = set.getString("chance");

        type = (type == null) ? "s" : type.trim().toLowerCase();

        if (itemIdsRaw == null || itemIdsRaw.trim().isEmpty()) {
            // A subscription-only crackable, such as a club gift box, has no furniture payout by
            // design: its reward comes from subscription_type/subscription_duration and is granted
            // in InteractionCrackable, which never consults the tier outcomes. Read those columns
            // from this row rather than the instance, because addRow is also called for additional
            // tier rows of an already-loaded crackable.
            int rowSubscriptionDuration = set.getInt("subscription_duration");
            RedeemableSubscriptionType rowSubscriptionType =
                    RedeemableSubscriptionType.fromString(set.getString("subscription_type"));

            if (rowSubscriptionType == null || rowSubscriptionDuration <= 0) {
                LOGGER.error("items_crackable: empty item_ids and no subscription reward (crackable_id={}, tier={}). Skipping row.", this.crackableId, tierId);
            }
            return;
        }

        String[] idTokens = itemIdsRaw.split(",");
        Tier tier = this.tiers.computeIfAbsent(tierId, k -> new Tier());

        try {
            if (type.equals("b")) {
                int[] ids = new int[idTokens.length];
                for (int i = 0; i < idTokens.length; i++) {
                    ids[i] = Integer.parseInt(idTokens[i].trim());
                }
                tier.add(new Outcome(ids, parseWeight(chanceRaw)));
            } else {
                String[] chanceTokens = (chanceRaw == null ? "" : chanceRaw).split(",");
                if (chanceTokens.length != idTokens.length) {
                    LOGGER.error("items_crackable: simple row item_ids/chance length mismatch (crackable_id={}, tier={}). Skipping row.", this.crackableId, tierId);
                    return;
                }
                for (int i = 0; i < idTokens.length; i++) {
                    int id = Integer.parseInt(idTokens[i].trim());
                    tier.add(new Outcome(new int[]{id}, parseWeight(chanceTokens[i])));
                }
            }
        } catch (NumberFormatException e) {
            LOGGER.error("items_crackable: bad number in row (crackable_id={}, tier={}). Skipping row.", this.crackableId, tierId, e);
        }
    }

    /** Decimal chance -> integer weight, scaled x100 (8.33 -> 833). */
    private static int parseWeight(String chance) {
        if (chance == null) return 0;
        chance = chance.trim();
        if (chance.isEmpty()) return 0;
        return (int) Math.round(Double.parseDouble(chance) * 100.0D);
    }

    /** Roll one outcome per tier; return the union of every tier's winning item ids. */
    public List<Integer> roll() {
        List<Integer> rewards = new ArrayList<>();
        for (Tier tier : this.tiers.values()) {
            if (tier.totalWeight <= 0) continue;

            int random = Emulator.getRandom().nextInt(tier.totalWeight);
            int cumulative = 0;
            for (Outcome outcome : tier.outcomes) {
                cumulative += outcome.weight;
                if (random < cumulative) {
                    for (int id : outcome.itemIds) {
                        rewards.add(id);
                    }
                    break;
                }
            }
        }
        return rewards;
    }

    public boolean hasRewards() {
        for (Tier tier : this.tiers.values()) {
            if (tier.totalWeight > 0) return true;
        }
        return false;
    }

    private static class Tier {
        final List<Outcome> outcomes = new ArrayList<>();
        int totalWeight = 0;

        void add(Outcome outcome) {
            if (outcome.weight <= 0) return;
            this.outcomes.add(outcome);
            this.totalWeight += outcome.weight;
        }
    }

    private static class Outcome {
        final int[] itemIds;
        final int weight;

        Outcome(int[] itemIds, int weight) {
            this.itemIds = itemIds;
            this.weight = weight;
        }
    }
}
