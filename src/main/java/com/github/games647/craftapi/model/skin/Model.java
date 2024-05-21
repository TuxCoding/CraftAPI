package com.github.games647.craftapi.model.skin;

import com.google.gson.annotations.SerializedName;

/**
 * Skin texture models
 */
public enum Model {

    /**
     * Default model with square arms (Steve style) also known as classic
     */
    SQUARE("Steve"),

    /**
     * Slim model with slim arms (Alex style)
     */
    //force lowercase
    @SerializedName("slim")
    SLIM("Alex");

    private final String name;

    Model(String name) {
        this.name = name;
    }

    /**
     * @return skin model representation names like Steve for square arms and Alex for slim arms
     */
    public String getName() {
        return name;
    }
}
