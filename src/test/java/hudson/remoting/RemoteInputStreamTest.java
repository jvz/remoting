package hudson.remoting;

import org.apache.commons.io.input.BrokenInputStream;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.Arrays;

import static hudson.remoting.RemoteInputStream.Flag.*;

/**
 * @author Kohsuke Kawaguchi
 */
public class RemoteInputStreamTest extends RmiTestBase {
    /**
     * Makes sure non-greedy RemoteInputStream is not completely dead on arrival.
     */
    public void testNonGreedy() throws Exception {
        ByteArrayInputStream in = new ByteArrayInputStream("12345678".getBytes());
        channel.call(new Read(new RemoteInputStream(in, NOT_GREEDY),"1234".getBytes()));
        assertTrue(Arrays.equals(readFully(in, 4), "5678".getBytes()));
    }

    /**
     * Makes sure greedy RemoteInputStream is not completely dead on arrival.
     */
    public void testGreedy() throws Exception {
        ByteArrayInputStream in = new ByteArrayInputStream("12345678".getBytes());
        channel.call(new Read(new RemoteInputStream(in, GREEDY),"1234".getBytes()));
        // not very reliable but the intention is to have it greedily read
        Thread.sleep(100);

        assertEquals(in.read(), -1);
    }

    /**
     * Reads N bytes and verify that it matches what's expected.
     */
    private static class Read implements Callable<Object,IOException> {
        private final RemoteInputStream in;
        private final byte[] expected;

        private Read(RemoteInputStream in, byte[] expected) {
            this.in = in;
            this.expected = expected;
        }

        public Object call() throws IOException {
            assertTrue(Arrays.equals(readFully(in,expected.length),expected));
            return null;
        }
    }


    /**
     * Read in multiple chunks.
     */
    public void testGreedy2() throws Exception {
        ByteArrayInputStream in = new ByteArrayInputStream("12345678".getBytes());
        final RemoteInputStream i = new RemoteInputStream(in, GREEDY);

        channel.call(new TestGreedy2(i));
        assertEquals(in.read(),-1);
    }

    private static class TestGreedy2 implements Callable<Void,IOException> {
        private final RemoteInputStream i;

        public TestGreedy2(RemoteInputStream i) {
            this.i = i;
        }

        public Void call() throws IOException {
            assertTrue(Arrays.equals(readFully(i, 4), "1234".getBytes()));
            assertTrue(Arrays.equals(readFully(i, 4), "5678".getBytes()));
            assertEquals(i.read(),-1);
            return null;
        }
    }


    /**
     * Greedy {@link RemoteInputStream} should propagate error.
     */
    public void testErrorPropagation() throws Exception {
        for (RemoteInputStream.Flag f : Arrays.asList(GREEDY,NOT_GREEDY)) {
            InputStream in = new SequenceInputStream(
                    new ByteArrayInputStream("1234".getBytes()),
                    new BrokenInputStream(new SkyIsFalling())
            );
            final RemoteInputStream i = new RemoteInputStream(in, f);

            channel.call(new TestErrorPropagation(i));
        }
    }

    private static class SkyIsFalling extends IOException {}

    private static class TestErrorPropagation implements Callable<Void, IOException> {
        private final RemoteInputStream i;

        public TestErrorPropagation(RemoteInputStream i) {
            this.i = i;
        }

        public Void call() throws IOException {
            assertTrue(Arrays.equals(readFully(i, 4), "1234".getBytes()));
            try {
                i.read();
                throw new AssertionError();
            } catch (SkyIsFalling e) {
                // non-greedy implementation rethrows the same exception, which produces confusing stack trace,
                // but in case someone is using it as a signal I'm not changing that behaviour.
                return null;
            } catch (IOException e) {
                if (e.getCause() instanceof SkyIsFalling)
                    return null;
                throw e;
            }
        }
    }


    private static byte[] readFully(InputStream in, int n) throws IOException {
        byte[] actual = new byte[n];
        new DataInputStream(in).readFully(actual);
        return actual;
    }
}