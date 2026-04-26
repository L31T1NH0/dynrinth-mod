package com.dynrinth.code;

public class CodeDecoder {

    private static final String SAFE = "23456789ABCDEFGHJKMNPQRSTVWXYZ";

    public static String decodeMcVersion(String code) {
        if (code == null || code.length() != 10) return null;
        code = code.toUpperCase();
        int minorIdx = SAFE.indexOf(code.charAt(1));
        int patchIdx = SAFE.indexOf(code.charAt(2));
        if (minorIdx < 0 || patchIdx < 0) return null;
        return "1." + (minorIdx + 7) + "." + patchIdx;
    }

    public static boolean isValidFormat(String code) {
        if (code == null) return false;
        int len = code.toUpperCase().length();
        return len == 10 || len == 8;
    }
}
