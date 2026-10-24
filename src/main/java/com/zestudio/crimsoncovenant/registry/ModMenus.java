package com.zestudio.crimsoncovenant.registry;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.skill.SkillsMenu;
import com.zestudio.crimsoncovenant.curse.CurseforgeMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, CrimsonCovenant.MODID);

    /// Where players put on and take off their blood skills
    public static final DeferredHolder<MenuType<?>, MenuType<SkillsMenu>> SKILLS = MENUS.register("skills",
            () -> new MenuType<>(SkillsMenu::new, FeatureFlags.DEFAULT_FLAGS));

    /// Laying curses on weapons (see curse.CurseforgeMenu)
    public static final DeferredHolder<MenuType<?>, MenuType<CurseforgeMenu>> CURSEFORGE = MENUS.register("curseforge",
            () -> new MenuType<>(CurseforgeMenu::new, FeatureFlags.DEFAULT_FLAGS));

    private ModMenus() {}
}
