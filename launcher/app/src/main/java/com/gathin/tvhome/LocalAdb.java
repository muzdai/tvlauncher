package com.gathin.tvhome;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;

/**
 * tvlauncher fork: a minimal ADB client that talks to this TV's own adbd on 127.0.0.1:5555, for the tile menu's
 * Close (tile_menu_close). A launcher cannot force-stop another app itself (FORCE_STOP_PACKAGES is for system
 * apps), but the ADB shell can, once the owner has allowed this launcher's key on the TV's "Allow USB debugging?"
 * prompt. It only works while USB/network debugging stays on.
 *
 * The key pair lives in the launcher's private files (adbkey, adbkey.pub). Only "am force-stop" and "pidof" for a
 * package name are ever sent.
 */
final class LocalAdb {
    private static final String TAG = "LocalAdb";
    private static final int CNXN = 0x4e584e43, AUTH = 0x48545541, OPEN = 0x4e45504f, OKAY = 0x59414b4f,
            CLSE = 0x45534c43, WRTE = 0x45545257;
    private static final int VERSION = 0x01000001, MAX_DATA = 256 * 1024;
    private static final int AUTH_TOKEN = 1, AUTH_SIGNATURE = 2, AUTH_RSAPUBLICKEY = 3;
    private static final int PORT = 5555;
    /** How long the owner has to answer the TV's "Allow USB debugging?" prompt for this launcher's key. */
    private static final int APPROVAL_TIMEOUT_MS = 60_000;
    private static final byte[] SHA1_DIGEST_INFO = {0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02,
            0x1a, 0x05, 0x00, 0x04, 0x14};

    enum Result { STOPPED, STILL_RUNNING, NO_ADB, NOT_ALLOWED, FAILED }

    /** A trusted key pushed through ThemeProvider (PKCS#8 DER private key); imported once, then deleted. */
    static final String IMPORT_NAME = "adbkey.import";

    private LocalAdb() { }

