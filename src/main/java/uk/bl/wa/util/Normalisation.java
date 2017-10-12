package uk.bl.wa.util;
/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 */

import org.apache.commons.httpclient.URIException;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.Log;
import org.archive.wayback.util.url.AggressiveUrlCanonicalizer;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.regex.Pattern;

/**
 * String- and URL-normalisation helper class.
 *
 * TODO: It seems that https://github.com/iipc/urlcanon is a much better base for normalisation.
 * That should be incorporated here instead of the AggressiveUrlCanonicalizer and the custom code.
 */
public class Normalisation {
    private static Charset UTF8_CHARSET = Charset.forName("UTF-8");
    private static AggressiveUrlCanonicalizer canon = new AggressiveUrlCanonicalizer();

    public static String canonicaliseHost(String host) throws URIException {
        return canon.urlStringToKey(host).replace("/", "");
    }

    /**
     * Default and very aggressive normaliser. Shorthand for {@code canonicaliseURL(url, true, true)}.
     */
    public static String canonicaliseURL(String url) {
        return canonicaliseURL(url, true, true);
    }

    /**
     * Multi-step URL canonicalization. Besides using the {@link AggressiveUrlCanonicalizer} from wayback.org it
     * normalises https → http,
     * removes trailing slashes (except when the url is to domain-level),
     * fixed %-escape errors
     * Optionally normalises %-escapes.
     * @param allowHighOrder if true, high-order Unicode (> code point 127) are represented without escaping.
     *                       This is technically problematic as URLs should be plain ASCII, but most tools handles
     *                       them fine and they are easier to read.
     * @param createUnambiguous if true, all non-essential %-escapes are normalised to their escaping character.
     *                          e.g. http://example.com/%2A.html → http://example.com/*.html
     *                          If false, valid %-escapes are kept as-is.
     */
    public static String canonicaliseURL(String url, boolean allowHighOrder, boolean createUnambiguous) {
        // Basic normalisation, as shared with Heritrix, Wayback et al
        url = canon.canonicalize(url);

        // Protocol: https → http
        url = url.startsWith("https://") ? "http://" + url.substring(8) : url;

        // TODO: Consider if this should only be done if createUnambiguous == true
        // Trailing slashes: http://example.com/foo/ → http://example.com/foo
        while (url.endsWith("/")) { // Trailing slash affects the URL semantics
            url = url.substring(0, url.length() - 1);
        }

        // If the link is domain-only (http://example.com), is _must_ end with slash
        if (DOMAIN_ONLY.matcher(url).matches()) {
            url += "/";
        }

        // Create temporary url with %-fixing and high-order characters represented directly
        byte[] urlBytes = fixEscapeErrorsAndUnescapeHighOrderUTF8(url);
        // Normalise


        // Hex escapes, including faulty hex escape handling:
        // http://example.com/all%2A rosé 10%.html → http://example.com/all*%20rosé%2010%25.html or
        // http://example.com/all%2A rosé 10%.html → http://example.com/all*%20ros%C3%A9%2010%25.html if produceValidURL
        url = escapeUTF8(urlBytes, !allowHighOrder, createUnambiguous);

        return url;
    }
    private static Pattern DOMAIN_ONLY = Pattern.compile("https?://[^/]+");

    // Normalisation to UTF-8 form
    private static byte[] fixEscapeErrorsAndUnescapeHighOrderUTF8(final String url) {
        ByteArrayOutputStream sb = new ByteArrayOutputStream(url.length()*2);
        final byte[] utf8 = url.getBytes(UTF8_CHARSET);
        int i = 0;
        while (i < utf8.length) {
            int c = utf8[i];
            if (c == '%') {
                if (i < utf8.length-2 && isHex(utf8[i+1]) && isHex(utf8[i+2])) {
                    int u = Integer.parseInt("" + (char)utf8[i+1] + (char)utf8[i+2], 16);
                    if ((0b10000000 & u) == 0) { // ASCII, so don't touch!
                        sb.write('%'); sb.write(utf8[i+1]); sb.write(utf8[i+2]);
                    } else { // UTF-8, so write raw byte
                        sb.write(0xFF & u);
                    }
                    i += 3;
                } else { // Faulty, so fix by escaping percent
                    sb.write('%'); sb.write('2'); sb.write('5');
                    i++;
                }
                // https://en.wikipedia.org/wiki/UTF-8
            } else { // Not part of escape, just pass the byte
                sb.write(0xff & utf8[i++]);
            }
        }
        return sb.toByteArray();
    }

    // Requires valid %-escapes (as produced by fixEscapeErrorsAndUnescapeHighOrderUTF8) and UTF-8 bytes
    private static String escapeUTF8(final byte[] utf8, boolean escapeHighOrder, boolean normaliseLowOrder) {
        ByteArrayOutputStream sb = new ByteArrayOutputStream(utf8.length*2);
        int i = 0;
        while (i < utf8.length) {
            int c = utf8[i];
            if (c == '%') {
                int codePoint = Integer.parseInt("" + (char) utf8[i + 1] + (char) utf8[i + 2], 16);
                if (mustEscape(codePoint) || !normaliseLowOrder) { // Pass on unmodified
                    hexEscape(codePoint, sb);
                } else { // Normalise to ASCII
                    sb.write(0xFF & codePoint);
                }
                i += 2;
            } else if ((0b10000000 & c) == 0) { // ASCII
                if (mustEscape(c)) {
                    hexEscape(c, sb);
                } else {
                    sb.write(0xFF & c);
                }
            } else if ((0b11100000 & c) == 0b11000000) { // 2 byte UTF-8
                if (escapeHighOrder) {
                    hexEscape(0xff & utf8[i++], sb);
                    hexEscape(0xff & utf8[i], sb);
                } else {
                    sb.write(utf8[i++]);
                    sb.write(utf8[i]);
                }
            } else if ((0b11110000 & utf8[i]) == 0b11100000) { // 3 byte UTF-8
                hexEscape(0xff & utf8[i++], sb);
                hexEscape(0xff & utf8[i++], sb);
                hexEscape(0xff & utf8[i], sb);
            } else if ((0b11111000 & utf8[i]) == 0b11110000) { // 4 byte UTF-8
                hexEscape(0xff & utf8[i++], sb);
                hexEscape(0xff & utf8[i++], sb);
                hexEscape(0xff & utf8[i++], sb);
                hexEscape(0xff & utf8[i], sb);
            } else {
                // TODO: Make a hex-dump of the first x bytes for a better error message
                throw new IllegalArgumentException("The input byte-array did not translate to supported UTF-8");
            }
            i++;
        }
        try {
            return sb.toString("utf-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("Internal error: UTF-8 must be supported by the JVM", e);
        }
    }

    private static void hexEscape(int codePoint, ByteArrayOutputStream sb) {
        sb.write('%');
        sb.write(HEX[codePoint >> 4]);
        sb.write(HEX[codePoint & 0xF]);
    }
    private final static byte[] HEX = "0123456789abcdef".getBytes(UTF8_CHARSET); // Assuming lowercase

    // Some low-order characters must always be escaped
    private static boolean mustEscape(int codePoint) {
        return codePoint == ' ' || codePoint == '%';
    }

    private static boolean isHex(byte b) {
        return (b >= '0' && b <= '9') || (b >= 'a' && b <= 'f') || (b >= 'A' && b <= 'F');
    }


}
