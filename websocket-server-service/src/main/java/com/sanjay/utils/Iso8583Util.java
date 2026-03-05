package com.sanjay.utils;

import java.nio.ByteBuffer;

public class Iso8583Util {

    // Build a balance enquiry request: MTI(4) + Company(20) + Currency(3)
    public static byte[] buildRequest(String company) {
        ByteBuffer buf = ByteBuffer.allocate(27);
        buf.putInt(0x0200);                          // MTI
        buf.put(padRight(company, 20));              // DE41 Terminal/Company
        buf.put("840".getBytes());                   // DE49 Currency (USD)
        return buf.array();
    }

    // Build a balance enquiry response: MTI(4) + RC(2) + Amount(8)
    public static byte[] buildResponse(String rc, long balanceCents) {
        ByteBuffer buf = ByteBuffer.allocate(14);
        buf.putInt(0x0210);                          // MTI
        buf.put(padRight(rc, 2));                    // DE39 Response Code
        buf.putLong(balanceCents);                   // DE54 Amount in cents
        return buf.array();
    }

    // Parse response — returns "Balance=10.00 USD" or "Declined RC=25"
    public static String parseResponse(byte[] raw) {
        ByteBuffer buf = ByteBuffer.wrap(raw);
        int mti = buf.getInt();
        String rc = new String(new byte[]{buf.get(), buf.get()}).trim();
        long cents = buf.getLong();
        if ("00".equals(rc))
            return String.format("Balance=%.2f USD", cents / 100.0);
        return "Declined RC=" + rc;
    }

    private static byte[] padRight(String s, int len) {
        return String.format("%-" + len + "s", s).substring(0, len).getBytes();
    }
}
