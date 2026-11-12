package org.pixelify.callscreen;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class PhenotypeTest {
    private static final byte[] SNAPSHOT = {
        0x0a, 1, 'a', 0x12, 1, 'b', 0x35, 1, 2, 3, 4,
    };
    private static final String ENABLED = "G__speak_easy_enabled";

    public static void main(String[] args) throws IOException {
        byte[] unrelated = flag("another_flag", 1);
        byte[] original = join(SNAPSHOT, unrelated, flag(ENABLED, 0));
        byte[] patched = Phenotype.patch(original);
        byte[] retained = join(SNAPSHOT, unrelated, flag(ENABLED, 1));
        assert Arrays.equals(Arrays.copyOf(patched, retained.length), retained);
        assert Arrays.equals(patched, Phenotype.patch(patched));

        byte[] config = {
            0x32, 0x16, 0x0a, 0x14, 0x0a, 2, 'j', 'p', 0x12, 0x0e,
            0x0a, 0x0c, 0x0a, 5, 'j', 'a', '-', 'J', 'P', 0x12, 3, 0x0a, 1, 2,
        };
        assert new String(patched, StandardCharsets.ISO_8859_1)
                .contains(new String(config, StandardCharsets.ISO_8859_1));
        assertRejected(new byte[] {0x0a, 8, 1});
        assertRejected(new byte[0]);
        assertRejected(join(SNAPSHOT, flag(ENABLED, 0), flag(ENABLED, 1)));
        byte[] wrongType = flag(ENABLED, 0);
        wrongType[2] = 0x20;
        assertRejected(join(SNAPSHOT, wrongType));
    }

    private static void assertRejected(byte[] snapshot) {
        try {
            Phenotype.patch(snapshot);
            throw new AssertionError("Invalid snapshot accepted");
        } catch (IOException expected) {
            // A malformed or incompatible snapshot must never be rewritten.
        }
    }

    private static byte[] flag(String name, int value) {
        byte[] key = name.getBytes(StandardCharsets.US_ASCII);
        return join(new byte[] {0x22, (byte) (key.length + 4), 0x10, (byte) value, 0x52, (byte) key.length}, key);
    }

    private static byte[] join(byte[]... parts) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            output.write(part, 0, part.length);
        }
        return output.toByteArray();
    }
}
