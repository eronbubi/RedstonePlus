package de.eron.redstoneplus.guide.client;

import de.eron.redstoneplus.RedstonePlus;
import de.eron.redstoneplus.guide.Guide;
import de.eron.redstoneplus.realm.Realm;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the guide's pages from what is registered right now and shows them in the vanilla book screen.
 * Text is measured with the game font, so an entry never runs off a page.
 */
public final class GuideScreen {
    private static final int WIDTH = 114;
    private static final int LINES = 14;
    private static final String NS = RedstonePlus.MODID;

    private GuideScreen() {
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new BookViewScreen(new BookViewScreen.BookAccess(build())));
    }

    /** Collects entries into pages that fit. */
    private static final class Pager {
        final Font font = Minecraft.getInstance().font;
        final List<Component> pages = new ArrayList<>();
        MutableComponent page = Component.empty();
        int lines;

        int lines(Component c) {
            return Math.max(1, this.font.split(c, WIDTH).size());
        }

        void entry(Component c) {
            int n = this.lines(c) + (this.lines > 0 ? 1 : 0);
            if (this.lines > 0 && this.lines + n > LINES) {
                this.flush();
                n = this.lines(c);
            }
            if (this.lines > 0) {
                this.page.append("\n\n");
            }
            this.page.append(c);
            this.lines += n;
        }

        void fullPage(Component c) {
            this.flush();
            this.pages.add(c);
        }

        void flush() {
            if (this.lines > 0) {
                this.pages.add(this.page);
                this.page = Component.empty();
                this.lines = 0;
            }
        }
    }

    private static MutableComponent t(String key) {
        return Component.translatable(key);
    }

    private static Component heading(Component name) {
        return name.copy().withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
    }

    private static Component entry(Component name, String descKey) {
        MutableComponent c = heading(name).copy();
        if (I18n.exists(descKey)) {
            c.append("\n").append(t(descKey).withStyle(ChatFormatting.BLACK));
        }
        return c;
    }

    static List<Component> build() {
        Set<Item> realmItems = new HashSet<>();
        for (RegistryObject<Item> ro : Realm.ITEMS.getEntries()) {
            realmItems.add(ro.get());
        }
        // sort every item of the mod into its chapter, in registration order
        Map<String, List<Item>> byChapter = new LinkedHashMap<>();
        Guide.CHAPTERS.forEach(ch -> byChapter.put(ch.id(), new ArrayList<>()));
        byChapter.put("world", new ArrayList<>());
        for (Item item : ForgeRegistries.ITEMS) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            if (id == null || !id.getNamespace().equals(NS) || item == Guide.GUIDE.get() || id.getPath().endsWith("_spawn_egg")) {
                continue;
            }
            String chapter = realmItems.contains(item) ? "realm" : "other";
            if (!realmItems.contains(item)) {
                for (Guide.Chapter ch : Guide.CHAPTERS) {
                    if (ch.takes().test(id.getPath())) {
                        chapter = ch.id();
                        break;
                    }
                }
            }
            byChapter.get(chapter).add(item);
        }

        List<String> order = new ArrayList<>();
        for (Guide.Chapter ch : Guide.CHAPTERS) {
            if (ch.id().equals("realm")) {
                order.add("world");
            }
            order.add(ch.id());
        }

        Pager p = new Pager();
        Map<String, Integer> chapterPage = new LinkedHashMap<>();
        for (String chapter : order) {
            List<Item> items = byChapter.getOrDefault(chapter, List.of());
            boolean hasText = I18n.exists("guide." + NS + ".chapter." + chapter);
            if (items.isEmpty() && !chapter.equals("world") && !chapter.equals("realm")) {
                continue;
            }
            p.flush();
            chapterPage.put(chapter, p.pages.size());
            MutableComponent head = t("guide." + NS + ".chapter." + chapter).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD, ChatFormatting.UNDERLINE);
            if (!hasText) {
                head = Component.literal(chapter).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
            }
            String intro = "guide." + NS + ".chapter." + chapter + ".intro";
            p.entry(I18n.exists(intro) ? head.append("\n\n").append(t(intro).withStyle(ChatFormatting.BLACK)) : head);

            if (chapter.equals("realm")) {
                // the realm's field guide pages come first, whole
                for (int i = 0; I18n.exists("book." + NS + ".realm_guide." + i); i++) {
                    p.fullPage(t("book." + NS + ".realm_guide." + i).withStyle(ChatFormatting.BLACK));
                }
            }
            List<Component> plain = new ArrayList<>();
            for (Item item : items) {
                String key = item.getDescriptionId() + ".desc";
                if (I18n.exists(key)) {
                    p.entry(entry(item.getDescription(), key));
                } else {
                    plain.add(Component.literal("- ").append(item.getDescription()).withStyle(ChatFormatting.BLACK));
                }
            }
            // items without their own text (tools, armor): listed compactly
            MutableComponent list = Component.empty();
            int n = 0;
            for (Component c : plain) {
                list.append(n == 0 ? Component.empty() : Component.literal("\n")).append(c);
                if (++n == 12) {
                    p.entry(list);
                    list = Component.empty();
                    n = 0;
                }
            }
            if (n > 0) {
                p.entry(list);
            }
            if (chapter.equals("world") || chapter.equals("realm")) {
                creatures(p, chapter.equals("realm"));
                biomes(p, chapter.equals("realm"));
            }
        }
        p.flush();

        // cover and contents go in front; chapter links point at their page (1-based, after the front pages)
        List<Component> front = new ArrayList<>();
        front.add(Component.empty()
                .append(t("guide." + NS + ".title").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
                .append("\n\n").append(t("guide." + NS + ".cover").withStyle(ChatFormatting.BLACK)));
        List<MutableComponent> toc = new ArrayList<>();
        MutableComponent current = t("guide." + NS + ".contents").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        int tocLines = 2;
        List<Map.Entry<String, Integer>> chapters = new ArrayList<>(chapterPage.entrySet());
        // the first contents page holds 11 links under its heading, the next ones 13 each (same as the loop below)
        int tocPages = 1 + Math.max(0, (chapters.size() - 11 + 12) / 13);
        for (Map.Entry<String, Integer> ch : chapters) {
            int page = ch.getValue() + 1 + tocPages + 1;
            MutableComponent link = Component.literal("\n").append(t("guide." + NS + ".chapter." + ch.getKey())
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_BLUE).withUnderlined(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.CHANGE_PAGE, String.valueOf(page)))))
                    .append(Component.literal("  " + page).withStyle(ChatFormatting.GRAY));
            if (tocLines >= 13) {
                toc.add(current);
                current = Component.empty();
                tocLines = 0;
            }
            current.append(link);
            tocLines++;
        }
        toc.add(current);
        front.addAll(toc);
        List<Component> all = new ArrayList<>(front);
        all.addAll(p.pages);
        return all;
    }

    private static void creatures(Pager p, boolean realm) {
        Set<EntityType<?>> realmTypes = new HashSet<>();
        for (RegistryObject<EntityType<?>> ro : Realm.ENTITIES.getEntries()) {
            realmTypes.add(ro.get());
        }
        for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES) {
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
            if (id == null || !id.getNamespace().equals(NS) || type.getCategory() == MobCategory.MISC || realmTypes.contains(type) != realm) {
                continue;
            }
            p.entry(entry(type.getDescription(), type.getDescriptionId() + ".desc"));
        }
    }

    private static void biomes(Pager p, boolean realm) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        var registry = level.registryAccess().registryOrThrow(Registries.BIOME);
        for (ResourceLocation id : registry.keySet()) {
            if (!id.getNamespace().equals(NS)) {
                continue;
            }
            String key = "biome." + NS + "." + id.getPath();
            boolean isRealm = Realm.BIOMES.contains(id.getPath());
            if (isRealm == realm) {
                p.entry(entry(t(key), key + ".desc"));
            }
        }
    }
}
