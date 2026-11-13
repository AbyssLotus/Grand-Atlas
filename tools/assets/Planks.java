import java.io.*;
import java.util.*;

/** Which god trees contain planks or plank slabs (legacy ids 5, 125, 126) and how many. */
public final class Planks {
    public static void main(String[] a) throws Exception {
        File[] files = new File(a[0]).listFiles((d, n) -> n.endsWith(".schematic"));
        Arrays.sort(files);
        for (File f : files) {
            Map<String, Object> d = Nbt.read(new FileInputStream(f));
            byte[] B = (byte[]) d.get("Blocks");
            int planks = 0, slabs = 0, stoneSlab = 0, all = 0;
            for (byte b : B) { int id = b & 255; if (id != 0) all++; if (id == 5 || id == 125) planks++; if (id == 126) slabs++; if (id == 44 || id == 43) stoneSlab++; }
            if (planks + slabs + stoneSlab > 0) System.out.println(f.getName() + ": planks " + planks + ", wood slabs " + slabs + ", stone slabs " + stoneSlab + " of " + all);
        }
    }
}
