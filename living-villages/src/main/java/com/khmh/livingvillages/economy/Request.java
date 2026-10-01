package com.khmh.livingvillages.economy;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import javax.annotation.Nullable;
import java.util.UUID;

/** "Somebody needs N of this": posted by workers, fulfilled by whoever can make or bring it. */
public class Request {
    private final UUID id;
    private final Item item;
    private int remaining;
    private final String requester;
    private final long created;
    @Nullable
    private UUID claimedBy;
    private long unobtainableSince = -1;

    public Request(UUID id, Item item, int remaining, String requester, long created) {
        this.id = id;
        this.item = item;
        this.remaining = remaining;
        this.requester = requester;
        this.created = created;
    }

    public UUID id() {
        return id;
    }

    public Item item() {
        return item;
    }

    public int remaining() {
        return remaining;
    }

    public void setRemaining(int remaining) {
        this.remaining = Math.max(0, remaining);
    }

    public void fulfil(int amount) {
        remaining = Math.max(0, remaining - amount);
    }

    public String requester() {
        return requester;
    }

    public long created() {
        return created;
    }

    @Nullable
    public UUID claimedBy() {
        return claimedBy;
    }

    public void claim(@Nullable UUID worker) {
        claimedBy = worker;
    }

    /** When nobody could make it any more (no recipe, no materials); -1 while it still looks possible. */
    public long unobtainableSince() {
        return unobtainableSince;
    }

    public void setUnobtainable(long since) {
        unobtainableSince = since;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("item", BuiltInRegistries.ITEM.getKey(item).toString());
        t.putInt("remaining", remaining);
        t.putString("requester", requester);
        t.putLong("created", created);
        return t;
    }

    @Nullable
    public static Request load(CompoundTag t) {
        ResourceLocation key = ResourceLocation.tryParse(t.getString("item"));
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
            return null;
        }
        return new Request(t.getUUID("id"), BuiltInRegistries.ITEM.get(key), t.getInt("remaining"),
                t.getString("requester"), t.getLong("created"));
    }
}