    /** Force-stops {@code pkg} through the TV's own ADB. Blocking: call it off the main thread. */
    static Result forceStop(Context ctx, String pkg) {
        if (!pkg.matches("[A-Za-z0-9_.]+")) return Result.FAILED;
        try (Socket socket = new Socket()) {
            try {
                socket.connect(new InetSocketAddress("127.0.0.1", PORT), 2000);
            } catch (ConnectException e) {
                return Result.NO_ADB;
            }
            socket.setSoTimeout(10_000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream();
            KeyPair keys = keys(ctx);
            send(out, CNXN, VERSION, MAX_DATA, "host::\0".getBytes(StandardCharsets.US_ASCII));
            boolean sentSignature = false;
            while (true) {
                Message m = read(in);
                if (m.command == CNXN) break;
                if (m.command != AUTH || m.arg0 != AUTH_TOKEN) return Result.FAILED;
                if (!sentSignature) {
                    send(out, AUTH, AUTH_SIGNATURE, 0, sign((RSAPrivateKey) keys.getPrivate(), m.data));
                    sentSignature = true;
                } else {
                    // Not known yet: offer the public key, which brings up the TV's prompt.
                    send(out, AUTH, AUTH_RSAPUBLICKEY, 0, publicKey((RSAPublicKey) keys.getPublic()));
                    socket.setSoTimeout(APPROVAL_TIMEOUT_MS);
                    Message reply;
                    try {
                        reply = read(in);
                    } catch (SocketTimeoutException e) {
                        return Result.NOT_ALLOWED;
                    }
                    if (reply.command != CNXN) return Result.NOT_ALLOWED;
                    socket.setSoTimeout(10_000);
                    break;
                }
            }
            String output = shell(in, out, "am force-stop " + pkg + "; pidof " + pkg + " || echo gone");
            return output.contains("gone") ? Result.STOPPED : Result.STILL_RUNNING;
        } catch (SocketTimeoutException e) {
            Log.w(TAG, "timeout");
            return Result.FAILED;
        } catch (Exception e) {
            Log.w(TAG, "adb: " + e);
            return Result.FAILED;
        }
    }

    private static String shell(DataInputStream in, OutputStream out, String command) throws IOException {
        int local = 1;
        send(out, OPEN, local, 0, ("shell:" + command + "\0").getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream text = new ByteArrayOutputStream();
        int remote = 0;
        while (true) {
            Message m = read(in);
            if (m.command == OKAY) {
                remote = m.arg0;
            } else if (m.command == WRTE) {
                text.write(m.data);
                send(out, OKAY, local, m.arg0, new byte[0]);
            } else if (m.command == CLSE) {
                send(out, CLSE, local, remote, new byte[0]);
                break;
            }
        }
        return text.toString(StandardCharsets.UTF_8.name());
    }

    // ---- wire format --------------------------------------------------------------------------------

    private static final class Message {
        int command, arg0, arg1;
        byte[] data;
    }

    private static void send(OutputStream out, int command, int arg0, int arg1, byte[] data) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(24 + data.length).order(ByteOrder.LITTLE_ENDIAN);
        int sum = 0;
        for (byte x : data) sum += x & 0xff;
        b.putInt(command).putInt(arg0).putInt(arg1).putInt(data.length).putInt(sum).putInt(~command).put(data);
        out.write(b.array());
        out.flush();
    }

    private static Message read(DataInputStream in) throws IOException {
        byte[] header = new byte[24];
        in.readFully(header);
        ByteBuffer b = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        Message m = new Message();
        m.command = b.getInt();
        m.arg0 = b.getInt();
        m.arg1 = b.getInt();
        int length = b.getInt();
        if (length < 0 || length > MAX_DATA) throw new IOException("bad length " + length);
        m.data = new byte[length];
        in.readFully(m.data);
        return m;
    }

    // ---- keys ---------------------------------------------------------------------------------------

    private static synchronized KeyPair keys(Context ctx) throws Exception {
        File priv = new File(ctx.getFilesDir(), "adbkey"), pub = new File(ctx.getFilesDir(), "adbkey.pub");
        KeyFactory kf = KeyFactory.getInstance("RSA");
        File dir = Config.dir(ctx);
        File imported = dir == null ? null : new File(dir, IMPORT_NAME);
        if (imported != null && imported.isFile()) {
            try {
                java.security.interfaces.RSAPrivateCrtKey key = (java.security.interfaces.RSAPrivateCrtKey)
                        kf.generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(imported.toPath())));
                java.security.PublicKey publicKey = kf.generatePublic(
                        new java.security.spec.RSAPublicKeySpec(key.getModulus(), key.getPublicExponent()));
                try (FileOutputStream o = new FileOutputStream(priv)) { o.write(key.getEncoded()); }
                try (FileOutputStream o = new FileOutputStream(pub)) { o.write(publicKey.getEncoded()); }
            } finally {
                if (!imported.delete()) Log.w(TAG, "could not delete " + IMPORT_NAME);
            }
        }
        if (priv.isFile() && pub.isFile()) {
            return new KeyPair(kf.generatePublic(new X509EncodedKeySpec(Files.readAllBytes(pub.toPath()))),
                    kf.generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(priv.toPath()))));
        }
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair pair = g.generateKeyPair();
        try (FileOutputStream o = new FileOutputStream(priv)) { o.write(pair.getPrivate().getEncoded()); }
        try (FileOutputStream o = new FileOutputStream(pub)) { o.write(pair.getPublic().getEncoded()); }
        return pair;
    }

    /** adbd's AUTH signature: PKCS#1 v1.5 over the 20-byte token taken as a SHA-1 digest. */
    private static byte[] sign(RSAPrivateKey key, byte[] token) throws Exception {
        int size = (key.getModulus().bitLength() + 7) / 8;
        byte[] block = new byte[size];
        int t = size - SHA1_DIGEST_INFO.length - token.length;
        block[0] = 0;
        block[1] = 1;
        for (int i = 2; i < t - 1; i++) block[i] = (byte) 0xff;
        block[t - 1] = 0;
        System.arraycopy(SHA1_DIGEST_INFO, 0, block, t, SHA1_DIGEST_INFO.length);
        System.arraycopy(token, 0, block, t + SHA1_DIGEST_INFO.length, token.length);
        Cipher c = Cipher.getInstance("RSA/ECB/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key);
        return c.doFinal(block);
    }

    /** The public key in adbd's format: Android's RSAPublicKey struct, base64, then a name and a NUL. */
    private static byte[] publicKey(RSAPublicKey key) {
        BigInteger r32 = BigInteger.ONE.shiftLeft(32);
        BigInteger n = key.getModulus();
        int words = 2048 / 32;
        BigInteger rr = BigInteger.ONE.shiftLeft(4096).mod(n);
        BigInteger n0inv = r32.subtract(n.mod(r32).modInverse(r32)).mod(r32);
        ByteBuffer b = ByteBuffer.allocate(4 + 4 + words * 8 + 4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(words).putInt(n0inv.intValue());
        BigInteger x = n;
        for (int i = 0; i < words; i++) { b.putInt(x.mod(r32).intValue()); x = x.shiftRight(32); }
        x = rr;
        for (int i = 0; i < words; i++) { b.putInt(x.mod(r32).intValue()); x = x.shiftRight(32); }
        b.putInt(key.getPublicExponent().intValue());
        String text = Base64.encodeToString(b.array(), Base64.NO_WRAP) + " tvlauncher@tv\0";
        return text.getBytes(StandardCharsets.US_ASCII);
    }
}
