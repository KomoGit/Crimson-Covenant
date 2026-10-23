package com.zestudio.crimsoncovenant.entity;

import com.zestudio.crimsoncovenant.CrimsonCovenant;

import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/// Hunters attack every undead mob, so every hostile undead mob attacks hunters on sight too: vanilla zombies,
/// skeletons, drowned, phantoms and so on, plus other mods' undead. This mod's own undead already target
/// hunters themselves. Peaceful undead (like skeleton horses) are left alone.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class UndeadHuntHunters {
    private UndeadHuntHunters() {}

    @SubscribeEvent
    static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()
                || !(event.getEntity() instanceof Mob mob)
                || !(mob instanceof Enemy)
                || !mob.is(EntityTypeTags.UNDEAD)
                || mob.getType().builtInRegistryHolder().key().identifier().getNamespace().equals(CrimsonCovenant.MODID)) {
            return;
        }
        mob.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(mob, Hunter.class, true));
    }
}
