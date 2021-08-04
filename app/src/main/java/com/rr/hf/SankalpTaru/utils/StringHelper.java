package com.rr.hf.SankalpTaru.utils;

public class StringHelper {

    public static byte[] stringToBytes(String str, int radix) {
        if (str.length() > radix)
            str = str.substring(0, radix-1);

        byte[] finalData = new byte[radix];
        for (int i = 0; i < str.length(); i++) {
            finalData[i] = (byte) str.charAt(i);
        }
        return finalData;
    }


}
