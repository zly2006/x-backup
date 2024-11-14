package com.github.zly2006.xbackup.multi;

public interface IChunkHolder {
    int x();
    int z();

    /**
     * Entities, block entities, POI
     *
     * record tick, if tick mismatch, crash
     */
    void unloadEntities();
    void loadRegion();
    void loadEntities();
}
