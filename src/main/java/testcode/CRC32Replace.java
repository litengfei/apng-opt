package testcode;

import java.util.HashSet;
import java.util.Random;
import java.util.zip.CRC32;

/**
 * @author ltf
 * @since 16/11/30, 上午9:47
 */
public class CRC32Replace implements Runnable {

    private CRC32 crc32 = new CRC32();
    private Random random = new Random();

    private static byte[] data = new byte[1024*1024];

    static {
        data[2] = 'A';
        data[3] = 'T';
    }


    public void run() {
        genXorCodeTable();
    }

    private void genXorCodeTable() {
        HashSet<Integer> codes = new HashSet<>();
        for (int i = 0; i < data.length - 4; i++) {
            int x = getXorCodeForBodySize(i);
            System.out.println(String.format("%d: %H  - %B", i, x, codes.contains(x)));
            codes.add(x);
        }
    }

    private int getXorCodeForBodySize(int bodySize) {
        int code = getXorCode(bodySize + 4);
        for (int i = 0; i < 10; i++) { // test times
            if (code != getXorCode(bodySize + 4))
                throw new IllegalStateException("Not same XorCode for bodySize: " + bodySize);
        }
        return code;
    }

    private int getXorCode(int len) {
        fillBody(len);
        IDATHeader();
        int crcID = getCrc(data, len);
        fdATHeader();
        int crcfd = getCrc(data, len);
        return crcID ^ crcfd;
    }

    private void fdATHeader() {
        data[0] = 'f';
        data[1] = 'd';
    }

    private void IDATHeader() {
        data[0] = 'I';
        data[1] = 'D';
    }

    private void fillBody(int len) {
        for (int i = 4; i < (len < data.length ? len : data.length); i++) {
            data[i] = (byte) random.nextInt();
        }
    }

    private int getCrc(byte[] data, int len) {
        crc32.reset();
        crc32.update(data, 0, len);
        return (int) crc32.getValue();
    }
}
