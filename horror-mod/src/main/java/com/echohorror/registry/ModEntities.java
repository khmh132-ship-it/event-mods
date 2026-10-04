package com.echohorror.registry;

import com.echohorror.EchoHorror;
import com.echohorror.entity.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, EchoHorror.MODID);

    public static final RegistryObject<EntityType<PhantomEntity>> PHANTOM = ENTITIES.register("phantom",
            () -> EntityType.Builder.<PhantomEntity>of(PhantomEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F).clientTrackingRange(10).noSave().fireImmune()
                    .build(EchoHorror.MODID + ":phantom"));
    public static final RegistryObject<EntityType<MimicEntity>> MIMIC = ENTITIES.register("mimic",
            () -> EntityType.Builder.<MimicEntity>of(MimicEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F).clientTrackingRange(10)
                    .build(EchoHorror.MODID + ":mimic"));
    public static final RegistryObject<EntityType<CrawlerEntity>> CRAWLER = ENTITIES.register("crawler",
            () -> EntityType.Builder.<CrawlerEntity>of(CrawlerEntity::new, MobCategory.MONSTER)
                    .sized(0.9F, 0.6F).clientTrackingRange(8)
                    .build(EchoHorror.MODID + ":crawler"));
    public static final RegistryObject<EntityType<SilentEntity>> SILENT = ENTITIES.register("silent",
            () -> EntityType.Builder.<SilentEntity>of(SilentEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).clientTrackingRange(10).fireImmune()
                    .build(EchoHorror.MODID + ":silent"));
    public static final RegistryObject<EntityType<EchoBossEntity>> ECHO_BOSS = ENTITIES.register("echo_boss",
            () -> EntityType.Builder.<EchoBossEntity>of(EchoBossEntity::new, MobCategory.MONSTER)
                    .sized(1.3F, 4.2F).clientTrackingRange(12).fireImmune()
                    .build(EchoHorror.MODID + ":echo_boss"));

    private ModEntities() {}

    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(PHANTOM.get(), PhantomEntity.createAttributes().build());
        event.put(MIMIC.get(), MimicEntity.createAttributes().build());
        event.put(CRAWLER.get(), CrawlerEntity.createAttributes().build());
        event.put(SILENT.get(), SilentEntity.createAttributes().build());
        event.put(ECHO_BOSS.get(), EchoBossEntity.createAttributes().build());
    }
}
