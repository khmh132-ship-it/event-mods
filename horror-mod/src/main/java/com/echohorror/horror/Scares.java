package com.echohorror.horror;

import com.echohorror.Config;
import com.echohorror.EchoHorror;
import com.echohorror.entity.CrawlerEntity;
import com.echohorror.entity.MimicEntity;
import com.echohorror.entity.PhantomEntity;
import com.echohorror.item.NoteItem;
import com.echohorror.network.Fx;
import com.echohorror.network.Net;
import com.echohorror.network.SoundSeqPacket;
import com.echohorror.registry.ModEntities;
import com.echohorror.registry.ModItems;
import com.echohorror.story.StoryData;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/** Catalogue of scare events the director can play on a single player. */
public final class Scares {
    public static final class Ctx {
        public final ServerPlayer p;
        public final ServerLevel level;
        public final StoryData d;
        public final int chapter;
        public final float sanity;
        public final boolean night, underground;
        public final RandomSource r;

        Ctx(ServerPlayer p, StoryData d) {
            this.p = p;
            this.level = p.serverLevel();
            this.d = d;
            this.chapter = com.echohorror.story.StoryManager.effectiveChapter(d);
            this.sanity = Sanity.get(p);
            this.night = HorrorUtil.isNight(level);
            this.underground = HorrorUtil.isUnderground(p);
            this.r = p.getRandom();
        }

        boolean dark() {
            return night || underground;
        }
    }

    public record Scare(String name, int minChapter, float maxSanity, int weight, int cooldownSec,
                        Predicate<Ctx> cond, Function<Ctx, Boolean> run) {}

    private static final List<Scare> ALL = new ArrayList<>();
    private static final Map<String, Scare> BY_NAME = new LinkedHashMap<>();

    private static void add(String name, int minChapter, float maxSanity, int weight, int cooldownSec, Predicate<Ctx> cond, Function<Ctx, Boolean> run) {
        Scare s = new Scare(name, minChapter, maxSanity, weight, cooldownSec, cond, run);
        ALL.add(s);
        BY_NAME.put(name, s);
    }

    public static List<Scare> all() {
        return ALL;
    }

    public static Scare get(String name) {
        return BY_NAME.get(name);
    }

    public static Ctx ctx(ServerPlayer p, StoryData d) {
        return new Ctx(p, d);
    }

    private static final String[] CREEPY_CHAT = {
            "иди сюда", "я в пещере, тут что-то есть", "ты где?", "кто это стоит за тобой?", "не оборачивайся",
            "почему ты меня не слышишь", "я тебя вижу", "зачем ты ушёл", "мне холодно", "открой", "это не я",
            "ты это видел??", "у тебя за спиной кто-то", "СЗАДИ", "я у колодца. спускайся", "не верь мне",
            "сколько нас было?", "ты слышишь стук?", "почему ты молчишь", "всё нормально, иди ко мне"};
    private static final String[] FAKE_NAMES = {"Лисицын", "Zoya_K", "Glushko", "Ryabov", "Voronov_AS", "Masha", "Nikodim"};
    private static final String[] SCREEN_TEXT = {"ОБЕРНИСЬ", "НЕ СПИ", "ТЫ НЕ ОРИГИНАЛ", "ОНО ЗА ТОБОЙ", "МЫ СЛЫШИМ",
            "СКОЛЬКО ВАС БЫЛО?", "НЕ ОТВЕЧАЙ", "ТИШЕ"};
    private static final String[] RENAMES = {"ПОМОГИ МНЕ", "это не твоё", "оно знает", "ВЕРНИ", "я был здесь", "не открывай",
            "ты уже один из нас", "тише"};
    private static final String[] SIGNS = {"ОБЕРНИСЬ", "Я ЗДЕСЬ", "ТЫ НЕ ОДИН", "НЕ ОГЛЯДЫВАЙСЯ", "Я СЧИТАЮ ТЕБЯ"};
    private static final String[] DEATHS = {"Тебя не было здесь", "Эхо ответило", "Ты обернулся", "Тебя скопировали",
            "Был убит игроком %s", "Ты сам открыл дверь"};

