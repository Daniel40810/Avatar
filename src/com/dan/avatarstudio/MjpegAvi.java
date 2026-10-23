package com.dan.avatarstudio;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 * Schreibt ein Video als AVI mit Motion-JPEG (jedes Bild ein JPEG), ohne Fremdbibliothek. Läuft in
 * VLC, im Windows Media Player und in den meisten Schnittprogrammen. Ab Phase 16 auf Wunsch mit Ton
 * (PCM 16 Bit mono, zweiter Strom, je Bild ein Stück, verzahnt).
 */
final class MjpegAvi implements AutoCloseable {

    private final RandomAccessFile f;
    private final int w, h, fps;
    private final float quality;
    private final List<long[]> index = new ArrayList<>();
    private long moviStart, riffSizePos, moviSizePos, framesPos1, framesPos2, maxBytesPos, bufPos1, bufPos2;
    private int maxFrame;
    /** Ton: Abtastrate (0 = ohne), Proben bisher, Stellen zum Nachtragen. */
    private final int audioRate;
    private long audioSamples, audioLenPos, audioBufPos;
    private int maxAudio;

    MjpegAvi(File file, int w, int h, int fps, float quality) throws IOException { this(file, w, h, fps, quality, 0); }

    /** Mit Ton ({@code audioRate} > 0): nach jedem Bild mit {@link #addAudio} die Proben dieses Bilds anhängen. */
    MjpegAvi(File file, int w, int h, int fps, float quality, int audioRate) throws IOException {
        this.audioRate = audioRate;
        file.getAbsoluteFile().getParentFile().mkdirs();
        if (file.exists()) file.delete();
        f = new RandomAccessFile(file, "rw");
        this.w = w; this.h = h; this.fps = fps; this.quality = quality;
        fourcc("RIFF"); riffSizePos = f.getFilePointer(); i32(0); fourcc("AVI ");
        fourcc("LIST"); long hdrlSize = f.getFilePointer(); i32(0); fourcc("hdrl");
        long hdrlStart = f.getFilePointer() - 4;
        fourcc("avih"); i32(56);
        i32(1_000_000 / fps); maxBytesPos = f.getFilePointer(); i32(0); i32(0); i32(0x10);
        framesPos1 = f.getFilePointer(); i32(0); i32(0); i32(audioRate > 0 ? 2 : 1); bufPos1 = f.getFilePointer(); i32(0); i32(w); i32(h);
        i32(0); i32(0); i32(0); i32(0);
        fourcc("LIST"); long strlSize = f.getFilePointer(); i32(0); fourcc("strl");
        long strlStart = f.getFilePointer() - 4;
        fourcc("strh"); i32(56);
        fourcc("vids"); fourcc("MJPG"); i32(0); i16(0); i16(0); i32(0); i32(1); i32(fps); i32(0);
        framesPos2 = f.getFilePointer(); i32(0); bufPos2 = f.getFilePointer(); i32(0); i32(-1); i32(0);
        i16(0); i16(0); i16(w); i16(h);
        fourcc("strf"); i32(40);
        i32(40); i32(w); i32(h); i16(1); i16(24); fourcc("MJPG"); i32(w * h * 3); i32(0); i32(0); i32(0); i32(0);
        patch(strlSize, f.getFilePointer() - strlStart);
        if (audioRate > 0) {
            fourcc("LIST"); long aSize = f.getFilePointer(); i32(0); fourcc("strl");
            long aStart = f.getFilePointer() - 4;
            fourcc("strh"); i32(56);
            fourcc("auds"); i32(0); i32(0); i16(0); i16(0); i32(0);
            i32(2); i32(audioRate * 2); i32(0);                       // Maßstab = Block (2 Byte), Rate = Byte je s, Anfang
            audioLenPos = f.getFilePointer(); i32(0); audioBufPos = f.getFilePointer(); i32(0); i32(-1); i32(2);
            i16(0); i16(0); i16(0); i16(0);
            fourcc("strf"); i32(18);
            i16(1); i16(1); i32(audioRate); i32(audioRate * 2); i16(2); i16(16); i16(0);
            patch(aSize, f.getFilePointer() - aStart);
        }
        patch(hdrlSize, f.getFilePointer() - hdrlStart);
        fourcc("LIST"); moviSizePos = f.getFilePointer(); i32(0); fourcc("movi");
        moviStart = f.getFilePointer() - 4;
    }

