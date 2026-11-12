package org.pixelify.callscreen;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class Phenotype {
    private static final String JAPANESE_CONFIG = "CallScreenI18n__call_screen_i18n_config";
    private static final String[] FLAGS = {
        "G__speak_easy_enabled",
        "enable_call_screen_i18n_tidepods",
        "G__speak_easy_use_soda_asr",
        JAPANESE_CONFIG,
    };
    private static final int MAX_SNAPSHOT_BYTES = 8 * 1024 * 1024;
    private static final int MAX_DEX_BYTES = 64 * 1024 * 1024;

    public static void main(String[] args) {
        try {
            if (args.length == 2 && args[0].equals("supports")) {
                System.exit(supports(args[1].split("\n")) ? 0 : 2);
            } else if (args.length == 3 && args[0].equals("patch")) {
                Path source = Paths.get(args[1]);
                if (Files.size(source) > MAX_SNAPSHOT_BYTES) {
                    throw new IOException("Snapshot is too large");
                }
                Files.write(Paths.get(args[2]), patch(Files.readAllBytes(source)));
            } else {
                throw new IllegalArgumentException("Expected supports APK_PATHS or patch INPUT OUTPUT");
            }
        } catch (IOException | IllegalArgumentException error) {
            System.err.println(error.getMessage());
            System.exit(1);
        }
    }

    private static boolean supports(String[] apkPaths) throws IOException {
        Map<String, String> remaining = new LinkedHashMap<>();
        for (String name : FLAGS) {
            // DEX stores this ASCII string with a one-byte UTF-16 length and a NUL terminator.
            remaining.put(name, (char) name.length() + name + '\0');
        }
        for (String path : apkPaths) {
            try (ZipFile apk = new ZipFile(path)) {
                for (ZipEntry entry : Collections.list(apk.entries())) {
                    if (!entry.getName().matches("classes\\d*\\.dex")) {
                        continue;
                    }
                    try (InputStream stream = apk.getInputStream(entry)) {
                        String dex = new String(readDex(stream, entry.getSize()), StandardCharsets.ISO_8859_1);
                        remaining.values().removeIf(dex::contains);
                    }
                    if (remaining.isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static byte[] readDex(InputStream stream, long size) throws IOException {
        if (size < 8 || size > MAX_DEX_BYTES) {
            throw new IOException("Unexpected DEX size");
        }
        byte[] dex = new byte[(int) size];
        new DataInputStream(stream).readFully(dex);
        if (dex[0] != 'd' || dex[1] != 'e' || dex[2] != 'x' || dex[3] != '\n') {
            throw new IOException("Unexpected DEX header");
        }
        return dex;
    }

    static byte[] patch(byte[] snapshot) throws IOException {
        if (snapshot.length > MAX_SNAPSHOT_BYTES) {
            throw new IOException("Snapshot is too large");
        }
        Map<String, byte[]> pending = new LinkedHashMap<>();
        for (String name : FLAGS) {
            pending.put(name, name.equals(JAPANESE_CONFIG) ? japaneseConfig() : new byte[] {0x10, 1});
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream(snapshot.length + 256);
        Reader record = new Reader(snapshot, 0, snapshot.length);
        int tokens = 0;
        while (record.next()) {
            if (record.wire == 2 && (record.number == 1 || record.number == 2)) {
                tokens |= record.number;
            }
            if (record.number != 4) {
                record.copyTo(output);
                continue;
            }
            if (record.wire != 2) {
                throw new IOException("Unexpected snapshot flag type");
            }

            Reader flag = record.message();
            String name = null;
            while (flag.next()) {
                if (flag.number == 10 && flag.wire == 2) {
                    name = new String(snapshot, flag.valueStart, flag.position - flag.valueStart,
                            StandardCharsets.UTF_8);
                }
            }
            if (name == null) {
                throw new IOException("Snapshot flag has no name");
            }
            byte[] replacement = pending.remove(name);
            if (replacement == null) {
                if (Arrays.asList(FLAGS).contains(name)) {
                    throw new IOException("Duplicate Call Screen flag");
                }
                record.copyTo(output);
            } else {
                replaceFlag(output, record.message(), replacement);
            }
        }
        if (tokens != 3) {
            throw new IOException("Missing Phenotype snapshot tokens");
        }
        for (Map.Entry<String, byte[]> entry : pending.entrySet()) {
            write(output, field(4, concat(entry.getValue(), field(10, ascii(entry.getKey())))));
        }
        return output.toByteArray();
    }

    private static void replaceFlag(ByteArrayOutputStream output, Reader flag, byte[] replacement)
            throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        boolean replaced = false;
        while (flag.next()) {
            int key = (flag.number << 3) | flag.wire;
            if (key == replacement[0]) {
                if (replaced) {
                    throw new IOException("Duplicate flag value");
                }
                write(payload, replacement);
                replaced = true;
            } else {
                if (flag.number <= 6) {
                    throw new IOException("Unexpected Call Screen flag type");
                }
                flag.copyTo(payload);
            }
        }
        if (!replaced) {
            throw new IOException("Unexpected Call Screen flag type");
        }
        write(output, field(4, payload.toByteArray()));
    }

    private static byte[] japaneseConfig() {
        // Country jp, language ja-JP, ASR model 2 (SODA).
        byte[] language = concat(field(1, ascii("ja-JP")), field(2, field(1, new byte[] {2})));
        byte[] country = concat(field(1, ascii("jp")), field(2, field(1, language)));
        return field(6, field(1, country));
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] field(int number, byte[] value) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write((number << 3) | 2);
        int size = value.length;
        while (size >= 128) {
            output.write((size & 127) | 128);
            size >>>= 7;
        }
        output.write(size);
        write(output, value);
        return output.toByteArray();
    }

    private static byte[] concat(byte[] first, byte[] second) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(first.length + second.length);
        write(output, first);
        write(output, second);
        return output.toByteArray();
    }

    private static void write(ByteArrayOutputStream output, byte[] bytes) {
        output.write(bytes, 0, bytes.length);
    }

    private static final class Reader {
        private final byte[] input;
        private final int limit;
        private int position;
        private int start;
        private int number;
        private int wire;
        private int valueStart;

        Reader(byte[] input, int start, int limit) {
            this.input = input;
            this.position = start;
            this.limit = limit;
        }

        boolean next() throws IOException {
            if (position == limit) {
                return false;
            }
            start = position;
            long key = varint();
            if (key < 8 || key > 0xffff_ffffL) {
                throw new IOException("Invalid protobuf field number");
            }
            number = (int) (key >>> 3);
            wire = (int) (key & 7);
            valueStart = position;
            switch (wire) {
                case 0:
                    varint();
                    break;
                case 1:
                    skip(8);
                    break;
                case 2:
                    long size = varint();
                    valueStart = position;
                    skip(size);
                    break;
                case 5:
                    skip(4);
                    break;
                default:
                    throw new IOException("Unsupported protobuf wire type");
            }
            return true;
        }

        Reader message() {
            return new Reader(input, valueStart, position);
        }

        void copyTo(ByteArrayOutputStream output) {
            output.write(input, start, position - start);
        }

        private void skip(long count) throws IOException {
            if (count < 0 || count > limit - position) {
                throw new IOException("Truncated protobuf field");
            }
            position += (int) count;
        }

        private long varint() throws IOException {
            long value = 0;
            for (int shift = 0; shift < 64 && position < limit; shift += 7) {
                int next = input[position++] & 255;
                if (shift == 63 && next > 1) {
                    break;
                }
                value |= (long) (next & 127) << shift;
                if (next < 128) {
                    return value;
                }
            }
            throw new IOException("Invalid protobuf varint");
        }
    }
}