    // ================================================================================================ registry
    static {
        // ---- sound only
        add("scream_far", 0, 100, 5, 120, Ctx::dark, c -> {
            Vec3 dir = HorrorUtil.rotate(HorrorUtil.horizontalLook(c.p), c.r.nextInt(360));
            HorrorUtil.playTo(c.p, "scare.scream_far", c.p.getEyePosition().add(dir.scale(45)), 4f, 0.8f + c.r.nextFloat() * 0.3f);
            Sanity.add(c.p, -2);
            return true;
        });
        add("whisper", 1, 100, 8, 60, c -> true, c -> {
            HorrorUtil.playTo(c.p, "whisper", HorrorUtil.behind(c.p, 1.2), 0.7f, 0.9f + c.r.nextFloat() * 0.2f);
            Sanity.add(c.p, -2);
            return true;
        });
        add("footsteps", 1, 100, 8, 90, c -> true, Scares::footsteps);
        add("knock", 1, 100, 7, 120, c -> c.night, Scares::knock);
        add("breath", 1, 92, 5, 90, c -> true, c -> {
            HorrorUtil.playTo(c.p, "scare.breath", HorrorUtil.behind(c.p, 0.9), 0.9f, 0.9f);
            Sanity.add(c.p, -3);
            return true;
        });
        add("cave_noise", 0, 100, 4, 90, c -> c.underground, c -> {
            Vec3 dir = HorrorUtil.rotate(HorrorUtil.horizontalLook(c.p), c.r.nextInt(360));
            HorrorUtil.playTo(c.p, SoundEvents.AMBIENT_CAVE.value(), c.p.getEyePosition().add(dir.scale(12)), 1f, 0.8f + c.r.nextFloat() * 0.3f);
            return true;
        });
        add("music_box", 3, 75, 2, 400, c -> true, c -> {
            HorrorUtil.playTo(c.p, "scare.music_box", HorrorUtil.behind(c.p, 18), 1.2f, 0.95f);
            Sanity.add(c.p, -3);
            return true;
        });
        add("hum", 2, 100, 3, 300, Ctx::dark, c -> {
            Vec3 dir = HorrorUtil.rotate(HorrorUtil.horizontalLook(c.p), 90 + c.r.nextInt(180));
            HorrorUtil.playTo(c.p, "scare.hum", c.p.getEyePosition().add(dir.scale(14)), 1.3f, 1f);
            Sanity.add(c.p, -3);
            return true;
        });
        add("bell_far", 3, 100, 2, 600, c -> c.night, c -> {
            HorrorUtil.playAt(c.p, "story.bell_far", 0.7f, 0.75f + c.r.nextFloat() * 0.1f);
            return true;
        });
        add("call", 1, 100, 4, 240, Ctx::dark, c -> {
            String[] calls = {"voice.call", "voice.help", "voice.child", "voice.open"};
            Vec3 dir = HorrorUtil.rotate(HorrorUtil.horizontalLook(c.p), 120 + c.r.nextInt(120));
            HorrorUtil.playTo(c.p, calls[c.r.nextInt(calls.length)], c.p.getEyePosition().add(dir.scale(16 + c.r.nextInt(12))), 1.8f, 1f);
            Sanity.add(c.p, -3);
            return true;
        });
        add("laugh", 3, 40, 2, 400, c -> true, c -> {
            HorrorUtil.playTo(c.p, "voice.laugh", HorrorUtil.behind(c.p, 20), 1.5f, 0.85f);
            Sanity.add(c.p, -3);
            return true;
        });
        add("radio_burst", 1, 100, 3, 300, c -> c.p.getInventory().contains(new ItemStack(ModItems.LOCATOR.get())), c -> {
            int n = 1 + c.r.nextInt(9);
            String[] words = {"ноль", "один", "два", "три", "четыре", "пять", "шесть", "семь", "восемь", "девять"};
            Net.send(c.p, new SoundSeqPacket.Builder().sound("scare.static", 1, "[ пеленгатор хрипит ]")
                    .sound("radio.num" + n, 30, "«..." + words[n] + "...»").sub("", 50).build());
            Sanity.add(c.p, -1);
            return true;
        });
        add("voice_mimic", 3, 95, 5, 240, c -> VoiceBridge.available(), Scares::voiceMimic);
        add("own_voice", 3, 70, 3, 600, c -> VoiceBridge.hasClip(c.p.getUUID()), c -> {
            // your own voice, from right behind you
            boolean ok = VoiceBridge.play(c.p, c.p.getUUID(), HorrorUtil.behind(c.p, 1.5));
            if (ok) Sanity.add(c.p, -6);
            return ok;
        });
        add("fake_mob", 1, 100, 5, 150, c -> true, Scares::fakeMob);
        add("circle", 3, 90, 3, 1200, c -> c.night && !c.underground, c -> HorrorUtil.spotAround(c.p, 34, 48, 0, 50, false).map(center -> {
            // a ring of familiar faces in a clearing, humming, facing each other
            int n = 6;
            List<ServerPlayer> faces = new ArrayList<>(c.p.server.getPlayerList().getPlayers());
            boolean any = false;
            for (int i = 0; i < n; i++) {
                double a2 = i * Math.PI * 2 / n;
                int x = Mth.floor(center.x + Math.cos(a2) * 3), z = Mth.floor(center.z + Math.sin(a2) * 3);
                Optional<BlockPos> g = HorrorUtil.ground(c.level, x, z, Mth.floor(center.y) + 1, 3, 4);
                if (g.isEmpty()) continue;
                ServerPlayer f = faces.get(c.r.nextInt(faces.size()));
                PhantomEntity.spawn(c.p, PhantomEntity.KIND_FAKE_PLAYER, PhantomEntity.MODE_CIRCLE, Vec3.atBottomCenterOf(g.get()), 1600)
                        .skin(f.getUUID(), null).anchor(center, !any);
                any = true;
            }
            if (any) HorrorUtil.playTo(c.p, "scare.hum", center.add(0, 1.5, 0), 2.5f, 0.85f);
            return any;
        }).orElse(false));
        add("procession", 3, 100, 3, 1200, c -> c.night && !c.underground, c -> {
            // figures walking in single file across your path, far away
            Vec3 look = HorrorUtil.horizontalLook(c.p);
            Vec3 side = HorrorUtil.rotate(look, 90);
            Vec3 mid = c.p.position().add(look.scale(34));
            Vec3 from = mid.add(side.scale(-22)), to = mid.add(side.scale(22));
            int made = 0;
            for (int i = 0; i < 5; i++) {
                Vec3 p0 = from.add(side.scale(-i * 2.2));
                Optional<BlockPos> g = HorrorUtil.ground(c.level, Mth.floor(p0.x), Mth.floor(p0.z), c.p.getBlockY() + 2, 8, 10);
                if (g.isEmpty()) continue;
                PhantomEntity.spawn(c.p, i == 2 ? PhantomEntity.KIND_SILENT : PhantomEntity.KIND_WATCHER, PhantomEntity.MODE_PROCESSION,
                        Vec3.atBottomCenterOf(g.get()), 1400).anchor(to, false);
                made++;
            }
            if (made > 0) HorrorUtil.playAt(c.p, "story.bell_far", 0.8f, 0.7f);
            return made > 0;
        });
        add("underwater_face", 1, 100, 8, 240, c -> c.level.getFluidState(BlockPos.containing(c.p.getEyePosition())).is(net.minecraft.tags.FluidTags.WATER), c -> {
            // something looking up at you from the dark water — below if it is deep, otherwise ahead in the murk
            Vec3 look = HorrorUtil.horizontalLook(c.p);
            BlockPos eye = BlockPos.containing(c.p.getEyePosition());
            int[][] tries = {{0, -5, 2}, {0, -4, 3}, {0, -3, 2}, {0, -2, 4}, {0, -1, 5}, {0, 0, 6}, {0, -1, 7}, {0, 1, 5}, {0, 0, 4}, {0, 1, 3}};
            for (int[] t : tries) {
                BlockPos bp = BlockPos.containing(c.p.getEyePosition().add(look.scale(t[2])).add(0, t[1] - 1, 0));
                    if (!c.level.getFluidState(bp).isEmpty() && !c.level.getFluidState(bp.above()).isEmpty()
                        && HorrorUtil.canSee(c.p, Vec3.atCenterOf(bp.above()))) {
                    PhantomEntity e = PhantomEntity.spawn(c.p, PhantomEntity.KIND_WATCHER, PhantomEntity.MODE_STARE,
                            Vec3.atBottomCenterOf(bp), 300).floating().vanishDistance(2).watchLimit(10);
                    if (c.sanity < 50) e.withJumpscare();
                    return true;
                }
            }
            return false;
        });
        add("self_double", 2, 90, 3, 900, c -> true, c -> HorrorUtil.spotAround(c.p, 15, 24, 0, 40, false).map(s -> {
            // you, walking away from yourself
            PhantomEntity.spawn(c.p, PhantomEntity.KIND_FAKE_PLAYER, PhantomEntity.MODE_WALK_AWAY, s, 500)
                    .skin(c.p.getUUID(), c.p.getGameProfile().getName());
            Sanity.add(c.p, -4);
            return true;
        }).orElse(false));
        add("replay", 1, 90, 4, 600, c -> (c.night || c.underground) && PathMemory.of(c.p.getUUID()).size() > 1500, c -> {
            // a minute or two later, "you" walk the exact same way you did — and it notices you watching
            List<PathMemory.Sample> trail = PathMemory.of(c.p.getUUID());
            Vec3 now = c.p.position();
            for (int tries = 0; tries < 30; tries++) {
                int start = trail.size() - 600 - c.r.nextInt(Math.max(1, trail.size() - 900));
                if (start < 0) continue;
                PathMemory.Sample s = trail.get(start);
                double d0 = s.pos().distanceTo(now);
                if (d0 < 12 || d0 > 32) continue;
                // the segment must actually move and stay a little away from where the player stands now
                List<PathMemory.Sample> seg = trail.subList(start, Math.min(trail.size() - 100, start + 500));
                if (seg.size() < 120 || seg.get(0).pos().distanceTo(seg.get(seg.size() - 1).pos()) < 8) continue;
                if (HorrorUtil.isLookingAt(c.p, s.pos().add(0, 1, 0), 0.9)) continue; // don't pop into view
                PhantomEntity.spawn(c.p, PhantomEntity.KIND_FAKE_PLAYER, PhantomEntity.MODE_REPLAY, s.pos(), seg.size() * 2 + 60)
                        .skin(c.p.getUUID(), c.p.getGameProfile().getName()).replay(new ArrayList<>(seg));
                Scheduler.schedule(60, () -> HorrorUtil.playTo(c.p, SoundEvents.ARMOR_EQUIP_LEATHER, s.pos(), 0.5f, 0.9f));
                return true;
            }
            return false;
        });
        add("hand_on_shoulder", 2, 70, 3, 900, c -> true, c -> {
            HorrorUtil.playTo(c.p, SoundEvents.WOOL_STEP, HorrorUtil.behind(c.p, 0.6), 0.6f, 0.7f);
            Scheduler.schedule(15, () -> {
                if (c.p.hasDisconnected()) return;
                Net.fx(c.p, Fx.SHAKE, 8, 0.6f, "");
                c.p.displayClientMessage(Component.literal("Кто-то положил руку тебе на плечо.").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), true);
                HorrorUtil.playTo(c.p, "scare.breath", HorrorUtil.behind(c.p, 0.5), 0.9f, 1f);
            });
            Sanity.add(c.p, -5);
            return true;
        });
        add("echo_steps", 1, 100, 5, 300, c -> c.night || c.underground, c -> {
            // your own footsteps, half a second late, from behind — only while you walk
            final Vec3[] last = {c.p.position()};
            for (int i = 0; i < 16; i++) {
                Scheduler.schedule(10 + i * 9, () -> {
                    if (c.p.hasDisconnected()) return;
                    Vec3 now = c.p.position();
                    boolean moved = now.distanceToSqr(last[0]) > 0.5;
                    last[0] = now;
                    if (!moved) return;
                    BlockState st = c.level.getBlockState(c.p.blockPosition().below());
                    Scheduler.schedule(8, () -> HorrorUtil.playTo(c.p, st.getSoundType().getStepSound(),
                            HorrorUtil.behind(c.p, 3).subtract(0, 1.5, 0), 0.45f, 0.95f));
                });
            }
            Sanity.add(c.p, -2);
            return true;
        });
        add("window_face", 2, 100, 6, 300, c -> !c.p.level().canSeeSky(c.p.blockPosition()), Scares::windowFace);
        add("dig_below", 2, 90, 3, 400, c -> true, c -> {
            // something is digging right under your feet... and stops when you do
            Vec3 under = c.p.position().subtract(0, 3, 0);
            for (int i = 0; i < 9; i++) {
                final int k = i;
                Scheduler.schedule(i * 11 + c.r.nextInt(5), () -> {
                    if (c.p.hasDisconnected()) return;
                    BlockState st = c.level.getBlockState(c.p.blockPosition().below(2));
                    HorrorUtil.playTo(c.p, st.isAir() ? SoundEvents.STONE_HIT : st.getSoundType().getHitSound(), under.add(0, -k * 0.2, 0), 0.9f, 0.7f);
                });
            }
            Sanity.add(c.p, -3);
            return true;
        });
        add("tunnel", 3, 85, 2, 1200, c -> Config.WORLD_TAMPERING.get() && c.underground, Scares::tunnel);
        add("bed_double", 2, 100, 4, 900, c -> c.night && c.p.getRespawnPosition() != null
                && c.p.getRespawnDimension() == c.level.dimension()
                && c.p.getRespawnPosition().distSqr(c.p.blockPosition()) < 28 * 28, c -> {
            BlockPos bed = c.p.getRespawnPosition();
            Optional<BlockPos> g = HorrorUtil.ground(c.level, bed.getX() + (c.r.nextBoolean() ? 1 : -1), bed.getZ(), bed.getY() + 1, 2, 3);
            if (g.isEmpty()) return false;
            PhantomEntity.spawn(c.p, PhantomEntity.KIND_FAKE_PLAYER, PhantomEntity.MODE_STARE, Vec3.atBottomCenterOf(g.get()), 900)
                    .skin(c.p.getUUID(), c.p.getGameProfile().getName()).vanishDistance(4).watchLimit(60);
            return true;
        });
        // weight 0: never random, fired by the director when you come home after a long trip
        add("homecoming", 2, 100, 0, 0, c -> c.p.getRespawnPosition() != null && c.p.getRespawnDimension() == c.level.dimension()
                && c.p.getRespawnPosition().distSqr(c.p.blockPosition()) < 16 * 16, Scares::homecoming);
        add("fake_restart", 3, 60, 1, 2400, c -> Config.FAKE_CHAT.get(), c -> {
            c.p.sendSystemMessage(Component.literal("[Сервер] Внимание! Экстренная перезагрузка через 10 секунд.").withStyle(ChatFormatting.LIGHT_PURPLE));
            for (int i = 5; i >= 1; i--) {
                final int n = i;
                Scheduler.schedule((10 - n) * 20, () -> {
                    if (!c.p.hasDisconnected())
                        c.p.sendSystemMessage(Component.literal("[Сервер] " + n + "...").withStyle(ChatFormatting.LIGHT_PURPLE));
                });
            }
            Scheduler.schedule(200, () -> {
                if (c.p.hasDisconnected()) return;
                if (Config.FAKE_SCREENS.get()) Net.fx(c.p, Fx.FAKE_DISCONNECT, 0);
                else c.p.sendSystemMessage(Component.literal("[Сервер] ...передумал.").withStyle(ChatFormatting.DARK_RED));
            });
            return true;
        });
        add("friend_death", 3, 60, 2, 1200, c -> Config.FAKE_CHAT.get() && !HorrorUtil.others(c.p).isEmpty(), c -> {
            ServerPlayer f = randomFace(c);
            String[] causes = {"death.attack.generic", "death.attack.outOfWorld", "death.attack.magic", "death.fell.accident.generic"};
            c.p.sendSystemMessage(Component.translatable(causes[c.r.nextInt(causes.length)], f.getGameProfile().getName()));
            Sanity.add(c.p, -3);
            return true;
        });
        add("corrupt_chat", 3, 55, 3, 300, c -> Config.FAKE_CHAT.get(), c -> {
            ServerPlayer f = randomFace(c);
            String base = ChatMemory.randomOf(c.r, f.getUUID()).map(ChatMemory.Line::text).orElse(CREEPY_CHAT[c.r.nextInt(CREEPY_CHAT.length)]);
            c.p.sendSystemMessage(Component.translatable("chat.type.text", f.getGameProfile().getName(), zalgo(base, c.r)));
            Sanity.add(c.p, -2);
            return true;
        });

