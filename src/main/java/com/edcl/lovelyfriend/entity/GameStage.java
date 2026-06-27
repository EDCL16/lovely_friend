package com.edcl.lovelyfriend.entity;

public enum GameStage {
    WOOD,
    STONE,
    IRON,
    DIAMOND,
    NETHER,
    STRONGHOLD,
    END,
    HOME_BUILDING,
    POST_GAME;

    public GameStage next() {
        GameStage[] values = values();
        int i = ordinal() + 1;
        return i < values.length ? values[i] : POST_GAME;
    }
}
