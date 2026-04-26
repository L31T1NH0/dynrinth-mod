package com.dynrinth.code;

/**
 * Decodes Dynrinth mod list codes.
 *
 * Format: [V(1)][MINOR(1)][PATCH(1)][MAIN(6)][AUTH(1)] = 10 chars
 *
 * SAFE alphabet: "23456789ABCDEFGHJKMNPQRSTVWXYZ" (30 chars, no 0/O/1/I/L)
 *   MINOR = SAFE[minor - 7]  →  1.7='2', 1.20='F', 1.21='G'
 *   PATCH = SAFE[patch]      →  .0='2', .4='6', .11='D'
 *
 * This mirrors the TypeScript implementation in lib/codes.ts.
 */
public class CodeDecoder {

    private static final String SAFE = "23456789ABCDEFGHJKMNPQRSTVWXYZ";

    /** Returns the Minecraft version embedded in a 10-char code, e.g. "1.21.11".
     *  Returns null for 8-char legacy codes (version unknown). */
    public static String decodeMcVersion(String code) {
        if (code == null || code.length() != 10) return null;
        code = code.toUpperCase();
        int minorIdx = SAFE.indexOf(code.charAt(1));
        int patchIdx = SAFE.indexOf(code.charAt(2));
        if (minorIdx < 0 || patchIdx < 0) return null;
        return "1." + (minorIdx + 7) + "." + patchIdx;
    }

    /** Basic length check — full checksum validation happens server-side. */
    public static boolean isValidFormat(String code) {
        if (code == null) return false;
        int len = code.toUpperCase().length();
        return len == 10 || len == 8;
    }
}
