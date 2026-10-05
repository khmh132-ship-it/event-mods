package com.echohorror.compat;

import com.echohorror.Config;
import com.echohorror.EchoHorror;
import com.echohorror.horror.VoiceBridge;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Simple Voice Chat plugin: the Echo listens to players' microphones and keeps a few short clips per player
 * in memory (never on disk). Later it plays them back from the darkness — only to one listener.
 */
@ForgeVoicechatPlugin
public class VoiceMimicPlugin implements VoicechatPlugin, VoiceBridge.Impl {
    private static final ScheduledExecutorService EXEC = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "EchoHorror-VoiceMimic");
        t.setDaemon(true);
        return t;
    });

    private volatile VoicechatServerApi api;
    private final Map<UUID, Recorder> recorders = new ConcurrentHashMap<>();
    private final Random random = new Random();

    @Override
    public String getPluginId() {
        return EchoHorror.MODID;
    }

    private final java.util.Map<UUID, Long> lastHeard = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void registerEvents(EventRegistration reg) {
        reg.registerEvent(VoicechatServerStartedEvent.class, e -> {
            api = e.getVoicechat();
            VoiceBridge.install(this);
            EchoHorror.LOG.info("Echo is listening (Simple Voice Chat integration active)");
        });
        reg.registerEvent(MicrophonePacketEvent.class, this::onMic);
    }

    private void onMic(MicrophonePacketEvent e) {
        VoicechatConnection c = e.getSenderConnection();
        if (c == null || c.getPlayer() == null) return;
        UUID id = c.getPlayer().getUuid();
        boolean mimic, heard;
        try {
            mimic = Config.VOICE_MIMIC.get();
            heard = Config.VOICE_HEARD.get();
        } catch (Exception ignored) {
            return;
        }
        if (heard && !e.getPacket().isWhispering() && c.getPlayer().getPlayer() instanceof ServerPlayer sp) {
            long now = System.currentTimeMillis();
            Long last = lastHeard.get(id);
            if (last == null || now - last > 500) {
                lastHeard.put(id, now);
                sp.server.execute(() -> com.echohorror.horror.VoiceNoise.onVoice(sp));
            }
        }
        if (mimic) recorders.computeIfAbsent(id, k -> new Recorder()).add(e.getPacket().getOpusEncodedData());
    }

    @Override
    public boolean hasClip(UUID speaker) {
        Recorder r = recorders.get(speaker);
        return r != null && r.count() > 0;
    }

    @Override
    public boolean play(ServerPlayer listener, UUID speaker, Vec3 pos) {
        VoicechatServerApi a = api;
        Recorder rec = recorders.get(speaker);
        if (a == null || rec == null) return false;
        List<byte[]> clip = rec.random(random);
        if (clip == null) return false;
        LocationalAudioChannel ch = a.createLocationalAudioChannel(UUID.randomUUID(),
                a.fromServerLevel(listener.serverLevel()), a.createPosition(pos.x, pos.y, pos.z));
        if (ch == null) return false;
        ch.setDistance(48f);
        UUID lid = listener.getUUID();
        ch.setFilter(p -> p.getUuid().equals(lid));
        AtomicInteger idx = new AtomicInteger();
        AtomicReference<ScheduledFuture<?>> future = new AtomicReference<>();
        future.set(EXEC.scheduleAtFixedRate(() -> {
            int i = idx.getAndIncrement();
            if (i >= clip.size()) {
                ch.flush();
                ScheduledFuture<?> f = future.get();
                if (f != null) f.cancel(false);
                return;
            }
            ch.send(clip.get(i));
        }, 0, 20, TimeUnit.MILLISECONDS));
        return true;
    }

    private static final class Recorder {
        private List<byte[]> current = new ArrayList<>();
        private final Deque<List<byte[]>> clips = new ArrayDeque<>();
        private long last;

        synchronized void add(byte[] data) {
            long now = System.currentTimeMillis();
            if (now - last > 400) finish();
            last = now;
            if (data == null || data.length == 0) {
                finish();
                return;
            }
            current.add(data.clone());
            if (current.size() >= 250) finish(); // max 5 seconds per clip
        }

        private void finish() {
            if (current.size() >= 25) { // at least half a second of speech
                clips.addLast(current);
                while (clips.size() > 10) clips.removeFirst();
            }
            current = new ArrayList<>();
        }

        synchronized int count() {
            return clips.size();
        }

        synchronized List<byte[]> random(Random r) {
            if (System.currentTimeMillis() - last > 400) finish();
            if (clips.isEmpty()) return null;
            List<List<byte[]>> l = new ArrayList<>(clips);
            return l.get(r.nextInt(l.size()));
        }
    }
}
