import java.io.*;
import java.util.*;

public final class Inspect {
    public static void main(String[] a) throws Exception {
        Map<String, Object> d = Nbt.read(new FileInputStream(a[0]));
        int W = ((Number) d.get("Width")).intValue(), L = ((Number) d.get("Length")).intValue(), H = ((Number) d.get("Height")).intValue();
        byte[] B = (byte[]) d.get("Blocks"), D = (byte[]) d.get("Data");
        System.out.println(W + " x " + L + " x " + H + " add=" + d.containsKey("AddBlocks"));
        long[] cnt = new long[4096];
        long[] perY = new long[H];
        for (int y = 0; y < H; y++) for (int i = y * L * W, e = i + L * W; i < e; i++) { int id = B[i] & 255; if (id != 0) { cnt[id * 16 + (D[i] & 15)]++; perY[y]++; } }
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < 4096; k++) if (cnt[k] > 0) sb.append(k / 16).append(':').append(k % 16).append('=').append(cnt[k]).append("  ");
        System.out.println(sb);
        StringBuilder py = new StringBuilder();
        for (int y = 0; y < H; y++) if (perY[y] > 0) py.append(y).append('=').append(perY[y]).append(' ');
        System.out.println(py.length() > 1500 ? py.substring(0, 1500) : py);
    }
}
