import java.io.*;
import java.util.*;

public final class Inspect2 {
    public static void main(String[] a) throws Exception {
        long[] cnt = new long[4096];
        for (File f : new File(a[0]).listFiles((d, n) -> n.endsWith(".schematic"))) {
            Map<String, Object> d = Nbt.read(new FileInputStream(f));
            int W = ((Number) d.get("Width")).intValue(), L = ((Number) d.get("Length")).intValue(), H = ((Number) d.get("Height")).intValue();
            byte[] B = (byte[]) d.get("Blocks"), D = (byte[]) d.get("Data");
            long n = 0;
            for (int i = 0; i < B.length; i++) { int id = B[i] & 255; if (id != 0) { cnt[id * 16 + (D[i] & 15)]++; n++; } }
            System.out.println(f.getName() + " " + W + "x" + L + "x" + H + " blocks " + n);
        }
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < 4096; k++) if (cnt[k] > 0) sb.append(k / 16).append(':').append(k % 16).append('=').append(cnt[k]).append("  ");
        System.out.println(sb);
    }
}
