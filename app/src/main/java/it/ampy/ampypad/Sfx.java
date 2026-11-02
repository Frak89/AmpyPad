package it.ampy.ampypad;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.SystemClock;

import java.util.Random;

/** Tiny synthesised sounds (no audio files): quill scratch, page swish, pop, chime. */
public final class Sfx {
    private Sfx() { }
    private static final int SR = 22050;
    private static AudioTrack scratch, swish, pop, chime;
    private static long lastScratch;
    public static boolean on = true;

    private static AudioTrack track(short[] pcm) {
        try {
            AudioTrack t = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(SR)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.length * 2).build();
            t.write(pcm, 0, pcm.length);
            return t;
        } catch (Exception e) { return null; }
    }

    private static synchronized void init() {
        if (scratch != null) return;
        Random r = new Random(5);
        // scratch: band-passed noise, 45 ms
        int n = (int) (SR * 0.045);
        short[] a = new short[n];
        float lp1 = 0, lp2 = 0;
        for (int i = 0; i < n; i++) {
            float x = r.nextFloat() * 2 - 1;
            lp1 += 0.55f * (x - lp1); lp2 += 0.08f * (x - lp2);
            float env = (float) Math.exp(-i / (SR * 0.012)) * Math.min(1, i / 40f);
            a[i] = (short) ((lp1 - lp2) * env * 9000);
        }
        scratch = track(a);
        // swish: sweeping low-passed noise, 380 ms
        n = (int) (SR * 0.38);
        short[] b = new short[n];
        float lp = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) n;
            float k = 0.03f + 0.25f * (float) Math.sin(t * Math.PI);
            lp += k * ((r.nextFloat() * 2 - 1) - lp);
            b[i] = (short) (lp * Math.sin(t * Math.PI) * 14000);
        }
        swish = track(b);
        // pop: falling sine, 90 ms
        n = (int) (SR * 0.09);
        short[] c = new short[n];
        double ph = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) n, f = 900 - 600 * t;
            ph += 2 * Math.PI * f / SR;
            c[i] = (short) (Math.sin(ph) * Math.exp(-t * 5) * 9000);
        }
        pop = track(c);
        // chime: two bells, 420 ms
        n = (int) (SR * 0.42);
        short[] e = new short[n];
        for (int i = 0; i < n; i++) {
            double t = i / (double) SR;
            double v = Math.sin(2 * Math.PI * 1318.5 * t) * Math.exp(-t * 9) + 0.6 * Math.sin(2 * Math.PI * 1975.5 * t) * Math.exp(-t * 12);
            e[i] = (short) (v * 6000 * Math.min(1, i / 60.0));
        }
        chime = track(e);
    }

    private static void play(AudioTrack t, float rate) {
        if (!on || t == null) return;
        try {
            if (t.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) t.stop();
            t.reloadStaticData();
            t.setPlaybackRate(Math.max(4000, Math.round(SR * rate)));
            t.play();
        } catch (Exception ignored) { }
    }

    public static void scratch() {
        if (!on) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastScratch < 40) return;
        lastScratch = now;
        init();
        play(scratch, 0.8f + (float) Math.random() * 0.5f);
    }
    public static void swish() { if (!on) return; init(); play(swish, 1f); }
    public static void pop() { if (!on) return; init(); play(pop, 0.9f + (float) Math.random() * 0.3f); }
    public static void chime() { if (!on) return; init(); play(chime, 1f); }
}
