package com.github.games647.craftapi.model.skin;

import java.net.URL;

public class ChangeSkin {

    private final Model variant;
    private final URL url;

    public ChangeSkin(Model variant, URL url) {
        this.variant = variant;
        this.url = url;
    }
}