        // ---- world tampering
        add("door_open", 1, 100, 4, 180, c -> Config.WORLD_TAMPERING.get(), Scares::doorOpen);
        add("torches_out", 2, 100, 4, 300, c -> Config.WORLD_TAMPERING.get() && c.dark(), Scares::torchesOut);
        add("chest_note", 2, 90, 2, 900, c -> Config.WORLD_TAMPERING.get(), Scares::chestNote);
        add("sign", 3, 70, 2, 600, c -> Config.WORLD_TAMPERING.get(), Scares::sign);
        add("rename_item", 3, 45, 2, 900, c -> true, Scares::renameItem);

        // ---- visions
        add("watcher", 1, 100, 6, 180, c -> true, c -> HorrorUtil.spotAround(c.p, 26, 44, 0, 70, false).map(s -> {
            PhantomEntity.spawn(c.p, PhantomEntity.KIND_WATCHER, PhantomEntity.MODE_STARE, s, 600);
            return true;
        }).orElse(false));
        add("behind", 2, 85, 4, 240, c -> true, c -> {
            Vec3 b = HorrorUtil.behind(c.p, 2.6);
            return HorrorUtil.ground(c.level, Mth.floor(b.x), Mth.floor(b.z), c.p.getBlockY() + 1, 2, 3).map(g -> {
                PhantomEntity e = PhantomEntity.spawn(c.p, c.r.nextBoolean() ? PhantomEntity.KIND_WATCHER : PhantomEntity.KIND_SHADE,
                        PhantomEntity.MODE_BEHIND, Vec3.atBottomCenterOf(g), 200);
                if (c.sanity < 30) e.withJumpscare();
                HorrorUtil.playTo(c.p, "whisper", Vec3.atCenterOf(g).add(0, 1, 0), 0.8f, 0.8f);
                return true;
            }).orElse(false);
        });
        add("shade", 1, 100, 4, 120, c -> true, c -> HorrorUtil.spotAround(c.p, 12, 20, c.r.nextBoolean() ? 80 : -80, 15, false).map(s -> {
            PhantomEntity.spawn(c.p, PhantomEntity.KIND_SHADE, PhantomEntity.MODE_STARE, s, 100);
            return true;
        }).orElse(false));
        add("fake_player", 2, 100, 5, 300, c -> true, c -> HorrorUtil.spotAround(c.p, 22, 36, 0, 60, false).map(s -> {
            ServerPlayer face = randomFace(c);
            PhantomEntity.spawn(c.p, PhantomEntity.KIND_FAKE_PLAYER, c.r.nextFloat() < 0.4f ? PhantomEntity.MODE_WALK_AWAY : PhantomEntity.MODE_STARE, s, 700)
                    .skin(face.getUUID(), face.getGameProfile().getName());
            return true;
        }).orElse(false));
        add("silent_figure", 5, 50, 2, 600, c -> true, c -> HorrorUtil.spotAround(c.p, 14, 20, 180, 60, false).map(s -> {
            PhantomEntity.spawn(c.p, PhantomEntity.KIND_SILENT, PhantomEntity.MODE_CREEP, s, 900).withJumpscare();
            HorrorUtil.playTo(c.p, "scare.music_box", s, 1f, 0.9f);
            return true;
        }).orElse(false));
        add("look_turn", 3, 40, 2, 600, c -> true, Scares::lookTurn);

