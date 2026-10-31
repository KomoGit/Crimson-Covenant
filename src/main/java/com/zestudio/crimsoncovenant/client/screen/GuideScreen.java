package com.zestudio.crimsoncovenant.client.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.zestudio.crimsoncovenant.CrimsonCovenant;
import com.zestudio.crimsoncovenant.guide.Bestiary;
import com.zestudio.crimsoncovenant.guide.GuideBooks;
import com.zestudio.crimsoncovenant.guide.SkillLore;
import com.zestudio.crimsoncovenant.guide.Souls;
import com.zestudio.crimsoncovenant.network.SkillPayloads;
import com.zestudio.crimsoncovenant.registry.ModAttachments;
import com.zestudio.crimsoncovenant.registry.ModTags;
import com.zestudio.crimsoncovenant.skill.BloodSkill;
import com.zestudio.crimsoncovenant.skill.SkillUpgrade;
import com.zestudio.crimsoncovenant.vampirism.VampireAge;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/// The guide books, read: an open book of two pages at a time, turned with the arrows at the foot, its contents linked
/// from the first spread. What's in it is worked out from what the reader knows right now (the skills they've unlocked
/// and had translated, the creatures they've seen, their age), and worked out again whenever that changes.
///
/// The Night's Ledger: the factions (and how to join them), the night's events, and the bestiary. The Codex Sanguinis:
/// a vampire's gifts and banes, growing old, builds, souls and translation, every blood skill, events and bestiary.
public class GuideScreen extends Screen {
    private static final int BOOK_WIDTH = 300;
    private static final int BOOK_HEIGHT = 190;
    private static final int PAGE_WIDTH = 116;
    private static final int LINES_PER_PAGE = 15;
    private static final int LINE_HEIGHT = 10;
    private static final int TEXT = 0xFF3A2A1C;
    private static final int HEADING = 0xFF7A1018;
    private static final int LINK = 0xFF5B1F6E;
    private static final int LINK_HOVER = 0xFFA0306A;
    private static final int BUTTON = 0xFF1F5E2A;
    private static final int MUTED = 0xFF7A6A58;

    private final GuideBooks.Book book;
    private final Identifier texture;
    private final List<List<Line>> pages = new ArrayList<>();
    private final Map<String, Integer> anchors = new HashMap<>();
    private int spread;
    private String knownState = "";
    private @Nullable PageButton forward;
    private @Nullable PageButton back;

    /// One line of a page: its text, colour, and what clicking it does (a jump to an anchor, or an action), if anything
    private record Line(FormattedCharSequence text, int color, @Nullable String target, @Nullable Runnable action) {}

    public GuideScreen(GuideBooks.Book book) {
        super(book.title());
        this.book = book;
        this.texture = CrimsonCovenant.id(book == GuideBooks.Book.CODEX ? "textures/gui/guide_codex.png" : "textures/gui/guide_ledger.png");
    }

    public static void open(GuideBooks.Book book) {
        Minecraft.getInstance().gui.setScreen(new GuideScreen(book));
    }

    @Override
    protected void init() {
        int left = this.left();
        int top = this.top();
        this.back = this.addRenderableWidget(new PageButton(left + 24, top + BOOK_HEIGHT - 22, false, button -> this.turn(-1), true));
        this.forward = this.addRenderableWidget(new PageButton(left + BOOK_WIDTH - 47, top + BOOK_HEIGHT - 22, true, button -> this.turn(1), true));
        this.rebuild();
    }

    private int left() {
        return (this.width - BOOK_WIDTH) / 2;
    }

    private int top() {
        return Math.max(4, (this.height - BOOK_HEIGHT) / 2);
    }

    private void turn(int by) {
        this.spread = Math.clamp(this.spread + by, 0, this.lastSpread());
        this.updateButtons();
    }

    private int lastSpread() {
        return Math.max(0, (this.pages.size() - 1) / 2);
    }

    private void updateButtons() {
        if (this.back != null && this.forward != null) {
            this.back.visible = this.spread > 0;
            this.forward.visible = this.spread < this.lastSpread();
        }
    }

    private void jump(String anchor) {
        Integer page = this.anchors.get(anchor);
        if (page != null) {
            this.spread = page / 2;
            this.updateButtons();
            if (this.minecraft != null) {
                this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
            }
        }
    }

    // ---- What's in it ----

