package com.khmh.livingvillages.entity;

import com.khmh.livingvillages.LivingVillages;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class LVEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, LivingVillages.MODID);

    public static final RegistryObject<EntityType<VillageWorker>> WORKER = ENTITIES.register("worker",
            () -> EntityType.Builder.of(VillageWorker::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build(LivingVillages.MODID + ":worker"));

    private LVEntities() {
    }

    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(WORKER.get(), PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .build());
    }
}
