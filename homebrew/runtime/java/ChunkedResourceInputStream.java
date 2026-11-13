/* SPDX-License-Identifier: MIT */
import java.io.IOException;
import java.io.InputStream;

/**
 * A bounded-resource view of one native image. Some S40 implementations expand
 * a complete JAR resource when getResourceAsStream() is called. Keeping each
 * resource small avoids placing the whole native image in the Java heap.
 * Construct and use this only after the forged-array interval is restored.
 */
final class ChunkedResourceInputStream extends InputStream {
    private static final int MAGIC = 0x50433453; // little-endian "S4CP"

    interface ResourceSource {
        InputStream open(String name) throws IOException;
    }

    private static final class ClassResources implements ResourceSource {
        private final Class owner;
        ClassResources(Class owner) {
            this.owner = owner;
        }
        public InputStream open(String name) {
            return owner.getResourceAsStream(name);
        }
    }

    private final ResourceSource source;
    private final String base;
    private final int totalBytes, chunkBytes, chunkCount;
    private InputStream current;
    private int nextChunk, remaining, chunkRemaining;
    private boolean closed;

    ChunkedResourceInputStream(Class owner, String resource) throws IOException {
        this(new ClassResources(owner), resource);
    }

    /* Package-private source injection lets host tests check close ordering
     * and truncation without depending on desktop ClassLoader buffering. */
    ChunkedResourceInputStream(ResourceSource source, String resource) throws IOException {
        if (source == null || resource == null || !resource.endsWith(".parts")
                || resource.length() <= 6)
            throw new IOException("Bad native chunk resource name");
        this.source = source;
        base = resource.substring(0, resource.length() - 6);
        InputStream index = source.open(resource);
        if (index == null)
            throw new IOException("Missing native chunk index");
        int bytes, size, count;
        try {
            if (word(index) != MAGIC || word(index) != 1)
                throw new IOException("Bad native chunk index version");
            bytes = word(index);
            size = word(index);
            count = word(index);
            if (bytes <= 0 || size < 4096 || size > 65536 || (size & (size - 1)) != 0
                    || count != (bytes - 1) / size + 1 || index.read() != -1)
                throw new IOException("Bad native chunk index bounds");
        } finally {
            index.close();
        }
        totalBytes = bytes;
        chunkBytes = size;
        chunkCount = count;
        remaining = totalBytes;
    }

    private static int word(InputStream in) throws IOException {
        int a = in.read(), b = in.read(), c = in.read(), d = in.read();
        if ((a | b | c | d) < 0)
            throw new IOException("Truncated native chunk index");
        return a | (b << 8) | (c << 16) | (d << 24);
    }

    private void finishChunk() throws IOException {
        InputStream old = current;
        current = null;
        if (old == null)
            return;
        try {
            if (old.read() != -1)
                throw new IOException("Trailing native chunk bytes");
        } finally {
            // Drop the only retained resource before opening its successor.
            old.close();
        }
    }

    private boolean ensureChunk() throws IOException {
        if (closed)
            throw new IOException("Native chunk stream closed");
        if (current != null && chunkRemaining == 0)
            finishChunk();
        if (remaining == 0) {
            if (nextChunk != chunkCount)
                throw new IOException("Native chunk count mismatch");
            return false;
        }
        if (current == null) {
            if (nextChunk >= chunkCount)
                throw new IOException("Missing native image bytes");
            current = source.open(base + ".part" + nextChunk);
            if (current == null)
                throw new IOException("Missing native image chunk " + nextChunk);
            nextChunk++;
            chunkRemaining = remaining < chunkBytes ? remaining : chunkBytes;
        }
        return true;
    }

    public int read() throws IOException {
        if (!ensureChunk())
            return -1;
        int value = current.read();
        if (value < 0)
            throw new IOException("Truncated native image chunk");
        chunkRemaining--;
        remaining--;
        return value;
    }

    public int read(byte[] data, int offset, int length) throws IOException {
        if (data == null)
            throw new NullPointerException();
        if (offset < 0 || length < 0 || offset > data.length - length)
            throw new IndexOutOfBoundsException();
        if (closed)
            throw new IOException("Native chunk stream closed");
        if (length == 0)
            return 0;
        if (!ensureChunk())
            return -1;
        int wanted = length < chunkRemaining ? length : chunkRemaining;
        int got = current.read(data, offset, wanted);
        if (got < 0)
            throw new IOException("Truncated native image chunk");
        if (got == 0) {
            // Preserve InputStream progress even for a broken bulk reader.
            int value = current.read();
            if (value < 0)
                throw new IOException("Truncated native image chunk");
            data[offset] = (byte) value;
            got = 1;
        }
        chunkRemaining -= got;
        remaining -= got;
        return got;
    }

    public void close() throws IOException {
        if (closed)
            return;
        closed = true;
        InputStream old = current;
        current = null;
        if (old != null)
            old.close();
    }
}