    void add(BufferedImage img) throws IOException {
        BufferedImage rgb = img;
        if (img.getType() != BufferedImage.TYPE_INT_RGB || img.getWidth() != w || img.getHeight() != h) {
            rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = rgb.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(img, 0, 0, w, h, null);
            g.dispose();
        }
        ByteArrayOutputStream bo = new ByteArrayOutputStream(1 << 16);
        ImageWriter wr = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam p = wr.getDefaultWriteParam();
        p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        p.setCompressionQuality(quality);
        try (MemoryCacheImageOutputStream os = new MemoryCacheImageOutputStream(bo)) {
            wr.setOutput(os);
            wr.write(null, new IIOImage(rgb, null, null), p);
        }
        wr.dispose();
        byte[] jpg = bo.toByteArray();
        long off = f.getFilePointer() - moviStart;
        fourcc("00dc"); i32(jpg.length); f.write(jpg);
        if ((jpg.length & 1) != 0) f.write(0);
        index.add(new long[]{off, jpg.length, 0});
        maxFrame = Math.max(maxFrame, jpg.length);
    }

    /** Tonproben (PCM 16 Bit mono) als ein Stück des Tonstroms anhängen. */
    void addAudio(short[] pcm, int from, int n) throws IOException {
        if (audioRate <= 0 || n <= 0) return;
        byte[] b = new byte[n * 2];
        for (int i = 0; i < n; i++) { short s = pcm[from + i]; b[2 * i] = (byte) s; b[2 * i + 1] = (byte) (s >> 8); }
        long off = f.getFilePointer() - moviStart;
        fourcc("01wb"); i32(b.length); f.write(b);
        index.add(new long[]{off, b.length, 1});
        audioSamples += n;
        maxAudio = Math.max(maxAudio, b.length);
    }

    int frames() { int n = 0; for (long[] e : index) if (e[2] == 0) n++; return n; }

    @Override public void close() throws IOException {
        patch(moviSizePos, f.getFilePointer() - moviStart);
        fourcc("idx1"); i32(index.size() * 16);
        int videoFrames = 0;
        for (long[] e : index) { fourcc(e[2] == 1 ? "01wb" : "00dc"); i32(0x10); i32((int) e[0]); i32((int) e[1]); if (e[2] == 0) videoFrames++; }
        long end = f.getFilePointer();
        patch(riffSizePos, end - 8);
        patch(framesPos1, videoFrames);
        patch(framesPos2, videoFrames);
        if (audioRate > 0) { patch(audioLenPos, audioSamples); patch(audioBufPos, maxAudio + 8); }
        patch(bufPos1, maxFrame + 8);
        patch(bufPos2, maxFrame + 8);
        patch(maxBytesPos, (long) maxFrame * fps);
        f.close();
    }

    private void patch(long pos, long v) throws IOException {
        long cur = f.getFilePointer();
        f.seek(pos);
        i32((int) v);
        f.seek(cur);
    }

    private void fourcc(String s) throws IOException { f.write(s.getBytes(java.nio.charset.StandardCharsets.US_ASCII)); }

    private void i32(int v) throws IOException { f.write(v & 255); f.write((v >> 8) & 255); f.write((v >> 16) & 255); f.write((v >>> 24) & 255); }

    private void i16(int v) throws IOException { f.write(v & 255); f.write((v >> 8) & 255); }
}