    /// A signature of everything the book shows that can change while it's open
    private String state(LocalPlayer player) {
        return player.getData(ModAttachments.UNLOCKED_SKILLS) + "|" + player.getData(ModAttachments.TRANSLATED_SKILLS) + "|"
                + player.getData(ModAttachments.ENCOUNTERED) + "|" + VampireAge.days(player) + "|" + Souls.count(player) + "|" + SkillLore.isNearTranslator(player);
    }

    private void rebuild() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        this.knownState = this.state(player);
        this.pages.clear();
        this.anchors.clear();
        Writer writer = new Writer();
        if (this.book == GuideBooks.Book.CODEX) {
            this.writeCodex(writer, player);
        } else {
            this.writeLedger(writer, player);
        }
        this.spread = Math.clamp(this.spread, 0, this.lastSpread());
        this.updateButtons();
    }

    private static Component key(String key, Object... args) {
        return Component.translatable("guide.crimsoncovenant." + key, args);
    }

    // The Night's Ledger
    private void writeLedger(Writer w, LocalPlayer player) {
        w.title(this.book.title());
        w.para(key("ledger.welcome"));
        w.contents(new String[] {"night", "vampires", "hunters", "events", "bestiary"}, "ledger.chapter.");
        w.chapter("night", key("ledger.chapter.night"));
        w.paras("ledger.night", 4);
        w.chapter("vampires", key("ledger.chapter.vampires"));
        w.paras("ledger.vampires", 5);
        w.chapter("hunters", key("ledger.chapter.hunters"));
        w.paras("ledger.hunters", 5);
        this.writeEvents(w);
        this.writeBestiary(w, player);
    }

    // The Codex Sanguinis
    private void writeCodex(Writer w, LocalPlayer player) {
        w.title(this.book.title());
        int days = VampireAge.days(player);
        int tier = VampireAge.tier(player);
        w.para(key("codex.welcome"));
        w.para(key(tier == 0 ? "codex.age.fledgling" : "codex.age.now", days, VampireAge.tierName(tier)).copy().withStyle(ChatFormatting.DARK_RED));
        w.contents(new String[] {"gift", "curse", "age", "builds", "souls", "skills", "events", "bestiary"}, "codex.chapter.");
        w.chapter("gift", key("codex.chapter.gift"));
        w.paras("codex.gift", 6);
        w.chapter("curse", key("codex.chapter.curse"));
        w.paras("codex.curse", 7);
        w.chapter("age", key("codex.chapter.age"));
        w.para(key("codex.age.intro"));
        for (int t = 1; t <= VampireAge.MAX_TIER; t++) {
            MutableComponent line = key("codex.age.tier." + t, VampireAge.tierName(t)).copy();
            w.para(t == tier ? line.withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD) : line);
        }
        w.paras("codex.age.more", 3);
        w.chapter("builds", key("codex.chapter.builds"));
        w.paras("codex.builds", 5);
        w.chapter("souls", key("codex.chapter.souls"));
        w.paras("codex.souls", 6);
        this.writeSkills(w, player);
        this.writeEvents(w);
        this.writeBestiary(w, player);
    }

    private void writeSkills(Writer w, LocalPlayer player) {
        w.chapter("skills", key("codex.chapter.skills"));
        w.para(key("codex.skills.intro", Souls.count(player)));
        for (BloodSkill.Tier tier : BloodSkill.Tier.values()) {
            boolean open = SkillLore.isTierOpen(player, tier);
            w.heading(tier.displayName().copy().append(open ? Component.empty() : key("codex.skills.sealed_tier")));
            for (BloodSkill skill : BloodSkill.values()) {
                if (skill.tier() != tier) {
                    continue;
                }
                String status = SkillLore.isTranslated(player, skill) ? "translated" : SkillLore.isUnlocked(player, skill) ? "unlocked" : "locked";
                w.link(skill.displayName().copy().append(key("codex.skills.status." + status)), "skill." + skill.getName());
            }
        }
        for (BloodSkill skill : BloodSkill.values()) {
            this.writeSkillPage(w, player, skill);
        }
    }

    private void writeSkillPage(Writer w, LocalPlayer player, BloodSkill skill) {
        w.chapter("skill." + skill.getName(), skill.displayName());
        w.para(key("codex.skill.tier", skill.tier().displayName()));
        if (SkillLore.isTranslated(player, skill)) {
            w.para(Component.translatable("item.crimsoncovenant.skill.description." + skill.getName()));
            int cost = VampireAge.discounted(skill.cost(), player);
            w.para(key("codex.skill.cost", cost));
            w.para(key("codex.skill.cooldown", String.format("%.1f", skill.cooldown() / 20.0)));
            SkillUpgrade upgrade = SkillUpgrade.forSkill(skill);
            if (upgrade != null) {
                w.heading(key("codex.skill.upgrade", Component.translatable("skill_upgrade.crimsoncovenant." + upgrade.getName())));
                w.para(Component.translatable("skill_upgrade.crimsoncovenant." + upgrade.getName() + ".description"));
                w.para(key("codex.skill.upgrade_how", upgrade.extraCost()));
            }
            w.heading(key("codex.skill.found_heading"));
            w.para(key("codex.skill.found." + skill.getName()));
        } else if (SkillLore.isUnlocked(player, skill)) {
            w.para(key("codex.skill.unlocked"));
            w.heading(key("codex.skill.found_heading"));
            w.para(key("codex.skill.found." + skill.getName()));
            w.para(key("codex.skill.translate", skill.tier().translationTicks() / 1200));
        } else {
            int souls = SkillLore.soulCost(skill.tier());
            w.para(key("codex.skill.locked", souls));
            if (!SkillLore.isTierOpen(player, skill.tier())) {
                w.para(key("codex.skill.sealed", SkillLore.TO_OPEN_NEXT_TIER, skill.tier() == BloodSkill.Tier.ANCIENT
                        ? BloodSkill.Tier.UNUSUAL.displayName() : BloodSkill.Tier.NEO.displayName()).copy().withStyle(ChatFormatting.DARK_GRAY));
            } else {
                w.para(key(SkillLore.isNearTranslator(player) ? "codex.skill.at_translator" : "codex.skill.go_to_translator", Souls.count(player), souls));
                w.button(key("codex.skill.unlock", souls), () -> ClientPacketDistributor.sendToServer(new SkillPayloads.UnlockSkill(skill.ordinal())));
            }
        }
    }

    private void writeEvents(Writer w) {
        w.chapter("events", key("chapter.events"));
        // Only what happens, never who: the creatures are for the bestiary, once they've been met
        for (String event : new String[] {"blood_moon", "hunter_parties", "graveyards", "rabies", "moon_gift"}) {
            w.heading(key("event." + event));
            w.para(key("event." + event + ".text"));
        }
    }

    private void writeBestiary(Writer w, LocalPlayer player) {
        w.chapter("bestiary", key("chapter.bestiary"));
        w.para(key("bestiary.intro"));
        List<EntityType<?>> creatures = new ArrayList<>();
        BuiltInRegistries.ENTITY_TYPE.getTagOrEmpty(ModTags.Entities.BESTIARY).forEach(holder -> creatures.add(holder.value()));
        for (EntityType<?> type : creatures) {
            if (Bestiary.hasSeen(player, type)) {
                w.link(type.getDescription(), "creature." + BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath());
            } else {
                w.line(key("bestiary.unseen"), MUTED);
            }
        }
        for (EntityType<?> type : creatures) {
            if (Bestiary.hasSeen(player, type)) {
                String name = BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
                w.chapter("creature." + name, type.getDescription());
                w.para(key("bestiary." + name));
            }
        }
    }

    /// Lays text out into pages: wrapping it to the page, starting a new page when one's full, and marking where each
    /// chapter begins
    private final class Writer {
        private List<Line> page;

        Writer() {
            this.page = new ArrayList<>();
            GuideScreen.this.pages.add(this.page);
        }

        void newPage() {
            if (!this.page.isEmpty()) {
                this.page = new ArrayList<>();
                GuideScreen.this.pages.add(this.page);
            }
        }

        private void add(Line line) {
            if (this.page.size() >= LINES_PER_PAGE) {
                this.page = new ArrayList<>();
                GuideScreen.this.pages.add(this.page);
            }
            // No blank line at the top of a page
            if (this.page.isEmpty() && line.text() == FormattedCharSequence.EMPTY) {
                return;
            }
            this.page.add(line);
        }

        private void wrapped(Component text, int color, @Nullable String target, @Nullable Runnable action) {
            for (FormattedCharSequence line : GuideScreen.this.font.split(text, PAGE_WIDTH)) {
                this.add(new Line(line, color, target, action));
            }
        }

        void title(Component title) {
            this.wrapped(title.copy().withStyle(ChatFormatting.BOLD), HEADING, null, null);
            this.spacer();
        }

        void chapter(String anchor, Component title) {
            this.newPage();
            GuideScreen.this.anchors.put(anchor, GuideScreen.this.pages.size() - 1);
            this.wrapped(title.copy().withStyle(ChatFormatting.BOLD, ChatFormatting.UNDERLINE), HEADING, null, null);
            this.spacer();
        }

        void heading(Component heading) {
            if (!this.page.isEmpty() && this.page.getLast().text() != FormattedCharSequence.EMPTY) {
                this.spacer();
            }
            this.wrapped(heading.copy().withStyle(ChatFormatting.BOLD), HEADING, null, null);
        }

        void para(Component text) {
            this.wrapped(text, TEXT, null, null);
            this.spacer();
        }

        void paras(String prefix, int count) {
            for (int i = 1; i <= count; i++) {
                this.para(key(prefix + "." + i));
            }
        }

        void line(Component text, int color) {
            this.wrapped(text, color, null, null);
        }

        void link(Component text, String target) {
            this.wrapped(Component.literal("» ").append(text), LINK, target, null);
        }

        void button(Component text, Runnable action) {
            this.spacer();
            this.wrapped(Component.literal("[ ").append(text).append(" ]").withStyle(ChatFormatting.BOLD), BUTTON, null, action);
        }

        void contents(String[] chapters, String keyPrefix) {
            this.spacer();
            this.wrapped(key("contents").copy().withStyle(ChatFormatting.BOLD), HEADING, null, null);
            for (String chapter : chapters) {
                this.link(key(keyPrefix + chapter), chapter);
            }
        }

        void spacer() {
            this.add(new Line(FormattedCharSequence.EMPTY, TEXT, null, null));
        }
    }

    // ---- Drawing and clicking ----

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        graphics.blit(RenderPipelines.GUI_TEXTURED, this.texture, this.left(), this.top(), 0.0F, 0.0F, BOOK_WIDTH, BOOK_HEIGHT, 512, 256);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && !this.state(player).equals(this.knownState)) {
            this.rebuild();
        }
        super.extractRenderState(graphics, mouseX, mouseY, a);
        for (int side = 0; side < 2; side++) {
            int index = this.spread * 2 + side;
            if (index >= this.pages.size()) {
                continue;
            }
            int x = this.pageX(side);
            int y = this.top() + 14;
            List<Line> page = this.pages.get(index);
            for (int i = 0; i < page.size(); i++) {
                Line line = page.get(i);
                int lineY = y + i * LINE_HEIGHT;
                boolean hovered = (line.target() != null || line.action() != null) && this.isOver(mouseX, mouseY, x, lineY);
                int color = hovered ? LINK_HOVER : line.color();
                graphics.text(this.font, line.text(), x, lineY, color, false);
            }
            // The page number, at the foot
            String number = String.valueOf(index + 1);
            graphics.text(this.font, number, x + (PAGE_WIDTH - this.font.width(number)) / 2, this.top() + BOOK_HEIGHT - 18, MUTED, false);
        }
        // Back to the contents, from anywhere past them
        if (this.spread > 0) {
            Component contents = key("back_to_contents");
            int x = this.left() + 10;
            int y = this.top() - 10;
            boolean hovered = mouseX >= x && mouseX < x + this.font.width(contents) && mouseY >= y && mouseY < y + 9;
            graphics.text(this.font, contents, x, y, hovered ? 0xFFFFE0A0 : 0xFFE0D0B0, true);
        }
    }

    private int pageX(int side) {
        return this.left() + (side == 0 ? 22 : BOOK_WIDTH / 2 + 12);
    }

    private boolean isOver(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + PAGE_WIDTH && mouseY >= y - 1 && mouseY < y + LINE_HEIGHT - 1;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (this.spread > 0) {
            int x = this.left() + 10;
            int y = this.top() - 10;
            if (mouseX >= x && mouseX < x + this.font.width(key("back_to_contents")) && mouseY >= y && mouseY < y + 9) {
                this.spread = 0;
                this.updateButtons();
                return true;
            }
        }
        for (int side = 0; side < 2; side++) {
            int index = this.spread * 2 + side;
            if (index >= this.pages.size()) {
                continue;
            }
            List<Line> page = this.pages.get(index);
            for (int i = 0; i < page.size(); i++) {
                Line line = page.get(i);
                if ((line.target() != null || line.action() != null) && this.isOver(mouseX, mouseY, this.pageX(side), this.top() + 14 + i * LINE_HEIGHT)) {
                    if (line.target() != null) {
                        this.jump(line.target());
                    } else {
                        line.action().run();
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
