package com.echohorror.loot;

import com.echohorror.item.NoteItem;
import com.echohorror.story.Notes;
import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;

import java.util.function.Supplier;

/** Scatters torn pages of the story into ordinary chests across the world. */
public class LoreNoteModifier extends LootModifier {
    public static final Supplier<Codec<LoreNoteModifier>> CODEC = Suppliers.memoize(() -> RecordCodecBuilder.create(inst ->
            codecStart(inst).and(Codec.FLOAT.fieldOf("chance").forGetter(m -> m.chance)).apply(inst, LoreNoteModifier::new)));

    private final float chance;

    public LoreNoteModifier(LootItemCondition[] conditions, float chance) {
        super(conditions);
        this.chance = chance;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext ctx) {
        if (!ctx.getQueriedLootTableId().getPath().startsWith("chests/")) return loot;
        if (ctx.getRandom().nextFloat() < chance) {
            loot.add(NoteItem.create(Notes.LORE[ctx.getRandom().nextInt(Notes.LORE.length)]));
        }
        return loot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
