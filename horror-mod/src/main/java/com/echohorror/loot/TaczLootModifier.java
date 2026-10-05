package com.echohorror.loot;

import com.echohorror.compat.TaczCompat;
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

/** Somebody hid a gun in this chest once. Rarely. */
public class TaczLootModifier extends LootModifier {
    public static final Supplier<Codec<TaczLootModifier>> CODEC = Suppliers.memoize(() -> RecordCodecBuilder.create(inst ->
            codecStart(inst).and(Codec.FLOAT.fieldOf("pistol").forGetter(m -> m.pistol))
                    .and(Codec.FLOAT.fieldOf("shotgun").forGetter(m -> m.shotgun))
                    .and(Codec.FLOAT.fieldOf("ammo").forGetter(m -> m.ammo))
                    .apply(inst, TaczLootModifier::new)));

    private final float pistol, shotgun, ammo;

    public TaczLootModifier(LootItemCondition[] conditions, float pistol, float shotgun, float ammo) {
        super(conditions);
        this.pistol = pistol;
        this.shotgun = shotgun;
        this.ammo = ammo;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext ctx) {
        if (!TaczCompat.active() || !ctx.getQueriedLootTableId().getPath().startsWith("chests/")) return loot;
        var r = ctx.getRandom();
        if (r.nextFloat() < pistol) {
            loot.add(TaczCompat.gun(TaczCompat.PISTOL, r.nextInt(6)));
            loot.add(TaczCompat.ammo(TaczCompat.AMMO_PISTOL, 6 + r.nextInt(10)));
        } else if (r.nextFloat() < shotgun) {
            loot.add(TaczCompat.gun(TaczCompat.SHOTGUN, r.nextInt(2)));
            loot.add(TaczCompat.ammo(TaczCompat.AMMO_SHOTGUN, 3 + r.nextInt(5)));
        } else if (r.nextFloat() < ammo) {
            loot.add(r.nextBoolean() ? TaczCompat.ammo(TaczCompat.AMMO_PISTOL, 8 + r.nextInt(14))
                    : TaczCompat.ammo(TaczCompat.AMMO_SHOTGUN, 4 + r.nextInt(7)));
        }
        loot.removeIf(ItemStack::isEmpty);
        return loot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