        // ---- social engineering
        add("fake_chat", 2, 100, 6, 150, c -> Config.FAKE_CHAT.get(), c -> {
            ServerPlayer face = randomFace(c);
            String name = face == c.p && c.r.nextFloat() < 0.5f ? FAKE_NAMES[c.r.nextInt(FAKE_NAMES.length)] : face.getGameProfile().getName();
            String text = c.r.nextFloat() < 0.5f
                    ? ChatMemory.randomOf(c.r, face.getUUID()).map(ChatMemory.Line::text).orElse(CREEPY_CHAT[c.r.nextInt(CREEPY_CHAT.length)])
                    : CREEPY_CHAT[c.r.nextInt(CREEPY_CHAT.length)];
            c.p.sendSystemMessage(Component.translatable("chat.type.text", name, text));
            Sanity.add(c.p, -2);
            return true;
        });
        add("fake_whisper_msg", 3, 75, 3, 300, c -> Config.FAKE_CHAT.get(), c -> {
            ServerPlayer face = randomFace(c);
            c.p.sendSystemMessage(Component.translatable("commands.message.display.incoming", face.getGameProfile().getName(),
                    CREEPY_CHAT[c.r.nextInt(CREEPY_CHAT.length)]).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            Sanity.add(c.p, -3);
            return true;
        });
        add("fake_join", 3, 60, 3, 600, c -> Config.FAKE_CHAT.get(), c -> {
            String name = c.r.nextFloat() < 0.3f ? c.p.getGameProfile().getName() : FAKE_NAMES[c.r.nextInt(FAKE_NAMES.length)];
            c.p.sendSystemMessage(Component.translatable("multiplayer.player.joined", name).withStyle(ChatFormatting.YELLOW));
            if (c.r.nextFloat() < 0.5f) {
                Scheduler.schedule(400 + c.r.nextInt(800), () -> {
                    if (!c.p.hasDisconnected())
                        c.p.sendSystemMessage(Component.translatable("multiplayer.player.left", name).withStyle(ChatFormatting.YELLOW));
                });
            }
            Sanity.add(c.p, -3);
            return true;
        });
        add("fake_advancement", 3, 60, 2, 900, c -> Config.FAKE_CHAT.get(), c -> {
            ServerPlayer face = randomFace(c);
            String[] adv = {"Не оборачивайся", "Ты не один", "Открой дверь", "Посчитай нас", "Тише"};
            c.p.sendSystemMessage(Component.translatable("chat.type.advancement.task", face.getGameProfile().getName(),
                    Component.literal("[" + adv[c.r.nextInt(adv.length)] + "]").withStyle(ChatFormatting.GREEN)));
            return true;
        });
        add("fake_tab", 4, 50, 2, 900, c -> Config.FAKE_CHAT.get(), Scares::fakeTab);

        // ---- screen
        add("flicker", 1, 100, 3, 120, c -> true, c -> {
            Net.fx(c.p, Fx.FLICKER, 30);
            return true;
        });
        add("screen_text", 2, 70, 3, 300, c -> true, c -> {
            String t = SCREEN_TEXT[c.r.nextInt(SCREEN_TEXT.length)];
            if (c.r.nextFloat() < 0.2f) t = c.p.getGameProfile().getName().toUpperCase(Locale.ROOT);
            Net.fx(c.p, Fx.SCREEN_TEXT, 3, 0f, t);
            return true;
        });
        add("heartbeat", 0, 35, 4, 120, c -> c.chapter >= 1, c -> {
            Net.fx(c.p, Fx.HEARTBEAT, 200);
            return true;
        });
        add("glitch", 3, 40, 2, 300, c -> true, c -> {
            Net.fx(c.p, Fx.GLITCH, 12);
            HorrorUtil.playAt(c.p, "scare.glitch", 0.8f, 1f);
            return true;
        });
        add("fog", 3, 100, 2, 900, c -> c.night && !c.underground, c -> {
            Net.fx(c.p, Fx.FOG, 1200, 14f, "");
            return true;
        });
        add("jumpscare", 2, 25, 2, 600, c -> Config.JUMPSCARES.get(), c -> {
            Net.fx(c.p, Fx.JUMPSCARE, c.r.nextInt(3));
            HorrorUtil.playAt(c.p, "scare.scream_near", 1f, 0.9f + c.r.nextFloat() * 0.2f);
            Sanity.add(c.p, -6);
            return true;
        });
        add("fake_death", 3, 20, 1, 1800, c -> Config.FAKE_SCREENS.get(), c -> {
            String cause = DEATHS[c.r.nextInt(DEATHS.length)];
            if (cause.contains("%s")) cause = String.format(cause, randomFace(c).getGameProfile().getName());
            Net.fx(c.p, Fx.FAKE_DEATH, cause);
            return true;
        });
        add("fake_disconnect", 4, 30, 1, 2400, c -> Config.FAKE_SCREENS.get(), c -> {
            Net.fx(c.p, Fx.FAKE_DISCONNECT, 0);
            return true;
        });

        // ---- real danger
        add("mimic", 3, 55, 3, 600, c -> Config.HOSTILE_SPAWNS.get() && c.night && !c.underground
                && c.level.getEntitiesOfClass(MimicEntity.class, c.p.getBoundingBox().inflate(64)).size() < 2, c ->
                HorrorUtil.spotAround(c.p, 24, 40, 0, 90, false).map(s -> {
                    MimicEntity.spawnAs(c.level, s, randomFace(c));
                    return true;
                }).orElse(false));
        add("crawlers", 4, 100, 3, 240, c -> Config.HOSTILE_SPAWNS.get() && (c.underground || c.night && c.sanity < 35), c -> {
            int n = 1 + c.r.nextInt(c.underground ? 3 : 2);
            int spawned = 0;
            for (int i = 0; i < n; i++) {
                Optional<Vec3> s = HorrorUtil.spotAround(c.p, 14, 24, 180, 90, true);
                if (s.isEmpty()) continue;
                CrawlerEntity e = new CrawlerEntity(ModEntities.CRAWLER.get(), c.level);
                e.moveTo(s.get().x, s.get().y, s.get().z, c.r.nextFloat() * 360, 0);
                c.level.addFreshEntity(e);
                spawned++;
            }
            if (spawned > 0) HorrorUtil.playTo(c.p, "entity.crawler.click", HorrorUtil.behind(c.p, 12), 1.5f, 0.9f);
            return spawned > 0;
        });
    }

