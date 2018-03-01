/*
 * Copyright 2007 Sun Microsystems, Inc.
 */

package com.sun.voip;

/**
 * This class has static methods to convert between 8-bit ulaw data
 * and 16-bit linear data.
 */
public class AudioConversion {
    public static final int PCMU_SILENCE   = 0x7f;
    public static final int PCM_SILENCE = 0x0;

    /*
     * table for converting from linear to ulaw
     */
    static private byte[] linearToUlawTable;

    static {
    long start = System.currentTimeMillis();

        int BIAS = 0x84;   // add-in bias for 16 bit samples
        int CLIP = 32635;

        int exp_lut[] = {
            0,0,1,1,2,2,2,2,3,3,3,3,3,3,3,3,
            4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,
            5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,
            5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,
            6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,
            6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,
            6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,
            6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,
            7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
            7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
            7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
            7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
            7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
            7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
            7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
            7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7
        };

    linearToUlawTable = new byte[65536];

    for (int i = 0; i < 65536; i++) {
            int sign, exponent, mantissa;
            byte ulawbyte;

        short sample = (short) i;

            /* get the sample into sign-magnitude */
            sign = (sample >> 8) & 0x80;        // set aside the sign

            if (sign != 0) {
            sample = (short)-sample;        // get magnitude
        }

            if (sample > CLIP) {
            sample = (short) CLIP;   	// clip the magnitude
        }

            /* convert from 16 bit linear to ulaw */
            sample = (short)(sample + BIAS);
            exponent = exp_lut[(sample>>7) & 0xFF];
            mantissa = (sample >> (exponent+3)) & 0x0F;
            ulawbyte = (byte)(~(sign | (exponent << 4) | mantissa));

            if (ulawbyte == 0) {
                ulawbyte = 0x02;  	    // optional CCITT trap
        }

        /*
         * For debugging to match files sent and received
         */
        //if (ulawbyte == 0xff) {
        //    ulawbyte = 0x7f;
        //}

        linearToUlawTable[i] = ulawbyte;
    }

    Logger.writeFile("Time to generate l2u table "
        + (System.currentTimeMillis() - start));
    }

    /*
     * Index = ulaw value + 128, entry = signed 16 bit linear value.
     */
    static final private int[] ulawToLinearTable = {
     32635,  31608,  30584,  29560,  28536,  27512,  26488,  25464,
     24440,  23416,  22392,  21368,  20344,  19320,  18296,  17272,
     16248,  15736,  15224,  14712,  14200,  13688,  13176,  12664,
     12152,  11640,  11128,  10616,  10104,   9592,   9080,   8568,
      8056,   7800,   7544,   7288,   7032,   6776,   6520,   6264,
      6008,   5752,   5496,   5240,   4984,   4728,   4472,   4216,
          3960,   3832,   3704,   3576,   3448,   3320,   3192,   3064,
          2936,   2808,   2680,   2552,   2424,   2296,   2168,   2040,
      1912,   1848,   1784,   1720,   1656,   1592,   1528,   1464,
          1400,   1336,   1272,   1208,   1144,   1080,   1016,    952,
           888,    856,    824,    792,    760,    728,    696,    664,
       632,    600,    568,    536,    504,    472,    440,    408,
       376,    360,    344,    328,    312,    296,    280,    264,
       248,    232,    216,    200,    184,    168,    152,    136,
       120,    112,    104,     96,     88,     80,     72,     64,
        56,     48,     40,     32,     24,     16,      8,      0,
        -32760, -31608, -30584, -29560, -28536, -27512, -26488, -25464,
    -24440, -23416, -22392, -21368, -20344, -19320, -18296, -17272,
    -16248, -15736, -15224, -14712, -14200, -13688, -13176, -12664,
    -12152, -11640, -11128, -10616, -10104,  -9592,  -9080,  -8568,
     -8056,  -7800,  -7544,  -7288,  -7032,  -6776,  -6520,  -6264,
     -6008,  -5752,  -5496,  -5240,  -4984,  -4728,  -4472,  -4216,
     -3960,  -3832,  -3704,  -3576,  -3448,  -3320,  -3192,  -3064,
     -2936,  -2808,  -2680,  -2552,  -2424,  -2296,  -2168,  -2040,
         -1912,  -1848,  -1784,  -1720,  -1656,  -1592,  -1528,  -1464,
     -1400,  -1336,  -1272,  -1208,  -1144,  -1080,  -1016,   -952,
      -888,   -856,   -824,   -792,   -760,   -728,   -696,   -664,
      -632,   -600,   -568,   -536,   -504,   -472,   -440,   -408,
      -376,   -360,   -344,   -328,   -312,   -296,   -280,   -264,
      -248,   -232,   -216,   -200,   -184,   -168,   -152,   -136,
      -120,   -112,   -104,    -96,    -88,    -80,    -72,    -64,
       -56,    -48,    -40,    -32,    -24,    -16,    -8,       0
    };

    /**
     * Uninstantiable class.
     */
    private AudioConversion() {}

    /**
     * Convert a ulaw byte array to a int array of linearData.
     *
     * @param ulawData   byte array with ulaw data
     * @param ulawOffset offset from the start of ulawData
     * @param length	 length of ulawData
     * @param linearData int array in which linear data will be placed
     * @param linearOffset offset from the start of linearData
     */
    public static void ulawToLinear(byte[] ulawData, int ulawOffset, int length,
            int[] linearData) {

    int inIx = ulawOffset;

        for (int i = 0; i < length; i++) {
        linearData[i] = ulawToLinearTable[ulawData[inIx] + 128];
        inIx++;
    }
    }

