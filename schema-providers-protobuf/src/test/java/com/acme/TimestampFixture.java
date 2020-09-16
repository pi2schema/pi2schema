package com.acme;

import com.google.protobuf.Timestamp;

import java.time.Instant;

public class TimestampFixture {

    public static Timestamp.Builder now() {
        return Timestamp.newBuilder()
                .setSeconds(
                        Instant.now().getEpochSecond()
                );
    }


}
