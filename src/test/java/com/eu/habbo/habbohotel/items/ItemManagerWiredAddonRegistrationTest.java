package com.eu.habbo.habbohotel.items;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonExecuteInOrder;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonConditionEvaluation;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonRandomEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonUnseenEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonExecutionLimit;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonNoMoveAnimation;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonMovementPhysics;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonCarryUsers;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonAnimationTime;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonFurniSelectorFilter;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonUserSelectorFilter;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonUsernamePlaceholder;
import com.eu.habbo.habbohotel.items.interactions.wired.addons.WiredAddonFurniNamePlaceholder;
import com.eu.habbo.habbohotel.items.interactions.wired.variables.WiredVariableGlobal;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectSendSignal;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectSendSignalNegative;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectRemoveVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectChangeVariableValue;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectWriteToLogs;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectWriteToLogsNegative;
import com.eu.habbo.habbohotel.items.interactions.wired.selectors.WiredSelectorFurniFromSignal;
import com.eu.habbo.habbohotel.items.interactions.wired.selectors.WiredSelectorUsersFromSignal;
import com.eu.habbo.habbohotel.items.interactions.wired.selectors.WiredSelectorRemote;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerReceiveSignal;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerVariableChanged;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemManagerWiredAddonRegistrationTest {
    @TempDir
    Path tempDir;

    private Field configField;
    private Object previousConfig;

    @BeforeEach
    void installMinimalConfig() throws Exception {
        Path configFile = this.tempDir.resolve("item-manager.ini");
        Files.writeString(configFile, "youtube.apikey=\n");
        this.configField = Emulator.class.getDeclaredField("config");
        this.configField.setAccessible(true);
        this.previousConfig = this.configField.get(null);
        this.configField.set(null, new ConfigurationManager(configFile.toString()));
    }

    @AfterEach
    void restoreConfig() throws Exception {
        this.configField.set(null, this.previousConfig);
    }

    @Test
    void officialFurnitureNameMapsToJulyAddonImplementation() {
        TestItemManager manager = new TestItemManager();
        manager.registerInteractions();

        assertEquals(WiredAddonExecuteInOrder.class,
                manager.getItemInteraction("wf_xtra_exec_in_order").getType());
        assertEquals(WiredAddonConditionEvaluation.class, manager.getItemInteraction("wf_xtra_or_eval").getType());
        assertEquals(WiredAddonRandomEffect.class, manager.getItemInteraction("wf_xtra_random").getType());
        assertEquals(WiredAddonUnseenEffect.class, manager.getItemInteraction("wf_xtra_unseen").getType());
        assertEquals(WiredAddonExecutionLimit.class, manager.getItemInteraction("wf_xtra_execution_limit").getType());
        assertEquals(WiredAddonNoMoveAnimation.class, manager.getItemInteraction("wf_xtra_mov_no_animation").getType());
        assertEquals(WiredAddonMovementPhysics.class, manager.getItemInteraction("wf_xtra_mov_physics").getType());
        assertEquals(WiredAddonCarryUsers.class, manager.getItemInteraction("wf_xtra_mov_carry_users").getType());
        assertEquals(WiredAddonAnimationTime.class, manager.getItemInteraction("wf_xtra_anim_time").getType());
        assertEquals(WiredAddonFurniSelectorFilter.class, manager.getItemInteraction("wf_xtra_filter_furni").getType());
        assertEquals(WiredAddonUserSelectorFilter.class, manager.getItemInteraction("wf_xtra_filter_users").getType());
        assertEquals(WiredAddonUsernamePlaceholder.class, manager.getItemInteraction("wf_xtra_text_output_username").getType());
        assertEquals(WiredAddonFurniNamePlaceholder.class, manager.getItemInteraction("wf_xtra_text_output_furni_name").getType());
        assertEquals(WiredVariableGlobal.class,
                manager.getItemInteraction("wf_var_room").getType());
        assertEquals(WiredTriggerReceiveSignal.class,
                manager.getItemInteraction("wf_trg_recv_signal").getType());
        assertEquals(WiredTriggerVariableChanged.class,
                manager.getItemInteraction("wf_trg_var_changed").getType());
        assertEquals(WiredEffectSendSignal.class,
                manager.getItemInteraction("wf_act_send_signal").getType());
        assertEquals(WiredEffectSendSignalNegative.class,
                manager.getItemInteraction("wf_act_send_signal_negative").getType());
        assertEquals(WiredEffectGiveVariable.class,
                manager.getItemInteraction("wf_act_give_var").getType());
        assertEquals(WiredEffectRemoveVariable.class,
                manager.getItemInteraction("wf_act_remove_var").getType());
        assertEquals(WiredEffectChangeVariableValue.class,
                manager.getItemInteraction("wf_act_change_var_val").getType());
        assertEquals(WiredEffectWriteToLogs.class,
                manager.getItemInteraction("wf_act_log").getType());
        assertEquals(WiredEffectWriteToLogsNegative.class,
                manager.getItemInteraction("wf_act_neg_log").getType());
        assertEquals(WiredSelectorFurniFromSignal.class,
                manager.getItemInteraction("wf_slc_furni_signal").getType());
        assertEquals(WiredSelectorUsersFromSignal.class,
                manager.getItemInteraction("wf_slc_users_signal").getType());
        assertEquals(WiredSelectorRemote.class,
                manager.getItemInteraction("wf_slc_remote").getType());
    }

    private static final class TestItemManager extends ItemManager {
        private void registerInteractions() {
            super.loadItemInteractions();
        }
    }
}
