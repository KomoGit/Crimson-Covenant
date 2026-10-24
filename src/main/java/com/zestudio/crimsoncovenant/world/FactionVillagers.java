package com.zestudio.crimsoncovenant.world;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.registry.ModBlocks;
import com.zestudio.crimsoncovenant.registry.ModVillagers;
import com.zestudio.crimsoncovenant.vampirism.Vampirism;
import com.zestudio.crimsoncovenant.entity.PlagueDoctor;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/// A village sides with the hunters or with the vampires, never both. The Plague Doctor (Hunter's Table) and the
/// Ancient Butler (Blood Altar) can't live within 64 blocks of each other, so a faction's workstation can't be
/// placed near the other faction's workstation or villager.
/// The Plague Doctor won't deal with vampire players at all, and cures anyone else who brings him emeralds.
@EventBusSubscriber(modid = CrimsonCovenant.MODID)
public final class FactionVillagers {
    public static final int VILLAGE_RANGE = 64;

    public enum Faction {
        HUNTERS(ModVillagers.PLAGUE_DOCTOR, ModVillagers.HUNTERS_TABLE_POI, "hunters"),
        VAMPIRES(ModVillagers.ANCIENT_BUTLER, ModVillagers.BLOOD_ALTAR_POI, "vampires");

        private final DeferredHolder<VillagerProfession, VillagerProfession> profession;
        private final DeferredHolder<PoiType, PoiType> jobSite;
        private final String name;

        Faction(DeferredHolder<VillagerProfession, VillagerProfession> profession, DeferredHolder<PoiType, PoiType> jobSite, String name) {
            this.profession = profession;
            this.jobSite = jobSite;
            this.name = name;
        }

        public Faction opposite() {
            return this == HUNTERS ? VAMPIRES : HUNTERS;
        }
    }

    private FactionVillagers() {}

    public static Faction factionOf(BlockState state) {
        if (state.is(ModBlocks.HUNTERS_TABLE.get())) {
            return Faction.HUNTERS;
        }
        return state.is(ModBlocks.BLOOD_ALTAR.get()) ? Faction.VAMPIRES : null;
    }

    /// Whether the other faction already has a workstation or a villager within village range of this spot
    public static boolean otherFactionNearby(ServerLevel level, BlockPos pos, Faction faction) {
        Faction other = faction.opposite();
        boolean workstation = level.getPoiManager()
                .findClosest(poi -> poi.is(other.jobSite.getKey()), pos, VILLAGE_RANGE, PoiManager.Occupancy.ANY)
                .isPresent();
        return workstation || !level.getEntitiesOfClass(Villager.class, new AABB(pos).inflate(VILLAGE_RANGE),
                villager -> isProfession(villager.getVillagerData().profession(), other)).isEmpty();
    }

    private static boolean isProfession(Holder<VillagerProfession> profession, Faction faction) {
        return profession.is(faction.profession.getKey());
    }

    @SubscribeEvent
    static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        Faction faction = factionOf(event.getPlacedBlock());
        if (faction == null || !(event.getLevel() instanceof ServerLevel level) || !otherFactionNearby(level, event.getPos(), faction)) {
            return;
        }
        event.setCanceled(true);
        if (event.getEntity() instanceof Player player) {
            player.sendSystemMessage(Component.translatable("message.crimsoncovenant.faction_conflict." + faction.opposite().name).withStyle(ChatFormatting.RED));
        }
    }

    @SubscribeEvent
    static void refuseVampires(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (!(event.getTarget() instanceof Villager villager) || !isProfession(villager.getVillagerData().profession(), Faction.HUNTERS)
                || !Vampirism.isVampire(player)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!villager.level().isClientSide()) {
            // Shakes his head, the way a villager with nothing to sell does
            villager.setUnhappyCounter(40);
            villager.makeSound(SoundEvents.VILLAGER_NO);
            player.sendOverlayMessage(Component.translatable("message.crimsoncovenant.plague_doctor_refuses").withStyle(ChatFormatting.RED));
        }
    }

    /// A Plague Doctor villager cures, for emeralds, the same as the wandering one (see PlagueDoctor.treat)
    @SubscribeEvent
    static void doctorsCure(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (event.isCanceled() || !(event.getTarget() instanceof Villager villager) || !isProfession(villager.getVillagerData().profession(), Faction.HUNTERS)
                || Vampirism.isVampire(player) || !player.getItemInHand(event.getHand()).is(Items.EMERALD) || PlagueDoctor.cureCost(player) == 0) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!villager.level().isClientSide()) {
            PlagueDoctor.treat(villager, player);
        }
    }
}