    // ================================================================================================ helpers
    /** Another online player to impersonate, or the victim themself when alone. */
    static ServerPlayer randomFace(Ctx c) {
        List<ServerPlayer> others = HorrorUtil.others(c.p);
        if (others.isEmpty()) return c.p;
        return others.get(c.r.nextInt(others.size()));
    }

    private static boolean footsteps(Ctx c) {
        for (int i = 0; i < 7; i++) {
            final int k = i;
            Scheduler.schedule(8 + i * 9, () -> {
                if (c.p.hasDisconnected()) return;
                Vec3 pos = HorrorUtil.behind(c.p, 9 - k * 1.15).subtract(0, 1.5, 0);
                BlockPos below = BlockPos.containing(pos).below();
                BlockState st = c.level.getBlockState(below);
                if (st.isAir()) st = c.level.getBlockState(below.below());
                HorrorUtil.playTo(c.p, st.getSoundType().getStepSound(), pos, 0.5f, 0.9f);
            });
        }
        if (c.r.nextFloat() < 0.35f) Scheduler.schedule(80, () -> HorrorUtil.playTo(c.p, "scare.breath", HorrorUtil.behind(c.p, 1), 0.8f, 1f));
        Sanity.add(c.p, -3);
        return true;
    }

    private static List<BlockPos> scan(Ctx c, int rh, int rv, Predicate<BlockState> test) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos o = c.p.blockPosition();
        for (BlockPos bp : BlockPos.betweenClosed(o.offset(-rh, -rv, -rh), o.offset(rh, rv, rh))) {
            if (test.test(c.level.getBlockState(bp))) out.add(bp.immutable());
        }
        return out;
    }

    static String zalgo(String text, RandomSource r) {
        StringBuilder sb = new StringBuilder();
        for (char ch : text.toCharArray()) {
            sb.append(ch);
            if (ch == ' ') continue;
            int n = r.nextInt(4);
            for (int i = 0; i < n; i++) sb.append((char) (0x0300 + r.nextInt(0x6F)));
        }
        return sb.toString();
    }

    /** A face pressed to the glass. Only when you are inside. */
    private static boolean windowFace(Ctx c) {
        BlockPos eye = BlockPos.containing(c.p.getEyePosition());
        List<BlockPos> glass = new ArrayList<>();
        for (BlockPos bp : BlockPos.betweenClosed(eye.offset(-7, -2, -7), eye.offset(7, 2, 7))) {
            BlockState st = c.level.getBlockState(bp);
            if (st.is(net.minecraft.tags.BlockTags.IMPERMEABLE) || st.getBlock() instanceof StainedGlassPaneBlock || st.is(Blocks.GLASS_PANE)) glass.add(bp.immutable());
        }
        Collections.shuffle(glass);
        // windows you can see from the corner of your eye first
        glass.sort(Comparator.comparingInt(g -> HorrorUtil.isLookingAt(c.p, Vec3.atCenterOf(g), 0.35) ? 0 : 1));
        for (BlockPos g : glass) {
            Vec3 gc = Vec3.atCenterOf(g);
            Vec3 dir = gc.subtract(c.p.getEyePosition());
            if (dir.lengthSqr() < 4) continue;
            // outward direction: dominant horizontal axis from the player to the glass
            net.minecraft.core.Direction out = Math.abs(dir.x) > Math.abs(dir.z)
                    ? (dir.x > 0 ? net.minecraft.core.Direction.EAST : net.minecraft.core.Direction.WEST)
                    : (dir.z > 0 ? net.minecraft.core.Direction.SOUTH : net.minecraft.core.Direction.NORTH);
            BlockPos head = g.relative(out);
            BlockPos feet = head.below();
            if (!c.level.getBlockState(head).isAir() || !c.level.getBlockState(feet).isAir()) continue;
            if (!c.level.canSeeSky(head)) continue; // must really be outside
            if (!HorrorUtil.canSee(c.p, gc)) continue;
            int kind = c.chapter >= 3 && c.r.nextBoolean() ? PhantomEntity.KIND_FAKE_PLAYER : PhantomEntity.KIND_WATCHER;
            double drop = kind == PhantomEntity.KIND_WATCHER ? 1.8 : 1.2; // put the face in the glass, not the legs
            Vec3 pos = new Vec3(head.getX() + 0.5, g.getY() - drop, head.getZ() + 0.5);
            PhantomEntity e = PhantomEntity.spawn(c.p, kind, PhantomEntity.MODE_STARE, pos, 600).floating().vanishDistance(1.5).watchLimit(12);
            if (kind == PhantomEntity.KIND_FAKE_PLAYER) {
                ServerPlayer f = randomFace(c);
                e.skin(f.getUUID(), null);
            }
            if (c.sanity < 40) e.withJumpscare();
            HorrorUtil.playTo(c.p, SoundEvents.GLASS_HIT, gc, 0.6f, 0.5f);
            return true;
        }
        return false;
    }

    /** Something dug a fresh tunnel into the cave wall. It ends with a red torch. */
    private static boolean tunnel(Ctx c) {
        net.minecraft.core.Direction[] dirs = {net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH,
                net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.WEST};
        BlockPos base = c.p.blockPosition();
        for (int attempt = 0; attempt < 8; attempt++) {
            net.minecraft.core.Direction d = dirs[c.r.nextInt(4)];
            // find the wall
            BlockPos start = null;
            for (int i = 1; i <= 5; i++) {
                BlockPos bp = base.relative(d, i);
                if (isNaturalStone(c.level.getBlockState(bp)) && isNaturalStone(c.level.getBlockState(bp.above()))) {
                    start = bp;
                    break;
                }
                if (!c.level.getBlockState(bp).isAir()) break;
            }
            if (start == null) continue;
            if (HorrorUtil.isLookingAt(c.p, Vec3.atCenterOf(start), 0.4)) continue;
            List<BlockPos> carve = new ArrayList<>();
            BlockPos cur = start;
            for (int i = 0; i < 7; i++) {
                if (!isNaturalStone(c.level.getBlockState(cur)) || !isNaturalStone(c.level.getBlockState(cur.above()))) break;
                carve.add(cur);
                cur = cur.relative(d);
            }
            if (carve.size() < 4) continue;
            for (int i = 0; i < carve.size(); i++) {
                BlockPos bp = carve.get(i);
                final boolean last = i == carve.size() - 1;
                Scheduler.schedule(i * 6, () -> {
                    c.level.setBlock(bp, Blocks.AIR.defaultBlockState(), 3);
                    c.level.setBlock(bp.above(), Blocks.AIR.defaultBlockState(), 3);
                    if (last) c.level.setBlock(bp, Blocks.REDSTONE_TORCH.defaultBlockState(), 3);
                    if (!c.p.hasDisconnected()) HorrorUtil.playTo(c.p, SoundEvents.STONE_BREAK, Vec3.atCenterOf(bp), 0.8f, 0.6f);
                });
            }
            Sanity.add(c.p, -4);
            return true;
        }
        return false;
    }

    private static boolean isNaturalStone(BlockState st) {
        return st.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD) || st.is(Blocks.DIRT) || st.is(Blocks.GRAVEL);
    }

    /** Vanilla danger sounds with nothing behind them. The creeper hiss is the cruellest. */
    private static boolean fakeMob(Ctx c) {
        int roll = c.r.nextInt(c.chapter >= 2 ? 7 : 4);
        Vec3 behind = HorrorUtil.behind(c.p, 1.6).subtract(0, 1, 0);
        switch (roll) {
            case 0 -> HorrorUtil.playTo(c.p, SoundEvents.CREEPER_PRIMED, behind, 1f, 0.5f);
            case 1 -> HorrorUtil.playTo(c.p, SoundEvents.ZOMBIE_AMBIENT, HorrorUtil.behind(c.p, 5), 0.9f, 0.8f);
            case 2 -> {
                HorrorUtil.playTo(c.p, SoundEvents.SKELETON_SHOOT, HorrorUtil.behind(c.p, 12), 1f, 1f);
                Scheduler.schedule(8, () -> HorrorUtil.playTo(c.p, SoundEvents.ARROW_HIT, c.p.position().add(c.r.nextGaussian(), 0.5, c.r.nextGaussian()), 1f, 1f));
            }
            case 3 -> HorrorUtil.playTo(c.p, SoundEvents.SPIDER_AMBIENT, HorrorUtil.behind(c.p, 3), 0.8f, 0.7f);
            case 4 -> HorrorUtil.playTo(c.p, SoundEvents.ENDERMAN_STARE, c.p.getEyePosition(), 0.7f, 0.6f);
            case 5 -> {
                List<BlockPos> doors = scan(c, 10, 3, st -> st.getBlock() instanceof DoorBlock && st.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER);
                if (doors.isEmpty()) return false;
                Vec3 d = Vec3.atCenterOf(doors.get(c.r.nextInt(doors.size())));
                for (int i = 0; i < 4; i++) {
                    Scheduler.schedule(i * 14, () -> HorrorUtil.playTo(c.p, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, d, 1f, 0.8f));
                }
            }
            default -> HorrorUtil.playTo(c.p, SoundEvents.GLASS_BREAK, HorrorUtil.behind(c.p, 6), 1f, 0.8f);
        }
        Sanity.add(c.p, -3);
        return true;
    }

    private static boolean knock(Ctx c) {
        List<BlockPos> doors = scan(c, 12, 3, s -> s.getBlock() instanceof DoorBlock && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER);
        if (doors.isEmpty()) return false;
        BlockPos door = doors.get(c.r.nextInt(doors.size()));
        Vec3 at = Vec3.atCenterOf(door).add(0, 0.5, 0);
        HorrorUtil.playTo(c.p, c.r.nextFloat() < 0.2f ? "scare.knock_frantic" : "scare.knock", at, 1.3f, 1f);
        if (c.chapter >= 2 && c.r.nextFloat() < 0.4f) {
            Scheduler.schedule(70, () -> {
                if (!c.p.hasDisconnected()) HorrorUtil.playTo(c.p, "voice.open", at, 1.2f, 1f);
            });
        }
        Sanity.add(c.p, -3);
        return true;
    }

    private static boolean doorOpen(Ctx c) {
        List<BlockPos> doors = scan(c, 10, 3, s -> s.getBlock() instanceof DoorBlock && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                && s.getBlock() != Blocks.IRON_DOOR);
        doors.removeIf(d -> HorrorUtil.isLookingAt(c.p, Vec3.atCenterOf(d), 0.3));
        if (doors.isEmpty()) return false;
        BlockPos d = doors.get(c.r.nextInt(doors.size()));
        BlockState s = c.level.getBlockState(d);
        ((DoorBlock) s.getBlock()).setOpen(null, c.level, s, d, !s.getValue(DoorBlock.OPEN));
        Sanity.add(c.p, -2);
        return true;
    }

    private static boolean torchesOut(Ctx c) {
        List<BlockPos> lights = scan(c, 10, 4, s -> s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH) || s.is(Blocks.LANTERN)
                || (s.getBlock() instanceof CampfireBlock && s.getValue(CampfireBlock.LIT))
                || (s.getBlock() instanceof CandleBlock && s.getValue(CandleBlock.LIT)));
        lights.removeIf(l -> HorrorUtil.isLookingAt(c.p, Vec3.atCenterOf(l), 0.5));
        if (lights.isEmpty()) return false;
        Collections.shuffle(lights);
        int n = Math.min(lights.size(), 2 + c.r.nextInt(4));
        for (int i = 0; i < n; i++) {
            BlockPos l = lights.get(i);
            BlockState s = c.level.getBlockState(l);
            if (s.hasProperty(BlockStateProperties.LIT)) {
                c.level.setBlock(l, s.setValue(BlockStateProperties.LIT, false), 3);
            } else {
                c.level.setBlock(l, Blocks.AIR.defaultBlockState(), 3);
                ItemStack drop = new ItemStack(s.is(Blocks.LANTERN) ? net.minecraft.world.item.Items.LANTERN : net.minecraft.world.item.Items.TORCH);
                c.level.addFreshEntity(new ItemEntity(c.level, l.getX() + 0.5, l.getY() + 0.3, l.getZ() + 0.5, drop));
            }
            c.level.sendParticles(ParticleTypes.SMOKE, l.getX() + 0.5, l.getY() + 0.6, l.getZ() + 0.5, 6, 0.1, 0.1, 0.1, 0.01);
            c.level.playSound(null, l, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4f, 1.6f);
        }
        Sanity.add(c.p, -4);
        return true;
    }

    /** While you were away, someone was home. */
    private static boolean homecoming(Ctx c) {
        boolean opened = false;
        for (BlockPos d : scan(c, 12, 3, s -> s.getBlock() instanceof DoorBlock && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                && s.getBlock() != Blocks.IRON_DOOR && !s.getValue(DoorBlock.OPEN))) {
            BlockState s = c.level.getBlockState(d);
            ((DoorBlock) s.getBlock()).setOpen(null, c.level, s, d, true);
            opened = true;
        }
        List<BlockPos> chests = scan(c, 14, 5, s -> s.is(Blocks.CHEST) || s.is(Blocks.BARREL) || s.is(Blocks.TRAPPED_CHEST));
        Collections.shuffle(chests);
        boolean note = false;
        for (BlockPos ch : chests) {
            if (!(c.level.getBlockEntity(ch) instanceof Container cont)) continue;
            int size = cont.getContainerSize();
            // things are not where you left them
            for (int k = 0; k < 40; k++) {
                int a = c.r.nextInt(size), b = c.r.nextInt(size);
                if (cont.getItem(a).isEmpty()) continue;
                ItemStack sa = cont.getItem(a);
                cont.setItem(a, cont.getItem(b));
                cont.setItem(b, sa);
            }
            if (!note) {
                for (int i = 0; i < size && !note; i++) {
                    if (cont.getItem(i).isEmpty()) {
                        cont.setItem(i, NoteItem.create("home" + (1 + c.r.nextInt(4))));
                        note = true;
                    }
                }
            }
            cont.setChanged();
        }
        if (!opened && !note) return false;
        Net.fx(c.p, Fx.SUBTITLE, 90, 0, opened ? "Дверь открыта. Ты её закрывал." : "Здесь кто-то был.");
        Scheduler.schedule(50, () -> {
            if (c.p.hasDisconnected()) return;
            HorrorUtil.playTo(c.p, SoundEvents.WOODEN_DOOR_CLOSE, HorrorUtil.behind(c.p, 7), 0.8f, 0.8f);
            if (c.night) force("bed_double", c);
        });
        Sanity.add(c.p, -6);
        return true;
    }

    private static void force(String name, Ctx c) {
        Scare s = BY_NAME.get(name);
        if (s != null && s.cond().test(c)) s.run().apply(c);
    }

    private static boolean chestNote(Ctx c) {
        List<BlockPos> chests = scan(c, 20, 6, s -> s.is(Blocks.CHEST) || s.is(Blocks.BARREL) || s.is(Blocks.TRAPPED_CHEST));
        chests.removeIf(ch -> HorrorUtil.isLookingAt(c.p, Vec3.atCenterOf(ch), 0.5));
        Collections.shuffle(chests);
        for (BlockPos ch : chests) {
            BlockEntity be = c.level.getBlockEntity(ch);
            if (!(be instanceof Container cont)) continue;
            for (int i = 0; i < cont.getContainerSize(); i++) {
                int slot = (i + c.r.nextInt(cont.getContainerSize())) % cont.getContainerSize();
                if (cont.getItem(slot).isEmpty()) {
                    cont.setItem(slot, NoteItem.create("creepy" + (1 + c.r.nextInt(8))));
                    be.setChanged();
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean sign(Ctx c) {
        Vec3 b = HorrorUtil.behind(c.p, 3.5);
        return HorrorUtil.ground(c.level, Mth.floor(b.x), Mth.floor(b.z), c.p.getBlockY() + 1, 2, 3).map(g -> {
            if (!c.level.getBlockState(g).isAir()) return false;
            double dx = c.p.getX() - (g.getX() + 0.5), dz = c.p.getZ() - (g.getZ() + 0.5);
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            int rot = Mth.floor(yaw * 16f / 360f + 0.5) & 15;
            c.level.setBlock(g, Blocks.DARK_OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, rot), 3);
            if (c.level.getBlockEntity(g) instanceof SignBlockEntity sbe) {
                String text = c.r.nextFloat() < 0.25f ? c.p.getGameProfile().getName() : SIGNS[c.r.nextInt(SIGNS.length)];
                SignText st = new SignText().setMessage(1, Component.literal(text)).setColor(net.minecraft.world.item.DyeColor.RED);
                sbe.setText(st, true);
                sbe.setWaxed(true);
            }
            HorrorUtil.playTo(c.p, "scare.breath", Vec3.atCenterOf(g).add(0, 1, 0), 0.6f, 1f);
            Sanity.add(c.p, -4);
            return true;
        }).orElse(false);
    }

    private static boolean renameItem(Ctx c) {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < c.p.getInventory().items.size(); i++) {
            ItemStack s = c.p.getInventory().items.get(i);
            if (!s.isEmpty() && !s.hasCustomHoverName() && !net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem()).getNamespace().equals(EchoHorror.MODID))
                slots.add(i);
        }
        if (slots.isEmpty()) return false;
        ItemStack s = c.p.getInventory().items.get(slots.get(c.r.nextInt(slots.size())));
        s.setHoverName(Component.literal(RENAMES[c.r.nextInt(RENAMES.length)]).withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
        return true;
    }

    private static boolean lookTurn(Ctx c) {
        Net.fx(c.p, Fx.BLACKOUT, 14);
        Scheduler.schedule(6, () -> {
            if (c.p.hasDisconnected()) return;
            float yaw = c.p.getYRot() + 180f;
            c.p.teleportTo(c.level, c.p.getX(), c.p.getY(), c.p.getZ(), yaw, 0f);
            Vec3 front = c.p.position().add(HorrorUtil.rotate(new Vec3(0, 0, 1), yaw).scale(3.5));
            HorrorUtil.ground(c.level, Mth.floor(front.x), Mth.floor(front.z), c.p.getBlockY() + 1, 2, 3).ifPresent(g -> {
                PhantomEntity.spawn(c.p, PhantomEntity.KIND_WATCHER, PhantomEntity.MODE_STILL, Vec3.atBottomCenterOf(g), 14);
                HorrorUtil.playAt(c.p, "scare.stinger", 1f, 1f);
                Net.fx(c.p, Fx.SHAKE, 20, 1.5f, "");
            });
            Sanity.add(c.p, -8);
        });
        return true;
    }

    private static boolean voiceMimic(Ctx c) {
        List<ServerPlayer> others = HorrorUtil.others(c.p);
        Collections.shuffle(others);
        for (ServerPlayer o : others) {
            if (!VoiceBridge.hasClip(o.getUUID())) continue;
            Optional<Vec3> spot = HorrorUtil.spotAround(c.p, 10, 20, 150, 60, false);
            if (spot.isEmpty()) return false;
            if (VoiceBridge.play(c.p, o.getUUID(), spot.get().add(0, 1.6, 0))) {
                Sanity.add(c.p, -4);
                return true;
            }
        }
        return false;
    }

    private static boolean fakeTab(Ctx c) {
        try {
            UUID fake = UUID.randomUUID();
            GameProfile prof = c.p.getGameProfile();
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeEnumSet(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY), ClientboundPlayerInfoUpdatePacket.Action.class);
            buf.writeVarInt(1);
            buf.writeUUID(fake);
            buf.writeUtf(prof.getName(), 16);
            buf.writeGameProfileProperties(prof.getProperties());
            buf.writeBoolean(true);
            buf.writeVarInt(c.p.latency);
            c.p.connection.send(new ClientboundPlayerInfoUpdatePacket(buf));
            Scheduler.schedule(1200, () -> {
                if (!c.p.hasDisconnected()) c.p.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(fake)));
            });
            return true;
        } catch (Exception e) {
            EchoHorror.LOG.debug("fake tab failed", e);
            return false;
        }
    }

    /** Neighbouring hostile horror entities make it worse. */
    public static boolean horrorNearby(ServerPlayer p, double r) {
        AABB box = p.getBoundingBox().inflate(r);
        return !p.level().getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class, box,
                e -> e.getType().builtInRegistryHolder().key().location().getNamespace().equals(EchoHorror.MODID)).isEmpty();
    }
}