    public static void ulawToLinear(byte[] ulawData, int ulawOffset, int length,
            byte[] linearData) {

    ulawToLinear(ulawData, ulawOffset, length, linearData, 0);
    }

    public static void ulawToLinear(byte[] ulawData, int ulawOffset, int length,
            byte[] linearData, int linearOffset) {

    int inIx = ulawOffset;
    int outIx = linearOffset;

        for (int i = 0; i < length; i++) {
            int s = ulawToLinearTable[ulawData[inIx] + 128];

            linearData[outIx] = (byte) ((s >> 8) & 0xff);
            outIx++;
            linearData[outIx] = (byte) (s & 0xff);
            outIx++;
            inIx++;
    }
    }

    /**
     * Convert a linear byte array to a ulaw byte array starting from offset
     * @param linearData byte array of linear data
     * @param offset	 integer offset from start of the arrays
     * @param ulawData   byte array in which ulaw data will be placed
     */
    public static void linearToUlaw(byte[] linearData,
                                    byte[] ulawData, int ulawOffset) {

    linearToUlaw(linearData, 0, ulawData, ulawOffset);
    }

    public static void linearToUlaw(byte[] linearData, int linearOffset,
                                    byte[] ulawData, int ulawOffset) {

        int outIx = ulawOffset;

        for (int inIx = linearOffset; inIx < linearData.length; inIx += 2) {
            ulawData[outIx++] = linearToUlaw(
        (linearData[inIx] << 8) | (linearData[inIx + 1] & 0xff));
        }
    }

    /**
     * Convert a linear int array to a ulaw byte array starting from offset
     * @param linearData int array of linear data
     * @param offset	 integer offset from start of the arrays
     * @param ulawData   byte array in which ulaw data will be placed
     */
    public static void linearToUlaw(int[] linearData, byte[] ulawData,
    int ulawOffset) {

    int outIx = ulawOffset;

        for (int inIx = 0; inIx < linearData.length; inIx++) {
            ulawData[outIx++] = linearToUlaw(linearData[inIx]);
        }
    }

    /**
     * Convert a linear int to a ulaw byte
     *
     * @param sample int with linear data
     * @return       byte of ulaw data
     */
    public static byte linearToUlaw(int sample) {
    return linearToUlawTable[sample & 0xffff];
    }

    public static void intsToBytes(int[] intData, byte[] byteData,
        int outOffset) {

        int outIx = outOffset;

        for (int i = 0; i < intData.length; i++) {
            byteData[outIx] = (byte) ((intData[i] >> 8) & 0xff);
            byteData[outIx + 1] = (byte) (intData[i] & 0xff);

            outIx += 2;
        }
    }

    public static byte[] littleEndianIntsToBytes(int[] intData) {
        byte[] byteData = new byte[intData.length * 2];

        int outIx = 0;

        for (int i = 0; i < intData.length; i++) {
            byteData[outIx + 1] = (byte) ((intData[i] >> 8) & 0xff);
            byteData[outIx] = (byte) (intData[i] & 0xff);

            outIx += 2;
        }

        return byteData;
    }

    public static byte[] intsToBytes(int[] intData) {
        byte[] byteData = new byte[intData.length * 2];

        int outIx = 0;

        for (int i = 0; i < intData.length; i++) {
            byteData[outIx] = (byte) ((intData[i] >> 8) & 0xff);
            byteData[outIx + 1] = (byte) (intData[i] & 0xff);

            outIx += 2;
        }

        return byteData;
    }

    public static int[] bytesToLittleEndianInts(byte[] byteData) {
        int[] intData = new int[byteData.length / 2];

        int inIx = 0;

        for (int i = 0; i < intData.length; i++) {
            short s = (short) (((byteData[inIx + 1] << 8) & 0xff00) | (byteData[inIx] & 0xff));
            intData[i] = (int) s;
            inIx += 2;
        }

        return intData;
    }

    public static int[] bytesToInts(byte[] byteData) {
    int[] intData = new int[byteData.length / 2];

        int inIx = 0;

        for (int i = 0; i < intData.length; i++) {
            short s = (short) (((byteData[inIx] << 8) & 0xff00) |
                (byteData[inIx + 1] & 0xff));

        intData[i] = (int) s;

            inIx += 2;
        }

    return intData;
    }

    public static void bytesToInts(byte[] byteData, int byteDataOffset,
            int byteDataLength, int[] intData) {

        int inIx = byteDataOffset;

        for (int i = 0; i < intData.length; i++) {
            short s = (short) (((byteData[inIx] << 8) & 0xff00) |
                (byteData[inIx + 1] & 0xff));

        intData[i] = (int) s;

            inIx += 2;
        }
    }


    public static short[] intsToShorts(int[] intData) {
    short[] shortData = new short[intData.length];

    for (int i = 0; i < intData.length; i++) {
        shortData[i] = (short) intData[i];
    }

    return shortData;
    }

    public static int clip(int sample) {
        if (sample > 32767) {
        if (Logger.logLevel == -49) {
            Logger.println("Clip " + sample + " to 0x7fff (32767)");
        }
            return 0x7fff;
        }

        if (sample < -32768) {
        if (Logger.logLevel == -49) {
            Logger.println("Clip " + sample + " to 0xffff8000 (-32768)");
        }
            return 0xffff8000;
        }

        return sample;
    }

    public static void clip(int[] data) {

    for (int i = 0; i < data.length; i++) {
        data[i] = clip(data[i]);
    }
    }

}
