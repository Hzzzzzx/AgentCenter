package com.agentcenter.bridge.infrastructure.id;

import java.security.SecureRandom;
import java.util.Random;

import org.springframework.stereotype.Component;

@Component
public class UlidIdGenerator implements IdGenerator {

    private static final char[] ENCODING = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int TIMESTAMP_LEN = 10;
    private static final int RANDOMNESS_LEN = 16;
    private final Random random = new SecureRandom();

    @Override
    public String nextId() {
        long timestamp = System.currentTimeMillis();
        char[] chars = new char[TIMESTAMP_LEN + RANDOMNESS_LEN];

        for (int i = TIMESTAMP_LEN - 1; i >= 0; i--) {
            chars[i] = ENCODING[(int) (timestamp & 0x1F)];
            timestamp >>>= 5;
        }

        byte[] entropy = new byte[RANDOMNESS_LEN];
        random.nextBytes(entropy);
        for (int i = 0; i < RANDOMNESS_LEN; i++) {
            chars[TIMESTAMP_LEN + i] = ENCODING[entropy[i] & 0x1F];
        }

        return new String(chars);
    }
}
