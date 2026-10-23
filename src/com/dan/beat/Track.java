package com.dan.beat;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;

/**
 * Eine Tonspur (Phase 17): WAV-Datei (PCM 8/16/24/32 Bit, mehrere Kanäle gemittelt) als Proben −1 … 1. Gepackte
 * Formate (MP3, M4A) liest das JDK nicht; sie werden vorher umgewandelt (z. B. {@code ffmpeg -i lied.mp3 lied.wav}).
 */
public final class Track {

    public final float[] samples;
    public final int rate;
    public final String name;

    public Track(float[] samples, int rate, String name) {
        this.samples = samples;
        this.rate = rate;
        this.name = name;
    }

    public double seconds() { return samples.length / (double) rate; }

    /** WAV-Datei lesen. */
    public static Track read(File f) throws IOException { return read(Files.readAllBytes(f.toPath()), f.getName()); }

    public static Track read(byte[] b, String name) throws IOException {
        ByteBuffer bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
        if (b.length < 44 || bb.getInt(0) != 0x46464952 || bb.getInt(8) != 0x45564157) {
            boolean mp3 = b.length > 3 && (b[0] == 'I' && b[1] == 'D' && b[2] == '3' || (b[0] & 0xFF) == 0xFF && (b[1] & 0xE0) == 0xE0);
            boolean m4a = b.length > 8 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p';
            throw new IOException(name + (mp3 ? " ist MP3" : m4a ? " ist M4A/AAC" : " ist keine WAV-Datei")
                    + (mp3 || m4a ? ", bitte als WAV (PCM) speichern, z. B. ffmpeg -i lied.mp3 lied.wav" : ""));
        }
        int pos = 12, ch = 1, sr = 44100, bits = 16, fmt = 1, dataOff = -1, dataLen = 0;
        while (pos + 8 <= b.length) {
            int id = bb.getInt(pos), len = bb.getInt(pos + 4);
            if (id == 0x20746d66) {
                fmt = bb.getShort(pos + 8) & 0xFFFF; ch = bb.getShort(pos + 10); sr = bb.getInt(pos + 12); bits = bb.getShort(pos + 22);
                if (fmt == 0xFFFE && len >= 26) fmt = bb.getShort(pos + 32) & 0xFFFF;   // WAVE_FORMAT_EXTENSIBLE
            } else if (id == 0x61746164) { dataOff = pos + 8; dataLen = Math.min(len < 0 ? Integer.MAX_VALUE : len, b.length - dataOff); break; }
            pos += 8 + len + (len & 1);
        }
        if (dataOff < 0) throw new IOException(name + ": keine Daten");
        if (fmt != 1 && fmt != 3) throw new IOException(name + ": nur PCM (Format " + fmt + ")");
        int bps = bits / 8, n = dataLen / (bps * ch);
        float[] s = new float[n];
        for (int i = 0; i < n; i++) {
            double acc = 0;
            for (int c = 0; c < ch; c++) {
                int o = dataOff + (i * ch + c) * bps;
                double v;
                if (fmt == 3 && bits == 32) v = bb.getFloat(o);
                else v = switch (bits) {
                    case 8 -> ((b[o] & 0xFF) - 128) / 128.0;
                    case 16 -> bb.getShort(o) / 32768.0;
                    case 24 -> ((b[o] & 0xFF) | (b[o + 1] & 0xFF) << 8 | b[o + 2] << 16) / 8388608.0;
                    case 32 -> bb.getInt(o) / 2147483648.0;
                    default -> 0;
                };
                acc += v;
            }
            s[i] = (float) (acc / ch);
        }
        return new Track(s, sr, name);
    }

    /** Ausschnitt [t0, t1) in s. */
    public Track cut(double t0, double t1) {
        int a = (int) Math.max(0, Math.round(t0 * rate)), e = (int) Math.min(samples.length, Math.round(t1 * rate));
        float[] s = new float[Math.max(0, e - a)];
        System.arraycopy(samples, a, s, 0, s.length);
        return new Track(s, rate, name);
    }

    /** PCM 16 Bit (für Video mit Ton). */
    public short[] pcm16() {
        short[] o = new short[samples.length];
        for (int i = 0; i < o.length; i++) o[i] = (short) Math.round(Math.max(-1, Math.min(1, samples[i])) * 32767);
        return o;
    }

    /** Als WAV (PCM 16 Bit, mono). */
    public byte[] wav() {
        short[] p = pcm16();
        ByteBuffer bb = ByteBuffer.allocate(44 + p.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(0x46464952).putInt(36 + p.length * 2).putInt(0x45564157);
        bb.putInt(0x20746d66).putInt(16).putShort((short) 1).putShort((short) 1).putInt(rate).putInt(rate * 2).putShort((short) 2).putShort((short) 16);
        bb.putInt(0x61746164).putInt(p.length * 2);
        for (short v : p) bb.putShort(v);
        return bb.array();
    }
}
