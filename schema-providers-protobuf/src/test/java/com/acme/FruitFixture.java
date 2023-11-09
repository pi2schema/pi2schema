package com.acme;

import com.acme.FruitOuterClass.Fruit;

public class FruitFixture {

    public static Fruit.Builder waterMelon() {
        return Fruit.newBuilder().setName("Watermelon").setSeedless(true).setFamily("Cucurbitaceae");
    }
}
