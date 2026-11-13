import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Minimal NBT reader for the asset importer: compounds as maps, lists as lists, arrays as arrays. */
public final class Nbt {
    public static Map<String, Object> read(InputStream raw) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw), 1 << 16));
        byte t = in.readByte();
        in.readUTF();
        @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) payload(in, t);
        return m;
    }

    static Object payload(DataInputStream in, int t) throws IOException {
        switch (t) {
            case 1: return in.readByte();
            case 2: return in.readShort();
            case 3: return in.readInt();
            case 4: return in.readLong();
            case 5: return in.readFloat();
            case 6: return in.readDouble();
            case 7: { byte[] b = new byte[in.readInt()]; in.readFully(b); return b; }
            case 8: return in.readUTF();
            case 9: { int et = in.readByte(), n = in.readInt(); List<Object> l = new ArrayList<>(Math.max(0, n)); for (int i = 0; i < n; i++) l.add(payload(in, et)); return l; }
            case 10: { Map<String, Object> m = new LinkedHashMap<>(); while (true) { int tt = in.readByte(); if (tt == 0) return m; String k = in.readUTF(); m.put(k, payload(in, tt)); } }
            case 11: { int[] a = new int[in.readInt()]; for (int i = 0; i < a.length; i++) a[i] = in.readInt(); return a; }
            case 12: { long[] a = new long[in.readInt()]; for (int i = 0; i < a.length; i++) a[i] = in.readLong(); return a; }
            default: throw new IOException("tag " + t);
        }
    }
}
